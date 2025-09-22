package com.dev.trackify_backend.status;

import com.dev.trackify_backend.dto.response.stomp.RespStompLeaveDto;
import com.dev.trackify_backend.dto.response.stomp.RespStompUserDto;
import com.dev.trackify_backend.event.UserLeaveEvent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 역할 
 * 업서트(접속/재접속): 클라이언트가 연결되면 존재 여부에 따라 새 Presence를 생성하거나 갱신
 * 위치 업데이트 + 방송 스로틀링: 시간/거리 기준으로 의미가 있을 때만 브로드캐스트
 * 작업 상태 토글: working on/off 변경 시 주변 사용자에게 알림
 * PING/RTT 기록: 네트워크 지연 측정값을 Presence에 저장
 * 강제 퇴장 처리: disconnect시 저장소 삭제 + 이벤트 발행 + 타 유저에게 LEAVE 알림
 * 스냅샷 제공(REST): "본인"을 제외한 전체 현재 인원 목록 반환
 * 주기 이동(DEMO): 샘플 클라이언트들 좌표를 1초마다 조금식 이동시켜 화면에 움직임 재현
 * 오프라인 스윕: 30초 이상 무응답 클라이언트 강제 오프라인 처리 + 이벤트 발행
 * */
@Slf4j
@Service
@EnableScheduling // @Scheduled 메서드들 주기 실행
public class PresenceStatus {

    // 특정 사용자 큐로 STOMP 메세지 전송(convertAndSendToUser)
    @Autowired
    private SimpMessagingTemplate broker;

    // 현재 연결 중인 STOMP 사용자 목록/카운트 조회
    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private PresenceStore store;

    // 도메인 이벤트 발행(퇴장 기록)
    @Autowired
    private ApplicationEventPublisher publisher;

    // 현재 상태의 단일
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @Builder
    public static class Presence {
        private String userCode;   // 사용자 식별자
        private String userName;   // 표시 이름
        private double lat;        // 위도
        private double lng;        // 경도
        private boolean working;   // 작업 중 여부(배달 중)
        private long lastMsgAt;    // 마지막 메시지/핑 시각(ms)
        private long lastPingRtt;  // 최근 RTT(ms)

        // 브로드캐스트 스로틀 메타: 최근에 방송한 시점/위치 저장 > 시간/거리 조건을 동시에 만족할 때만 재방송
        @Builder.Default
        private long lastBroadcastAt = 0L;
        private Double lastLat;    // 마지막 방송한 위도
        private Double lastLng;    // 마지막 방송한 경도
    }

    // [DEMO] 이동 파라미터
    private final Map<String, Double> sampleBearingDeg = new ConcurrentHashMap<>();
    private final Map<String, Double> sampleSpeedMps  = new ConcurrentHashMap<>();

    // 튜닝 파라미터
    private static final long OFFLINE_AFTER  = 30_000; // 30초 이상 응답 없으면 오프라인 처리(오프라인 판정)
    private static final long MIN_BCAST_INTERVAL_MS = 800; // 최소 방송 간격(ms)
    private static final double MIN_BCAST_DISTANCE_M = 5.0; // 최소 이동 거리(m)

    // 두 좌표 간의 거리(m)
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0; // 지구 반경 사용

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);

        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // [DEMO] (lat,lng)에서 bearing(방위각) 방향으로 meters만큼 진행된 새 좌표 계산 > 샘플 유저 이동 시 사용
    private static double[] moveFrom(double lat, double lng, double meters, double bearingDeg) {
        final double R = 6_371_000.0; // m

        double brng = Math.toRadians(bearingDeg);
        double lat1 = Math.toRadians(lat);
        double lon1 = Math.toRadians(lng);
        double dr = meters / R;
        
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(dr) +
                Math.cos(lat1) * Math.sin(dr) * Math.cos(brng));
        double lon2 = lon1 + Math.atan2(
                Math.sin(brng) * Math.sin(dr) * Math.cos(lat1),
                Math.cos(dr) - Math.sin(lat1) * Math.sin(lat2));

        return new double[]{ Math.toDegrees(lat2), Math.toDegrees(lon2) };
    }
    
    // 브로드캐스트 유틸
    // - 현재 연결된 모든 STOMP 사용자에게 전송하되, 본인(excludeUserCode)은 제외
    // - 각 사용자 /user/queue/events
    private void sendToOthers(String excludeUserCode, Object payload) {
        for (SimpUser su : simpUserRegistry.getUsers()) {
            String name = su.getName();            // Principal.getName()
            if (name.equals(excludeUserCode)) continue;
            // 모든 세션으로 전송 (특정 세션으로만 보내려면 sessionId 헤더 활용)
            broker.convertAndSendToUser(su.getName(), "/queue/events", payload);
        }
    }

    // 접속/재접속 업서트
    // - 없으면 Presence 생성 후 추가, 있으면 좌표/시각 갱신 > 다른 클라이언트에게 접속/갱신 알림
    public Presence upsertOnConnect(String userCode, String userName, double lat, double lng) {
        long now = System.currentTimeMillis();
        Presence cur = store.get(userCode);

        if (cur == null) {
            cur = Presence.builder()
                    .userCode(userCode)
                    .userName(userName)
                    .lat(lat).lng(lng)
                    .working(false)
                    .lastMsgAt(now)
                    .lastPingRtt(0)
                    .build();
        } else {
            cur.setLat(lat);
            cur.setLng(lng);
            cur.setLastMsgAt(now);
            if (cur.getUserName() == null || cur.getUserName().isBlank()) {
                cur.setUserName(userName);
            }
        }

        store.save(cur);
        sendToOthers(userCode, RespStompUserDto.from(cur));

        return cur;
    }

    // 위치 업데이트 + 스토틀링
    public Presence updateLocation(String userCode, double lat, double lng) {
        Presence p = store.get(userCode);
        if (p == null) return null;

        long now = System.currentTimeMillis();

        p.setLat(lat);
        p.setLng(lng);
        p.setLastMsgAt(now);

        boolean timeOk = (now - p.getLastBroadcastAt()) >= MIN_BCAST_INTERVAL_MS;
        boolean distOk = true;
        if (p.getLastLat() != null && p.getLastLng() != null) {
            distOk = haversineMeters(p.getLastLat(), p.getLastLng(), lat, lng) >= MIN_BCAST_DISTANCE_M;
        }
        if (timeOk && distOk) {
            p.setLastBroadcastAt(now);
            p.setLastLat(lat);
            p.setLastLng(lng);
            store.save(p); // redis에 저장
            sendToOthers(userCode, RespStompUserDto.from(p));
        } else {
            store.save(p); // 위치/시각 갱신만 저장
        }

        return p;
    }

    // 작업 상태 토글
    // - UI 토글을 즉시 영속/전파
    // - lastMsgAt도 갱신해서 타임아웃 오탐 방지
    public Presence setWorking(String userCode, boolean working) {
        Presence p = store.get(userCode);
        if (p == null) return null;

        p.setWorking(working);
        p.setLastMsgAt(System.currentTimeMillis());

        store.save(p);
        sendToOthers(userCode, RespStompUserDto.from(p));

        return p;
    }

    // PING/RTT 기록
    // - ClientSendTs: 클라이언트가 보낸 송신 시각(왕복 시간 근사로 RTT 추정)
    public Presence onPing(String userCode, long clientSendTs) {
        Presence p = store.get(userCode);
        if (p == null) return null;

        long now = System.currentTimeMillis();

        p.setLastMsgAt(now);
        p.setLastPingRtt(Math.max(0, now - clientSendTs)); // 음수 방지

        store.save(p);

        return p;
    }

    // 강제 제거-로그아웃(LEAVE + 이벤트)
    public void disconnect(String userCode, Double cachedLat, Double cachedLng) {
        Presence removed = store.get(userCode);
        store.delete(userCode); // 저장소에서 즉시 삭제 > 스냅샷/브로드캐스트에서 제외
        
        // 마지막 위치 저장을 위한  좌표 정보 추출
        Double lat = cachedLat, lng = cachedLng;
        if ((lat == null || lng == null) && removed != null) {
            lat = removed.getLat(); lng = removed.getLng();
        }

        // 다른 클라언트들에게 퇴장 알림(프론트에서 map에서 제거)
        sendToOthers(userCode, RespStompLeaveDto.builder()
                .userCode(userCode)
                .respTime(Instant.now())
                .build());

        // 이벤트 발행 (DB 저장은 StompService가 처리)
        publisher.publishEvent(new UserLeaveEvent(userCode, lat, lng, Instant.now(), "DISCONNECT"));
    }

    // 스냅샷(REST 진입 시)
    // - "본인"을 제외한 현재 인원 목록
    // - 앱 진입/재연결 시 초기 렌더에 사용
    public List<RespStompUserDto> snapshot(String userCode) {
        return store.findAll().stream()
                .filter(p -> !p.getUserCode().equals(userCode))
                .map(RespStompUserDto::from)
                .toList();
    }

    // [DEMO] 초기화/이동
    // - 서버 기동 직후 샘플 클라이언트 생성
    @PostConstruct
    public void initSamples() {
        upsertOnConnect("user001", "user1", 37.5665, 126.9780); // 서울
        upsertOnConnect("user002", "user2",35.1796, 129.0756); // 부산
        upsertOnConnect("user003", "user3",35.8714, 128.6014); // 대구

        sampleSpeedMps.put("user001", 8.0);
        sampleSpeedMps.put("user002", 9.0);
        sampleSpeedMps.put("user003", 7.5);

        sampleBearingDeg.put("user001", 45.0);   // 북동
        sampleBearingDeg.put("user002", 135.0);  // 남동
        sampleBearingDeg.put("user003", 300.0);  // 북서

        setWorking("user002", true); // 대모용 상태
    }

    // [DEMO] 1초 주기 이동 > 샘플 클라이언트 실시간 이동 변화 확인
    @Scheduled(fixedDelay = 1000)
    public void moveSamples() {
        for (Presence p : store.findAll()) {
            String u = p.getUserCode();
            if (!u.startsWith("user")) continue; // 샘플만 이동
            double speed = sampleSpeedMps.getOrDefault(u, 8.0);
            double bearing = sampleBearingDeg.getOrDefault(u, 0.0);
            double[] next = moveFrom(p.getLat(), p.getLng(), speed, bearing);
            updateLocation(u, next[0], next[1]);
            sampleBearingDeg.put(u, (bearing + 5.0) % 360.0);
        }
    }

    // 30초 무응답 시 > TIMEOUT 처리
    @Scheduled(fixedDelay = 5_000)
    public void sweepAndMark() {
        long now = System.currentTimeMillis();

        for (Presence p : new ArrayList<>(store.findAll())) {
            long idle = now - p.getLastMsgAt();
            if (idle > OFFLINE_AFTER) {
                store.delete(p.getUserCode());
                sendToOthers(p.getUserCode(),
                        RespStompLeaveDto.builder()
                                .userCode(p.getUserCode())
                                .respTime(Instant.now())
                                .build());

                // 이벤트 발행 (이유: TIMEOUT)
                publisher.publishEvent(new UserLeaveEvent(
                        p.getUserCode(), p.getLat(), p.getLng(), Instant.now(), "TIMEOUT"));
            }
        }
    }
}

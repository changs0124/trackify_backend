package com.dev.trackify_backend.status;

import com.dev.trackify_backend.dto.response.stomp.RespStompLeaveDto;
import com.dev.trackify_backend.dto.response.stomp.RespStompUserDto;
import com.dev.trackify_backend.entity.User;
import com.dev.trackify_backend.repository.UserMapper;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 클라이언트(WebSocket)의 접속/위치/작업상태를 기록하고
 * 주기적으로 점검하여 실시간 브로드캐스트하는 서비스.
 * 상태 표시는 boolean working 만 사용(working=true 파랑, false 초록).
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
public class PresenceStatus {

    @Autowired
    private SimpMessagingTemplate broker;

    @Autowired
    private UserMapper userMapper;

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

        // 브로드캐스트 스로틀 메타
        @Builder.Default
        private long lastBroadcastAt = 0L;
        private Double lastLat;    // 마지막 방송한 위도
        private Double lastLng;    // 마지막 방송한 경도
    }

    // 현재 접속자 상태: userCode > Presence
    private final Map<String, Presence> presences = new ConcurrentHashMap<>();

    // [SAMPLE] 이동 파라미터
    private final Map<String, Double> sampleBearingDeg = new ConcurrentHashMap<>();
    private final Map<String, Double> sampleSpeedMps  = new ConcurrentHashMap<>();

    // 튜닝 파라미터
    private static final long OFFLINE_AFTER  = 30_000; // 30초 이상 응답 없으면 오프라인 처리
    private static final long MIN_BCAST_INTERVAL_MS = 800; // 최소 방송 간격
    private static final double MIN_BCAST_DISTANCE_M = 5.0; // 최소 이동 거리

    // 하버사인(거리 m)
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // [SAMPLE] (lat,lng)에서 bearing(도) 방향으로 meters 전진
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

    /** 접속/재접속 시 upsert + 브로드캐스트 */
    public Presence upsertOnConnect(String userCode, String userName, double lat, double lng) {

        var p = presences.compute(userCode, (k, old) -> {
            long now = System.currentTimeMillis();
            if (old == null) {
                return Presence.builder()
                        .userCode(userCode)
                        .userName(userName)
                        .lat(lat)
                        .lng(lng)
                        .working(false)
                        .lastMsgAt(now)
                        .lastPingRtt(0)
                        .build();
            }
            old.setLat(lat);
            old.setLng(lng);
            old.setLastMsgAt(now);
            if (old.getUserName() == null || old.getUserName().isBlank()) {
                old.setUserName(userName);
            }
            return old;
        });

        broker.convertAndSend("/topic/all", RespStompUserDto.from(p));
        return p;
    }

    /** 위치 업데이트 + 스로틀 기준 충족 시 브로드캐스트 */
    public Presence updateLocation(String userCode, double lat, double lng) {
        var p = presences.get(userCode);
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
            broker.convertAndSend("/topic/all", RespStompUserDto.from(p));
        }
        return p;
    }

    /** 작업 시작/종료 (working만 관리) */
    public Presence setWorking(String userCode, boolean working) {
        var p = presences.get(userCode);
        if (p == null) return null;

        p.setWorking(working);
        p.setLastMsgAt(System.currentTimeMillis());
        broker.convertAndSend("/topic/all", RespStompUserDto.from(p));
        return p;
    }

    /** 클라이언트 핑 수신 → RTT/최종시각 갱신 */
    public Presence onPing(String userCode, long clientSendTs) {
        var p = presences.get(userCode);
        if (p == null) return null;
        long now = System.currentTimeMillis();
        p.setLastMsgAt(now);
        p.setLastPingRtt(Math.max(0, now - clientSendTs));
        return p;
    }

    /** 특정 사용자 제거(강제 로그아웃 등) + LEAVE 브로드캐스트 */
    public void disconnect(String userCode, Double cachedLat, Double cachedLng) {
        var removed = presences.remove(userCode);

        Double lat = cachedLat, lng = cachedLng;
        if (lat == null || lng == null) {
            if (removed != null) { lat = removed.getLat(); lng = removed.getLng(); }
        }
        if (lat != null && lng != null) {
            try { userMapper.update(userCode, lat, lng); } catch (Exception ignore) {}
        }
        broker.convertAndSend("/topic/all",
                RespStompLeaveDto.builder()
                        .userCode(userCode)
                        .respTime(Instant.now())
                        .build());
    }

    /** 전체 스냅샷 반환(REST) */
    public Collection<Presence> snapshot() { return presences.values(); }

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

        // ✅ user002를 강제로 working 상태로 설정
        setWorking("user002", true);
    }

    /** 샘플 이동(1초 주기) */
    @Scheduled(fixedDelay = 1000)
    public void moveSamples() {
        presences.values().forEach(p -> {
            String u = p.getUserCode();
            if (!u.startsWith("user")) return; // 샘플 계정만 이동

            double speed = sampleSpeedMps.getOrDefault(u, 8.0);     // m/s
            double bearing = sampleBearingDeg.getOrDefault(u, 0.0); // deg

            double[] next = moveFrom(p.getLat(), p.getLng(), speed, bearing);
            updateLocation(u, next[0], next[1]); // 스로틀 통과 시 /topic/all 브로드캐스트

            sampleBearingDeg.put(u, (bearing + 5.0) % 360.0); // 곡선 이동
        });
    }

    /**
     * 주기 점검(5초): 타임아웃된 유저만 제거 + LEAVE 브로드캐스트
     * (UNSTABLE/ONLINE 같은 상태는 사용하지 않음)
     */
    @Scheduled(fixedDelay = 5_000)
    public void sweepAndMark() {
        long now = System.currentTimeMillis();
        for (var p : presences.values()) {
            long idle = now - p.getLastMsgAt();
            if (idle > OFFLINE_AFTER) {
                presences.remove(p.getUserCode());
                broker.convertAndSend("/topic/all",
                        RespStompLeaveDto.builder()
                                .userCode(p.getUserCode())
                                .respTime(Instant.now())
                                .build());
            }
        }
    }
}

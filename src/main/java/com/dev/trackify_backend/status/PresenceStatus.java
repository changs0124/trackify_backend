package com.dev.trackify_backend.status;

import com.dev.trackify_backend.dto.response.stomp.RespStompLeaveDto;
import com.dev.trackify_backend.dto.response.stomp.RespStompUserDto;
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
import java.util.concurrent.ConcurrentHashMap;
// 클라이언트(WebSocket 연결)의 접속, 위치, 작업 상태를 기록하고 주기적으로 상태를 점검해서 실시간으로 전체에게 알려주는 서비스
@Service
@EnableScheduling
@RequiredArgsConstructor
public class PresenceStatus {
    // STOMP 브로커로 메세지를 전달하기 위한 Spring 제공 객체
    // "/topic/all" 같은 경로로 실시간 브로드캐스트 가능
    @Autowired
    private SimpMessagingTemplate broker;

    @Autowired
    private UserMapper userMapper;

    // 한 사용자의 접속 상태를 표현하는 데이터 모델
    // 위치, 작업 여부, 마지막 메시지 시각을 기록하고, 상태값을 통해 UI에서 색상(초록, 주황, 회색)으로 표시
    @Data
    @Builder
    public static class Presence {
        private String userCode; // 사용자 식별자
        private double lat; // 현재 위도
        private double lng; // 현재 경도
        private boolean working; // 작업 중 여부(배달 시작 여부 등)
        private long lastMsgAt; // 마지막으로 메시지/핑을 받은 시각(ms)
        private long lastPingRtt; // 최근 핑 응답 시간(RTT)
        private Status status; // 접속 상태 (ONLINE, WORKING, UNSTABLE, OFFLINE)

        // 브로드캐스트 스로틀 메타데이터
        @Builder.Default
        private long lastBroadcastAt = 0L;
        private Double lastLat; // 마지막 방송한 위도
        private Double lastLng; // 마지막 방송한 경도
    }
    public enum Status { ONLINE, WORKING, UNSTABLE, OFFLINE }

    // 현재 접속자 상태: userCode > Presence
    // 현재 접속 중인 모든 사용자의 상태를 메모리에 보관(userCode 기준)
    // 동시성, 안전하게 접근하기 위해 ConcurrentHashMap 사용
    private final Map<String, Presence> presences = new ConcurrentHashMap<>();

    // [SAMPLE] 이동 파라미터: userCode 별 진행 방향(도)과 속도(m/s)
    private final Map<String, Double> sampleBearingDeg = new ConcurrentHashMap<>();
    private final Map<String, Double> sampleSpeedMps  = new ConcurrentHashMap<>();

    // 튜닝 파라미터
    // 서버와 클라이언트간 heartbeat 간격(10초 기준)으로 유저 상태를 판별하기 위한 시간 임계값
    private static final long UNSTABLE_AFTER = 15_000; // 15초 이상 응답 없음 > 불안정(주황)
    private static final long OFFLINE_AFTER  = 30_000; // 30초 이상 응답 없음 > 오프라인(회색)

    // 브로드캐스트 스로틀
    // 위치 방송 최소 간격/거리(상황 맞게 조정)
    private static final long  MIN_BCAST_INTERVAL_MS = 800; // 0.8s
    private static final double MIN_BCAST_DISTANCE_M = 5.0; // 5m

    // 표준 하버사인 거리(m)
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                + Math.sin(dLon/2) * Math.sin(dLon/2) - Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)); // 안정적 계산
        // 위 줄이 약간 복잡하면, 전형식 사용:
        a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);

        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // [SAMPLE](lat, lng)에서 bearing(도) 방향으로 meters만큼 이동한 좌표 반환
    private static double[] moveFrom(double lat, double lng, double meters, double bearingDeg) {
        final double R = 6371000.0; // m
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

    // 사용자의 접속 상태를 직접 갱신하는 APIs
    // 클라이언트에서 위치 업데이트, 작업 시작, 핑 신호를 보낼 때 호출 됨
    // 새로운 사용자가 접속했거나 기존 사용자가 재접속 시 Presence를 추가, 갱신
    public Presence upsertOnConnect(String userCode, double lat, double lng) {
        var p = presences.compute(userCode, (k, old) -> {
            long now = System.currentTimeMillis();
            if (old == null) {
                return Presence.builder()
                        .userCode(userCode).lat(lat).lng(lng)
                        .working(false).lastMsgAt(now).lastPingRtt(0)
                        .status(Status.ONLINE).build();
            }
            old.setLat(lat); old.setLng(lng);
            old.setLastMsgAt(now);
            old.setStatus(Status.ONLINE);
            return old;
        });
        broker.convertAndSend("/topic/all", RespStompUserDto.from(p));
        return p;
    }
    
    // 사용자 위치(lat, lng) 갱신
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
    
    // 작업 시작, 종료 상태 갱신
    public Presence setWorking(String userCode, boolean working) {
        var p = presences.get(userCode);
        if (p == null) return null;
        p.setWorking(working);
        p.setLastMsgAt(System.currentTimeMillis());
        broker.convertAndSend("/topic/all", RespStompUserDto.from(p));
        return p;
    }
    
    // 클라이언트의 핑(PING) 신호를 받고 RTT 계산
    public Presence onPing(String userCode, long clientSendTs) {
        var p = presences.get(userCode);
        if (p == null) return null;
        long now = System.currentTimeMillis();
        p.setLastMsgAt(now);
        p.setLastPingRtt(Math.max(0, now - clientSendTs));
        return p;
    }

    // 특정 사용자를 맵에서 제거(강제 로그아웃 등)
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

    // 현재 모든 접속자 상태 반환(REST API로 전체 조회 시 사용 가능)
    public Collection<Presence> snapshot() { return presences.values(); }

    @PostConstruct
    public void initSamples() {
        upsertOnConnect("user001", 37.5665, 126.9780); // 서울
        upsertOnConnect("user002", 35.1796, 129.0756); // 부산
        upsertOnConnect("user003", 35.8714, 128.6014); // 대구

        // 샘플 이동 파라미터: 1초에 8m 이상(스로틀 거리 5m 초과) + 임의 방향
        sampleSpeedMps.put("user001", 8.0);
        sampleSpeedMps.put("user002", 9.0);
        sampleSpeedMps.put("user003", 7.5);

        sampleBearingDeg.put("user001", 45.0);   // 북동
        sampleBearingDeg.put("user002", 135.0);  // 남동
        sampleBearingDeg.put("user003", 300.0);  // 북서

    }

    // 샘플 유저를 주기적으로 ‘이동’시키는 스케줄러 (1초 주기)
    @Scheduled(fixedDelay = 1000)
    public void moveSamples() {
        presences.values().forEach(p -> {
            String u = p.getUserCode();
            if (!u.startsWith("user")) return; // 샘플 계정만 이동

            double speed = sampleSpeedMps.getOrDefault(u, 8.0);     // m/s
            double bearing = sampleBearingDeg.getOrDefault(u, 0.0); // deg

            // 1초에 speed(m) 전진
            double[] next = moveFrom(p.getLat(), p.getLng(), speed, bearing);
            updateLocation(u, next[0], next[1]); // 내부에서 스로틀 통과 시 /topic/all 브로드캐스트

            // 방향을 조금씩 변하게 해서 곡선 이동(옵션)
            sampleBearingDeg.put(u, (bearing + 5.0) % 360.0);
        });
    }

    // 주기적으로 품질/상태 재계산 + “변경된 경우”만 브로드캐스트하는 스케줄러
    @Scheduled(fixedDelay = 5_000)
    public void sweepAndMark() {
        long now = System.currentTimeMillis();
        for (var p : presences.values()) {
            Status old = p.getStatus();
            long idle = now - p.getLastMsgAt();

            Status next;
            if (idle > OFFLINE_AFTER) next = Status.OFFLINE;
            else if (idle > UNSTABLE_AFTER) next = Status.UNSTABLE;
            else if (p.isWorking()) next = Status.WORKING;
            else next = Status.ONLINE;

            if (next != old) {
                p.setStatus(next);
                if (next == Status.OFFLINE) {
                    presences.remove(p.getUserCode());
                    broker.convertAndSend("/topic/all",
                            RespStompLeaveDto.builder()
                                    .userCode(p.getUserCode())
                                    .respTime(Instant.now()).build());
                } else {
                    broker.convertAndSend("/topic/all", RespStompUserDto.from(p));
                }
            }
        }
    }
}
package com.dev.trackify_backend.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 역할
 * RedisConfig를 바탕으로 실제 Presence(접속자 상태)객체 데이터를 Redis에 저장/조회/삭제/전체 조회
 * PresenceStatus.Presence를 저장할 때 항상 "presence:" 접두사를 붙여서 key를 구성 > Redis 내부의 namespace 구분
 * RedisTemplate을 직접 사용하는 대신, 이 클래스가 중간에서 캡슐화하여 코드의 일관성과 재사용성을 높여줌
 * 전체 조회 시 SCAN 사용으로 대량 데이터에서도 블로킹 없이 동작
 * */
@Component
public class PresenceStore {
    // Key 접두사
    private static final String PREFIX = "presence:";

    // RedisConfig에서 만든 RedisTemplate<String, PresenceStatus.Presence> 주입
    @Autowired
    private RedisTemplate<String, PresenceStatus.Presence> redisTemplate;
    
    // Key 생성 헬퍼
    // - 코드 중복 및 실수 방지
    private String k(String userCode) {
        return PREFIX + userCode;
    }

    // 단일 조회
    // - opsForValue(): Redis의 String(Value)타입 명령어 사용 > Value는 Presence 객체, JSON 직렬화/역직렬화됨
    public PresenceStatus.Presence get(String userCode) {
        return redisTemplate.opsForValue().get(k(userCode));
    }

    // 저장
    public void save(PresenceStatus.Presence p) {
        redisTemplate.opsForValue().set(k(p.getUserCode()), p);
    }

    // 삭제
    public void delete(String userCode) {
        redisTemplate.delete(k(userCode));
    }

    // 전체 스캔
    // keys presence:* 명령어로 전체 조회가 가능하지만 성능상 비효율적(대규모 데이터에서 블로킹) > SCAN 사용 - 점진적으로 키를 탐색(non-blocking)
    public List<PresenceStatus.Presence> findAll() {
        List<PresenceStatus.Presence> out = new ArrayList<>();

        // ScanOptions:
        // - .match(PREFIX + "*"): "presence:*" 패턴으로 찾음
        // - .count(512): 한 번에 가져올 키 개수 힌트
        ScanOptions opts = ScanOptions.scanOptions().match(PREFIX + "*").count(512).build();

        // conn.scan(opts): Redis 커넥션에서 SCAN 실행.
        var conn = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();

        // Cursor<byte[]> cur: 키들을 순회할 수 있는 커서.
        try (Cursor<byte[]> cur = conn.scan(opts)) {
            while (cur.hasNext()) {
                String key = new String(cur.next(), StandardCharsets.UTF_8); // btye[] > String으로 변환
                PresenceStatus.Presence p = redisTemplate.opsForValue().get(key); // 해당 키의 객체 조회
                if (p != null) out.add(p);
            }
        }
        return out;
    }
}

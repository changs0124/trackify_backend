package com.dev.trackify_backend.event;

import java.time.Instant;

/** PresenceStatus가 유저를 제거할 때 발행하는 도메인 이벤트 */
public record UserLeaveEvent(
        String userCode,
        Double lat,
        Double lng,
        Instant at,
        String reason // "DISCONNECT" | "TIMEOUT"
) {}

package com.dev.trackify_backend.dto.response.stomp;

import com.dev.trackify_backend.status.PresenceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RespStompUserDto {
    private final String type = "PRESENCE";
    private String userCode;
    private String userName;
    private double lat;
    private double lng;
    private long rtt;            // 핑 왕복(ms)
    private Boolean working;
    private Instant respTime;

    public static RespStompUserDto from(PresenceStatus.Presence p) {
        return RespStompUserDto.builder()
                .userCode(p.getUserCode())
                .userName(p.getUserName())
                .lat(p.getLat())
                .lng(p.getLng())
                .rtt(p.getLastPingRtt())
                .working(p.isWorking())
                .respTime(Instant.now())
                .build();
    }
}

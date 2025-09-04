package com.dev.trackify_backend.dto.request.stomp;

import lombok.Data;

import java.time.Instant;

@Data
public class ReqStompUserDto {
    private String userCode;
    private double lat;
    private double lng;
    private Long clientTime;
    private Boolean working;
    private Instant respTime;
}

package com.dev.trackify_backend.dto.response.stomp;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class RespStompLeaveDto {
    private final String type = "LEAVE";
    private String userCode;
    private Instant respTime;
}

package com.dev.trackify_backend.dto.request.stomp;

import lombok.Data;

@Data
public class ReqStompPingDto {
    private String userCode;
    private Long clientTime;
}

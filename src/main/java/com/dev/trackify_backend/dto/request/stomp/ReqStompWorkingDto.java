package com.dev.trackify_backend.dto.request.stomp;

import lombok.Data;

@Data
public class ReqStompWorkingDto {
    private String userCode;
    private boolean working;
}

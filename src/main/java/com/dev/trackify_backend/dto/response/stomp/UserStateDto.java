package com.dev.trackify_backend.dto.response.stomp;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UserStateDto {
    private final String userCode;
    private final double lat;
    private final double lng;
}

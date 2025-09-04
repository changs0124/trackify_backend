package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RespRestUserDto {
    private String userName;
    private String modelNumber;
    private double modelVolume;
}

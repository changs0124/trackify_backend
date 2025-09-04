package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RespRestCargoDto {
    private long id;
    private String cargoName;
    private double lat;
    private double lng;
}

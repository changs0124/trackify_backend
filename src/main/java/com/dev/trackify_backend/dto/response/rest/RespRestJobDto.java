package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RespRestJobDto {
    private String userName;
    private String modelNumber;
    private double modelVolume;
    private String cargoName;
    private String productName;
    private double productVolume;
    private int productCount;
    private int status;
}

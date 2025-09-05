package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RespRestRunningJobDto {
    private long cargoId;
    private long productId;
    private int productCount;
    private String paths;
}

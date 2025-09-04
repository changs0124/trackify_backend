package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RespRestProductDto {
    private long id;
    private String productName;
    private double volume;
}

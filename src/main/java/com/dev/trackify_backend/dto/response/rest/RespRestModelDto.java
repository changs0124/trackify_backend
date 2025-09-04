package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RespRestModelDto {
    private long id;
    private String modelNumber;
}

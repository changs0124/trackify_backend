package com.dev.trackify_backend.dto.response.rest;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RespRestHistoryDto {
    private long id;
    private int productCount;
    private String paths;
    private int status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String CargoName;
    private String productName;
    private String userName;
}

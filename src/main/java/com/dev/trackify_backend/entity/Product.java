package com.dev.trackify_backend.entity;

import com.dev.trackify_backend.dto.response.rest.RespRestProductDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Product {
    private long id;
    private String productName;
    private double volume;
    private LocalDateTime registerDate;
    private LocalDateTime updateDate;

    public RespRestProductDto toDto() {
        return RespRestProductDto.builder()
                .id(id)
                .productName(productName)
                .volume(volume)
                .build();
    }
}

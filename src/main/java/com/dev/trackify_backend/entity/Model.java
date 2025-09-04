package com.dev.trackify_backend.entity;

import com.dev.trackify_backend.dto.response.rest.RespRestModelDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Model {
    private long id;
    private String modelNumber;
    private double volume;
    private LocalDateTime registerDate;
    private LocalDateTime updateDate;

    public RespRestModelDto toDto() {
        return RespRestModelDto.builder()
                .id(id)
                .modelNumber(modelNumber)
                .build();
    }
}

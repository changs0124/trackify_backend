package com.dev.trackify_backend.entity;

import com.dev.trackify_backend.dto.response.rest.RespRestCargoDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Cargo {
    private long id;
    private String cargoName;
    private double lat;
    private double lng;
    private LocalDateTime registerDate;
    private LocalDateTime updateDate;

    public RespRestCargoDto toDto() {
        return RespRestCargoDto.builder()
                .id(id)
                .cargoName(cargoName)
                .lat(lat)
                .lng(lng)
                .build();
    }
}

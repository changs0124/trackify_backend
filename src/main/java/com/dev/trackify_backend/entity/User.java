package com.dev.trackify_backend.entity;

import com.dev.trackify_backend.dto.response.rest.RespRestUserDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class User {
    private long id;
    private String userCode;
    private String userName;
    private long modelId;
    private double lat;
    private double lng;
    private LocalDateTime registerDate;
    private LocalDateTime updateDate;

    private Model model;

    public RespRestUserDto toDto() {
        return RespRestUserDto.builder()
                .userName(userName)
                .modelNumber(model.getModelNumber())
                .modelVolume(model.getVolume())
                .build();
    }
}

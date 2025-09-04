package com.dev.trackify_backend.dto.request.rest;

import com.dev.trackify_backend.entity.User;
import lombok.Data;

@Data
public class ReqRestUserDto {
    private String userCode;
    private String userName;
    private int modelId;
    private double lat;
    private double lng;

    public User toEntity() {
        return User.builder()
                .userCode(userCode)
                .userName(userName)
                .modelId(modelId)
                .lat(lat)
                .lng(lng)
                .build();
    }
}

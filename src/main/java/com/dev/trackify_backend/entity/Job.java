package com.dev.trackify_backend.entity;

import com.dev.trackify_backend.dto.response.rest.RespRestHistoryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class Job {
    private long id;
    private long userId;
    private long cargoId;
    private long productId;
    private int productCount;
    private String paths;
    private int status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private Cargo cargo;
    private Product product;
    private User user;

    public RespRestHistoryDto toDto() {
        return RespRestHistoryDto.builder()
                .id(id)
                .productCount(productCount)
                .paths(paths)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .CargoName(cargo.getCargoName())
                .productName(product.getProductName())
                .userName(user.getUserName())
                .build();
    }
}

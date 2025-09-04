package com.dev.trackify_backend.dto.request.rest;

import com.dev.trackify_backend.entity.Job;
import lombok.Data;

@Data
public class ReqRestJobDto {
    private long jobId;
    private String userCode;
    private long cargoId;
    private long productId;
    private int productCount;
    private String paths;

    public Job toSaveEntity(long id) {
        return Job.builder()
                .userId(id)
                .cargoId(cargoId)
                .productId(productId)
                .productCount(productCount)
                .build();
    }

    public Job toUpdateEntity() {
        return Job.builder()
                .id(jobId)
                .cargoId(cargoId)
                .productId(productId)
                .productCount(productCount)
                .build();
    }

    public Job toCompleteEntity() {
        return Job.builder()
                .id(jobId)
                .paths(paths)
                .build();
    }

}
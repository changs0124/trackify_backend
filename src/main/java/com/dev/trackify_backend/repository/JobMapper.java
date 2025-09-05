package com.dev.trackify_backend.repository;

import com.dev.trackify_backend.entity.Job;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface JobMapper {
    void save(Job job);
    void update(Job job);
    void complete(Job job);
    void cancel(long id);

    Optional<Job> findById(@Param("jobId") long jobId);
    Job findByUserId(@Param("userId") long userId);

    List<Job> findByCargoIdAndProductId(@Param("cargoId") Long cargoId, @Param("productId") Long productId);
    List<Job> findTop3Cargos();
}

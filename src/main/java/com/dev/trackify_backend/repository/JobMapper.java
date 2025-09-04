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

    Job findByUserId(@Param("userId") long userId);
    List<Job> findByCargoIdAndProductId(@Param("cargoId") long cargoId, @Param("productId") long productId);
}

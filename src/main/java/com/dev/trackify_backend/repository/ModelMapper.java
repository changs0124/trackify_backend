package com.dev.trackify_backend.repository;

import com.dev.trackify_backend.entity.Model;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ModelMapper {
    List<Model> modelList();
}

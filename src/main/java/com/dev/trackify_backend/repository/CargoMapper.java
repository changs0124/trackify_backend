package com.dev.trackify_backend.repository;

import com.dev.trackify_backend.entity.Cargo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CargoMapper {
    List<Cargo> cargoList();
}

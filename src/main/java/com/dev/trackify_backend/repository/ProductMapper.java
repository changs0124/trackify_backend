package com.dev.trackify_backend.repository;

import com.dev.trackify_backend.entity.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper {
    List<Product> productList();
}

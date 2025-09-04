package com.dev.trackify_backend.service.rest;

import com.dev.trackify_backend.dto.response.rest.RespRestProductDto;
import com.dev.trackify_backend.entity.Product;
import com.dev.trackify_backend.repository.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestProductService {
    @Autowired
    private ProductMapper productMapper;

    public List<RespRestProductDto> getProductList() {
        try {
            List<Product> tempProductList = productMapper.productList();
            if(tempProductList == null || tempProductList.isEmpty()) {
                return List.of();
            }
            return tempProductList.stream().map(Product::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error: get_product_list", e);
        }
    }

}

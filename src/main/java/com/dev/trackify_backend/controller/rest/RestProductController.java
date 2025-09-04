package com.dev.trackify_backend.controller.rest;

import com.dev.trackify_backend.service.rest.RestProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class RestProductController {

    @Autowired
    private RestProductService restProductService;

    @GetMapping("/products")
    public ResponseEntity<?> getProducts() {
        return ResponseEntity.ok().body(restProductService.getProductList());
    }
}

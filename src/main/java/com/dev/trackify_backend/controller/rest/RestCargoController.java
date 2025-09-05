package com.dev.trackify_backend.controller.rest;

import com.dev.trackify_backend.service.rest.RestCargoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RestCargoController {

    @Autowired
    private RestCargoService restCargoService;

    @GetMapping("/cargos")
    public ResponseEntity<?> getCargos() {
        return ResponseEntity.ok().body(restCargoService.getCargoList());
    }

    @GetMapping("/cargos/top")
    public ResponseEntity<?> getTopCargos() {
        return ResponseEntity.ok().body(restCargoService.getTopCargoList());
    }
}

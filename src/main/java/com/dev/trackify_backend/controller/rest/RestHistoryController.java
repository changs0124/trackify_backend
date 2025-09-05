package com.dev.trackify_backend.controller.rest;

import com.dev.trackify_backend.service.rest.RestHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class RestHistoryController {

    @Autowired
    private RestHistoryService restHistoryService;

    @GetMapping("/historys")
    public ResponseEntity<?> getHistorys(@RequestParam(required = false) Long cargoId, @RequestParam(required = false) Long productId) {
        log.info("{}", cargoId);
        log.info("{}", productId);
        return ResponseEntity.ok().body(restHistoryService.getHistorys(cargoId, productId));
    }
}

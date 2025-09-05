package com.dev.trackify_backend.controller.rest;

import com.dev.trackify_backend.dto.request.rest.ReqRestUserDto;
import com.dev.trackify_backend.service.rest.RestUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class RestUserController {

    @Autowired
    private RestUserService restUserService;

    @GetMapping("/user/{userCode}")
    public ResponseEntity<?> validateUser(@PathVariable String userCode) {
        log.info("{}", userCode);
        return ResponseEntity.ok().body(restUserService.validateUser(userCode));
    }

    @GetMapping("/user/my/{userCode}")
    public ResponseEntity<?> getMyInfo(@PathVariable String userCode) {
        log.info("{}", userCode);
        return ResponseEntity.ok().body(restUserService.getMyInfo(userCode));
    }

    @PostMapping("/user")
    public ResponseEntity<?> registerUser(@RequestBody ReqRestUserDto reqRestUserDto) {
        log.info("{}", reqRestUserDto);
        restUserService.registerUser(reqRestUserDto);
        return ResponseEntity.ok().body("register user success");
    }
}

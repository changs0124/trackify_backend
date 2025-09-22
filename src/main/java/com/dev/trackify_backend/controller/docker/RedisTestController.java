package com.dev.trackify_backend.controller.docker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/redis-test")
public class RedisTestController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/set")
    public String setValue() {
        stringRedisTemplate.opsForValue().set("testKey", "trackify");
        return "Saved!";
    }

    @GetMapping("/get")
    public String getValue() {
        return stringRedisTemplate.opsForValue().get("testKey");
    }
}

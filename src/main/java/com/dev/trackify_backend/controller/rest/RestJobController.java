package com.dev.trackify_backend.controller.rest;

import com.dev.trackify_backend.dto.request.rest.ReqRestJobDto;
import com.dev.trackify_backend.service.rest.RestJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class RestJobController {

    @Autowired
    private RestJobService restJobService;

    @GetMapping("/job/{userCode}")
    public ResponseEntity<?> getJob(@PathVariable String userCode) {
        log.info("{}", userCode);
        return ResponseEntity.ok().body(restJobService.getJob(userCode));
    }

    @PostMapping("/job/register")
    public ResponseEntity<?> registerJob(ReqRestJobDto reqRestJobDto) {
        log.info("{}", reqRestJobDto);
        return ResponseEntity.ok().body(restJobService.registerJob(reqRestJobDto));
    }

    @PutMapping("/job/update")
    public ResponseEntity<?> updateJob(ReqRestJobDto reqRestJobDto) {
        log.info("{}", reqRestJobDto);
        restJobService.updateJob(reqRestJobDto);
        return ResponseEntity.ok().body("update job success");
    }

    @PutMapping("/job/complete")
    public ResponseEntity<?> completeJob(ReqRestJobDto reqRestJobDto) {
        log.info("{}", reqRestJobDto);
        restJobService.completeJob(reqRestJobDto);
        return ResponseEntity.ok().body("complete job success");
    }

    @PutMapping("/job/cancel/{jobId}")
    public ResponseEntity<?> cancelJob(@PathVariable long jobId) {
        log.info("{}", jobId);
        restJobService.cancelJob(jobId);
        return ResponseEntity.ok().body("cancel job success");
    }
}

package com.dev.trackify_backend.service.rest;

import com.dev.trackify_backend.dto.request.rest.ReqRestJobDto;
import com.dev.trackify_backend.dto.response.rest.RespRestJobDto;
import com.dev.trackify_backend.dto.response.rest.RespRestRunningJobDto;
import com.dev.trackify_backend.entity.Job;
import com.dev.trackify_backend.entity.User;
import com.dev.trackify_backend.repository.JobMapper;
import com.dev.trackify_backend.repository.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Slf4j
@Service
public class RestJobService {
    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private UserMapper userMapper;

    public RespRestRunningJobDto getJobById(long jobId) {
        Job tempJob = jobMapper.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        if(tempJob.getStatus() == 2) {
            throw new RuntimeException("Error: Already Completed Job");
        }

        if(tempJob.getStatus() == 0) {
            throw new RuntimeException("Error: Canceled job");
        }

        return tempJob.toRunningJobDto();
    }

    public RespRestJobDto getJob(String userCode) {
        User tempUser = userMapper.findByUserCodeWithModel(userCode)
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        long userId = tempUser.getId();
        String userName = tempUser.getUserName();
        String modelNumber = tempUser.getModel() != null ? tempUser.getModel().getModelNumber() : "";
        double modelVolume = tempUser.getModel() != null ? tempUser.getModel().getVolume() : 0.0;

        RespRestJobDto.RespRestJobDtoBuilder respRestJobDto = RespRestJobDto.builder()
                .userName(userName)
                .modelNumber(modelNumber)
                .modelVolume(modelVolume)
                .cargoName("")        // 기본값
                .productName("")      // 기본값
                .productVolume(0.0)   // 기본값
                .productCount(0)      // 기본값
                .status(0);           // 기본: 미진행/취소

        Job tempJob = jobMapper.findByUserId(userId);
        if(tempJob != null && tempJob.getStatus() == 1) {
            respRestJobDto
                    .cargoName(tempJob.getCargo() != null ? tempJob.getCargo().getCargoName() : "")
                    .productName(tempJob.getProduct() != null ? tempJob.getProduct().getProductName() : "")
                    .productVolume(tempJob.getProduct() != null ? tempJob.getProduct().getVolume() : 0.0)
                    .productCount(tempJob.getProductCount())
                    .status(1); // 진행중일 때만 1로 세팅
        }

        return respRestJobDto.build();
    }

    @Transactional
    public long registerJob(ReqRestJobDto reqRestJobDto) {
        User tempUser = userMapper.findByUserCode(reqRestJobDto.getUserCode())
                .orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        Job job = reqRestJobDto.toSaveEntity(tempUser.getId());

        try {
            jobMapper.save(job);
            return job.getId();
        } catch (Exception e) {
            throw new RuntimeException("등록 중 오류 발생");
        }
    }

    public void updateJob(ReqRestJobDto reqRestJobDto) {
        try {
            jobMapper.update(reqRestJobDto.toUpdateEntity());
        } catch (Exception e) {
            throw new RuntimeException("업데이트 중 오류 발생");
        }
    }

    public void completeJob(ReqRestJobDto reqRestJobDto) {
        try {
            jobMapper.complete(reqRestJobDto.toCompleteEntity());
        } catch (Exception e) {
            throw new RuntimeException("완료 중 오류 발생");
        }
    }

    public void cancelJob(long id) {
        try {
            jobMapper.cancel(id);
        } catch (Exception e) {
            throw new RuntimeException("취소 중 오류 발생");
        }
    }
}


package com.dev.trackify_backend.service.rest;

import com.dev.trackify_backend.dto.response.rest.RespRestHistoryDto;
import com.dev.trackify_backend.entity.Job;
import com.dev.trackify_backend.repository.JobMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestHistoryService {

    @Autowired
    private JobMapper jobMapper;

    public List<RespRestHistoryDto> getHistorys(long cargoId, long productId) {
        List<Job> tempHistorys = jobMapper.findByCargoIdAndProductId(cargoId, productId);
        return tempHistorys.stream().map(Job::toDto).collect(Collectors.toList());
    }
}

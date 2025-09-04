package com.dev.trackify_backend.service.rest;

import com.dev.trackify_backend.dto.response.rest.RespRestModelDto;
import com.dev.trackify_backend.entity.Model;
import com.dev.trackify_backend.repository.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestModelService {
    @Autowired
    private ModelMapper modelMapper;

    public List<RespRestModelDto> getModelList() {
        try {
            List<Model> tempModelList = modelMapper.modelList();
            if(tempModelList == null || tempModelList.isEmpty()) {
                return List.of();
            }
            return tempModelList.stream().map(Model::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error: get_model_list", e);
        }
    }
}

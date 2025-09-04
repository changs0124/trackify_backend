package com.dev.trackify_backend.service.rest;

import com.dev.trackify_backend.dto.response.rest.RespRestCargoDto;
import com.dev.trackify_backend.entity.Cargo;
import com.dev.trackify_backend.repository.CargoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RestCargoService {
    @Autowired
    private CargoMapper cargoMapper;

    public List<RespRestCargoDto> getCargoList() {
        try {
            List<Cargo> tempCargoList = cargoMapper.cargoList();
            if(tempCargoList == null || tempCargoList.isEmpty()) {
                return List.of();
            }
            return tempCargoList.stream().map(Cargo::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error: get_cargo_list", e);
        }
    }
}

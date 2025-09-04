package com.dev.trackify_backend.service.rest;

import com.dev.trackify_backend.dto.request.rest.ReqRestUserDto;
import com.dev.trackify_backend.dto.response.rest.RespRestUserDto;
import com.dev.trackify_backend.entity.User;
import com.dev.trackify_backend.repository.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class RestUserService {
    @Autowired
    private UserMapper userMapper;

    public boolean validateUser(String userCode) {
        return userMapper.findByUserCode(userCode).isPresent();
    }

    @Transactional
    public void registerUser(ReqRestUserDto reqRestUserDto) {
        if(duplicateUserName(reqRestUserDto.getUserName())) {
            throw new DuplicateKeyException("Error: duplicateUserName");
        }

        try {
            userMapper.save(reqRestUserDto.toEntity());
        } catch (Exception e) {
            throw  new RuntimeException("Error: save_user", e);
        }
    }

    public RespRestUserDto getMyInfo(String userCode) {
        User tempUser = userMapper.findByUserCodeWithModel(userCode).orElseThrow(() -> new NoSuchElementException("Error: NoSuchElementException"));

        return tempUser.toDto();
    }

    private boolean duplicateUserName(String userName) {
        try {
            return userMapper.findByUserName(userName).isPresent();
        } catch (Exception e) {
            throw new RuntimeException("Error: findByUserName", e);
        }
    }
}

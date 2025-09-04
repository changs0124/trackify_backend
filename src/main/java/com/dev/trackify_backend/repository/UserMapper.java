package com.dev.trackify_backend.repository;

import com.dev.trackify_backend.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {
    void save(User user);
    void update(@Param("userCode") String userCode,
                @Param("lat") double lat,
                @Param("lng") double lng);

    Optional<User> findByUserName(@Param("userName") String userName);
    Optional<User> findByUserCode(@Param("userCode") String userCode);

    Optional<User> findByUserCodeWithModel(@Param("userCode") String userCode);
}

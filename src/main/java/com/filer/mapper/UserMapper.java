package com.filer.mapper;

import com.filer.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Optional;

@Mapper
public interface UserMapper {
    void insert(User user);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findById(Long id);
    void updatePasswordHash(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}

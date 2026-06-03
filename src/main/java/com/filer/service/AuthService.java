package com.filer.service;

import com.filer.dto.AuthDto;
import com.filer.mapper.UserMapper;
import com.filer.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (userMapper.findByEmail(req.getEmail()).isPresent())
            throw new IllegalArgumentException("Email already registered");
        if (userMapper.findByUsername(req.getUsername()).isPresent())
            throw new IllegalArgumentException("Username already taken");

        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setRole("USER");
        userMapper.insert(user);

        String token = jwtService.generate(user.getEmail(),
                Map.of("role", user.getRole(), "username", user.getUsername()));
        return new AuthDto.AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole());
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        User user = userMapper.findByEmail(req.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid credentials");
        String token = jwtService.generate(user.getEmail(),
                Map.of("role", user.getRole(), "username", user.getUsername()));
        return new AuthDto.AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole());
    }
}

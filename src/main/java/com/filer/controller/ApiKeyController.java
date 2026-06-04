package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.mapper.ApiKeyMapper;
import com.filer.model.ApiKey;
import com.filer.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apikeys")
@RequiredArgsConstructor
public class ApiKeyController {
    private final ApiKeyMapper apiKeyMapper;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKey>>> list(@AuthenticationPrincipal UserDetails u) {
        return ResponseEntity.ok(ApiResponse.ok(apiKeyMapper.findByUserId(userId(u))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApiKey>> create(
            @AuthenticationPrincipal UserDetails u,
            @RequestBody(required = false) Map<String, String> body) {
        ApiKey key = new ApiKey();
        key.setUserId(userId(u));
        byte[] raw = new byte[32]; new SecureRandom().nextBytes(raw);
        key.setKeyValue("fk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        key.setName(body != null ? body.getOrDefault("name", "API Key") : "API Key");
        apiKeyMapper.insert(key);
        return ResponseEntity.ok(ApiResponse.ok(key));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(
            @AuthenticationPrincipal UserDetails u,
            @PathVariable Long id) {
        apiKeyMapper.deactivate(id, userId(u));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private Long userId(UserDetails u) {
        if (u == null) return -1L;
        com.filer.model.User user = userService.findByUsername(u.getUsername());
        if (user == null) user = userService.findByEmail(u.getUsername());
        return user != null ? user.getId() : -1L;
    }
}

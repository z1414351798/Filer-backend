package com.filer.controller;

import com.filer.dto.ApiResponse;
import com.filer.dto.AuthDto;
import com.filer.model.User;
import com.filer.service.AuthService;
import com.filer.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> register(
            @Valid @RequestBody AuthDto.RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.success(authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthDto.AuthResponse>> login(
            @Valid @RequestBody AuthDto.LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(req)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> me(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        // JWT subject is email
        User user = userService.findByEmail(principal.getName());
        if (user == null) return ResponseEntity.status(401).body(ApiResponse.error("User not found"));
        var info = java.util.Map.of(
            "id",       user.getId(),
            "username", user.getUsername(),
            "email",    user.getEmail() != null ? user.getEmail() : "",
            "role",     user.getRole()
        );
        return ResponseEntity.ok(ApiResponse.ok(info));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Principal principal,
            @RequestBody java.util.Map<String, String> body) {
        if (principal == null) return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || newPwd == null || newPwd.length() < 6)
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid request"));
        try {
            authService.changePassword(principal.getName(), oldPwd, newPwd);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}

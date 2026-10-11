package com.app.auth.controller;

import com.app.auth.dto.response.ApiResponse;
import com.app.auth.dto.response.AuthResponse;
import com.app.auth.dto.request.LoginRequest;
import com.app.auth.dto.request.RegisterRequest;
import com.app.auth.dto.response.UserResponse;
import com.app.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(
            AuthService authService
    ) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid
            @RequestBody
            RegisterRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                authService.register(request),
                "Registration successful"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid
            @RequestBody
            LoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.login(request),
                "Login successful"
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.getUser(Long.valueOf(authentication.getName())),
                "Current user retrieved"
        ));
    }

    @GetMapping("/admin/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserForAdmin(
            @PathVariable
            Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                authService.getUser(id),
                "User retrieved"
        ));
    }
}

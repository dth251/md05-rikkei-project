package com.app.auth.service;

import com.app.auth.dto.request.LoginRequest;
import com.app.auth.dto.request.RegisterRequest;
import com.app.auth.dto.response.AuthResponse;
import com.app.auth.dto.response.UserResponse;

public interface AuthService {

    UserResponse register(
            RegisterRequest request
    );

    AuthResponse login(
            LoginRequest request
    );

    UserResponse getUser(
            Long id
    );
}

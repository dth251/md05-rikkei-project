package com.app.auth.service.impl;

import com.app.auth.dto.response.AuthResponse;
import com.app.auth.dto.request.LoginRequest;
import com.app.auth.dto.request.RegisterRequest;
import com.app.auth.dto.response.UserResponse;
import com.app.auth.entity.User;
import com.app.auth.repository.UserRepository;
import com.app.auth.security.TokenService;
import com.app.auth.config.exception.ApiException;
import com.app.auth.mapper.UserMapper;
import com.app.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final String dummyHash;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.dummyHash = passwordEncoder.encode("constant-for-unknown-user-timing");
    }

    @Override
    @Transactional
    public UserResponse register(
            RegisterRequest request
    ) {
        validatePasswordBytes(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User(
                request.fullName().strip(),
                email,
                passwordEncoder.encode(request.password())
        );
        return userMapper.toResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(
            LoginRequest request
    ) {
        validatePasswordBytes(request.password());
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        boolean matches = passwordEncoder.matches(
                request.password(),
                user == null ? dummyHash : user.getPasswordHash()
        );
        if (user == null || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new AuthResponse(
                tokenService.create(user),
                "Bearer",
                tokenService.expiresIn(),
                userMapper.toResponse(user)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUser(
            Long id
    ) {
        return userMapper.toResponse(userRepository.findById(id).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "User not found")
        ));
    }

    private String normalizeEmail(
            String email
    ) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordBytes(
            String password
    ) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
    }
}

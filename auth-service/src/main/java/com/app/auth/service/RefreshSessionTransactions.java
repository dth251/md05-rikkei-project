package com.app.auth.service;

import com.app.auth.dto.response.AuthResponse;
import com.app.auth.entity.User;
import java.util.Optional;

public interface RefreshSessionTransactions {

    AuthResponse create(User user);

    Optional<AuthResponse> rotate(String credential);

    void revoke(String credential);

    void cleanup();
}

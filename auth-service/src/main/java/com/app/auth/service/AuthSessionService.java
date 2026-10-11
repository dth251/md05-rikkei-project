package com.app.auth.service;

import com.app.auth.dto.response.AuthResponse;
import com.app.auth.entity.User;

public interface AuthSessionService {

    AuthResponse issue(User user);

    AuthResponse refresh(String credential);

    void logout(String credential);
}

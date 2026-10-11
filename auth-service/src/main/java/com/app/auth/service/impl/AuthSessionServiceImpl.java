package com.app.auth.service.impl;

import com.app.auth.config.exception.ApiException;
import com.app.auth.dto.response.AuthResponse;
import com.app.auth.entity.User;
import com.app.auth.service.AuthSessionService;
import com.app.auth.service.RefreshSessionTransactions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private final RefreshSessionTransactions transactions;

    public AuthSessionServiceImpl(RefreshSessionTransactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public AuthResponse issue(User user) {
        return transactions.create(user);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthResponse refresh(String credential) {
        return transactions.rotate(credential).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void logout(String credential) {
        transactions.revoke(credential);
    }
}

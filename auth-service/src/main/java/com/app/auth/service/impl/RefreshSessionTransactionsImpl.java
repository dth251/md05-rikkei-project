package com.app.auth.service.impl;

import com.app.auth.dto.response.AuthResponse;
import com.app.auth.entity.RefreshSession;
import com.app.auth.entity.RefreshToken;
import com.app.auth.entity.User;
import com.app.auth.mapper.AuthSessionMapper;
import com.app.auth.repository.RefreshSessionRepository;
import com.app.auth.repository.RefreshTokenRepository;
import com.app.auth.security.RefreshCredentials;
import com.app.auth.service.RefreshSessionTransactions;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshSessionTransactionsImpl implements RefreshSessionTransactions {

    private final RefreshSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final RefreshCredentials credentials;
    private final AuthSessionMapper mapper;
    private final Duration ttl;

    public RefreshSessionTransactionsImpl(
        RefreshSessionRepository sessions,
        RefreshTokenRepository tokens,
        RefreshCredentials credentials,
        AuthSessionMapper mapper,
        @Value("${auth.refresh-ttl:P7D}") Duration ttl
    ) {
        if (ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("Refresh TTL must be between one minute and thirty days");
        }
        this.sessions = sessions;
        this.tokens = tokens;
        this.credentials = credentials;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    @Override
    @Transactional
    public AuthResponse create(User user) {
        String raw = credentials.create();
        String hash = credentials.hash(raw);
        var session = sessions.saveAndFlush(new RefreshSession(user, hash, Instant.now().plus(ttl)));
        tokens.saveAndFlush(new RefreshToken(hash, session));
        return mapper.toResponse(user, raw, session.getExpiresAt());
    }

    @Override
    @Transactional
    public Optional<AuthResponse> rotate(String credential) {
        String hash = credentials.hash(credential);
        // Always lock the family first, including when an old and new token race.
        var session = tokens.findSessionIdByTokenHash(hash).flatMap(sessions::findLockedById).orElse(null);
        Instant now = Instant.now();
        if (session == null || !session.isUsable(now)) {
            return Optional.empty();
        }
        if (!credentials.matches(session.getActiveHash(), hash)) {
            session.revoke(now);
            // Return normally so revocation commits before the facade returns HTTP 401.
            return Optional.empty();
        }
        String next = credentials.create();
        String nextHash = credentials.hash(next);
        tokens.saveAndFlush(new RefreshToken(nextHash, session));
        session.rotate(nextHash);
        return Optional.of(mapper.toResponse(session.getUser(), next, session.getExpiresAt()));
    }

    @Override
    @Transactional
    public void revoke(String credential) {
        tokens.findSessionIdByTokenHash(credentials.hash(credential)).flatMap(sessions::findLockedById)
                .ifPresent(session -> session.revoke(Instant.now()));
    }

    @Override
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(1));
        for (var candidate : sessions.findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(cutoff)) {
            var session = sessions.findLockedById(candidate.getId()).orElse(null);
            if (session != null && session.getExpiresAt().isBefore(cutoff)) {
                tokens.deleteForSession(session.getId());
                sessions.delete(session);
            }
        }
    }
}

package com.app.auth.security;

import com.app.auth.entity.Role;
import com.app.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final SecretKey key;
    private final JwtSettings settings;

    public TokenService(
            SecretKey key,
            JwtSettings settings
    ) {
        this.key = key;
        this.settings = settings;
    }

    public String create(
            User user
    ) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(settings.issuer())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(settings.ttl())))
                .claim("roles", List.of(user.getRole().name()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verify(
            String token
    ) {
        var parsed = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(settings.issuer())
                .build()
                .parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())
                || claims.getExpiration() == null
                || claims.getIssuedAt() == null
                || claims.getIssuedAt().after(new Date())
                || claims.getSubject() == null) {
            throw new JwtException("Invalid token contract");
        }
        try {
            if (Long.parseLong(claims.getSubject()) <= 0) {
                throw new IllegalArgumentException();
            }
            Object rawRoles = claims.get("roles");
            if (!(rawRoles instanceof List<?> roles) || roles.size() != 1) {
                throw new IllegalArgumentException();
            }
            if (!(roles.getFirst() instanceof String role)) {
                throw new IllegalArgumentException();
            }
            Role.valueOf(role);
        } catch (IllegalArgumentException | ClassCastException exception) {
            throw new JwtException("Invalid token claims");
        }
        return claims;
    }

    public long expiresIn() {
        return settings.ttl().toSeconds();
    }
}

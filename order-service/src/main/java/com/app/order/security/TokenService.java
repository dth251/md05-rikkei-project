package com.app.order.security;

import com.app.order.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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

    public Claims verify(
            String token
    ) {
        var parsed = Jwts.parser().verifyWith(key).requireIssuer(settings.issuer())
                .build().parseSignedClaims(token);
        Claims claims = parsed.getPayload();
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())
                || claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getIssuedAt().after(new Date()) || claims.getSubject() == null) {
            throw new JwtException("Invalid token contract");
        }
        try {
            if (Long.parseLong(claims.getSubject()) <= 0) {
                throw new IllegalArgumentException();
            }
            Object rawRoles = claims.get("roles");
            if (!(rawRoles instanceof List<?> roles) || roles.size() != 1
                    || !(roles.getFirst() instanceof String role)) {
                throw new IllegalArgumentException();
            }
            Role.valueOf(role);
        } catch (IllegalArgumentException | ClassCastException exception) {
            throw new JwtException("Invalid token claims");
        }
        return claims;
    }
}

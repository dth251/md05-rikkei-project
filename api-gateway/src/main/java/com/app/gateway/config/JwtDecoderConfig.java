package com.app.gateway.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer
    ) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT key must contain at least 32 bytes");
        }
        var decoder = NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(bytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(issuer),
                claimsValidator()
        ));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> claimsValidator() {
        return jwt -> {
            try {
                Object rawRoles = jwt.getClaims().get("roles");
                if (jwt.getSubject() == null || Long.parseLong(jwt.getSubject()) <= 0
                        || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                        || jwt.getIssuedAt().isAfter(Instant.now())
                        || !(rawRoles instanceof List<?> roles) || roles.size() != 1
                        || !(roles.getFirst() instanceof String role)
                        || !List.of("USER", "ADMIN").contains(role)) {
                    throw new IllegalArgumentException();
                }
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException exception) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Invalid token claims", null)
                );
            }
        };
    }
}

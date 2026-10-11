package com.app.payment.security;

import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtSettings.class)
public class JwtConfiguration {

    @Bean
    SecretKey jwtSigningKey(
            JwtSettings settings
    ) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(settings.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be Base64 encoded");
        }
        if (bytes.length < 32 || settings.ttl().isZero() || settings.ttl().isNegative()) {
            throw new IllegalArgumentException("JWT key must be at least 32 bytes and TTL positive");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

}

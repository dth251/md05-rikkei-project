package com.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.app.auth.repository.UserRepository;
import com.app.auth.security.JwtSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ActiveProfiles("test")
class AuthApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private SecretKey key;

    @Autowired
    private JwtSettings jwt;

    @Test
    void privatePrometheusExportsJvmMetricsWhileSensitiveEndpointsStayProtected() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void registerLoginAndReadMeStoresHashAndNormalizesEmail() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String registered = register(email.toUpperCase(), "SecurePass123!");
        long id = json.readTree(registered).path("data").path("id").asLong();
        var user = users.findByEmail(email).orElseThrow();
        assertFalse(user.getPasswordHash().equals("SecurePass123!"));
        assertTrue(passwords.matches("SecurePass123!", user.getPasswordHash()));

        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", "SecurePass123!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.tokenType").value("Bearer"))
                .andExpect(jsonPath("data.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("data").path("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.id").value(id))
                .andExpect(jsonPath("data.email").value(email))
                .andExpect(jsonPath("data.role").value("USER"))
                .andExpect(jsonPath("data.passwordHash").doesNotExist());
    }

    @Test
    void duplicateCaseInsensitiveEmailReturnsConflict() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email, "SecurePass123!");
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Another User",
                                "email", email.toUpperCase(),
                                "password", "SecurePass123!"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("success").value(false));
    }

    @Test
    void wrongAndUnknownCredentialsReturnSameError() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email, "SecurePass123!");
        for (String address : List.of(email, UUID.randomUUID() + "@example.com")) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "email", address,
                                    "password", "WrongPass123!"
                            ))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("message").value("Invalid email or password"));
        }
    }

    @Test
    void missingOrMalformedTokensAreRejected() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Basic invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenAndWrongIssuerAreRejected() throws Exception {
        String expired = signedToken("1", List.of("USER"), jwt.issuer(), Instant.now().minusSeconds(60));
        String wrongIssuer = signedToken("1", List.of("USER"), "other-issuer", Instant.now().plusSeconds(60));
        for (String token : List.of(expired, wrongIssuer)) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tokenWithoutExpirationOrWithInvalidRoleIsRejected() throws Exception {
        String missingExpiry = Jwts.builder()
                .subject("1")
                .issuer(jwt.issuer())
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .claim("roles", List.of("USER"))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        String invalidRole = signedToken("1", List.of("SUPERUSER"), jwt.issuer(), Instant.now().plusSeconds(60));
        for (String token : List.of(missingExpiry, invalidRole)) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void userCannotReadAdminRouteAndAdminCan() throws Exception {
        String response = register(UUID.randomUUID() + "@example.com", "SecurePass123!");
        String id = json.readTree(response).path("data").path("id").asText();
        String user = signedToken(id, List.of("USER"), jwt.issuer(), Instant.now().plusSeconds(60));
        String admin = signedToken(id, List.of("ADMIN"), jwt.issuer(), Instant.now().plusSeconds(60));
        mvc.perform(get("/api/auth/admin/users/" + id).header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("success").value(false));
        mvc.perform(get("/api/auth/admin/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.id").value(Long.parseLong(id)));
        mvc.perform(get("/api/auth/admin/users/not-a-number").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("success").value(false));
        mvc.perform(get("/api/auth/admin/users/9223372036854775807").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("success").value(false));
    }

    @Test
    void registrationCannotChooseAdminRole() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Escalation User",
                                "email", UUID.randomUUID() + "@example.com",
                                "password", "SecurePass123!",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidInputReturnsFieldErrorsWithoutPasswordValues() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("errors.email").exists())
                .andExpect(jsonPath("errors.password").exists());
    }

    @Test
    void bcryptRejectsUnicodePasswordExceedingByteLimit() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Unicode User",
                                "email", UUID.randomUUID() + "@example.com",
                                "password", "\u754c".repeat(25)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value("Password must not exceed 72 UTF-8 bytes"));
    }

    private String register(
            String email,
            String password
    ) throws Exception {
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "fullName", "Demo User",
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
    }

    private String signedToken(
            String subject,
            List<String> roles,
            String issuer,
            Instant expiration
    ) {
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(expiration))
                .claim("roles", roles)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}

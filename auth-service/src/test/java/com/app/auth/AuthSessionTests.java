package com.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.auth.config.exception.ApiException;
import com.app.auth.dto.request.AddressRequest;
import com.app.auth.dto.response.AuthResponse;
import com.app.auth.repository.UserRepository;
import com.app.auth.security.RefreshCredentials;
import com.app.auth.service.AddressService;
import com.app.auth.service.AuthSessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class AuthSessionTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AuthSessionService sessions;

    @Autowired
    private AddressService addresses;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshCredentials credentials;

    @Test
    void rotatingOldTokenRevokesTheFamilyInACommittedTransactionButNotOtherLogins() throws Exception {
        JsonNode login = login();
        String first = login.path("refreshToken").asText();
        String access = login.path("accessToken").asText();
        long userId = login.path("user").path("id").asLong();
        AuthResponse other = sessions.issue(users.findById(userId).orElseThrow());
        String hash = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE token_hash=?",
                String.class, credentials.hash(first));
        assertTrue(hash != null && hash.length() == 64);
        assertFalse(first.equals(hash), "Only a hash may be stored in the database");
        String rotated = refresh(first, 200).path("refreshToken").asText();
        assertFalse(first.equals(rotated), "Each refresh must rotate the credential");
        refresh(first, 401);
        refresh(rotated, 401);
        refresh(other.refreshToken(), 200);
        // Logout/replay revocation does not pretend to revoke already issued stateless access JWTs.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshHasOneWinnerThenDetectsReplayAndRevokesItsSuccessor() throws Exception {
        String first = login().path("refreshToken").asText();
        List<Callable<AuthResponse>> tasks = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            tasks.add(() -> {
                try {
                    return sessions.refresh(first);
                } catch (ApiException exception) {
                    assertEquals(401, exception.getStatus().value());
                    return null;
                }
            });
        }
        List<AuthResponse> winners = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            for (var result : executor.invokeAll(tasks)) {
                AuthResponse response = result.get();
                if (response != null) {
                    winners.add(response);
                }
            }
        }
        assertEquals(1, winners.size());
        refresh(winners.getFirst().refreshToken(), 401);
    }

    @Test
    void logoutUsingAnOldTokenRevokesCurrentTokenAndIsIdempotent() throws Exception {
        String first = login().path("refreshToken").asText();
        String current = refresh(first, 200).path("refreshToken").asText();
        for (int index = 0; index < 2; index++) {
            mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("refreshToken", first))))
                    .andExpect(status().isNoContent());
        }
        refresh(current, 401);
    }

    @Test
    void expiredUnknownAndMalformedRefreshTokensDoNotAuthenticate() throws Exception {
        String expired = login().path("refreshToken").asText();
        jdbc.update("UPDATE refresh_sessions SET expires_at=? WHERE id IN "
                + "(SELECT session_id FROM refresh_tokens WHERE token_hash=?)",
                Timestamp.from(Instant.now().minusSeconds(1)), credentials.hash(expired));
        refresh(expired, 401);
        refresh("A".repeat(43), 401);
        refresh("invalid", 400);
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/addresses")).andExpect(status().isUnauthorized());
    }

    @Test
    void addressCrudKeepsOneDefaultAndPromotesAnotherAfterDelete() throws Exception {
        JsonNode login = login();
        String bearer = "Bearer " + login.path("accessToken").asText();
        long userId = login.path("user").path("id").asLong();
        JsonNode first = createAddress(bearer, false);
        assertTrue(first.path("defaultAddress").asBoolean());
        JsonNode second = createAddress(bearer, false);
        assertFalse(second.path("defaultAddress").asBoolean());
        long secondId = second.path("id").asLong();
        for (int index = 0; index < 2; index++) {
            mvc.perform(put("/api/auth/addresses/" + secondId).header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(address(true))))
                    .andExpect(status().isOk());
            assertEquals(1, defaultCount(userId));
        }
        mvc.perform(delete("/api/auth/addresses/" + secondId).header("Authorization", bearer))
                .andExpect(status().isNoContent());
        var remaining = addresses.list(userId);
        assertEquals(1, remaining.size());
        assertTrue(remaining.getFirst().defaultAddress());
        assertEquals(first.path("id").asLong(), remaining.getFirst().id());
    }

    @Test
    void anotherUserCannotReadChangeOrDeleteAnAddress() throws Exception {
        String owner = "Bearer " + login().path("accessToken").asText();
        long addressId = createAddress(owner, true).path("id").asLong();
        String other = "Bearer " + login().path("accessToken").asText();
        mvc.perform(get("/api/auth/addresses/" + addressId).header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/auth/addresses/" + addressId).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(address(true))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/auth/addresses/" + addressId).header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/addresses/" + addressId).header("Authorization", owner))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentAddressCreatesCannotExceedTenOrCreateTwoDefaults() throws Exception {
        long userId = login().path("user").path("id").asLong();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            tasks.add(() -> {
                try {
                    addresses.create(userId, address(true));
                    return 201;
                } catch (ApiException exception) {
                    return exception.getStatus().value();
                }
            });
        }
        int created = 0;
        int rejected = 0;
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(tasks)) {
                int status = result.get();
                if (status == 201) {
                    created++;
                } else {
                    assertEquals(409, status);
                    rejected++;
                }
            }
        }
        assertEquals(10, created);
        assertEquals(10, rejected);
        assertEquals(10, addresses.list(userId).size());
        assertEquals(1, defaultCount(userId));
    }

    @Test
    void addressesRejectInvalidFieldsAndCallerSuppliedOwnership() throws Exception {
        String bearer = "Bearer " + login().path("accessToken").asText();
        mvc.perform(post("/api/auth/addresses").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"recipientName\":\" \"}"))
                .andExpect(status().isBadRequest());
        var request = json.valueToTree(address(true));
        ((com.fasterxml.jackson.databind.node.ObjectNode) request).put("userId", 123);
        mvc.perform(post("/api/auth/addresses").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    private int defaultCount(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM addresses WHERE user_id=? AND default_user_key IS NOT NULL",
                Integer.class, userId);
    }

    private AddressRequest address(boolean makeDefault) {
        return new AddressRequest("Fixture Recipient", "0900000000", "Fixture street", "Fixture ward",
                "Fixture district", "Fixture province", "10000", makeDefault);
    }

    private JsonNode createAddress(String bearer, boolean makeDefault) throws Exception {
        String response = mvc.perform(post("/api/auth/addresses").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(address(makeDefault))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }

    private JsonNode refresh(String token, int expectedStatus) throws Exception {
        String response = mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", token))))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }

    private JsonNode login() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fullName", "Session fixture", "email", email,
                        "password", "SessionFixture123!"))))
                .andExpect(status().isCreated());
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", "SessionFixture123!"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data");
    }
}

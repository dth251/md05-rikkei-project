package com.app.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class OpenApiTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private SecretKey key;

    @Test
    void privateDocumentRequiresAdminAndExportsRealControllerSchemas() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isForbidden());
        String document = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var contract = json.readTree(document);
        assertTrue(contract.path("openapi").asText().startsWith("3."));
        assertEquals("auth-service", contract.path("info").path("title").asText());
        assertTrue(contract.path("paths").has("/api/auth/refresh"));
        assertFalse(contract.path("paths").has("/actuator/prometheus"));
        assertFalse(contract.path("paths").has("/v3/api-docs"));
        assertEquals("bearer", contract.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText());
        assertTrue(contract.path("paths").path("/api/auth/refresh").path("post").path("security").isEmpty());
        assertTrue(contract.path("paths").path("/api/auth/register").path("post").path("responses").has("201"));
        assertTrue(contract.path("components").path("schemas").path("LoginRequest").path("properties").path("password").path("writeOnly").asBoolean());
        assertTrue(contract.path("components").path("schemas").path("RefreshRequest").path("properties").path("refreshToken").path("writeOnly").asBoolean());
        mvc.perform(get("/swagger-ui/index.html").header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
        Path exported = Path.of("build", "openapi", "auth-service.json");
        Files.createDirectories(exported.getParent());
        Files.writeString(exported, json.writerWithDefaultPrettyPrinter().writeValueAsString(contract) + "\n");
    }

    private String token(String role) {
        return Jwts.builder().issuer("omnichannel-auth").subject("1")
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }
}

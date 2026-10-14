package com.app.payment;

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
        assertEquals("payment-service", contract.path("info").path("title").asText());
        assertTrue(contract.path("paths").has("/api/payments/{id}"));
        assertTrue(contract.path("components").path("schemas").has("ApiError"));
        var references = java.util.regex.Pattern.compile("\\\"\\$ref\\\"\\s*:\\s*\\\"#/components/schemas/([^\\\"]+)\\\"").matcher(document);
        while (references.find()) {
            assertTrue(contract.path("components").path("schemas").has(references.group(1)),
                    "Missing schema reference: " + references.group(1));
        }
        assertFalse(contract.path("paths").has("/actuator/prometheus"));
        assertFalse(contract.path("paths").has("/v3/api-docs"));
        assertEquals("bearer", contract.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asText());
        assertEquals("ADMIN", contract.path("paths").path("/api/payments/{id}/simulate").path("post").path("x-required-role").asText());
        assertEquals("ADMIN", contract.path("paths").path("/api/payments/{id}/cod/collect").path("post").path("x-required-role").asText());
        assertEquals("ADMIN", contract.path("paths").path("/api/payments/{id}/cod/refund").path("post").path("x-required-role").asText());
        assertEquals("USER", contract.path("paths").path("/api/payments/{id}/cod/refund").path("get").path("x-required-role").asText());
        assertEquals("ADMIN", contract.path("paths").path("/api/payments/{id}/simulate-refund").path("post").path("x-required-role").asText());
        assertTrue(contract.path("components").path("schemas").has("CollectCodRequest"));
        var returnContent = contract.path("paths").path("/api/payments/vnpay/return")
                .path("get").path("responses").path("200").path("content");
        assertTrue(returnContent.has("text/html"));
        assertTrue(returnContent.path("application/json").path("schema").has("$ref"));
        assertTrue(contract.path("components").path("schemas").path("VnpayReturnResponse")
                .path("properties").has("paymentStatus"));
        mvc.perform(get("/swagger-ui/index.html").header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
        Path exported = Path.of("build", "openapi", "payment-service.json");
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

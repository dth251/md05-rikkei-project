package com.app.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.product.repository.ProductRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ActiveProfiles("test")
class ProductApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ProductRepository products;

    @Autowired
    private SecretKey key;

    @Test
    void privatePrometheusExportsJvmMetricsWhileSensitiveEndpointsStayProtected() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorCreatesUpdatesAndSoftDeletesProduct() throws Exception {
        String sku = "sku-" + UUID.randomUUID();
        long id = create(sku);
        mvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("data.sku").value(sku.toUpperCase()));
        mvc.perform(put("/api/products/" + id).header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(sku, "Updated", "15.50")))
                .andExpect(status().isOk()).andExpect(jsonPath("data.name").value("Updated"));
        mvc.perform(delete("/api/products/" + id).header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        assertFalse(products.findById(id).orElseThrow().isActive());
    }

    @Test
    void anonymousMayReadCatalogButCannotWrite() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("PUBLIC", "Product", "10.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryUserCannotManageProducts() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("USER", "Product", "10.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateNormalizedSkuReturns409() throws Exception {
        String sku = "dup-" + UUID.randomUUID();
        create(sku);
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(sku.toUpperCase(), "Duplicate", "10.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPriceAndUnknownFieldReturn400() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("INVALID", "Product", "-1.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"SKU\",\"name\":\"P\",\"unitPrice\":1,\"active\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPaginationAndIdReturn400() throws Exception {
        mvc.perform(get("/api/products?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/not-a-number")).andExpect(status().isBadRequest());
    }

    @Test
    void wrongJwtAndSpoofedUserHeaderDoNotGrantAccess() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", "Bearer invalid")
                        .header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
                        .content(body("SPOOF", "Product", "10.00")))
                .andExpect(status().isUnauthorized());
    }

    private long create(
            String sku
    ) throws Exception {
        String response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + token("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(sku, "Product", "10.00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("id").asLong();
    }

    private String body(
            String sku,
            String name,
            String price
    ) throws Exception {
        return json.writeValueAsString(Map.of("sku", sku, "name", name, "unitPrice", price));
    }

    private String token(
            String role
    ) {
        return Jwts.builder().subject("1").issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }
}

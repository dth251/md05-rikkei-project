package com.app.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.product.config.exception.ApiException;
import com.app.product.dto.request.ProductRequest;
import com.app.product.dto.request.VariantRequest;
import com.app.product.service.ProductService;
import com.app.product.service.ProductVariantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class ProductSearchVariantTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private SecretKey key;

    @Autowired
    private ProductService products;

    @Autowired
    private ProductVariantService variants;

    @Test
    void searchCombinesPriceCategoryBrandSortAndPagination() throws Exception {
        String category = "Case-" + UUID.randomUUID();
        for (String price : List.of("10.00", "20.00", "30.00")) {
            products.create(request("Search fixture", category, price));
        }
        mvc.perform(get("/api/products").param("category", category.toUpperCase())
                .param("brand", "fixture BRAND").param("minPrice", "11").param("maxPrice", "30")
                .param("sort", "price-desc").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(2))
                .andExpect(jsonPath("data.totalPages").value(2))
                .andExpect(jsonPath("data.content[0].unitPrice").value(30.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    void textSearchTreatsSqlLikeSymbolsLiterally(String symbol) throws Exception {
        String category = UUID.randomUUID().toString();
        products.create(request("Literal " + symbol, category, "10.00"));
        products.create(request("Literal replacement", category, "20.00"));
        mvc.perform(get("/api/products").param("category", category).param("q", symbol))
                .andExpect(status().isOk()).andExpect(jsonPath("data.totalElements").value(1))
                .andExpect(jsonPath("data.content[0].name").value("Literal " + symbol));
    }

    @Test
    void invalidSearchCannotPassArbitrarySortPriceRangeOrUnboundedPage() throws Exception {
        mvc.perform(get("/api/products").param("minPrice", "30").param("maxPrice", "20"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("sort", "unitPrice;drop table products"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("page", "10001"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("minPrice", "1e10000"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("q", "q".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void variantsRequireAdminAndKeepSkuAndAttributesImmutable() throws Exception {
        long parent = products.create(request("Parent fixture", "variants", "10.00")).id();
        var request = new VariantRequest(request("Blue medium", "variants", "12.00"),
                Map.of("color", " BLUE ", "size", "M"));
        mvc.perform(post("/api/products/" + parent + "/variants").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products/" + parent + "/variants").header("Authorization", bearer("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        String response = mvc.perform(post("/api/products/" + parent + "/variants")
                .header("Authorization", bearer("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isCreated())
                .andExpect(jsonPath("data.parentId").value(parent))
                .andExpect(jsonPath("data.attributes.color").value("blue"))
                .andReturn().getResponse().getContentAsString();
        long child = json.readTree(response).path("data").path("id").asLong();
        mvc.perform(get("/api/products/" + parent + "/variants")).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1));
        mvc.perform(post("/api/products/" + child + "/variants").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/products/" + child).header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request("Changed identity", "variants", "15.00"))))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentEquivalentVariantDefinitionsCreateOnlyOneSku() throws Exception {
        long parent = products.create(request("Race parent", "race", "10.00")).id();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            final boolean upper = index % 2 == 0;
            tasks.add(() -> {
                try {
                    variants.create(parent, new VariantRequest(request("Race variant", "race", "12.00"),
                            Map.of("color", upper ? "\u00A0BLUE\u00A0" : "blue", "size", upper ? "M" : "m")));
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
                if (result.get() == 201) {
                    created++;
                } else {
                    assertEquals(409, result.get());
                    rejected++;
                }
            }
        }
        assertEquals(1, created);
        assertEquals(19, rejected);
        assertEquals(1, variants.list(parent).size());
    }

    @Test
    void deletingParentRetiresChildrenAndRemovesThemFromPublicSearch() throws Exception {
        String category = UUID.randomUUID().toString();
        long parent = products.create(request("Retired parent", category, "10.00")).id();
        long child = variants.create(parent, new VariantRequest(request("Retired child", category, "12.00"),
                Map.of("size", "M"))).id();
        mvc.perform(delete("/api/products/" + parent).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/products/" + child)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("category", category)).andExpect(status().isOk())
                .andExpect(jsonPath("data.totalElements").value(0));
        assertFalse(products.search(new com.app.product.dto.request.ProductSearchRequest(
                0, 20, null, category, null, null, null, null)).content().stream().anyMatch(p -> p.id().equals(child)));
    }

    @Test
    void unicodeNormalizationCannotOverflowThePersistedAttributeSize() throws Exception {
        long parent = products.create(request("Unicode parent", "unicode", "10.00")).id();
        var oversized = new VariantRequest(request("Unicode variant", "unicode", "10.00"),
                Map.of("size", "\uFB03".repeat(30)));
        mvc.perform(post("/api/products/" + parent + "/variants").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(oversized)))
                .andExpect(status().isBadRequest());
    }

    private ProductRequest request(String name, String category, String price) {
        return new ProductRequest("VAR-" + UUID.randomUUID(), name, "Fixture description", new BigDecimal(price),
                category, "Fixture Brand");
    }

    private String bearer(String role) {
        return "Bearer " + Jwts.builder().subject("7").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(Instant.now().plusSeconds(120))).claim("roles", List.of(role))
                .signWith(key, Jwts.SIG.HS256).compact();
    }
}

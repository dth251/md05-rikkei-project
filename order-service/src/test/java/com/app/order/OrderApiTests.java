package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.order.dto.response.ProductSnapshot;
import com.app.order.service.ProductCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiTests {

    private static final AtomicLong IDS = new AtomicLong(10000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SecretKey key;

    @MockitoBean
    private ProductCatalog products;

    @BeforeEach
    void catalog() {
        when(products.get(anyLong())).thenAnswer(call -> new ProductSnapshot(
                call.getArgument(0), "SNAPSHOT", "Product Snapshot", new BigDecimal("12.50")
        ));
    }

    @Test
    void webOrderReplaysWithoutReservingAgainAndRejectsChangedPayload() throws Exception {
        long product = stock(3);
        String requestKey = UUID.randomUUID().toString();
        String body = body("WEB", requestKey, product, 2);
        String response = mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.totalAmount").value(25.00))
                .andExpect(jsonPath("data.status").value("PENDING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).path("data").path("id").asLong();
        mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("data.id").value(id));
        mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("POS", requestKey, product, 2)))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT available FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(2, jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id=?", Integer.class, product));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE payload LIKE ?",
                Integer.class, "%\"orderId\":" + id + ",%"));
    }

    @Test
    void posOrderIsVisibleOnlyToOwnerOrAdmin() throws Exception {
        long product = stock(1);
        String response = mvc.perform(post("/api/orders").header("Authorization", token(2, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("POS", UUID.randomUUID().toString(), product, 1)))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.channel").value("POS"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).path("data").path("id").asLong();
        mvc.perform(get("/api/orders/" + id).header("Authorization", token(3, "USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + id).header("Authorization", token(3, "ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/orders/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void multiItemStockFailureRollsBackOrderAndEarlierReservation() throws Exception {
        long first = stock(1);
        long second = stock(0);
        String requestKey = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("channel", "WEB", "idempotencyKey", requestKey,
                "items", List.of(Map.of("productId", first, "quantity", 1), Map.of("productId", second, "quantity", 1))));
        mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT available FROM inventory WHERE product_id=?", Integer.class, first));
        assertEquals(0, jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id=?", Integer.class, first));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM orders WHERE idempotency_key=?", Integer.class, requestKey));
    }

    @Test
    void rejectsDuplicateItemsInvalidQuantityAndClientPrice() throws Exception {
        long product = stock(1);
        var item = Map.of("productId", product, "quantity", 1);
        var body = Map.of("channel", "WEB", "idempotencyKey", "duplicate", "items", List.of(item, item));
        mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("WEB", "invalid", product, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"channel\":\"WEB\",\"idempotencyKey\":\"bad\",\"unitPrice\":1,\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stockRequiresAdminAndInventoryRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/inventory/stocks").header("Authorization", token(1, "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/1")).andExpect(status().isUnauthorized());
    }

    private long stock(int quantity) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO inventory (product_id, available, reserved, version) VALUES (?, ?, 0, 0)", id, quantity);
        return id;
    }

    private String body(
        String channel,
        String requestKey,
        long productId,
        int quantity
    ) throws Exception {
        return json.writeValueAsString(Map.of("channel", channel, "idempotencyKey", requestKey,
                "items", List.of(Map.of("productId", productId, "quantity", quantity))));
    }

    private String token(
        long userId,
        String role
    ) {
        return "Bearer " + Jwts.builder().subject(Long.toString(userId)).issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }
}

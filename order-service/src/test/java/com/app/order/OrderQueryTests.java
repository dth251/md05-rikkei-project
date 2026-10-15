package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.order.dto.request.OrderSearchRequest;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.SalesChannel;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderQueryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderQueryTests {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderQueryService queries;

    @Autowired
    private MockMvc mvc;

    @Test
    void customerOnlySeesOwnOrdersAndCannotBypassOwnershipWithQueryParameters() throws Exception {
        save(90001L, SalesChannel.WEB);
        save(90002L, SalesChannel.WEB);
        var result = queries.list(90001L, false, new OrderSearchRequest(0, 10, null, null));
        assertEquals(1, result.totalElements());
        assertTrue(result.content().stream().allMatch(order -> order.userId().equals(90001L)));
        String response = mvc.perform(get("/api/orders?userId=90002&administrator=true")
                        .header("Authorization", "Bearer " + token(90001L, "USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
        assertEquals(1, json.path("data").path("totalElements").asInt());
        assertEquals(90001L, json.path("data").path("content").get(0).path("userId").asLong());
    }

    @Test
    void administratorCanPageAcrossCustomersAndFilterChannelAndStatus() {
        var web = save(90003L, SalesChannel.WEB);
        var pos = save(90004L, SalesChannel.POS);
        var result = queries.list(90003L, true, new OrderSearchRequest(0, 100, OrderStatus.PENDING_PAYMENT, SalesChannel.POS));
        assertTrue(result.content().stream().anyMatch(order -> order.id().equals(pos.getId())));
        assertTrue(result.content().stream().noneMatch(order -> order.id().equals(web.getId())));
        var first = queries.list(90003L, true, new OrderSearchRequest(0, 1, null, null));
        var second = queries.list(90003L, true, new OrderSearchRequest(1, 1, null, null));
        assertEquals(1, first.content().size());
        assertTrue(!first.content().get(0).id().equals(second.content().get(0).id()));
        assertTrue(first.totalElements() >= 2);
    }

    @Test
    void paginationIsBoundedAndAnonymousCannotListOrders() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        for (String query : List.of("page=-1", "page=10001", "size=0", "size=101", "status=UNKNOWN")) {
            mvc.perform(get("/api/orders?" + query).header("Authorization", "Bearer " + token(90001L, "USER")))
                    .andExpect(status().isBadRequest());
        }
    }

    private PurchaseOrder save(Long userId, SalesChannel channel) {
        var order = new PurchaseOrder(userId, UUID.randomUUID().toString(), channel);
        order.addItem(1L, "TEST", "Query fixture", new BigDecimal("125000"), 1);
        return orders.saveAndFlush(order);
    }

    private String token(Long userId, String role) {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
        return Jwts.builder().issuer("omnichannel-auth").subject(userId.toString())
                .issuedAt(Date.from(Instant.now().minusSeconds(1)))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }
}

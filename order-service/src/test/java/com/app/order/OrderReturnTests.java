package com.app.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.event.CommerceEvent;
import com.app.order.dto.request.DecideReturnRequest;
import com.app.order.dto.request.ReceiveReturnRequest;
import com.app.order.dto.request.RequestReturnRequest;
import com.app.order.dto.request.ShippingAddressRequest;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.ReturnStatus;
import com.app.order.entity.SalesChannel;
import com.app.order.mapper.ShippingAddressMapper;
import com.app.order.repository.EventInbox;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderReturnService;
import com.app.order.service.ProductCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class OrderReturnTests {

    private static final AtomicLong PRODUCTS = new AtomicLong(12000000);

    @Autowired private OrderReturnService returns;
    @Autowired private OrderRepository orders;
    @Autowired private ShippingAddressMapper addresses;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SecretKey key;
    @MockitoBean private EventInbox inbox;
    @MockitoBean private ProductCatalog catalog;

    @BeforeEach
    void claim() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void requestRequiresCompletedOwnedOrderAndReplaysOnlySameContent() {
        var order = completed();
        var request = request();
        assertEquals(404, assertThrows(ApiException.class, () -> returns.request(order.getId(), 78L, false, request))
                .getStatus().value());
        var result = returns.request(order.getId(), 77L, false, request);
        assertEquals(ReturnStatus.REQUESTED, result.status());
        assertEquals(result, returns.request(order.getId(), 77L, false, request));
        assertEquals(409, assertThrows(ApiException.class, () -> returns.request(order.getId(), 77L, false,
                new RequestReturnRequest(request.idempotencyKey(), "Changed reason"))).getStatus().value());
        assertEquals(404, assertThrows(ApiException.class, () -> returns.get(order.getId(), 78L, false)).getStatus().value());
        var pending = orders.saveAndFlush(new PurchaseOrder(77L, UUID.randomUUID().toString(), SalesChannel.WEB));
        assertEquals(409, assertThrows(ApiException.class, () -> returns.request(pending.getId(), 77L, false, request))
                .getStatus().value());
    }

    @Test
    void httpRequiresAdministratorForDecisionAndReceiptAndValidatesBody() throws Exception {
        var order = completed();
        String path = "/api/orders/" + order.getId() + "/return";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", bearer(77, "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"x\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization", bearer(77, "USER"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request())))
                .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(78, "USER"))).andExpect(status().isNotFound());
        mvc.perform(post(path + "/decision").header("Authorization", bearer(77, "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"note\":\"OK\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/receive").header("Authorization", bearer(77, "USER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"warehouseReceipt\":\"x\",\"restock\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/decision").header("Authorization", bearer(77, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"note\":\"OK\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/receive").header("Authorization", bearer(77, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"warehouseReceipt\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedReturnCannotReceiveOrRefundAndDecisionCannotBeOverwritten() {
        var order = completed();
        returns.request(order.getId(), 77L, false, request());
        assertEquals(409, assertThrows(ApiException.class, () -> returns.receive(order.getId(), 99L, receipt(true)))
                .getStatus().value());
        var rejection = new DecideReturnRequest(false, "Outside policy");
        returns.decide(order.getId(), 99L, rejection);
        assertEquals(ReturnStatus.REJECTED, returns.decide(order.getId(), 99L, rejection).status());
        assertEquals(409, assertThrows(ApiException.class, () -> returns.decide(order.getId(), 99L,
                new DecideReturnRequest(true, "Changed"))).getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> returns.receive(order.getId(), 99L, receipt(true)))
                .getStatus().value());
        assertThrows(IllegalArgumentException.class, () -> returns.acceptRefund(event(order, "payment.return-refunded", 123L)));
        assertEquals(3, available(order));
        assertEquals(0, count(order, "order.return-received"));
    }

    @Test
    void concurrentFullReceiptRestocksEveryItemOnceAndRefundAcknowledgementDoesNotRestockAgain() throws Exception {
        var order = approved();
        var receipt = receipt(true);
        race(() -> { returns.receive(order.getId(), 99L, receipt); return true; });
        assertEquals(5, available(order));
        assertEquals(1, count(order, "order.return-received"));
        assertEquals(409, assertThrows(ApiException.class, () -> returns.receive(order.getId(), 99L, receipt(false)))
                .getStatus().value());
        // Broker may deliver completion before the ready event; the terminal state must not regress.
        returns.acceptRefund(event(order, "payment.return-refunded", 123L));
        returns.acceptRefund(event(order, "payment.return-refund-ready", 123L));
        returns.acceptRefund(event(order, "payment.return-refunded", 123L));
        assertEquals(ReturnStatus.REFUNDED, returns.get(order.getId(), 77L, false).status());
        assertNotNull(returns.get(order.getId(), 77L, false).refundedAt());
        assertEquals(OrderStatus.COMPLETED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(5, available(order));
        assertThrows(IllegalArgumentException.class, () -> returns.acceptRefund(event(order, "payment.return-refunded", 124L)));
    }

    @Test
    void damagedGoodsCanBeReceivedWithoutRestocking() {
        var order = approved();
        var receipt = receipt(false);
        assertEquals(ReturnStatus.RECEIVED, returns.receive(order.getId(), 99L, receipt).status());
        returns.receive(order.getId(), 99L, receipt);
        assertEquals(3, available(order));
        assertEquals(1, count(order, "order.return-received"));
    }

    @Test
    void overflowRollsBackTheEntireReceiptAndAllItemStockUpdates() {
        var order = completed(true);
        returns.request(order.getId(), 77L, false, request());
        returns.decide(order.getId(), 99L, new DecideReturnRequest(true, "Approved"));
        var receipt = receipt(true);
        assertEquals(409, assertThrows(ApiException.class, () -> returns.receive(order.getId(), 99L, receipt))
                .getStatus().value());
        assertEquals(3, available(order));
        assertEquals(ReturnStatus.APPROVED, returns.get(order.getId(), 77L, false).status());
        assertEquals(0, count(order, "order.return-received"));
    }

    @Test
    void duplicateWarehouseReceiptOnAnotherOrderRollsBackRestock() {
        var first = approved();
        var second = approved();
        var receipt = receipt(true);
        returns.receive(first.getId(), 99L, receipt);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                returns.receive(second.getId(), 99L, receipt));
        assertEquals(3, available(second));
        assertEquals(ReturnStatus.APPROVED, returns.get(second.getId(), 77L, false).status());
    }

    private PurchaseOrder approved() {
        var order = completed();
        returns.request(order.getId(), 77L, false, request());
        returns.decide(order.getId(), 99L, new DecideReturnRequest(true, "Approved"));
        return order;
    }

    private PurchaseOrder completed() { return completed(false); }

    private PurchaseOrder completed(boolean overflowItem) {
        long productId = PRODUCTS.incrementAndGet();
        jdbc.update("INSERT INTO inventory(product_id,available,reserved,version) VALUES(?,3,0,0)", productId);
        var order = new PurchaseOrder(77L, UUID.randomUUID().toString(), SalesChannel.WEB);
        order.addItem(productId, "RETURN", "Return fixture", BigDecimal.TEN, 2);
        if (overflowItem) {
            long second = PRODUCTS.incrementAndGet();
            jdbc.update("INSERT INTO inventory(product_id,available,reserved,version) VALUES(?,2147483647,0,0)", second);
            order.addItem(second, "RETURN-OVERFLOW", "Overflow", BigDecimal.TEN, 1);
        }
        order.setShippingAddress(addresses.toEntity(new ShippingAddressRequest("Return fixture", "0901234567",
                "Street", "Ward", "District", "Province", null)));
        order.completePayment(true);
        order.ship("Fixture Carrier", "TRACK", Instant.now());
        order.complete(Instant.now());
        return orders.saveAndFlush(order);
    }

    private RequestReturnRequest request() { return new RequestReturnRequest(UUID.randomUUID().toString(), "Return all goods"); }
    private ReceiveReturnRequest receipt(boolean restock) { return new ReceiveReturnRequest(UUID.randomUUID().toString(), restock); }

    private CommerceEvent event(PurchaseOrder order, String type, Long paymentId) {
        return new CommerceEvent(UUID.randomUUID().toString(), type, order.getId(), order.getUserId(),
                order.getTotalAmount(), paymentId, Instant.now());
    }

    private int available(PurchaseOrder order) {
        return jdbc.queryForObject("SELECT available FROM inventory WHERE product_id=?", Integer.class,
                jdbc.queryForObject("SELECT min(product_id) FROM order_items WHERE order_id=?", Long.class, order.getId()));
    }

    private int count(PurchaseOrder order, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type=? AND payload LIKE ?", Integer.class,
                type, "%\"orderId\":" + order.getId() + ",%");
    }

    private void race(Callable<Boolean> task) throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(java.util.Collections.nCopies(24, task))) {
                assertEquals(true, result.get());
            }
        }
    }

    private String bearer(long userId, String role) {
        return "Bearer " + Jwts.builder().subject(Long.toString(userId)).issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
                .claim("roles", List.of(role)).signWith(key).compact();
    }
}

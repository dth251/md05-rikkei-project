package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.order.config.exception.ApiException;
import com.app.order.dto.event.CommerceEvent;
import com.app.order.dto.request.CreateOrderRequest;
import com.app.order.dto.request.OrderItemRequest;
import com.app.order.dto.request.ShipOrderRequest;
import com.app.order.dto.request.ShippingAddressRequest;
import com.app.order.dto.response.ProductSnapshot;
import com.app.order.entity.OrderStatus;
import com.app.order.entity.PurchaseOrder;
import com.app.order.entity.SalesChannel;
import com.app.order.entity.PaymentMethod;
import com.app.order.service.ReservationExpiryService;
import com.app.order.mapper.ShippingAddressMapper;
import com.app.order.repository.EventInbox;
import com.app.order.repository.OrderRepository;
import com.app.order.service.OrderFulfillmentService;
import com.app.order.service.OrderPaymentService;
import com.app.order.service.OrderService;
import com.app.order.service.ProductCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
class OrderFulfillmentTests {

    private static final AtomicLong PRODUCTS = new AtomicLong(960000);

    @Autowired
    private OrderFulfillmentService fulfillment;
    @Autowired
    private OrderPaymentService payments;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private ShippingAddressMapper addresses;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private SecretKey key;
    @Autowired
    private ReservationExpiryService expiry;
    @MockitoBean
    private ProductCatalog catalog;
    @MockitoBean
    private EventInbox inbox;

    @BeforeEach
    void fixtures() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
        when(catalog.get(anyLong())).thenAnswer(call ->
                new ProductSnapshot(call.getArgument(0), "SHIP", "Shipment fixture", BigDecimal.TEN));
        when(catalog.getAll(anyList())).thenAnswer(call ->
                call.<List<Long>>getArgument(0).stream().map(catalog::get).toList());
    }

    @Test
    void fulfillmentRequiresAdminAndValidConfirmedOrder() throws Exception {
        PurchaseOrder pending = fixture(false, true);
        String path = "/api/orders/" + pending.getId();
        var request = new ShipOrderRequest("Carrier", "TRACK", null);
        mvc.perform(post(path + "/ship").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request))).andExpect(status().isUnauthorized());
        mvc.perform(post(path + "/ship").header("Authorization", bearer("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/complete").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post(path + "/ship").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
        mvc.perform(post(path + "/ship").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ShipOrderRequest("Carrier", " ", null))))
                .andExpect(status().isBadRequest());
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findById(pending.getId()).orElseThrow().getStatus());
        assertEquals(0, count(pending, "order.shipped"));
    }

    @Test
    void concurrentShipAndCompleteReplayProduceOnlyOneEventPerStage() throws Exception {
        PurchaseOrder order = fixture(true, true);
        var request = new ShipOrderRequest("Carrier", "TRACK-" + UUID.randomUUID(), null);
        race(() -> { fulfillment.ship(order.getId(), request); return true; });
        var shipped = fulfillment.ship(order.getId(), request);
        assertNotNull(shipped.shipment().shippedAt());
        assertEquals(1, count(order, "order.shipped"));
        race(() -> { fulfillment.complete(order.getId()); return true; });
        var completed = fulfillment.complete(order.getId());
        assertEquals(OrderStatus.COMPLETED, completed.status());
        assertEquals(shipped.shipment().shippedAt(), completed.shipment().shippedAt());
        assertNotNull(completed.shipment().completedAt());
        assertEquals(1, count(order, "order.completed"));
        assertEquals(completed, fulfillment.ship(order.getId(), request));
        payments.accept(new CommerceEvent(UUID.randomUUID().toString(), "payment.failed", order.getId(),
                order.getUserId(), BigDecimal.TEN, 1L, Instant.now()));
        assertEquals(OrderStatus.COMPLETED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(4, jdbc.queryForObject("SELECT available FROM inventory WHERE product_id=?", Integer.class,
                productId(order)));
        assertEquals(0, jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id=?", Integer.class,
                productId(order)));
    }

    @Test
    void differentShipmentOrAddressCannotOverwriteSnapshot() {
        PurchaseOrder order = fixture(true, true);
        fulfillment.ship(order.getId(), new ShipOrderRequest("Carrier", "TRACK", null));
        ApiException carrier = assertThrows(ApiException.class, () ->
                fulfillment.ship(order.getId(), new ShipOrderRequest("Other carrier", "TRACK", null)));
        assertEquals(409, carrier.getStatus().value());
        ApiException address = assertThrows(ApiException.class, () -> fulfillment.ship(order.getId(),
                new ShipOrderRequest("Carrier", "TRACK", address("Changed street"))));
        assertEquals(409, address.getStatus().value());
        assertEquals("Original street", fulfillment.complete(order.getId()).shippingAddress().street());
        assertEquals(1, count(order, "order.shipped"));
    }

    @Test
    void legacyOrderNeedsAddressAndCannotSkipShipping() {
        PurchaseOrder order = fixture(true, false);
        assertEquals(409, assertThrows(ApiException.class, () -> fulfillment.complete(order.getId())).getStatus().value());
        assertEquals(400, assertThrows(ApiException.class, () ->
                fulfillment.ship(order.getId(), new ShipOrderRequest("Carrier", "TRACK", null))).getStatus().value());
        var shipped = fulfillment.ship(order.getId(), new ShipOrderRequest("Carrier", "TRACK", address("Original street")));
        assertEquals(OrderStatus.SHIPPING, shipped.status());
        assertEquals("Recipient fixture", shipped.shippingAddress().recipientName());
        assertEquals(OrderStatus.COMPLETED, fulfillment.complete(order.getId()).status());
    }

    @Test
    void createOrderReplaysOriginalAddressAndRejectsChangedAddress() {
        long productId = PRODUCTS.incrementAndGet();
        jdbc.update("INSERT INTO inventory(product_id,available,reserved,version) VALUES(?,2,0,0)", productId);
        String idempotency = UUID.randomUUID().toString();
        var request = new CreateOrderRequest(SalesChannel.WEB, idempotency,
                List.of(new OrderItemRequest(productId, 1)), address("Original street"));
        var created = orderService.create(961L, request);
        assertEquals("Original street", created.order().shippingAddress().street());
        assertEquals(false, orderService.create(961L, request).created());
        assertEquals(409, assertThrows(ApiException.class, () -> orderService.create(961L,
                new CreateOrderRequest(SalesChannel.WEB, idempotency, request.items(), address("Changed street"))))
                .getStatus().value());
        assertEquals(1, jdbc.queryForObject("SELECT reserved FROM inventory WHERE product_id=?", Integer.class, productId));
    }

    @Test
    void codCreateConfirmsWithoutPaymentAndReplaysOnlySameMethod() {
        var order = cod();
        assertEquals(OrderStatus.CONFIRMED, order.status());
        assertEquals(PaymentMethod.COD, order.paymentMethod());
        assertEquals(null, order.codPaidAt());
        assertEquals(1, stock(order.id(), "reserved"));
        assertEquals(1, stock(order.id(), "available"));
        String requestKey = jdbc.queryForObject("SELECT idempotency_key FROM orders WHERE id=?", String.class, order.id());
        var request = new CreateOrderRequest(SalesChannel.WEB, requestKey,
                List.of(new OrderItemRequest(product(order.id()), 1)), address("Original street"), PaymentMethod.COD);
        assertEquals(false, orderService.create(961L, request).created());
        assertEquals(409, assertThrows(ApiException.class, () -> orderService.create(961L,
                new CreateOrderRequest(request.channel(), requestKey, request.items(), request.shippingAddress())))
                .getStatus().value());
        assertEquals(false, expiry.expire(order.id(), Instant.now().plusSeconds(3600)));
        assertEquals(1, stock(order.id(), "reserved"));
    }

    @Test
    void codRequiresAddressAndCancellationRequiresAdmin() throws Exception {
        long productId = PRODUCTS.incrementAndGet();
        var request = new CreateOrderRequest(SalesChannel.WEB, UUID.randomUUID().toString(),
                List.of(new OrderItemRequest(productId, 1)), null, PaymentMethod.COD);
        assertEquals(400, assertThrows(ApiException.class, () -> orderService.create(961L, request)).getStatus().value());
        var order = cod();
        mvc.perform(post("/api/orders/" + order.id() + "/cod/cancel")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/" + order.id() + "/cod/cancel").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/orders/" + order.id() + "/cod/cancel").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void codConcurrentShipmentFinalizesStockOnceAndCompletionWaitsForCash() throws Exception {
        var order = cod();
        var request = new ShipOrderRequest("COD Carrier", "COD-TRACK", null);
        race(() -> { fulfillment.ship(order.id(), request); return true; });
        assertEquals(0, stock(order.id(), "reserved"));
        assertEquals(1, stock(order.id(), "available"));
        assertEquals(1, count(orders.findById(order.id()).orElseThrow(), "order.cod-shipped"));
        assertEquals(409, assertThrows(ApiException.class, () -> fulfillment.complete(order.id())).getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> fulfillment.cancelCod(order.id())).getStatus().value());
        payments.accept(new CommerceEvent(UUID.randomUUID().toString(), "payment.succeeded", order.id(), 961L,
                BigDecimal.TEN, 900L, Instant.now()));
        payments.accept(new CommerceEvent(UUID.randomUUID().toString(), "payment.succeeded", order.id(), 961L,
                BigDecimal.TEN, 900L, Instant.now()));
        assertEquals(900L, fulfillment.complete(order.id()).codPaymentId());
        race(() -> { fulfillment.complete(order.id()); return true; });
        assertEquals(OrderStatus.COMPLETED, orderService.get(order.id(), 961L, false).status());
        assertEquals(1, count(orders.findById(order.id()).orElseThrow(), "order.confirmed"));
        assertEquals(0, stock(order.id(), "reserved"));
        assertEquals(1, stock(order.id(), "available"));
    }

    @Test
    void codConcurrentCancellationRestoresStockOnce() throws Exception {
        var order = cod();
        race(() -> { fulfillment.cancelCod(order.id()); return true; });
        assertEquals(OrderStatus.CANCELLED, fulfillment.cancelCod(order.id()).status());
        assertEquals(2, stock(order.id(), "available"));
        assertEquals(0, stock(order.id(), "reserved"));
        assertEquals(1, count(orders.findById(order.id()).orElseThrow(), "order.cod-cancelled"));
        assertEquals(409, assertThrows(ApiException.class, () -> fulfillment.ship(order.id(),
                new ShipOrderRequest("Carrier", "TRACK", null))).getStatus().value());
        payments.accept(new CommerceEvent(UUID.randomUUID().toString(), "payment.failed", order.id(), 961L,
                BigDecimal.TEN, 901L, Instant.now()));
        assertEquals(OrderStatus.CANCELLED, orderService.get(order.id(), 961L, false).status());
    }

    @Test
    void codRejectsPrematureOrMismatchedPaymentWithoutChangingStock() {
        var order = cod();
        assertThrows(IllegalArgumentException.class, () -> payments.accept(new CommerceEvent(UUID.randomUUID().toString(),
                "payment.succeeded", order.id(), 961L, BigDecimal.TEN, 900L, Instant.now())));
        fulfillment.ship(order.id(), new ShipOrderRequest("Carrier", "TRACK", null));
        assertThrows(IllegalArgumentException.class, () -> payments.accept(new CommerceEvent(UUID.randomUUID().toString(),
                "payment.succeeded", order.id(), 962L, BigDecimal.TEN, 900L, Instant.now())));
        assertEquals(null, orderService.get(order.id(), 961L, false).codPaidAt());
        assertEquals(0, stock(order.id(), "reserved"));
    }

    private com.app.order.dto.response.OrderResponse cod() {
        long productId = PRODUCTS.incrementAndGet();
        jdbc.update("INSERT INTO inventory(product_id,available,reserved,version) VALUES(?,2,0,0)", productId);
        return orderService.create(961L, new CreateOrderRequest(SalesChannel.WEB, UUID.randomUUID().toString(),
                List.of(new OrderItemRequest(productId, 1)), address("Original street"), PaymentMethod.COD)).order();
    }

    private long product(long orderId) {
        return jdbc.queryForObject("SELECT product_id FROM order_items WHERE order_id=?", Long.class, orderId);
    }

    private int stock(long orderId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM inventory WHERE product_id=?", Integer.class, product(orderId));
    }

    private void race(
            Callable<Boolean> task
    ) throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            tasks.add(task);
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(tasks)) {
                assertEquals(true, result.get());
            }
        }
    }

    private PurchaseOrder fixture(
            boolean confirmed,
            boolean withAddress
    ) {
        long productId = PRODUCTS.incrementAndGet();
        jdbc.update("INSERT INTO inventory(product_id,available,reserved,version) VALUES(?,4,0,0)", productId);
        var order = new PurchaseOrder(961L, UUID.randomUUID().toString(), SalesChannel.WEB);
        order.addItem(productId, "SHIP", "Shipment fixture", BigDecimal.TEN, 1);
        if (withAddress) {
            order.setShippingAddress(addresses.toEntity(address("Original street")));
        }
        if (confirmed) {
            order.completePayment(true);
        }
        return orders.saveAndFlush(order);
    }

    private ShippingAddressRequest address(
            String street
    ) {
        return new ShippingAddressRequest("Recipient fixture", "0901234567", street, "Ward", "District", "Province", null);
    }

    private long productId(
            PurchaseOrder order
    ) {
        return jdbc.queryForObject("SELECT product_id FROM order_items WHERE order_id=?", Long.class, order.getId());
    }

    private int count(
            PurchaseOrder order,
            String type
    ) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type=? AND payload LIKE ?", Integer.class,
                type, "%\"orderId\":" + order.getId() + ",%");
    }

    private String bearer(
            String role
    ) {
        return "Bearer " + Jwts.builder().subject("961").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(1))).expiration(Date.from(Instant.now().plusSeconds(120)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }
}

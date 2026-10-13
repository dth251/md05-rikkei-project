package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.payment.config.CodOrderListener;
import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.CollectCodRequest;
import com.app.payment.entity.CodDelivery;
import com.app.payment.entity.CodDeliveryState;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.repository.CodDeliveryRepository;
import com.app.payment.repository.EventInbox;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.service.CodPaymentService;
import com.app.payment.service.PaymentService;
import com.app.payment.service.VnpayTransactionService;
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
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
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
class CodPaymentTests {

    private static final AtomicLong ORDERS = new AtomicLong(8000000);

    @Autowired
    private CodPaymentService cod;
    @Autowired
    private PaymentService legacy;
    @Autowired
    private VnpayTransactionService vnpay;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private CodDeliveryRepository deliveries;
    @Autowired
    private CodOrderListener listener;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private SecretKey key;
    @MockitoBean
    private PaymentOrderLock locks;
    @MockitoBean
    private EventInbox inbox;

    @BeforeEach
    void claim() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void collectionRequiresAdminAndValidFullAmount() throws Exception {
        var payment = pending(true);
        var request = request(payment);
        String path = "/api/payments/" + payment.getId() + "/cod/collect";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", bearer("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":0,\"idempotencyKey\":\"x\",\"receiptNumber\":\"r\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(409, assertThrows(ApiException.class, () -> cod.collect(payment.getId(),
                new CollectCodRequest(BigDecimal.ONE, request.idempotencyKey(), request.receiptNumber()), 10L))
                .getStatus().value());
        mvc.perform(post(path).header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentReplayProducesOneImmutableReceiptAndOneOutcome() throws Exception {
        var payment = pending(true);
        var request = request(payment);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            tasks.add(() -> {
                var result = cod.collect(payment.getId(), request, 10L);
                assertEquals(PaymentStatus.SUCCESS, result.status());
                assertEquals(10L, result.codReceipt().collectedBy());
                return true;
            });
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(tasks)) {
                assertEquals(true, result.get());
            }
        }
        var first = cod.collect(payment.getId(), request, 10L);
        assertEquals(first, cod.collect(payment.getId(), request, 10L));
        assertEquals(1, outcomes(payment));
        assertEquals(409, assertThrows(ApiException.class, () -> cod.collect(payment.getId(),
                new CollectCodRequest(payment.getAmount(), "changed", request.receiptNumber()), 10L)).getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> cod.collect(payment.getId(), request, 11L)).getStatus().value());
    }

    @Test
    void competingReceiptsHaveExactlyOneWinner() throws Exception {
        var payment = pending(true);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<Callable<Integer>>of(() -> attempt(payment), () -> attempt(payment)));
            var codes = List.of(results.get(0).get(), results.get(1).get());
            assertEquals(1, codes.stream().filter(code -> code == 200).count());
            assertEquals(1, codes.stream().filter(code -> code == 409).count());
        }
        assertEquals(1, outcomes(payment));
    }

    @Test
    void unshippedOrCancelledCannotCollectAndMockOrVnpayCannotBypassCod() {
        var payment = pending(false);
        assertEquals(409, assertThrows(ApiException.class, () -> cod.collect(payment.getId(), request(payment), 10L))
                .getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> legacy.simulate(payment.getId(), PaymentStatus.SUCCESS))
                .getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> vnpay.checkout(payment.getId(), 10L, "127.0.0.1"))
                .getStatus().value());
        cod.accept(event(payment, "order.cod-cancelled"));
        cod.accept(event(payment, "order.cod-cancelled"));
        assertEquals(PaymentStatus.FAILED, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, outcomes(payment));
        assertEquals(409, assertThrows(ApiException.class, () -> cod.collect(payment.getId(), request(payment), 10L))
                .getStatus().value());
    }

    @Test
    void lifecycleBeforeCreationIsDurableAndAppliedWhenPaymentArrives() {
        long orderId = ORDERS.incrementAndGet();
        cod.accept(new CommerceEvent(UUID.randomUUID().toString(), "order.cod-cancelled", orderId, 10L,
                BigDecimal.TEN, null, Instant.now()));
        Payment payment = payments.saveAndFlush(new Payment(orderId, 10L, BigDecimal.TEN, PaymentMethod.COD));
        // apply is intended for a managed entity in the creation transaction; accept exercises it with a locked row.
        cod.accept(event(payment, "order.cod-cancelled"));
        assertEquals(PaymentStatus.FAILED, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, outcomes(payment));
    }

    @Test
    void conflictingLifecycleOrIdentityRollsBackAndOnlineCannotCollect() {
        var payment = pending(true);
        assertThrows(IllegalArgumentException.class, () -> cod.accept(event(payment, "order.cod-cancelled")));
        assertThrows(IllegalArgumentException.class, () -> cod.accept(new CommerceEvent(UUID.randomUUID().toString(),
                "order.cod-shipped", payment.getOrderId(), 11L, payment.getAmount(), null, Instant.now())));
        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
        Payment online = payments.saveAndFlush(new Payment(ORDERS.incrementAndGet(), 10L, BigDecimal.TEN));
        assertEquals(409, assertThrows(ApiException.class, () -> cod.collect(online.getId(), request(online), 10L))
                .getStatus().value());
        assertThrows(IllegalArgumentException.class, () -> cod.accept(event(online, "order.cod-shipped")));
        assertEquals(false, deliveries.existsById(online.getOrderId()));
    }

    @Test
    void receiptAndIdempotencyCannotBeReusedForAnotherPayment() throws Exception {
        var first = pending(true);
        var second = pending(true);
        var request = request(first);
        cod.collect(first.getId(), request, 10L);
        mvc.perform(post("/api/payments/" + second.getId() + "/cod/collect").header("Authorization", bearer("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
        assertEquals(PaymentStatus.PENDING, payments.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(0, outcomes(second));
    }

    @Test
    void malformedLifecycleIsRejectedWithoutProjection() throws Exception {
        var payment = pending(false);
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("order.cod-cancelled");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message(
                json.writeValueAsBytes(event(payment, "order.cod-shipped")), properties)));
        properties.setReceivedRoutingKey("order.cod-shipped");
        listener.receive(new Message(json.writeValueAsBytes(event(payment, "order.cod-shipped")), properties));
        assertEquals(CodDeliveryState.SHIPPED, deliveries.findById(payment.getOrderId()).orElseThrow().getState());
    }

    private Payment pending(boolean shipped) {
        Payment payment = payments.saveAndFlush(new Payment(ORDERS.incrementAndGet(), 10L, BigDecimal.TEN, PaymentMethod.COD));
        if (shipped) {
            deliveries.saveAndFlush(new CodDelivery(payment.getOrderId(), 10L, payment.getAmount(),
                    CodDeliveryState.SHIPPED, Instant.now()));
        }
        return payment;
    }

    private CollectCodRequest request(Payment payment) {
        return new CollectCodRequest(payment.getAmount(), UUID.randomUUID().toString(), "COD-" + UUID.randomUUID());
    }

    private CommerceEvent event(Payment payment, String type) {
        return new CommerceEvent(UUID.randomUUID().toString(), type, payment.getOrderId(), payment.getUserId(),
                payment.getAmount(), null, Instant.now());
    }

    private int attempt(Payment payment) {
        try {
            cod.collect(payment.getId(), request(payment), 10L);
            return 200;
        } catch (ApiException exception) {
            return exception.getStatus().value();
        }
    }

    private int outcomes(Payment payment) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE payload LIKE ?", Integer.class,
                "%\"orderId\":" + payment.getOrderId() + ",%");
    }

    private String bearer(String role) {
        return "Bearer " + Jwts.builder().subject("10").issuer("omnichannel-auth")
                .issuedAt(Date.from(Instant.now().minusSeconds(1))).expiration(Date.from(Instant.now().plusSeconds(120)))
                .claim("roles", List.of(role)).signWith(key, Jwts.SIG.HS256).compact();
    }
}

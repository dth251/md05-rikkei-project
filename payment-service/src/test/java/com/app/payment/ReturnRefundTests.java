package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.payment.config.ReturnedOrderListener;
import com.app.payment.config.exception.ApiException;
import com.app.payment.dto.event.CommerceEvent;
import com.app.payment.dto.request.CashRefundRequest;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentMethod;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.repository.EventInbox;
import com.app.payment.repository.PaymentOrderLock;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.ReturnedOrderRepository;
import com.app.payment.service.ReturnRefundService;
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
class ReturnRefundTests {

    private static final AtomicLong ORDERS = new AtomicLong(14000000);

    @Autowired private ReturnRefundService refunds;
    @Autowired private PaymentRepository payments;
    @Autowired private ReturnedOrderRepository returnedOrders;
    @Autowired private ReturnedOrderListener listener;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SecretKey key;
    @MockitoBean private EventInbox inbox;
    @MockitoBean private PaymentOrderLock locks;

    @BeforeEach
    void claim() {
        when(inbox.claim(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void refundRequiresCollectedCodAndReceiptOfReturnedGoodsAndFullAmount() {
        var payment = captured();
        var request = request(payment);
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.refundCash(payment.getId(), 99L, request))
                .getStatus().value());
        refunds.accept(event(payment));
        assertEquals(RefundStatus.REQUIRED, payments.findById(payment.getId()).orElseThrow().getRefundStatus());
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.refundCash(payment.getId(), 99L,
                new CashRefundRequest(BigDecimal.ONE, request.idempotencyKey(), request.receiptNumber()))).getStatus().value());
        Payment online = payments.saveAndFlush(new Payment(ORDERS.incrementAndGet(), 77L, BigDecimal.TEN));
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.refundCash(online.getId(), 99L, request(online)))
                .getStatus().value());
    }

    @Test
    void concurrentRefundReplayProducesOneReceiptAndOneCompletionEvent() throws Exception {
        var payment = captured();
        var capturedAt = payments.findById(payment.getId()).orElseThrow().getCompletedAt();
        refunds.accept(event(payment));
        refunds.accept(event(payment));
        var request = request(payment);
        Callable<Boolean> task = () -> {
            assertEquals(request.receiptNumber(), refunds.refundCash(payment.getId(), 99L, request).receiptNumber());
            return true;
        };
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (var result : executor.invokeAll(java.util.Collections.nCopies(24, task))) {
                assertEquals(true, result.get());
            }
        }
        var first = refunds.refundCash(payment.getId(), 99L, request);
        assertEquals(first, refunds.refundCash(payment.getId(), 99L, request));
        refunds.accept(event(payment));
        var reloaded = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, reloaded.getStatus());
        assertEquals(RefundStatus.REFUNDED, reloaded.getRefundStatus());
        assertEquals(capturedAt, reloaded.getCompletedAt());
        assertEquals(1, count(payment, "payment.return-refund-ready"));
        assertEquals(1, count(payment, "payment.return-refunded"));
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.refundCash(payment.getId(), 98L, request))
                .getStatus().value());
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.refundCash(payment.getId(), 99L,
                new CashRefundRequest(payment.getAmount(), UUID.randomUUID().toString(), request.receiptNumber())))
                .getStatus().value());
    }

    @Test
    void distinctConcurrentRefundsHaveExactlyOneWinner() throws Exception {
        var payment = captured();
        refunds.accept(event(payment));
        Callable<Integer> task = () -> {
            try {
                refunds.refundCash(payment.getId(), 99L, request(payment));
                return 200;
            } catch (ApiException exception) {
                return exception.getStatus().value();
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(task, task));
            var codes = List.of(results.get(0).get(), results.get(1).get());
            assertEquals(1, codes.stream().filter(code -> code == 200).count());
            assertEquals(1, codes.stream().filter(code -> code == 409).count());
        }
        assertEquals(1, count(payment, "payment.return-refunded"));
    }

    @Test
    void duplicateReceiptOrKeyOnAnotherPaymentRollsBackRefundAndOutbox() throws Exception {
        var first = captured();
        var second = captured();
        refunds.accept(event(first));
        refunds.accept(event(second));
        var request = request(first);
        refunds.refundCash(first.getId(), 99L, request);
        mvc.perform(post("/api/payments/" + second.getId() + "/cod/refund").header("Authorization", bearer(99, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isConflict());
        assertEquals(RefundStatus.REQUIRED, payments.findById(second.getId()).orElseThrow().getRefundStatus());
        assertEquals(0, count(second, "payment.return-refunded"));
    }

    @Test
    void refundAndPrivateReceiptHaveCorrectPermissionsAndValidation() throws Exception {
        var payment = captured();
        refunds.accept(event(payment));
        String path = "/api/payments/" + payment.getId() + "/cod/refund";
        String body = json.writeValueAsString(request(payment));
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", bearer(77, "USER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(99, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10,\"idempotencyKey\":\"x\",\"receiptNumber\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization", bearer(99, "ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(77, "USER"))).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization", bearer(78, "USER"))).andExpect(status().isNotFound());
    }

    @Test
    void mismatchedOrUncapturedPaymentCannotBeMadeRefundable() {
        var payment = captured();
        assertThrows(IllegalArgumentException.class, () -> refunds.accept(new CommerceEvent(UUID.randomUUID().toString(),
                "order.return-received", payment.getOrderId(), 78L, payment.getAmount(), payment.getId(), Instant.now())));
        assertThrows(IllegalArgumentException.class, () -> refunds.accept(new CommerceEvent(UUID.randomUUID().toString(),
                "order.return-received", payment.getOrderId(), 77L, BigDecimal.ONE, payment.getId(), Instant.now())));
        assertThrows(IllegalArgumentException.class, () -> refunds.accept(new CommerceEvent(UUID.randomUUID().toString(),
                "order.return-received", payment.getOrderId(), 77L, payment.getAmount(), payment.getId()+1, Instant.now())));
        assertEquals(false, returnedOrders.existsById(payment.getOrderId()));
        var pending = payments.saveAndFlush(new Payment(ORDERS.incrementAndGet(), 77L, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class, () -> refunds.accept(event(pending)));
        assertEquals(false, returnedOrders.existsById(pending.getOrderId()));
        assertEquals(RefundStatus.NONE, payments.findById(payment.getId()).orElseThrow().getRefundStatus());
    }

    @Test
    void receiptListenerRejectsRoutingMismatchAndMalformedPayload() throws Exception {
        var payment = captured();
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("order.created");
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(new Message(json.writeValueAsBytes(event(payment)), properties)));
        properties.setReceivedRoutingKey("order.return-received");
        listener.receive(new Message(json.writeValueAsBytes(event(payment)), properties));
        assertEquals(RefundStatus.REQUIRED, payments.findById(payment.getId()).orElseThrow().getRefundStatus());
    }

    @Test
    void legacySimulationCanRefundAfterReceiptButCannotBypassCashOrProvider() throws Exception {
        var online = new Payment(ORDERS.incrementAndGet(), 77L, BigDecimal.TEN);
        online.complete(PaymentStatus.SUCCESS);
        var payment = payments.saveAndFlush(online);
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.simulateRefund(payment.getId())).getStatus().value());
        refunds.accept(event(payment));
        mvc.perform(post("/api/payments/" + payment.getId() + "/simulate-refund").header("Authorization", bearer(77, "USER")))
                .andExpect(status().isForbidden());
        assertEquals(RefundStatus.REFUNDED, refunds.simulateRefund(payment.getId()).refundStatus());
        refunds.simulateRefund(payment.getId());
        assertEquals(1, count(payment, "payment.return-refunded"));
        var cash = captured();
        refunds.accept(event(cash));
        assertEquals(409, assertThrows(ApiException.class, () -> refunds.simulateRefund(cash.getId())).getStatus().value());
    }

    private Payment captured() {
        var payment = new Payment(ORDERS.incrementAndGet(), 77L, BigDecimal.TEN, PaymentMethod.COD);
        payment.collectCod(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 99L);
        return payments.saveAndFlush(payment);
    }

    private CashRefundRequest request(Payment payment) {
        return new CashRefundRequest(payment.getAmount(), UUID.randomUUID().toString(), "REFUND-" + UUID.randomUUID());
    }

    private CommerceEvent event(Payment payment) {
        return new CommerceEvent(UUID.randomUUID().toString(), "order.return-received", payment.getOrderId(),
                payment.getUserId(), payment.getAmount(), payment.getId(), Instant.now());
    }

    private int count(Payment payment, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type=? AND payload LIKE ?", Integer.class,
                type, "%\"orderId\":" + payment.getOrderId() + ",%");
    }

    private String bearer(long userId, String role) {
        return "Bearer " + Jwts.builder().subject(Long.toString(userId)).issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(120)))
                .claim("roles", List.of(role)).signWith(key).compact();
    }
}

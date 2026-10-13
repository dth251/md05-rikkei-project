package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.payment.config.VnpaySettings;
import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.security.VnpaySignature;
import com.app.payment.service.PaymentService;
import com.app.payment.service.VnpayService;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vnpay;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "payment.vnpay.enabled=true",
        "payment.vnpay.tmn-code=FIXTURE1",
        "payment.vnpay.hash-secret=public-fixture-signing-key-only"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class VnpayApiTests {

    private static final AtomicLong IDS = new AtomicLong(9000000);
    private static final String TEST_SECRET = "public-fixture-signing-key-only";
    @Autowired private PaymentRepository payments;
    @Autowired private VnpayPaymentRepository attempts;
    @Autowired private VnpayService service;
    @Autowired private PaymentService simulations;
    @Autowired private MockMvc mvc;
    @Autowired private SecretKey key;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void checkoutUsesStoredAmountStableReferenceAndRejectsOtherOwnerAndSimulation() throws Exception {
        var payment = pending();
        mvc.perform(post("/api/payments/" + payment.getId() + "/vnpay/checkout"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments/" + payment.getId() + "/vnpay/checkout").header("Authorization", token(11)))
                .andExpect(status().isNotFound());
        var first = service.checkout(payment.getId(), 10L, "203.0.113.10");
        var second = service.checkout(payment.getId(), 10L, "203.0.113.20");
        assertEquals(first, second);
        assertTrue(first.checkoutUrl().contains("vnp_Amount=1250&"));
        assertTrue(first.checkoutUrl().startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertEquals(1, attempts.findAll().stream().filter(attempt -> attempt.getPaymentId().equals(payment.getId())).count());
        assertThrows(com.app.payment.config.exception.ApiException.class,
                () -> simulations.simulate(payment.getId(), PaymentStatus.SUCCESS));
    }

    @Test
    void anonymousSignedIpnCapturesOnceAndReturnDoesNotMutatePayment() throws Exception {
        var payment = pending();
        var checkout = service.checkout(payment.getId(), 10L, "203.0.113.10");
        var callback = callback(checkout, "00", "00", nextTransaction());
        mvc.perform(get("/api/payments/vnpay/return").params(callback)).andExpect(status().isOk());
        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/payments/vnpay/ipn").params(callback)).andExpect(status().isOk())
                .andExpect(jsonPath("RspCode").value("00")).andExpect(jsonPath("success").doesNotExist());
        mvc.perform(get("/api/payments/vnpay/ipn").params(callback)).andExpect(jsonPath("RspCode").value("02"));
        assertEquals(PaymentStatus.SUCCESS, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(1, outcomeCount(payment, "payment.succeeded"));
    }

    @Test
    void signatureAmountAndDuplicateQueryFailuresCannotCapture() throws Exception {
        var payment = pending();
        var checkout = service.checkout(payment.getId(), 10L, "203.0.113.10");
        var changed = callback(checkout, "00", "00", nextTransaction());
        changed.set("vnp_Amount", "1251");
        assertEquals("97", service.ipn(changed).responseCode());
        sign(changed);
        assertEquals("04", service.ipn(changed).responseCode());
        var duplicate = callback(checkout, "00", "00", nextTransaction());
        duplicate.add("vnp_Amount", "1250");
        mvc.perform(get("/api/payments/vnpay/ipn").params(duplicate))
                .andExpect(jsonPath("RspCode").value("97"));
        assertEquals(PaymentStatus.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(0, outcomeCount(payment, "payment.succeeded"));
    }

    @Test
    void concurrentIpnsProduceOneCaptureAndOneOutcome() throws Exception {
        var payment = pending();
        var checkout = service.checkout(payment.getId(), 10L, "203.0.113.10");
        var callback = callback(checkout, "00", "00", nextTransaction());
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<String>>mapToObj(ignored -> () -> service.ipn(callback).responseCode()).toList();
            var results = executor.invokeAll(calls);
            assertEquals(1, results.stream().filter(result -> {
                try { return result.get().equals("00"); } catch (Exception exception) { throw new AssertionError(exception); }
            }).count());
            for (var result : results) {
                assertTrue(List.of("00", "02").contains(result.get()));
            }
        }
        assertEquals(1, outcomeCount(payment, "payment.succeeded"));
    }

    @Test
    void expiredThenCapturedPaymentRetainsCaptureAndRequiresRefundWithoutSuccessEvent() {
        var payment = pending();
        var checkout = service.checkout(payment.getId(), 10L, "203.0.113.10");
        payment.expireReservation();
        payments.saveAndFlush(payment);
        var callback = callback(checkout, "00", "00", nextTransaction());
        assertEquals("00", service.ipn(callback).responseCode());
        var captured = payments.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, captured.getStatus());
        assertEquals(RefundStatus.REQUIRED, captured.getRefundStatus());
        assertTrue(captured.isReservationExpired());
        assertNotNull(attempts.findById(payment.getId()).orElseThrow().getCapturedAt());
        assertEquals(0, outcomeCount(payment, "payment.succeeded"));
        assertEquals("02", service.ipn(callback).responseCode());
    }

    @Test
    void providerFailureThenLateCaptureRequiresRefundAndCannotResurrectOrder() {
        var payment = pending();
        var checkout = service.checkout(payment.getId(), 10L, "203.0.113.10");
        assertEquals("00", service.ipn(callback(checkout, "24", "02", "0")).responseCode());
        assertEquals("02", service.ipn(callback(checkout, "24", "02", "0")).responseCode());
        assertEquals("00", service.ipn(callback(checkout, "00", "00", nextTransaction())).responseCode());
        var captured = payments.findById(payment.getId()).orElseThrow();
        assertEquals(RefundStatus.REQUIRED, captured.getRefundStatus());
        assertEquals(1, outcomeCount(payment, "payment.failed"));
        assertEquals(0, outcomeCount(payment, "payment.succeeded"));
    }

    @Test
    void providerTransactionNumberCannotCaptureTwoPaymentsAndFailureRollsBack() {
        var first = pending();
        var second = pending();
        var firstCheckout = service.checkout(first.getId(), 10L, "203.0.113.10");
        var secondCheckout = service.checkout(second.getId(), 10L, "203.0.113.10");
        String transaction = nextTransaction();
        assertEquals("00", service.ipn(callback(firstCheckout, "00", "00", transaction)).responseCode());
        assertEquals("99", service.ipn(callback(secondCheckout, "00", "00", transaction)).responseCode());
        assertEquals(PaymentStatus.PENDING, payments.findById(second.getId()).orElseThrow().getStatus());
        assertNull(attempts.findById(second.getId()).orElseThrow().getCapturedAt());
        assertEquals(0, outcomeCount(second, "payment.succeeded"));
    }

    @Test
    void unknownReferenceWrongMerchantAndInvalidCaptureDateAreRejected() {
        var checkout = service.checkout(pending().getId(), 10L, "203.0.113.10");
        var callback = callback(checkout, "00", "00", nextTransaction());
        callback.set("vnp_TxnRef", UUID.randomUUID().toString().replace("-", ""));
        sign(callback);
        assertEquals("01", service.ipn(callback).responseCode());
        callback.set("vnp_TmnCode", "OTHER123");
        sign(callback);
        assertEquals("97", service.ipn(callback).responseCode());
        callback.set("vnp_TmnCode", "FIXTURE1");
        callback.set("vnp_PayDate", "20260230010101");
        sign(callback);
        assertEquals("97", service.ipn(callback).responseCode());
        assertFalse(new VnpaySettings(false, "", "", "").enabled());
        assertThrows(IllegalStateException.class, () -> new VnpaySettings(true, "FIXTURE1", "", ""));
    }

    private Payment pending() {
        return payments.saveAndFlush(new Payment(IDS.incrementAndGet(), 10L, new BigDecimal("12.50")));
    }

    private String nextTransaction() {
        return Long.toString(IDS.incrementAndGet());
    }

    private LinkedMultiValueMap<String, String> callback(VnpayCheckoutResponse checkout, String response,
                                                       String status, String transaction) {
        var values = new LinkedMultiValueMap<String, String>();
        Map.of("vnp_TmnCode", "FIXTURE1", "vnp_TxnRef", checkout.reference(), "vnp_Amount", "1250",
                "vnp_ResponseCode", response, "vnp_TransactionStatus", status, "vnp_TransactionNo", transaction,
                "vnp_PayDate", DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                        .format(Instant.now())).forEach(values::set);
        sign(values);
        return values;
    }

    private void sign(LinkedMultiValueMap<String, String> values) {
        values.set("vnp_SecureHash", VnpaySignature.hmac(VnpaySignature.canonicalQuery(values.toSingleValueMap()), TEST_SECRET));
    }

    private int outcomeCount(Payment payment, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type=? AND payload LIKE ?",
                Integer.class, type, "%\"paymentId\":" + payment.getId() + ",%");
    }

    private String token(long user) {
        return "Bearer " + Jwts.builder().subject(Long.toString(user)).issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of("USER")).signWith(key).compact();
    }
}

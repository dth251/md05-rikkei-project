package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.security.VnpaySignature;
import com.app.payment.service.VnpayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vnpay-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "payment.vnpay.enabled=true",
        "payment.vnpay.tmn-code=FIXTURE1",
        "payment.vnpay.hash-secret=public-fixture-signing-key-only",
        "payment.vnpay.server-ip=203.0.113.10",
        "payment.vnpay.allow-local-fixture=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class VnpayPaymentQueryTests {

    private static final String SECRET = "public-fixture-signing-key-only";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final AtomicLong IDS = new AtomicLong(12000000);
    private static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicReference<String> MODE = new AtomicReference<>("SUCCESS");
    private static final AtomicReference<Map<String, String>> LAST_REQUEST = new AtomicReference<>();
    private static final Map<String, Map<String, String>> RESULTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    // Provider field ordering is independent of the production signature implementation.
    private static final List<String> REQUEST_FIELDS = List.of("vnp_RequestId", "vnp_Version", "vnp_Command",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
    private static final List<String> RESPONSE_FIELDS = List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode",
            "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
            "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo",
            "vnp_PromotionCode", "vnp_PromotionAmount");
    private static final HttpServer SERVER = server();

    @Autowired private PaymentRepository payments;
    @Autowired private VnpayPaymentRepository attempts;
    @Autowired private VnpayService service;
    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry properties) {
        properties.add("payment.vnpay.ledger-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/ledger");
    }

    @BeforeEach
    void reset() {
        MODE.set("SUCCESS");
        REQUESTS.set(0);
        LAST_REQUEST.set(null);
        RESULTS.clear();
    }

    @AfterAll
    static void stop() {
        SERVER.stop(0);
    }

    @Test
    void missingIpnIsRecoveredFromSignedProviderQueryAndRefreshDoesNotDuplicateCapture() throws Exception {
        var payment = pending();
        var checkout = service.checkout(payment.getId(), 10L, "203.0.113.20");
        var callback = callback(checkout);
        mvc.perform(get("/api/payments/vnpay/return").params(callback).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.paymentStatus").value("SUCCESS"));
        assertEquals(PaymentStatus.SUCCESS, reload(payment).getStatus());
        assertEquals(1, REQUESTS.get());
        var request = LAST_REQUEST.get();
        assertEquals("querydr", request.get("vnp_Command"));
        assertEquals("203.0.113.10", request.get("vnp_IpAddr"));
        assertEquals(TIME.format(attempts.findById(payment.getId()).orElseThrow().getCreatedAt()),
                request.get("vnp_TransactionDate"));
        assertEquals("02", service.ipn(callback).responseCode());
        service.verifyReturn(callback);
        assertEquals(1, REQUESTS.get());
        assertEquals(1, outcomes(payment));
    }

    @Test
    void unverifiedOrMismatchedProviderRepliesCannotCaptureAndPollingIsThrottled() {
        for (String mode : List.of("BAD_HASH", "AMOUNT", "MERCHANT", "REFERENCE", "NUMBER", "DATE",
                "REFUND", "PENDING", "ERROR", "HTTP_ERROR", "DUPLICATE_JSON")) {
            MODE.set(mode);
            var payment = pending();
            var callback = callback(service.checkout(payment.getId(), 10L, "203.0.113.20"));
            int before = REQUESTS.get();
            assertEquals(PaymentStatus.PENDING, service.verifyReturn(callback).paymentStatus(), mode);
            assertEquals(PaymentStatus.PENDING, service.verifyReturn(callback).paymentStatus(), mode);
            assertEquals(before + 1, REQUESTS.get(), mode);
            assertEquals(0, outcomes(payment), mode);
            assertNull(attempts.findById(payment.getId()).orElseThrow().getTransactionNumber(), mode);
        }
    }

    @Test
    void invalidReturnNeverContactsProvider() {
        var payment = pending();
        var callback = callback(service.checkout(payment.getId(), 10L, "203.0.113.20"));
        callback.set("vnp_Amount", "9999");
        assertThrows(com.app.payment.config.exception.ApiException.class, () -> service.verifyReturn(callback));
        sign(callback);
        assertThrows(com.app.payment.config.exception.ApiException.class, () -> service.verifyReturn(callback));
        assertEquals(0, REQUESTS.get());
        assertEquals(0, outcomes(payment));
    }

    @Test
    void expiredCaptureRequiresRefundAndNeverConfirmsOrder() {
        var payment = pending();
        var callback = callback(service.checkout(payment.getId(), 10L, "203.0.113.20"));
        payment.expireReservation();
        payments.saveAndFlush(payment);
        var result = service.verifyReturn(callback);
        assertEquals(PaymentStatus.SUCCESS, result.paymentStatus());
        assertTrue(result.reservationExpired());
        assertEquals(RefundStatus.REQUIRED, result.refundStatus());
        assertEquals(0, outcomes(payment));
        assertEquals("02", service.ipn(callback).responseCode());
    }

    @Test
    void concurrentIpnAndReturnsCommitOneCaptureAndOneEvent() throws Exception {
        var payment = pending();
        var callback = callback(service.checkout(payment.getId(), 10L, "203.0.113.20"));
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<Object>>mapToObj(index -> () -> index % 2 == 0
                            ? service.ipn(callback) : service.verifyReturn(callback)).toList();
            for (var result : executor.invokeAll(calls)) {
                assertNotNull(result.get());
            }
        }
        assertEquals(PaymentStatus.SUCCESS, reload(payment).getStatus());
        assertEquals(1, outcomes(payment));
        assertTrue(REQUESTS.get() <= 1);
    }

    @Test
    void cancellationDoesNotQueryAndDuplicateProviderTransactionRollsBack() {
        var first = pending();
        var firstCallback = callback(service.checkout(first.getId(), 10L, "203.0.113.20"));
        service.verifyReturn(firstCallback);
        var second = pending();
        var secondCheckout = service.checkout(second.getId(), 10L, "203.0.113.20");
        var secondCallback = callback(secondCheckout);
        secondCallback.set("vnp_TransactionNo", firstCallback.getFirst("vnp_TransactionNo"));
        sign(secondCallback);
        RESULTS.put(secondCheckout.reference(), secondCallback.toSingleValueMap());
        assertEquals(PaymentStatus.PENDING, service.verifyReturn(secondCallback).paymentStatus());
        assertNull(attempts.findById(second.getId()).orElseThrow().getTransactionNumber());
        assertEquals(0, outcomes(second));
        var third = pending();
        var cancelled = callback(service.checkout(third.getId(), 10L, "203.0.113.20"));
        cancelled.set("vnp_ResponseCode", "24");
        cancelled.set("vnp_TransactionStatus", "02");
        cancelled.set("vnp_TransactionNo", "0");
        sign(cancelled);
        int before = REQUESTS.get();
        service.verifyReturn(cancelled);
        assertEquals(before, REQUESTS.get());
        assertEquals(PaymentStatus.PENDING, reload(third).getStatus());
    }

    private Payment pending() {
        return payments.saveAndFlush(new Payment(IDS.incrementAndGet(), 10L, new BigDecimal("12.50")));
    }

    private Payment reload(Payment payment) {
        return payments.findById(payment.getId()).orElseThrow();
    }

    private int outcomes(Payment payment) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_type='payment.succeeded' AND payload LIKE ?",
                Integer.class, "%\"paymentId\":" + payment.getId() + ",%");
    }

    private LinkedMultiValueMap<String, String> callback(VnpayCheckoutResponse checkout) {
        var callback = new LinkedMultiValueMap<String, String>();
        callback.set("vnp_TmnCode", "FIXTURE1");
        callback.set("vnp_TxnRef", checkout.reference());
        callback.set("vnp_Amount", "1250");
        callback.set("vnp_ResponseCode", "00");
        callback.set("vnp_TransactionStatus", "00");
        callback.set("vnp_TransactionNo", Long.toString(IDS.incrementAndGet()));
        callback.set("vnp_PayDate", TIME.format(Instant.now()));
        sign(callback);
        RESULTS.put(checkout.reference(), callback.toSingleValueMap());
        return callback;
    }

    private void sign(LinkedMultiValueMap<String, String> callback) {
        callback.remove("vnp_SecureHash");
        callback.set("vnp_SecureHash", VnpaySignature.hmac(VnpaySignature.canonicalQuery(callback.toSingleValueMap()), SECRET));
    }

    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/ledger", exchange -> {
                REQUESTS.incrementAndGet();
                var request = new LinkedHashMap<String, String>();
                JSON.readTree(exchange.getRequestBody()).properties().forEach(entry ->
                        request.put(entry.getKey(), entry.getValue().asText()));
                LAST_REQUEST.set(request);
                String expectedHash = VnpaySignature.hmac(join(request, REQUEST_FIELDS), SECRET);
                if (!expectedHash.equals(request.get("vnp_SecureHash")) || MODE.get().equals("HTTP_ERROR")) {
                    exchange.sendResponseHeaders(400, -1);
                    exchange.close();
                    return;
                }
                var reply = new LinkedHashMap<>(RESULTS.get(request.get("vnp_TxnRef")));
                reply.remove("vnp_SecureHash");
                reply.put("vnp_ResponseId", UUID.randomUUID().toString().replace("-", ""));
                reply.put("vnp_Command", "querydr");
                reply.put("vnp_Message", "Query completed");
                reply.put("vnp_BankCode", "NCB");
                reply.put("vnp_TransactionType", "01");
                reply.put("vnp_OrderInfo", request.get("vnp_OrderInfo"));
                switch (MODE.get()) {
                    case "AMOUNT" -> reply.put("vnp_Amount", "1251");
                    case "MERCHANT" -> reply.put("vnp_TmnCode", "OTHER123");
                    case "REFERENCE" -> reply.put("vnp_TxnRef", "a".repeat(32));
                    case "NUMBER" -> reply.put("vnp_TransactionNo", "999");
                    case "DATE" -> reply.put("vnp_PayDate", "20260230000000");
                    case "REFUND" -> reply.put("vnp_TransactionType", "02");
                    case "PENDING" -> reply.put("vnp_TransactionStatus", "01");
                    case "ERROR" -> reply.put("vnp_ResponseCode", "91");
                    default -> { }
                }
                reply.put("vnp_SecureHash", MODE.get().equals("BAD_HASH") ? "a".repeat(128)
                        : VnpaySignature.hmac(join(reply, RESPONSE_FIELDS), SECRET));
                String body = JSON.writeValueAsString(reply);
                if (MODE.get().equals("DUPLICATE_JSON")) {
                    body = body.replaceFirst("\\{", "{\"vnp_Amount\":\"1250\",");
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String join(Map<String, String> parameters, List<String> fields) {
        return fields.stream().map(name -> parameters.getOrDefault(name, ""))
                .collect(java.util.stream.Collectors.joining("|"));
    }
}

package com.app.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.payment.dto.response.VnpayCheckoutResponse;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import com.app.payment.entity.VnpayRefundState;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.repository.VnpayPaymentRepository;
import com.app.payment.security.VnpaySignature;
import com.app.payment.service.VnpayRefundService;
import com.app.payment.service.VnpayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vnpay-refunds;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "payment.vnpay.enabled=true",
        "payment.vnpay.tmn-code=FIXTURE1",
        "payment.vnpay.hash-secret=public-fixture-signing-key-only",
        "payment.vnpay.server-ip=203.0.113.10",
        "payment.vnpay.allow-local-fixture=true"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class VnpayRefundTests {

    private static final String SECRET = "public-fixture-signing-key-only";
    private static final AtomicLong IDS = new AtomicLong(Math.floorMod(java.util.UUID.randomUUID().getMostSignificantBits(), 900_000_000_000L) + 1_000_000_000L);
    protected static final AtomicInteger REQUESTS = new AtomicInteger();
    private static final AtomicReference<String> MODE = new AtomicReference<>("PROCESSING");
    private static final AtomicReference<Map<String, String>> LAST_REQUEST = new AtomicReference<>();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpServer SERVER = server();
    private static final List<String> REFUND_FIELDS = List.of("vnp_RequestId", "vnp_Version", "vnp_Command",
            "vnp_TmnCode", "vnp_TransactionType", "vnp_TxnRef", "vnp_Amount", "vnp_TransactionNo",
            "vnp_TransactionDate", "vnp_CreateBy", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
    private static final List<String> QUERY_FIELDS = List.of("vnp_RequestId", "vnp_Version", "vnp_Command",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr", "vnp_OrderInfo");
    private static final List<String> RESPONSE_FIELDS = List.of("vnp_ResponseId", "vnp_Command", "vnp_ResponseCode",
            "vnp_Message", "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate",
            "vnp_TransactionNo", "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo");
    @Autowired private PaymentRepository payments;
    @Autowired private VnpayPaymentRepository captures;
    @Autowired private VnpayService checkout;
    @Autowired private VnpayRefundService refunds;
    @Autowired private MockMvc mvc;
    @Autowired private SecretKey key;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry properties) {
        properties.add("payment.vnpay.ledger-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/ledger");
    }

    @BeforeEach
    void resetProvider() {
        MODE.set("PROCESSING");
        REQUESTS.set(0);
        LAST_REQUEST.set(null);
    }

    @AfterAll
    static void stopProvider() {
        SERVER.stop(0);
    }

    @Test
    void refundSubmissionCommitsClaimAndUsesOriginalCreateDateAndFullAmount() {
        var payment = lateCapture();
        var result = refunds.submit(payment.getId(), 25L);
        assertEquals(VnpayRefundState.PROCESSING, result.state());
        assertEquals(RefundStatus.REQUIRED, reload(payment).getRefundStatus());
        var request = LAST_REQUEST.get();
        var capture = captures.findById(payment.getId()).orElseThrow();
        assertEquals("02", request.get("vnp_TransactionType"));
        assertEquals("1250", request.get("vnp_Amount"));
        assertEquals(capture.getTransactionNumber(), request.get("vnp_TransactionNo"));
        assertEquals(time(capture.getCreatedAt()), request.get("vnp_TransactionDate"));
        assertEquals("203.0.113.10", request.get("vnp_IpAddr"));
        assertEquals("ADMIN25", request.get("vnp_CreateBy"));
        assertEquals(result.requestId(), request.get("vnp_RequestId"));
        refunds.submit(payment.getId(), 25L);
        assertEquals(1, REQUESTS.get());
    }

    @Test
    void concurrentRefundRequestsSendOneProviderPost() throws Exception {
        var payment = lateCapture();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 20)
                    .<java.util.concurrent.Callable<String>>mapToObj(ignored -> () -> refunds.submit(payment.getId(), 25L).requestId())
                    .toList();
            var results = executor.invokeAll(calls);
            var ids = new java.util.HashSet<String>();
            for (var result : results) {
                ids.add(result.get());
            }
            assertEquals(1, ids.size());
        }
        assertEquals(1, REQUESTS.get());
        assertEquals(RefundStatus.REQUIRED, reload(payment).getRefundStatus());
    }

    @Test
    void uncertainSubmissionIsNeverResentAndOriginalPaymentQueryCannotCompleteRefund() {
        var payment = lateCapture();
        MODE.set("DROP");
        assertEquals(VnpayRefundState.UNKNOWN, refunds.submit(payment.getId(), 25L).state());
        refunds.submit(payment.getId(), 25L);
        assertEquals(1, REQUESTS.get());
        MODE.set("PAYMENT");
        assertEquals(VnpayRefundState.UNKNOWN, refunds.reconcile(payment.getId()).state());
        assertEquals("querydr", LAST_REQUEST.get().get("vnp_Command"));
        assertEquals(RefundStatus.REQUIRED, reload(payment).getRefundStatus());
        MODE.set("COMPLETE");
        assertEquals(VnpayRefundState.COMPLETED, refunds.reconcile(payment.getId()).state());
        assertEquals(RefundStatus.REFUNDED, reload(payment).getRefundStatus());
        refunds.reconcile(payment.getId());
        refunds.submit(payment.getId(), 25L);
        assertEquals(3, REQUESTS.get());
    }

    @Test
    void invalidSignatureDuplicateJsonAndWrongAmountCannotRecordRefund() {
        for (String mode : List.of("BAD_HASH", "DUPLICATE", "WRONG_AMOUNT", "OVERSIZE")) {
            var payment = lateCapture();
            MODE.set(mode);
            assertEquals(VnpayRefundState.UNKNOWN, refunds.submit(payment.getId(), 25L).state());
            assertEquals(RefundStatus.REQUIRED, reload(payment).getRefundStatus());
            refunds.submit(payment.getId(), 25L);
        }
        assertEquals(4, REQUESTS.get());
    }

    @Test
    void signedFinalRefundIsRecordedOnceAndCaptureStatusIsPreserved() {
        var payment = lateCapture();
        MODE.set("COMPLETE");
        assertEquals(VnpayRefundState.COMPLETED, refunds.submit(payment.getId(), 25L).state());
        assertEquals(PaymentStatus.SUCCESS, reload(payment).getStatus());
        assertEquals(RefundStatus.REFUNDED, reload(payment).getRefundStatus());
        refunds.submit(payment.getId(), 25L);
        refunds.reconcile(payment.getId());
        assertEquals(1, REQUESTS.get());
    }

    @Test
    void refundAndReconciliationRequireAdministrator() throws Exception {
        var payment = lateCapture();
        for (String operation : List.of("refund", "reconcile")) {
            String url = "/api/payments/" + payment.getId() + "/vnpay/" + operation;
            mvc.perform(post(url)).andExpect(status().isUnauthorized());
            mvc.perform(post(url).header("Authorization", token("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/payments/" + payment.getId() + "/vnpay/refund")
                .header("Authorization", token("ADMIN"))).andExpect(status().isOk());
        assertEquals(1, REQUESTS.get());
    }

    @Test
    void pendingOrSuccessfulNonExpiredPaymentCannotBeRefunded() {
        var pending = payments.saveAndFlush(new Payment(IDS.incrementAndGet(), 10L, BigDecimal.TEN));
        assertThrows(com.app.payment.config.exception.ApiException.class, () -> refunds.submit(pending.getId(), 25L));
        pending.complete(PaymentStatus.SUCCESS);
        payments.saveAndFlush(pending);
        assertThrows(com.app.payment.config.exception.ApiException.class, () -> refunds.submit(pending.getId(), 25L));
        assertEquals(0, REQUESTS.get());
    }

    protected Payment lateCapture() {
        var payment = payments.saveAndFlush(new Payment(IDS.incrementAndGet(), 10L, new BigDecimal("12.50")));
        VnpayCheckoutResponse attempt = checkout.checkout(payment.getId(), 10L, "203.0.113.50");
        payment.expireReservation();
        payments.saveAndFlush(payment);
        var values = new LinkedMultiValueMap<String, String>();
        Map.of("vnp_TmnCode", "FIXTURE1", "vnp_TxnRef", attempt.reference(), "vnp_Amount", "1250",
                "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00", "vnp_TransactionNo", Long.toString(IDS.incrementAndGet()),
                "vnp_PayDate", time(Instant.now())).forEach(values::set);
        values.set("vnp_SecureHash", VnpaySignature.hmac(VnpaySignature.canonicalQuery(values.toSingleValueMap()), SECRET));
        assertEquals("00", checkout.ipn(values).responseCode());
        return payment;
    }

    private Payment reload(Payment payment) {
        return payments.findById(payment.getId()).orElseThrow();
    }

    private String token(String role) {
        return "Bearer " + Jwts.builder().subject("25").issuer("omnichannel-auth")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("roles", List.of(role)).signWith(key).compact();
    }

    private static String time(Instant instant) {
        return DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(instant);
    }

    private static HttpServer server() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/ledger", VnpayRefundTests::reply);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Fixture unavailable");
        }
    }

    private static void reply(HttpExchange exchange) throws IOException {
        try (exchange) {
            REQUESTS.incrementAndGet();
            var node = JSON.readTree(exchange.getRequestBody());
            var request = new LinkedHashMap<String, String>();
            node.properties().forEach(field -> request.put(field.getKey(), field.getValue().asText()));
            LAST_REQUEST.set(request);
            String command = request.get("vnp_Command");
            List<String> fields = "refund".equals(command) ? REFUND_FIELDS : QUERY_FIELDS;
            assertEquals(VnpaySignature.hmac(fields.stream().map(name -> request.getOrDefault(name, ""))
                    .collect(java.util.stream.Collectors.joining("|")), SECRET), request.get("vnp_SecureHash"));
            String mode = MODE.get();
            if (mode.equals("DROP")) {
                exchange.sendResponseHeaders(200, 100);
                return;
            }
            var response = new LinkedHashMap<String, String>();
            response.put("vnp_ResponseId", "FIXTURE" + REQUESTS.get());
            response.put("vnp_Command", command);
            response.put("vnp_ResponseCode", "00");
            response.put("vnp_Message", "Fixture response");
            response.put("vnp_TmnCode", "FIXTURE1");
            response.put("vnp_TxnRef", request.get("vnp_TxnRef"));
            response.put("vnp_Amount", mode.equals("WRONG_AMOUNT") ? "1251" : "1250");
            response.put("vnp_BankCode", "NCB");
            response.put("vnp_PayDate", time(Instant.now()));
            response.put("vnp_TransactionNo", Integer.toString(Math.abs(request.get("vnp_TxnRef").hashCode())));
            response.put("vnp_TransactionType", mode.equals("PAYMENT") ? "01" : "02");
            response.put("vnp_TransactionStatus", mode.equals("PROCESSING") ? "05" : "00");
            response.put("vnp_OrderInfo", request.get("vnp_OrderInfo"));
            var signedFields = new ArrayList<>(RESPONSE_FIELDS);
            if (command.equals("querydr")) {
                signedFields.add("vnp_PromotionCode");
                signedFields.add("vnp_PromotionAmount");
            }
            response.put("vnp_SecureHash", mode.equals("BAD_HASH") ? "0".repeat(128)
                    : VnpaySignature.hmac(signedFields.stream().map(name -> response.getOrDefault(name, ""))
                            .collect(java.util.stream.Collectors.joining("|")), SECRET));
            String body = JSON.writeValueAsString(response);
            if (mode.equals("DUPLICATE")) {
                body = body.substring(0, body.length() - 1) + ",\"vnp_Amount\":\"1250\"}";
            } else if (mode.equals("OVERSIZE")) {
                body = "x".repeat(16385);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}

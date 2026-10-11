package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.payment.config.exception.ApiException;
import com.app.payment.entity.Payment;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.repository.PaymentRepository;
import com.app.payment.service.PaymentService;
import io.jsonwebtoken.Jwts;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApiTests {

    private static final AtomicLong ORDER_IDS = new AtomicLong(1000000);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SecretKey key;

    @Test
    void healthIsPublicButPaymentRequiresJwt() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/payments/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void successReplayProducesOnlyOneOutcomeAndRejectsChangingIt() throws Exception {
        Payment payment = pending();
        simulate(payment.getId(), "SUCCESS", "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("SUCCESS"));
        simulate(payment.getId(), "SUCCESS", "ADMIN").andExpect(status().isOk());
        simulate(payment.getId(), "FAILED", "ADMIN").andExpect(status().isConflict());
        assertEquals(1, outboxCount(payment));
    }

    @Test
    void failedReplayIsStableAndPendingCannotBeSimulated() throws Exception {
        Payment payment = pending();
        simulate(payment.getId(), "PENDING", "ADMIN").andExpect(status().isBadRequest());
        simulate(payment.getId(), "FAILED", "ADMIN").andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("FAILED"));
        simulate(payment.getId(), "FAILED", "ADMIN").andExpect(status().isOk());
        assertEquals(1, outboxCount(payment));
    }

    @Test
    void onlyAdministratorMaySimulate() throws Exception {
        Payment payment = pending();
        simulate(payment.getId(), "SUCCESS", "USER").andExpect(status().isForbidden());
        mvc.perform(post("/api/payments/" + payment.getId() + "/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUCCESS\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyOwnerOrAdministratorMayReadPayment() throws Exception {
        Payment payment = pending();
        mvc.perform(get("/api/payments/by-order/" + payment.getOrderId()).header("Authorization", token(10, "USER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/payments/" + payment.getId()).header("Authorization", token(11, "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/payments/" + payment.getId()).header("Authorization", token(11, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentOutcomesCommitOnlyOneEvent() throws Exception {
        Payment payment = pending();
        Callable<Integer> success = () -> complete(payment.getId(), PaymentStatus.SUCCESS);
        Callable<Integer> failure = () -> complete(payment.getId(), PaymentStatus.FAILED);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(success, failure));
            var statuses = List.of(results.get(0).get(), results.get(1).get());
            assertEquals(1, statuses.stream().filter(code -> code == 200).count());
            assertEquals(1, statuses.stream().filter(code -> code == 409).count());
        }
        assertEquals(1, outboxCount(payment));
    }

    @Test
    void invalidJsonUnknownStatusAndInvalidJwtAreRejected() throws Exception {
        Payment payment = pending();
        simulate(payment.getId(), "OTHER", "ADMIN").andExpect(status().isBadRequest());
        mvc.perform(post("/api/payments/" + payment.getId() + "/simulate")
                        .header("Authorization", token(10, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCESS\",\"amount\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/payments/" + payment.getId()).header("Authorization", "Bearer invalid")
                        .header("X-User-Id", "10")).andExpect(status().isUnauthorized());
    }

    private int complete(
            Long id,
            PaymentStatus outcome
    ) {
        try {
            paymentService.simulate(id, outcome);
            return 200;
        } catch (ApiException exception) {
            return exception.getStatus().value();
        }
    }

    private Payment pending() {
        return payments.saveAndFlush(new Payment(ORDER_IDS.incrementAndGet(), 10L, new BigDecimal("25.00")));
    }

    private int outboxCount(Payment payment) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE payload LIKE ?",
                Integer.class, "%\"orderId\":" + payment.getOrderId() + ",%");
    }

    private org.springframework.test.web.servlet.ResultActions simulate(
            Long id,
            String outcome,
            String role
    ) throws Exception {
        return mvc.perform(post("/api/payments/" + id + "/simulate").header("Authorization", token(10, role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + outcome + "\"}"));
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

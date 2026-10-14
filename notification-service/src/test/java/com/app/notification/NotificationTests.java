package com.app.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.notification.config.OrderEventListener;
import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.entity.NotificationLog;
import com.app.notification.mapper.NotificationMapper;
import com.app.notification.repository.NotificationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@ActiveProfiles("test")
class NotificationTests {

    private static final AtomicLong ORDER_IDS = new AtomicLong(800000);

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationMapper mapper;

    @Autowired
    private OrderEventListener listener;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void privatePrometheusExportsJvmMetricsWhileSensitiveEndpointsStayProtected() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyHealthIsAccessibleWithoutCredentials() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("success").value(false));
    }

    @Test
    void notificationStoresFinalSnapshotAndMapsOwnedResponse() {
        long orderId = ORDER_IDS.incrementAndGet();
        var event = event(orderId);
        notifications.saveAndFlush(new NotificationLog(event));
        var response = mapper.toResponse(notifications.findByOrderId(orderId).orElseThrow());
        assertEquals(event.eventId(), response.eventId());
        assertEquals("order.confirmed", response.eventType());
        assertEquals(orderId, response.orderId());
    }

    @Test
    void expirySnapshotAllowsNoPaymentButConfirmedSnapshotStillRequiresPayment() {
        long orderId = ORDER_IDS.incrementAndGet();
        var expired = new CommerceEvent(UUID.randomUUID().toString(), "order.expired", orderId,
                1L, new BigDecimal("12.50"), null, Instant.now());
        notifications.saveAndFlush(new NotificationLog(expired));
        assertEquals("order.expired", mapper.toResponse(
                notifications.findByOrderId(orderId).orElseThrow()).eventType());
        var invalid = new CommerceEvent(UUID.randomUUID().toString(), "order.confirmed",
                ORDER_IDS.incrementAndGet(), 1L, new BigDecimal("12.50"), null, Instant.now());
        assertThrows(DataIntegrityViolationException.class,
                () -> notifications.saveAndFlush(new NotificationLog(invalid)));
    }

    @Test
    void uniqueOrderConstraintRejectsSecondNotification() {
        long orderId = ORDER_IDS.incrementAndGet();
        notifications.saveAndFlush(new NotificationLog(event(orderId)));
        assertThrows(DataIntegrityViolationException.class, () -> notifications.saveAndFlush(new NotificationLog(event(orderId))));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_logs WHERE order_id=?", Integer.class, orderId));
    }

    @Test
    void malformedMessageIsRejectedWithoutInfiniteRedelivery() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.receive(
                new Message("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), new MessageProperties())));
    }

    private CommerceEvent event(long orderId) {
        return new CommerceEvent(UUID.randomUUID().toString(), "order.confirmed", orderId, 1L,
                new BigDecimal("12.50"), 1L, Instant.now());
    }
}

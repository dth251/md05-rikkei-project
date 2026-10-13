package com.app.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.app.notification.service.impl.EmailDeliveryServiceImpl;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EmailDeliveryTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistedClaimRetriesAreBoundedAndSentWorkIsNotReclaimed() {
        String id = pending();
        var service = new EmailDeliveryServiceImpl(jdbc, true, 2, 1, 120);
        var first = service.claim().getFirst();
        assertEquals(id, first.deliveryId());
        assertEquals(1, first.attempts());
        assertEquals("SENDING", state(id));
        assertTrue(service.claim().isEmpty());
        service.failed(first, "SMTP_FAILURE");
        assertEquals("RETRY", state(id));
        assertTrue(service.claim().isEmpty());
        jdbc.update("UPDATE email_deliveries SET next_attempt_at=? WHERE delivery_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), id);
        var second = service.claim().getFirst();
        assertEquals(2, second.attempts());
        service.failed(second, "SMTP_FAILURE");
        assertEquals("FAILED", state(id));
        assertTrue(service.claim().isEmpty());
        service.sent(first);
        assertEquals("FAILED", state(id));
    }

    @Test
    void crashAfterCommittedClaimBecomesUnknownWithoutBlindResend() {
        String id = pending();
        var beforeRestart = new EmailDeliveryServiceImpl(jdbc, true, 5, 1, 120);
        beforeRestart.claim();
        jdbc.update("UPDATE email_deliveries SET lease_until=? WHERE delivery_id=?",
                Timestamp.from(Instant.now().minusSeconds(1)), id);
        var afterRestart = new EmailDeliveryServiceImpl(jdbc, true, 5, 1, 120);
        assertTrue(afterRestart.claim().isEmpty());
        assertEquals("UNKNOWN", state(id));
        assertEquals("LEASE_EXPIRED", jdbc.queryForObject(
                "SELECT error_code FROM email_deliveries WHERE delivery_id=?", String.class, id));
    }

    @Test
    void sentDeliveryPersistsAndNeverReentersQueue() {
        String id = pending();
        var service = new EmailDeliveryServiceImpl(jdbc, true, 5, 1, 120);
        service.sent(service.claim().getFirst());
        assertEquals("SENT", state(id));
        assertNotNull(jdbc.queryForObject("SELECT sent_at FROM email_deliveries WHERE delivery_id=?", Timestamp.class, id));
        assertTrue(service.claim().isEmpty());
    }

    private String pending() {
        String id = UUID.randomUUID().toString();
        long order = Math.abs(UUID.randomUUID().getMostSignificantBits() / 2);
        jdbc.update("INSERT INTO email_deliveries(delivery_id,order_id,user_id,event_type,state,attempts,created_at,next_attempt_at) "
                        + "VALUES(?,?,1,'order.confirmed','PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, order);
        return id;
    }

    private String state(
            String id
    ) {
        return jdbc.queryForObject("SELECT state FROM email_deliveries WHERE delivery_id=?", String.class, id);
    }
}

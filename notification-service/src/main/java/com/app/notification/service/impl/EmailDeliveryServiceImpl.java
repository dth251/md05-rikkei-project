package com.app.notification.service.impl;

import com.app.notification.dto.event.CommerceEvent;
import com.app.notification.dto.response.EmailTask;
import com.app.notification.service.EmailDeliveryService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryServiceImpl implements EmailDeliveryService {

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int maxAttempts;
    private final int batchSize;
    private final int leaseSeconds;

    public EmailDeliveryServiceImpl(
            JdbcTemplate jdbc,
            @Value("${notification.email.enabled:false}") boolean enabled,
            @Value("${notification.email.max-attempts:5}") int maxAttempts,
            @Value("${notification.email.batch-size:5}") int batchSize,
            @Value("${notification.email.lease-seconds:120}") int leaseSeconds
    ) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.maxAttempts = Math.clamp(maxAttempts, 1, 10);
        this.batchSize = Math.clamp(batchSize, 1, 20);
        this.leaseSeconds = Math.clamp(leaseSeconds, 60, 600);
    }

    @Override
    @Transactional
    public void enqueue(
            CommerceEvent event
    ) {
        if (!enabled) {
            return;
        }
        jdbc.update("INSERT INTO email_deliveries(delivery_id,order_id,user_id,event_type,state,attempts,created_at,next_attempt_at) "
                        + "VALUES(?,?,?,?,'PENDING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(order_id,event_type) DO NOTHING",
                event.eventId(), event.orderId(), event.userId(), event.type());
    }

    @Override
    @Transactional
    public List<EmailTask> claim() {
        // SMTP may have accepted a message before a crashed worker saved SENT. Do not resend blindly.
        jdbc.update("UPDATE email_deliveries SET state='UNKNOWN',error_code='LEASE_EXPIRED',lease_until=NULL "
                + "WHERE state='SENDING' AND lease_until<CURRENT_TIMESTAMP");
        var tasks = jdbc.query("SELECT delivery_id,order_id,user_id,event_type,attempts FROM email_deliveries "
                        + "WHERE state IN ('PENDING','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY created_at,delivery_id LIMIT ? FOR UPDATE SKIP LOCKED",
                (row, index) -> new EmailTask(row.getString(1), row.getLong(2), row.getLong(3),
                        row.getString(4), row.getInt(5) + 1), batchSize);
        for (var task : tasks) {
            jdbc.update("UPDATE email_deliveries SET state='SENDING',attempts=?,lease_until=? WHERE delivery_id=?",
                    task.attempts(), Timestamp.from(Instant.now().plusSeconds(leaseSeconds)), task.deliveryId());
        }
        return tasks;
    }

    @Override
    @Transactional
    public void sent(
            EmailTask task
    ) {
        jdbc.update("UPDATE email_deliveries SET state='SENT',sent_at=CURRENT_TIMESTAMP,error_code=NULL,lease_until=NULL "
                        + "WHERE delivery_id=? AND state='SENDING' AND attempts=?",
                task.deliveryId(), task.attempts());
    }

    @Override
    @Transactional
    public void failed(
            EmailTask task,
            String errorCode
    ) {
        if (!List.of("SMTP_FAILURE", "RECIPIENT_UNAVAILABLE", "INVALID_RECIPIENT").contains(errorCode)) {
            throw new IllegalArgumentException("Normalized delivery error required");
        }
        boolean terminal = task.attempts() >= maxAttempts || "INVALID_RECIPIENT".equals(errorCode);
        long seconds = Math.min(300L, 1L << Math.min(task.attempts(), 8));
        jdbc.update("UPDATE email_deliveries SET state=?,error_code=?,next_attempt_at=?,lease_until=NULL "
                        + "WHERE delivery_id=? AND state='SENDING' AND attempts=?",
                terminal ? "FAILED" : "RETRY", errorCode, Timestamp.from(Instant.now().plusSeconds(seconds)),
                task.deliveryId(), task.attempts());
    }

    @Override
    @Transactional
    public void uncertain(
            EmailTask task
    ) {
        jdbc.update("UPDATE email_deliveries SET state='UNKNOWN',error_code='SMTP_UNCERTAIN',lease_until=NULL "
                        + "WHERE delivery_id=? AND state='SENDING' AND attempts=?",
                task.deliveryId(), task.attempts());
    }
}

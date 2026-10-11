package com.app.payment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentOrderLock {

    private final JdbcTemplate jdbc;

    public PaymentOrderLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lock(Long orderId) {
        // Transaction-scoped and database-local: serializes creation/expiry even before a payment row exists.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", (org.springframework.jdbc.core.RowCallbackHandler) row -> {}, orderId);
    }
}

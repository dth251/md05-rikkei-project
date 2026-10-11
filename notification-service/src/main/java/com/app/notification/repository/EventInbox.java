package com.app.notification.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EventInbox {

    private final JdbcTemplate jdbc;

    public EventInbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(
            String eventId,
            String eventType
    ) {
        return jdbc.update("INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (event_id) DO NOTHING", eventId, eventType) == 1;
    }
}

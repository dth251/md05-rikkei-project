package com.app.payment.service;

import com.app.payment.dto.event.CommerceEvent;
import java.util.List;

public interface OutboxService {

    void append(CommerceEvent event);

    List<String> pending();

    void publish(String eventId);

    void defer(String eventId);
}

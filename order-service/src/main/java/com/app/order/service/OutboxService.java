package com.app.order.service;

import com.app.order.dto.event.CommerceEvent;
import java.util.List;

public interface OutboxService {

    void append(CommerceEvent event);

    List<String> pending();

    void publish(String eventId);
}

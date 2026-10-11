package com.app.order.config;

import com.app.order.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "commerce.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxService outboxService;

    public OutboxRelay(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${commerce.outbox.delay-ms:3000}")
    public void relay() {
        for (String eventId : outboxService.pending()) {
            try {
                outboxService.publish(eventId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Commerce outbox pending; relay will retry");
                break;
            }
        }
    }
}

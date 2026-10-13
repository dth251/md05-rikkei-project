package com.app.notification.config;

import com.app.notification.service.EmailDeliveryService;
import com.app.notification.service.EmailTransport;
import com.app.notification.service.RecipientClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class EmailDispatcher {

    private final EmailDeliveryService deliveries;
    private final RecipientClient recipients;
    private final EmailTransport transport;
    private final MeterRegistry metrics;

    public EmailDispatcher(
            EmailDeliveryService deliveries,
            RecipientClient recipients,
            EmailTransport transport,
            MeterRegistry metrics
    ) {
        this.deliveries = deliveries;
        this.recipients = recipients;
        this.transport = transport;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${notification.email.delay-ms:2000}")
    public void dispatch() {
        for (var task : deliveries.claim()) {
            try {
                String email = recipients.email(task.userId());
                transport.send(task, email);
                deliveries.sent(task);
                metrics.counter("commerce.email.delivery", "result", "sent").increment();
            } catch (MailException exception) {
                if (definitelyNotAccepted(exception)) {
                    deliveries.failed(task, "SMTP_FAILURE");
                } else {
                    deliveries.uncertain(task);
                }
                metrics.counter("commerce.email.delivery", "result", "smtp_failure").increment();
            } catch (IllegalArgumentException exception) {
                deliveries.failed(task, "INVALID_RECIPIENT");
                metrics.counter("commerce.email.delivery", "result", "invalid_recipient").increment();
            } catch (IllegalStateException exception) {
                deliveries.failed(task, "RECIPIENT_UNAVAILABLE");
                metrics.counter("commerce.email.delivery", "result", "recipient_unavailable").increment();
            }
        }
    }

    private boolean definitelyNotAccepted(
            MailException exception
    ) {
        if (exception instanceof org.springframework.mail.MailAuthenticationException
                || exception instanceof org.springframework.mail.MailPreparationException) {
            return true;
        }
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 10; depth++, cause = cause.getCause()) {
            if (cause instanceof java.net.ConnectException || "MailConnectException".equals(cause.getClass().getSimpleName())) {
                return true;
            }
        }
        return false;
    }
}

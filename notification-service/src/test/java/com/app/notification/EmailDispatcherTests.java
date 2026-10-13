package com.app.notification;

import static org.mockito.Mockito.*;

import com.app.notification.config.EmailDispatcher;
import com.app.notification.dto.response.EmailTask;
import com.app.notification.service.EmailDeliveryService;
import com.app.notification.service.EmailTransport;
import com.app.notification.service.RecipientClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class EmailDispatcherTests {

    @Test
    void definiteConnectionFailureRetriesButAmbiguousTimeoutNeedsReview() {
        var deliveries = mock(EmailDeliveryService.class);
        var recipients = mock(RecipientClient.class);
        var transport = mock(EmailTransport.class);
        var task = new EmailTask(UUID.randomUUID().toString(), 1L, 1L, "order.confirmed", 1);
        when(deliveries.claim()).thenReturn(List.of(task));
        when(recipients.email(1L)).thenReturn("fixture@example.com");
        doThrow(new MailSendException("unavailable", new ConnectException())).when(transport).send(task, "fixture@example.com");
        var dispatcher = new EmailDispatcher(deliveries, recipients, transport, new SimpleMeterRegistry());
        dispatcher.dispatch();
        verify(deliveries).failed(task, "SMTP_FAILURE");
        verify(deliveries, never()).uncertain(task);
        reset(deliveries);
        when(deliveries.claim()).thenReturn(List.of(task));
        doThrow(new MailSendException("uncertain", new SocketTimeoutException())).when(transport).send(task, "fixture@example.com");
        dispatcher.dispatch();
        verify(deliveries).uncertain(task);
        verify(deliveries, never()).failed(any(), any());
    }
}

package com.app.notification.service.impl;

import com.app.notification.dto.response.EmailRecipientResponse;
import com.app.notification.service.RecipientClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class RecipientClientImpl implements RecipientClient {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final URI base;
    private final String credential;

    public RecipientClientImpl(
            ObjectMapper json,
            @Value("${notification.auth-url:http://auth-service:8081}") URI base,
            @Value("${notification.recipient-token:}") String credential
    ) {
        if (credential.length() < 32 || credential.length() > 256
                || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalStateException("Private Auth URL and Notification credential required");
        }
        this.json = json;
        this.base = base;
        this.credential = credential;
    }

    @Override
    public String email(
            Long userId
    ) {
        try {
            var request = HttpRequest.newBuilder(base.resolve("/internal/notification-recipients/" + userId))
                    .timeout(Duration.ofSeconds(5)).header("X-Notification-Credential", credential).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().length() > 4096) {
                throw new IllegalStateException("Recipient unavailable");
            }
            var recipient = json.treeToValue(json.readTree(response.body()).path("data"), EmailRecipientResponse.class);
            if (recipient == null || !userId.equals(recipient.userId()) || recipient.email() == null) {
                throw new IllegalStateException("Recipient unavailable");
            }
            return recipient.email();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recipient unavailable");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Recipient unavailable");
        }
    }
}

package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
class PaymentLiveTests extends PaymentLiveFixture {

    @Test
    void realOrderEventCreatesOwnedPaymentAndSuccessIsIdempotent() throws Exception {
        JsonNode payment = createPending();
        long id = payment.path("id").asLong();
        assertEquals(403, simulate(id, "SUCCESS", "USER").statusCode());
        assertEquals(404, send("GET", "/api/payments/" + id, null, token(900102, "USER")).statusCode());
        assertEquals(200, send("GET", "/api/payments/" + id, null, token(900102, "ADMIN")).statusCode());
        assertEquals(200, simulate(id, "SUCCESS", "ADMIN").statusCode());
        assertEquals(200, simulate(id, "SUCCESS", "ADMIN").statusCode());
        assertEquals(409, simulate(id, "FAILED", "ADMIN").statusCode());
        assertOutcome(payment.path("orderId").asLong(), "payment.succeeded");
    }

    @Test
    void failureProducesOneDurableOutcome() throws Exception {
        JsonNode payment = createPending();
        long id = payment.path("id").asLong();
        assertEquals(400, simulate(id, "PENDING", "ADMIN").statusCode());
        assertEquals(200, simulate(id, "FAILED", "ADMIN").statusCode());
        assertEquals(200, simulate(id, "FAILED", "ADMIN").statusCode());
        assertOutcome(payment.path("orderId").asLong(), "payment.failed");
    }

    @Test
    void duplicateEventAndDifferentEventForSameOrderCreateOnePayment() throws Exception {
        JsonNode payment = createPending();
        long orderId = payment.path("orderId").asLong();
        String eventId = UUID.randomUUID().toString();
        publishOrder(eventId, payment);
        publishOrder(eventId, payment);
        String anotherId = UUID.randomUUID().toString();
        publishOrder(anotherId, payment);
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, anotherId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, anotherId));
        assertEquals(1, scalar("SELECT count(*) FROM payments WHERE order_id=?", orderId));
    }

}

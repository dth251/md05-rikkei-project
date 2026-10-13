package com.app.notification;

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
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
class NotificationLiveTests extends NotificationLiveFixture {

    @Test
    void shipmentAndCompletionHaveOwnLogsAndReplayDoesNotDuplicateStages() throws Exception {
        var fixture = scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
        long orderId = fixture.order().path("id").asLong();
        var shipment = Map.of("carrier", "Fixture Carrier", "trackingNumber", "TRACK-" + UUID.randomUUID(),
                "shippingAddress", Map.of("recipientName", "Fixture Recipient", "phone", "0901234567",
                        "street", "Fixture street", "ward", "Ward", "district", "District", "province", "Province"));
        assertEquals(409, send("POST", "/api/orders/" + orderId + "/complete", null, adminToken()).statusCode());
        replayConcurrently("/api/orders/" + orderId + "/ship", shipment);
        var shipped = send("POST", "/api/orders/" + orderId + "/ship", shipment, adminToken());
        assertEquals(200, shipped.statusCode());
        assertEquals("SHIPPING", json.readTree(shipped.body()).path("data").path("status").asText());
        // Separate the two bursts within the Gateway's configured 20/s, burst-40 quota.
        Thread.sleep(1100);
        replayConcurrently("/api/orders/" + orderId + "/complete", null);
        var completed = send("POST", "/api/orders/" + orderId + "/complete", null, adminToken());
        assertEquals(200, completed.statusCode());
        assertEquals("COMPLETED", json.readTree(completed.body()).path("data").path("status").asText());
        assertTrue(!json.readTree(completed.body()).path("data").path("shipment").path("completedAt").isNull());
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM fulfillment_logs WHERE order_id=?", orderId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM fulfillment_logs WHERE order_id=?", orderId));
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=?", orderId));
        String replayId = UUID.randomUUID().toString();
        publishFulfillment(replayId, fixture, "order.shipped");
        publishFulfillment(replayId, fixture, "order.shipped");
        String completedId = UUID.randomUUID().toString();
        publishFulfillment(completedId, fixture, "order.completed");
        deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)
                && scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?,?)", replayId, completedId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?,?)", replayId, completedId));
        assertEquals(2, scalar("SELECT count(*) FROM fulfillment_logs WHERE order_id=?", orderId));
    }

    private void replayConcurrently(
            String path,
            Object body
    ) throws Exception {
        String admin = adminToken();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 20; index++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return send("POST", path, body, admin).statusCode();
                }));
            }
            start.countDown();
            for (var result : results) {
                assertEquals(200, result.get());
            }
        }
    }

    private void publishFulfillment(
            String eventId,
            Fixture fixture,
            String type
    ) throws Exception {
        var event = Map.of("eventId", eventId, "type", type, "orderId", fixture.order().path("id").asLong(),
                "userId", fixture.order().path("userId").asLong(), "amount", fixture.order().path("totalAmount").decimalValue(),
                "occurredAt", Instant.now().toString());
        String user = System.getenv().getOrDefault("RABBITMQ_LIVE_USER", "commerce_app");
        String password = System.getenv("RABBITMQ_LIVE_PASSWORD");
        assertTrue(password != null && !password.isBlank());
        String credentials = Base64.getEncoder().encodeToString((user + ":" + password)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var body = Map.of("properties", Map.of("delivery_mode", 2, "content_type", "application/json", "message_id", eventId),
                "routing_key", type, "payload", json.writeValueAsString(event), "payload_encoding", "string");
        var request = HttpRequest.newBuilder(URI.create(System.getenv().getOrDefault("RABBITMQ_LIVE_URL", "http://127.0.0.1:15672")
                        + "/api/exchanges/%2F/commerce.events/publish"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).path("routed").asBoolean());
    }

    @Test
    void confirmedOrderWithRealAuthTokenLogsNotification() throws Exception {
        scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
    }

    @Test
    void cancelledPosOrderLogsNotificationAndReturnsStock() throws Exception {
        scenario("FAILED", "CANCELLED", "order.cancelled", "POS");
    }

    @Test
    void duplicateEventAndDifferentIdForSameOrderLogOnlyOnce() throws Exception {
        var fixture = scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
        String eventId = UUID.randomUUID().toString();
        publish(eventId, fixture);
        publish(eventId, fixture);
        String anotherId = UUID.randomUUID().toString();
        publish(anotherId, fixture);
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline) && scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, anotherId) != 2) {
            Thread.sleep(300);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?, ?)", eventId, anotherId));
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=?", fixture.order().path("id").asLong()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NOTIFICATION_RECOVERY_TEST", matches = "true")
    void stoppedNotificationDoesNotBlockSagaAndConsumesQueueOnRestart() throws Exception {
        Fixture fixture;
        try {
            docker("stop", "notification-service");
            fixture = finalized("SUCCESS", "CONFIRMED", "WEB");
            assertEquals(0, scalar("SELECT count(*) FROM notification_logs WHERE order_id=?", fixture.order().path("id").asLong()));
        } finally {
            docker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "notification-service");
        }
        awaitNotification(fixture.order().path("id").asLong(), "order.confirmed");
    }

}

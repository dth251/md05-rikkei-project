package com.app.notification;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "SMTP_LIVE_TEST", matches = "true")
class EmailLiveTests extends NotificationLiveFixture {

    @Test
    void postgresDeduplicatesReplayAndSmtpReceivesRealAuthRecipient() throws Exception {
        var fixture = scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
        awaitSent(fixture);
        String event = UUID.randomUUID().toString();
        publish(event, fixture);
        publish(event, fixture);
        String second = UUID.randomUUID().toString();
        publish(second, fixture);
        Instant deadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(deadline) && scalar(
                "SELECT count(*) FROM processed_events WHERE event_id IN (?,?)", event, second) != 2) {
            Thread.sleep(200);
        }
        assertEquals(2, scalar("SELECT count(*) FROM processed_events WHERE event_id IN (?,?)", event, second));
        assertEquals(1, scalar("SELECT count(*) FROM email_deliveries WHERE order_id=?", fixture.order().path("id").asLong()));
        assertMailbox(fixture);
    }

    @Test
    void smtpConnectionOutageDoesNotBlockSagaAndDeliveryRecovers() throws Exception {
        Fixture fixture;
        try {
            smtpDocker("stop", "mailpit");
            fixture = scenario("SUCCESS", "CONFIRMED", "order.confirmed", "WEB");
            Instant deadline = Instant.now().plusSeconds(15);
            while (Instant.now().isBefore(deadline) && scalar(
                    "SELECT count(*) FROM email_deliveries WHERE order_id=? AND state='RETRY' AND attempts>0",
                    fixture.order().path("id").asLong()) != 1) {
                Thread.sleep(300);
            }
            assertEquals(1, scalar("SELECT count(*) FROM email_deliveries WHERE order_id=? AND state='RETRY'",
                    fixture.order().path("id").asLong()));
        } finally {
            smtpDocker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "120", "mailpit");
        }
        awaitSent(fixture);
        assertMailbox(fixture);
    }

    @Test
    void cancellationSendsRealEmailToRegisteredOrderOwner() throws Exception {
        var fixture = scenario("FAILED", "CANCELLED", "order.cancelled", "WEB");
        awaitSent(fixture);
        assertMailbox(fixture, "đã hủy");
    }

    @Test
    void unpaid150SecondDeadlineReleasesStockAndSendsExpiryEmail() throws Exception {
        var fixture = pending("WEB");
        long orderId = fixture.order().path("id").asLong();
        long productId = fixture.order().path("items").get(0).path("productId").asLong();
        Instant created = Instant.parse(fixture.order().path("createdAt").asText());
        boolean actualWait = "true".equals(System.getenv("SMTP_EXPIRY_REAL_WAIT"));
        if (!actualWait) {
            try (var connection = java.sql.DriverManager.getConnection(
                    System.getenv().getOrDefault("ORDER_LIVE_DB_URL", "jdbc:postgresql://localhost:15432/order_db"),
                    "order_user", System.getenv("ORDER_LIVE_DB_PASSWORD"));
                 var statement = connection.prepareStatement("UPDATE orders SET created_at=? WHERE id=? AND status='PENDING_PAYMENT'")) {
                statement.setTimestamp(1, java.sql.Timestamp.from(Instant.now().minusSeconds(151)));
                statement.setLong(2, orderId);
                assertEquals(1, statement.executeUpdate());
            }
        } else {
            while (Instant.now().isBefore(created.plusSeconds(149))) {
                var response = send("GET", "/api/orders/" + orderId, null, fixture.userToken());
                assertEquals(200, response.statusCode());
                assertEquals("PENDING_PAYMENT", json.readTree(response.body()).path("data").path("status").asText());
                Thread.sleep(1000);
            }
        }
        Instant deadline = actualWait ? created.plusSeconds(170) : Instant.now().plusSeconds(30);
        String status;
        do {
            var response = send("GET", "/api/orders/" + orderId, null, fixture.userToken());
            assertEquals(200, response.statusCode());
            status = json.readTree(response.body()).path("data").path("status").asText();
            if ("EXPIRED".equals(status)) {
                break;
            }
            Thread.sleep(500);
        } while (Instant.now().isBefore(deadline));
        assertEquals("EXPIRED", status);
        if (actualWait) {
            assertFalse(Instant.now().isBefore(created.plusSeconds(150)), "Order must not expire before 150 seconds");
        }
        awaitNotification(orderId, "order.expired");
        awaitSent(fixture);
        var stock = send("GET", "/api/inventory/" + productId, null, adminToken());
        assertEquals(200, stock.statusCode());
        assertEquals(1, json.readTree(stock.body()).path("data").path("available").asInt());
        assertEquals(0, json.readTree(stock.body()).path("data").path("reserved").asInt());
        assertMailbox(fixture, "đã hủy do quá hạn thanh toán");
        assertEquals(1, scalar("SELECT count(*) FROM notification_logs WHERE order_id=? AND payment_id IS NULL", orderId));
        String replay = UUID.randomUUID().toString();
        publish(replay, fixture, "order.expired");
        publish(replay, fixture, "order.expired");
        Instant replayDeadline = Instant.now().plusSeconds(20);
        while (Instant.now().isBefore(replayDeadline)
                && scalar("SELECT count(*) FROM processed_events WHERE event_id=?", replay) != 1) {
            Thread.sleep(200);
        }
        assertEquals(1, scalar("SELECT count(*) FROM processed_events WHERE event_id=?", replay));
        assertEquals(1, scalar("SELECT count(*) FROM email_deliveries WHERE order_id=?", orderId));
        assertEquals(409, send("POST", "/api/payments/" + fixture.payment().path("id").asLong() + "/simulate",
                java.util.Map.of("status", "SUCCESS"), adminToken()).statusCode());
        assertMailbox(fixture, "đã hủy do quá hạn thanh toán");
    }

    @Test
    void notificationRestartConsumesDurableEventAndSendsOnce() throws Exception {
        Fixture fixture;
        try {
            smtpDocker("stop", "notification-service");
            fixture = finalized("SUCCESS", "CONFIRMED", "WEB");
            assertEquals(0, scalar("SELECT count(*) FROM email_deliveries WHERE order_id=?",
                    fixture.order().path("id").asLong()));
        } finally {
            smtpDocker("up", "-d", "--no-deps", "--wait", "--wait-timeout", "180", "notification-service");
        }
        awaitNotification(fixture.order().path("id").asLong(), "order.confirmed");
        awaitSent(fixture);
        assertMailbox(fixture);
    }

    private void awaitSent(
            Fixture fixture
    ) throws Exception {
        Instant deadline = Instant.now().plusSeconds(45);
        while (Instant.now().isBefore(deadline) && scalar(
                "SELECT count(*) FROM email_deliveries WHERE order_id=? AND state='SENT' AND sent_at IS NOT NULL",
                fixture.order().path("id").asLong()) != 1) {
            Thread.sleep(300);
        }
        assertEquals(1, scalar("SELECT count(*) FROM email_deliveries WHERE order_id=? AND state='SENT'",
                fixture.order().path("id").asLong()));
    }

    private void assertMailbox(
            Fixture fixture
    ) throws Exception {
        assertMailbox(fixture, "đã xác nhận");
    }

    private void assertMailbox(
            Fixture fixture,
            String status
    ) throws Exception {
        String base = System.getenv().getOrDefault("SMTP_INBOX_URL", "http://127.0.0.1:18025");
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        int matching = 0;
        for (var message : json.readTree(response.body()).path("messages")) {
            if (("Đơn hàng #" + fixture.order().path("id").asLong() + " " + status).equals(message.path("Subject").asText())) {
                matching++;
                assertEquals(fixture.email(), message.path("To").get(0).path("Address").asText());
            }
        }
        assertEquals(1, matching, "Exactly one email expected for fixture order");
    }

    private void smtpDocker(
            String... arguments
    ) throws Exception {
        var command = new ArrayList<>(List.of("docker", "compose", "-f", "docker-compose.yml", "-f",
                "docker-compose.local.yml", "-f", "docker-compose.smtp-test.yml", "--profile", "smtp-test"));
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).directory(Path.of(
                System.getenv().getOrDefault("NOTIFICATION_SMOKE_PROJECT_DIR", "..")).toFile())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(210, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("SMTP recovery operation timed out");
        }
        assertEquals(0, process.exitValue(), "SMTP recovery operation failed");
    }
}

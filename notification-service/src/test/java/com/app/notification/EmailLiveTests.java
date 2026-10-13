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
        String base = System.getenv().getOrDefault("SMTP_INBOX_URL", "http://127.0.0.1:18025");
        var response = client.send(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        int matching = 0;
        for (var message : json.readTree(response.body()).path("messages")) {
            if (("Đơn hàng #" + fixture.order().path("id").asLong() + " đã xác nhận").equals(message.path("Subject").asText())) {
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

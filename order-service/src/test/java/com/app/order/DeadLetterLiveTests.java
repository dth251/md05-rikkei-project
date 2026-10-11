package com.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Tag("live")
@EnabledIfEnvironmentVariable(named = "ORDER_SAGA_LIVE_TEST", matches = "true")
class DeadLetterLiveTests {

    @Test
    void rejectedMessageRetainsBodyAndIdentityInDurableDeadLetterQueue() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv().getOrDefault("RABBITMQ_LIVE_HOST", "127.0.0.1"));
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_LIVE_AMQP_PORT", "15673")));
        factory.setUsername(System.getenv("RABBITMQ_LIVE_USER"));
        factory.setPassword(System.getenv("RABBITMQ_LIVE_PASSWORD"));
        String eventId = UUID.randomUUID().toString();
        byte[] payload = ("{\"eventId\":\"" + eventId + "\",\"type\":\"order.created\",\"orderId\":-1}")
                .getBytes(StandardCharsets.UTF_8);
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.confirmSelect();
            channel.basicPublish("commerce.events", "order.created", true,
                    new AMQP.BasicProperties.Builder().contentType("application/json")
                            .messageId(eventId).deliveryMode(2).build(), payload);
            channel.waitForConfirmsOrDie(5000);
            Instant deadline = Instant.now().plusSeconds(30);
            boolean found = false;
            while (Instant.now().isBefore(deadline) && !found) {
                var message = channel.basicGet("payment.order-created.q.dlq", false);
                if (message == null) {
                    Thread.sleep(250);
                    continue;
                }
                // Keep all DLQ data: unacked inspection messages requeue when this channel closes.
                if (eventId.equals(message.getProps().getMessageId())) {
                    assertEquals(new String(payload, StandardCharsets.UTF_8),
                            new String(message.getBody(), StandardCharsets.UTF_8));
                    assertNotNull(message.getProps().getHeaders().get("x-death"));
                    assertEquals(2, message.getProps().getDeliveryMode());
                    found = true;
                }
            }
            assertTrue(found, "Rejected event must remain inspectable in DLQ");
        }
    }
}

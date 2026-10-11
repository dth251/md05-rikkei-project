package com.app.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ConnectionFactory;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Scoped operator tool; payload/credentials are never printed. No queue deletion or purge. */
public final class DeadLetterTool {

    private static final Map<String, Set<String>> ROUTES = Map.of(
            "payment.order-created.q", Set.of("order.created"),
            "order.payment-result.q", Set.of("payment.succeeded", "payment.failed"),
            "payment.order-expired.q", Set.of("order.expired", "order.refund-requested"),
            "notification.order.q", Set.of("order.confirmed", "order.cancelled"),
            "notification.fulfillment.q", Set.of("order.shipped", "order.completed"));

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !Set.of("inspect", "redrive").contains(args[0]) || !ROUTES.containsKey(args[1])) {
            throw new IllegalArgumentException("Use inspect|redrive and an approved source queue");
        }
        int limit = Integer.parseInt(System.getenv().getOrDefault("DLQ_LIMIT", "1"));
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("DLQ_LIMIT must be 1–100");
        }
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv().getOrDefault("RABBITMQ_LIVE_HOST", "127.0.0.1"));
        factory.setPort(Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_LIVE_AMQP_PORT", "15673")));
        factory.setUsername(System.getenv("RABBITMQ_LIVE_USER"));
        factory.setPassword(System.getenv("RABBITMQ_LIVE_PASSWORD"));
        ObjectMapper json = new ObjectMapper();
        try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
            channel.confirmSelect();
            AtomicBoolean returned = new AtomicBoolean();
            channel.addReturnListener(message -> returned.set(true));
            for (int index = 0; index < limit; index++) {
                var message = channel.basicGet(args[1] + ".dlq", false);
                if (message == null) {
                    break;
                }
                if ("inspect".equals(args[0])) {
                    String identity = message.getProps().getMessageId();
                    try {
                        identity = UUID.fromString(identity).toString();
                    } catch (RuntimeException exception) {
                        identity = "invalid";
                    }
                    System.out.println("action=inspect queue=" + args[1] + " eventId=" + identity
                            + " bytes=" + message.getBody().length);
                    continue;
                }
                var event = json.readTree(message.getBody());
                String eventId = event.path("eventId").asText();
                String route = event.path("type").asText();
                UUID.fromString(eventId);
                if (!ROUTES.get(args[1]).contains(route) || event.path("orderId").asLong() <= 0
                        || event.path("userId").asLong() <= 0 || event.path("amount").decimalValue().signum() <= 0) {
                    throw new IllegalArgumentException("Invalid payload remains in DLQ; repair root cause before redrive");
                }
                if ("redrive".equals(args[0])) {
                    returned.set(false);
                    channel.basicPublish("commerce.events", route, true,
                            message.getProps().builder().messageId(eventId).deliveryMode(2).build(), message.getBody());
                    channel.waitForConfirmsOrDie(5000);
                    if (returned.get()) {
                        throw new IllegalStateException("Unroutable event remains in DLQ");
                    }
                    // Crash after confirm may duplicate. Preserve eventId for consumer dedup.
                    channel.basicAck(message.getEnvelope().getDeliveryTag(), false);
                }
                System.out.println("action=" + args[0] + " queue=" + args[1] + " eventId=" + eventId + " type=" + route);
            }
        }
    }
}

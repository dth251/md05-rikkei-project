package com.app.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.RetryPolicy;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RabbitRetryTests {

    @Autowired
    private RetryPolicy policy;

    @Test
    void malformedMessageIsNotRetriedThroughListenerWrapper() {
        var context = policy.open(null);
        policy.registerThrowable(context, new ListenerExecutionFailedException("rejected",
                new AmqpRejectAndDontRequeueException("invalid")));
        assertFalse(policy.canRetry(context));
    }

    @Test
    void transientErrorStopsAfterFiveAttempts() {
        var context = policy.open(null);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(policy.canRetry(context));
            policy.registerThrowable(context, new IllegalStateException("database temporarily unavailable"));
        }
        assertFalse(policy.canRetry(context));
    }
}

package com.app.order.config;

import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.RetryPolicy;

@Configuration
public class RabbitRetryConfiguration {

    @Bean
    RetryPolicy commerceRetryPolicy() {
        return new SimpleRetryPolicy(5,
                Map.of(AmqpRejectAndDontRequeueException.class, false), true, true);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        SimpleRabbitListenerContainerFactoryConfigurer configurer,
        ConnectionFactory connectionFactory,
        RetryPolicy commerceRetryPolicy
    ) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(commerceRetryPolicy)
                .backOffOptions(1000, 2.0, 8000)
                .recoverer((message, cause) -> {
                    // Do not add exception payload or stack trace to broker headers/logs.
                    throw new AmqpRejectAndDontRequeueException("Event sent to dead letter queue");
                })
                .build());
        return factory;
    }
}

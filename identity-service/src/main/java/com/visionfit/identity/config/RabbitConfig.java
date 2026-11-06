package com.visionfit.identity.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every event identity-service publishes goes to one topic exchange; the routing key names the event
 * (user.deleted). Each consuming service declares and binds its own queue, so adding a consumer never
 * touches this service. The consumers declare the same exchange, so start order does not matter.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "identity.events";

    @Bean
    TopicExchange identityEvents() {
        return new TopicExchange(EXCHANGE);
    }
}

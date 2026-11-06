package com.visionfit.meal.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This service's own queue for identity-service's account events, declared on startup so events wait
 * there while the service is down. Identity-service never knows the queue exists.
 * The same class lives in each service that reacts to user.deleted.
 */
@Configuration
class UserEventsConfig {

    static final String EXCHANGE = "identity.events";
    static final String QUEUE = "meal.user-deleted";
    /** A message that keeps failing after the retries lands here, to be looked at, instead of being lost. */
    static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

    /** Declared by identity-service too; declaring an exchange that exists with the same settings does nothing. */
    @Bean
    TopicExchange identityEvents() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue userDeletedQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue userDeletedDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding userDeletedBinding() {
        return BindingBuilder.bind(userDeletedQueue()).to(identityEvents()).with("user.deleted");
    }

    /** Bodies are JSON; the listener's parameter type says what to read them into. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}

package com.visionfit.identity.service;

import com.visionfit.identity.config.RabbitConfig;
import com.visionfit.identity.domain.OutboxEvent;
import com.visionfit.identity.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Sends the outbox to RabbitMQ every few seconds. A row is marked sent only after RabbitMQ confirms it,
 * so a crash in between sends it again later: consumers must cope with an event arriving twice
 * (the message id is the event id). While RabbitMQ is down, rows simply wait.
 * One instance only: two relays would read the same rows and send each event twice.
 */
@Component
@ConditionalOnProperty(name = "visionfit.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, RabbitTemplate rabbitTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${visionfit.outbox.relay.interval:2s}")
    public void sendPending() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50BySentAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            if (event.getRequestId() != null) MDC.put("requestId", event.getRequestId());
            try {
                rabbitTemplate.invoke(channel -> {
                    channel.send(RabbitConfig.EXCHANGE, event.getType(), toMessage(event));
                    // Waits for RabbitMQ to say it has the message (spring.rabbitmq.publisher-confirm-type=simple).
                    channel.waitForConfirmsOrDie(5_000);
                    return null;
                });
                event.markSent();
                outboxEventRepository.save(event);
                log.info("Event {} {} sent", event.getType(), event.getId());
            } catch (AmqpException e) {
                // Once per run, not per row: the rest of the batch would fail the same way.
                log.warn("RabbitMQ unreachable, {} event(s) waiting: {}", pending.size(), e.getMessage());
                return;
            } finally {
                MDC.remove("requestId");
            }
        }
    }

    private static Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getType())
                .setTimestamp(Date.from(event.getCreatedAt()))
                .setHeader("requestId", event.getRequestId())
                .build();
    }
}

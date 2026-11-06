package com.visionfit.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An event saved with the change it reports, then sent to RabbitMQ by OutboxRelay. */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

    /** Routing key of the event published when an account is deleted. */
    public static final String USER_DELETED = "user.deleted";

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(String type, String payload, String requestId) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.payload = payload;
        this.requestId = requestId;
        this.createdAt = Instant.now();
    }

    public static OutboxEvent userDeleted(UUID userId, String requestId) {
        return new OutboxEvent(USER_DELETED, "{\"userId\":\"" + userId + "\"}", requestId);
    }

    public void markSent() {
        sentAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}

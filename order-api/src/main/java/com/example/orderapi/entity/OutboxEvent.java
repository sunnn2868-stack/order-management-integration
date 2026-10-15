package com.example.orderapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private LocalDateTime claimedAt;

    // Required by JPA
    protected OutboxEvent() {
    }

    // Creates a new outbox event
    public OutboxEvent(
            Integer aggregateId,
            String eventType,
            String payload
    ) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // Reconstructs an existing outbox event from a database row
    public OutboxEvent(
            Long id,
            Integer aggregateId,
            String eventType,
            String payload,
            String status,
            LocalDateTime createdAt,
            LocalDateTime publishedAt,
            LocalDateTime claimedAt
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
        this.claimedAt = claimedAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    public Long getId() {
        return id;
    }

    public Integer getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void markPending() {
        this.status = "PENDING";
        this.claimedAt = null;
    }

    public void markInProgress() {
        this.status = "IN_PROGRESS";
        this.claimedAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = LocalDateTime.now();
        this.claimedAt = null;
    }
}
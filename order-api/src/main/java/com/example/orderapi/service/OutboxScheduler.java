package com.example.orderapi.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;

    public OutboxScheduler(
            OutboxPublisherService outboxPublisherService) {
        this.outboxPublisherService = outboxPublisherService;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        outboxPublisherService.publishPendingEvents();
    }
}
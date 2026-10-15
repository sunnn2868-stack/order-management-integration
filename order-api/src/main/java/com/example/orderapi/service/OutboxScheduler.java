package com.example.orderapi.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxScheduler {

    private final OutboxPublisherService outboxPublisherService;
    private final OutboxStaleClaimRecoveryService recoveryService;

    public OutboxScheduler(
            OutboxPublisherService outboxPublisherService,
            OutboxStaleClaimRecoveryService recoveryService
    ) {
        this.outboxPublisherService = outboxPublisherService;
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        int recovered = recoveryService.recoverStaleClaims();

        if (recovered > 0) {
            System.out.println(
                    "Recovered stale outbox events: " + recovered
            );
        }

        outboxPublisherService.publishPendingEvents();
    }
}
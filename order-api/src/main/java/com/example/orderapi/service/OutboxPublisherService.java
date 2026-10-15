package com.example.orderapi.service;

import com.example.orderapi.OrderEventPublisher;
import com.example.orderapi.entity.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxPublisherService {

    private static final Logger logger =
            LoggerFactory.getLogger(OutboxPublisherService.class);

    private static final int BATCH_SIZE = 10;

    private final OutboxEventClaimService outboxEventClaimService;
    private final OrderEventPublisher orderEventPublisher;
    private final OutboxEventStatusService outboxEventStatusService;

    public OutboxPublisherService(
            OutboxEventClaimService outboxEventClaimService,
            OrderEventPublisher orderEventPublisher,
            OutboxEventStatusService outboxEventStatusService
    ) {
        this.outboxEventClaimService = outboxEventClaimService;
        this.orderEventPublisher = orderEventPublisher;
        this.outboxEventStatusService = outboxEventStatusService;
    }

    public void publishPendingEvents() {
        List<OutboxEvent> claimedEvents =
                outboxEventClaimService.claimPendingEvents(BATCH_SIZE);

        for (OutboxEvent event : claimedEvents) {
            try {
                orderEventPublisher.publishOrderCreated(event.getPayload());

                outboxEventStatusService.markAsPublished(event);

                logger.info(
                        "Outbox event published: outboxId={}, orderId={}",
                        event.getId(),
                        event.getAggregateId()
                );
            } catch (Exception exception) {
                outboxEventStatusService.markAsPending(event);

                logger.error(
                        "Failed to publish outbox event: outboxId={}, orderId={}",
                        event.getId(),
                        event.getAggregateId(),
                        exception
                );
            }
        }
    }
}
package com.example.orderapi.service;

import com.example.orderapi.OrderEventPublisher;
import com.example.orderapi.entity.OutboxEvent;
import com.example.orderapi.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutboxPublisherService {

    private static final Logger logger =
            LoggerFactory.getLogger(OutboxPublisherService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher orderEventPublisher;

    private final OutboxEventStatusService outboxEventStatusService;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            OrderEventPublisher orderEventPublisher,
            OutboxEventStatusService outboxEventStatusService) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.outboxEventStatusService = outboxEventStatusService;
    }

    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : pendingEvents) {
            try {
                orderEventPublisher.publishOrderCreated(event.getPayload());
                outboxEventStatusService.markAsPublished(event);

                logger.info(
                        "Outbox event published: outboxId={}, orderId={}",
                        event.getId(),
                        event.getAggregateId()
                );
            } catch (Exception exception) {
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

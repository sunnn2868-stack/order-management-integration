package com.example.orderapi.service;

import com.example.orderapi.OrderEventPublisher;
import com.example.orderapi.entity.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventClaimService outboxEventClaimService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OutboxEventStatusService outboxEventStatusService;

    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {
        outboxPublisherService = new OutboxPublisherService(
                outboxEventClaimService,
                orderEventPublisher,
                outboxEventStatusService
        );
    }

    @Test
    void shouldPublishClaimedEventAndMarkItPublished() {
        OutboxEvent event = new OutboxEvent(
                50,
                "OrderCreatedEvent",
                """
                {"orderId":50,"customerId":404,"productId":5001,"quantity":2,"status":"CREATED"}
                """
        );

        when(outboxEventClaimService.claimPendingEvents(10))
                .thenReturn(List.of(event));

        outboxPublisherService.publishPendingEvents();

        verify(outboxEventClaimService).claimPendingEvents(10);
        verify(orderEventPublisher).publishOrderCreated(event.getPayload());
        verify(outboxEventStatusService).markAsPublished(event);
        verify(outboxEventStatusService, never()).markAsPending(event);
    }

    @Test
    void shouldResetEventToPendingWhenPublishingFails() {
        OutboxEvent event = new OutboxEvent(
                51,
                "OrderCreatedEvent",
                """
                {"orderId":51,"customerId":404,"productId":5001,"quantity":2,"status":"CREATED"}
                """
        );

        when(outboxEventClaimService.claimPendingEvents(10))
                .thenReturn(List.of(event));

        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(orderEventPublisher)
                .publishOrderCreated(event.getPayload());

        outboxPublisherService.publishPendingEvents();

        verify(outboxEventClaimService).claimPendingEvents(10);
        verify(orderEventPublisher).publishOrderCreated(event.getPayload());
        verify(outboxEventStatusService).markAsPending(event);
        verify(outboxEventStatusService, never()).markAsPublished(event);
    }
}
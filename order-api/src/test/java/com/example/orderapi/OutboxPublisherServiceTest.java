package com.example.orderapi;

import com.example.orderapi.entity.OutboxEvent;
import com.example.orderapi.repository.OutboxEventRepository;
import com.example.orderapi.service.OutboxEventStatusService;
import com.example.orderapi.service.OutboxPublisherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private OutboxPublisherService outboxPublisherService;

    @Mock
    private OutboxEventStatusService outboxEventStatusService;

    @BeforeEach
    void setUp() {
        outboxPublisherService = new OutboxPublisherService(
                outboxEventRepository,
                orderEventPublisher,
                outboxEventStatusService
        );
    }

    @Test
    void shouldPublishPendingEventAndMarkItPublished() {
        OutboxEvent event = new OutboxEvent(
                50,
                "OrderCreatedEvent",
                """
{"orderId":50,"customerId":404,"productId":5001,"quantity":2,"status":"CREATED"}
                        """
        );

        when(outboxEventRepository
                .findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));

        outboxPublisherService.publishPendingEvents();

        verify(orderEventPublisher).publishOrderCreated(event.getPayload());
        verify(outboxEventStatusService).markAsPublished(event);
    }

    @Test
    void shouldLeaveEventPendingWhenPublishingFails() {
        OutboxEvent event = new OutboxEvent(
                51,
                "OrderCreatedEvent",
                """
    {"orderId":51,"customerId":404,"productId":5001,"quantity":2,"status":"CREATED"}
    """
        );

        when(outboxEventRepository
                .findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));

        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(orderEventPublisher)
                .publishOrderCreated(event.getPayload());

        outboxPublisherService.publishPendingEvents();

        verify(orderEventPublisher).publishOrderCreated(event.getPayload());
        verify(outboxEventRepository, never()).save(event);
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING",
                event.getStatus()
        );
    }
}

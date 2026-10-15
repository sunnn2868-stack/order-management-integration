package com.example.orderapi.service;

import com.example.orderapi.entity.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRejectInvalidBatchSize() {
        OutboxEventClaimService service =
                new OutboxEventClaimService(jdbcTemplate);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.claimPendingEvents(0)
        );

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldReturnClaimedEventsFromDatabase() {
        OutboxEventClaimService service =
                new OutboxEventClaimService(jdbcTemplate);

        OutboxEvent event = new OutboxEvent(
                50,
                "OrderCreatedEvent",
                """
                {"orderId":50,"customerId":404,"productId":5001,"quantity":2,"status":"CREATED"}
                """
        );

        when(jdbcTemplate.query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(10)
        )).thenReturn(List.of(event));

        List<OutboxEvent> claimedEvents =
                service.claimPendingEvents(10);

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                claimedEvents.size()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                50,
                claimedEvents.get(0).getAggregateId()
        );

        verify(jdbcTemplate).query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                eq(10)
        );
    }
}
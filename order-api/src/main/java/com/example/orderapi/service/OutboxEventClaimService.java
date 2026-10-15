package com.example.orderapi.service;

import com.example.orderapi.entity.OutboxEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
public class OutboxEventClaimService {

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventClaimService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<OutboxEvent> claimPendingEvents(int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "Batch size must be greater than zero"
            );
        }

        return jdbcTemplate.query(
                """
                WITH claimed AS (
                    SELECT id
                    FROM outbox_events
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE outbox_events AS event
                SET status = 'IN_PROGRESS',
                    claimed_at = CURRENT_TIMESTAMP
                FROM claimed
                WHERE event.id = claimed.id
                RETURNING event.id,
                          event.aggregate_id,
                          event.event_type,
                          event.payload,
                          event.status,
                          event.created_at,
                          event.published_at,
                          event.claimed_at
                """,
                (rs, rowNum) -> {
                    Timestamp publishedAtTimestamp =
                            rs.getTimestamp("published_at");

                    Timestamp claimedAtTimestamp =
                            rs.getTimestamp("claimed_at");

                    return new OutboxEvent(
                            rs.getLong("id"),
                            rs.getInt("aggregate_id"),
                            rs.getString("event_type"),
                            rs.getString("payload"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            publishedAtTimestamp == null
                                    ? null
                                    : publishedAtTimestamp.toLocalDateTime(),
                            claimedAtTimestamp == null
                                    ? null
                                    : claimedAtTimestamp.toLocalDateTime()
                    );
                },
                batchSize
        );
    }
}
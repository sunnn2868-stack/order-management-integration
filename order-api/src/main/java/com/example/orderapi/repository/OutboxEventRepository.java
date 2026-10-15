package com.example.orderapi.repository;

import com.example.orderapi.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query(value = """
            UPDATE outbox_events
            SET status = 'IN_PROGRESS'
            WHERE id IN (
                SELECT id
                FROM outbox_events
                WHERE status = 'PENDING'
                ORDER BY created_at ASC, id ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            RETURNING *
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(
            @Param("batchSize") int batchSize
    );
}
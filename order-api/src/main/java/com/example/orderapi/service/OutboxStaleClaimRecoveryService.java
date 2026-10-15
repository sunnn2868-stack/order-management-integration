package com.example.orderapi.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxStaleClaimRecoveryService {

    private final JdbcTemplate jdbcTemplate;

    public OutboxStaleClaimRecoveryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int recoverStaleClaims() {
        return jdbcTemplate.update("""
                UPDATE outbox_events
                SET status = 'PENDING',
                    claimed_at = NULL
                WHERE status = 'IN_PROGRESS'
                  AND claimed_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                """);
    }
}
package com.example.orderapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class OutboxStaleClaimRecoveryServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    private JdbcTemplate jdbcTemplate;
    private OutboxStaleClaimRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );

        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("DROP TABLE IF EXISTS outbox_events");

        jdbcTemplate.execute("""
                CREATE TABLE outbox_events (
                    id BIGSERIAL PRIMARY KEY,
                    aggregate_id INTEGER NOT NULL,
                    event_type VARCHAR(100) NOT NULL,
                    payload TEXT NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    created_at TIMESTAMP NOT NULL,
                    published_at TIMESTAMP NULL,
                    claimed_at TIMESTAMP NULL
                )
                """);

        recoveryService =
                new OutboxStaleClaimRecoveryService(jdbcTemplate);
    }

    @Test
    void shouldRecoverStaleClaimButKeepRecentClaimInProgress() {
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (aggregate_id, event_type, payload, status,
                     created_at, claimed_at)
                VALUES
                    (101, 'OrderCreatedEvent', '{}', 'IN_PROGRESS',
                     CURRENT_TIMESTAMP - INTERVAL '10 minutes',
                     CURRENT_TIMESTAMP - INTERVAL '6 minutes'),
                    (102, 'OrderCreatedEvent', '{}', 'IN_PROGRESS',
                     CURRENT_TIMESTAMP - INTERVAL '1 minute',
                     CURRENT_TIMESTAMP - INTERVAL '1 minute')
                """);

        int recovered = recoveryService.recoverStaleClaims();

        assertEquals(1, recovered);

        assertEquals(
                "PENDING",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM outbox_events WHERE aggregate_id = 101",
                        String.class
                )
        );

        assertEquals(
                null,
                jdbcTemplate.queryForObject(
                        "SELECT claimed_at FROM outbox_events WHERE aggregate_id = 101",
                        java.sql.Timestamp.class
                )
        );

        assertEquals(
                "IN_PROGRESS",
                jdbcTemplate.queryForObject(
                        "SELECT status FROM outbox_events WHERE aggregate_id = 102",
                        String.class
                )
        );
    }

    @Test
    void shouldReturnZeroWhenNoStaleClaimsExist() {
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (aggregate_id, event_type, payload, status,
                     created_at, claimed_at)
                VALUES
                    (103, 'OrderCreatedEvent', '{}', 'IN_PROGRESS',
                     CURRENT_TIMESTAMP,
                     CURRENT_TIMESTAMP - INTERVAL '1 minute')
                """);

        int recovered = recoveryService.recoverStaleClaims();

        assertEquals(0, recovered);
    }
}
package com.example.orderapi.service;

import com.example.orderapi.entity.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class OutboxEventClaimServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("outbox_test")
                    .withUsername("test")
                    .withPassword("test");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private OutboxEventClaimService claimService;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

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

        claimService = new OutboxEventClaimService(jdbcTemplate);
    }

    @Test
    void shouldClaimPendingEventsAndChangeTheirStatus() {
        insertPendingEvent(50);

        List<OutboxEvent> claimedEvents =
                claimService.claimPendingEvents(10);

        assertEquals(1, claimedEvents.size());
        assertEquals(50, claimedEvents.get(0).getAggregateId());
        assertEquals("IN_PROGRESS", claimedEvents.get(0).getStatus());

        String databaseStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE aggregate_id = ?",
                String.class,
                50
        );

        assertEquals("IN_PROGRESS", databaseStatus);
    }

    @Test
    void shouldNotClaimSameEventTwice() {
        insertPendingEvent(51);

        List<OutboxEvent> firstClaim =
                claimService.claimPendingEvents(10);

        List<OutboxEvent> secondClaim =
                claimService.claimPendingEvents(10);

        assertEquals(1, firstClaim.size());
        assertTrue(secondClaim.isEmpty());
    }

    @Test
    void shouldSkipAnEventLockedByAnotherTransaction() throws Exception {
        insertPendingEvent(52);

        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            Connection connection =
                    org.springframework.jdbc.datasource.DataSourceUtils
                            .getConnection(dataSource);

            try (PreparedStatement statement =
                         connection.prepareStatement("""
                                 SELECT id
                                 FROM outbox_events
                                 WHERE aggregate_id = 52
                                 FOR UPDATE
                                 """)) {
                statement.executeQuery().close();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            CompletableFuture<List<OutboxEvent>> secondWorker =
                    CompletableFuture.supplyAsync(
                            () -> claimService.claimPendingEvents(10)
                    );

            try {
                List<OutboxEvent> secondWorkerEvents =
                        secondWorker.get(5, TimeUnit.SECONDS);

                assertTrue(
                        secondWorkerEvents.isEmpty(),
                        "The second worker should skip the locked event"
                );
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                org.springframework.jdbc.datasource.DataSourceUtils
                        .releaseConnection(connection, dataSource);
            }
        });

        // The first transaction has now committed and released its lock.
        List<OutboxEvent> claimedAfterUnlock =
                claimService.claimPendingEvents(10);

        assertEquals(1, claimedAfterUnlock.size());
        assertEquals(52, claimedAfterUnlock.get(0).getAggregateId());
        assertEquals("IN_PROGRESS", claimedAfterUnlock.get(0).getStatus());
    }

    private void insertPendingEvent(int orderId) {
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (aggregate_id, event_type, payload, status, created_at)
                VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """,
                orderId,
                "OrderCreatedEvent",
                "{\"orderId\":" + orderId + "}"
        );
    }
}
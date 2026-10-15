# Order Management Integration System

A Spring Boot project demonstrating reliable communication between services in an order processing system.

## Project Status

**Project 1 — Order Management Integration System**

The main purpose of this project is to demonstrate reliable communication between services in an order processing system. The Order API validates orders by checking product availability through the Inventory API, stores order data in PostgreSQL, and publishes order-created events through RabbitMQ.

The system implements retry handling, idempotency, correlation IDs, and the Transactional Outbox Pattern to improve reliability, prevent duplicate processing, and support recovery from temporary service or messaging failures.

## Features

- Create and retrieve orders through REST APIs
- PostgreSQL persistence using Spring Data JPA
- Inventory availability checks through a separate REST API
- Request validation and centralized exception handling
- Retry handling for temporary inventory service failures
- Idempotency keys to help prevent duplicate orders
- Correlation IDs for tracing requests across services
- Structured application logging
- RabbitMQ messaging for order-created events
- Transactional Outbox Pattern for reliable event publishing
- Concurrent outbox event claiming using PostgreSQL row locking
- Recovery of stale outbox events after application interruptions
- Automated unit and integration tests

## Technology Stack

- Java 21
- Spring Boot
- Maven
- PostgreSQL
- Spring Data JPA
- REST APIs
- RabbitMQ
- JUnit
- Mockito
- Testcontainers
- Git and GitHub
- Postman

## Architecture

The Order Management Integration System consists of two Spring Boot applications that communicate through REST APIs and RabbitMQ, with PostgreSQL providing persistent storage.

### Components

- **Order API:** Receives order requests, validates input, checks product availability through the Inventory API, and saves orders to PostgreSQL.
- **Inventory API:** Provides inventory availability information through a REST API and consumes order-created events from RabbitMQ.
- **PostgreSQL:** Stores order records, idempotency data, and outbox events.
- **RabbitMQ:** Transfers order-created events from the Order API to the Inventory API.
- **Transactional Outbox:** Stores order-created events in the same database transaction as the order, allowing events to be published reliably after the transaction commits.

### Request and Event Flow

1. A client sends an order request to the Order API.
2. The Order API checks product availability by calling the Inventory API through a REST API.
3. If the request is valid and inventory is available, the Order API saves the order and its corresponding outbox event in PostgreSQL within the same transaction.
4. The scheduled outbox publisher claims pending events and publishes them to RabbitMQ.
5. After receiving publisher confirmation, the Order API marks the event as `PUBLISHED`.
6. The Inventory API consumes the event and records the processed order ID to prevent duplicate processing.
7. If publishing fails or the application stops unexpectedly, pending-event retries and stale-claim recovery allow the publisher to attempt delivery again.

### Reliability Mechanisms

- **Retry handling:** Helps recover from temporary inventory service and messaging failures.
- **Idempotency:** Helps prevent duplicate order creation and duplicate event processing.
- **Correlation IDs:** Support tracing requests across service boundaries.
- **Concurrent event claiming:** PostgreSQL `FOR UPDATE SKIP LOCKED` helps prevent multiple workers from claiming the same pending event.
- **Stale-claim recovery:** Resets events that remain `IN_PROGRESS` for more than five minutes so they can be retried.

The system is designed to improve reliability during temporary service outages and application interruptions. Because message delivery can occur more than once, consumers must remain idempotent.

## Configuration

Configure the local database and RabbitMQ connection settings in the appropriate local application properties files.

The Order API uses the `orderdb` PostgreSQL database by default. Ensure the database credentials, connection URL, and RabbitMQ settings match your local environment.

For RabbitMQ, the default local connection typically uses:

- Host: `localhost`
- AMQP port: `5672`
- Management UI port: `15672`

The RabbitMQ management interface uses the credentials configured for your local broker. A default local Docker installation may use `guest` / `guest`.

**Security:** Keep database passwords and other credentials in local configuration files or environment variables. Never commit secrets to GitHub.

## Running the Project

### Prerequisites

Install or configure:

- Java 21
- Docker Desktop
- PostgreSQL
- Maven, or use the included Maven Wrapper
- Postman

### 1. Start PostgreSQL

Ensure PostgreSQL is running and the `orderdb` database exists.

### 2. Start RabbitMQ

Start the RabbitMQ management container if it has not already been created:

```powershell
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

If the container already exists but is stopped, start it with:

```powershell
docker start rabbitmq
```

Open `http://localhost:15672` to access the management interface. Ensure the `order.created` queue is configured as required by the application.

### 3. Start the Inventory API

Open the Inventory API project in IntelliJ and start the Spring Boot application. Confirm that it runs on port `8081`.

### 4. Start the Order API

Open the Order API project and start the Spring Boot application. Confirm that it runs on port `8080`.

### 5. Test with Postman

Send requests to the Order API to create and retrieve orders. Verify that inventory checks, database persistence, and event publishing work as expected.

## Transactional Outbox Pattern

The Transactional Outbox Pattern helps prevent order-created events from being lost when an order is successfully saved but RabbitMQ is temporarily unavailable.

When an order is created, the Order API saves the order and its corresponding outbox event in the same database transaction. The outbox event is initially stored with the status `PENDING`.

A scheduled publisher processes pending events and sends them to RabbitMQ.

### Event Publishing Workflow

1. The Order API validates the requested order using the Inventory API.
2. The order and its outbox event are saved in PostgreSQL within the same transaction.
3. The scheduled publisher claims pending outbox events in batches.
4. PostgreSQL `FOR UPDATE SKIP LOCKED` helps prevent concurrent workers from claiming the same pending event.
5. The publisher sends the event to RabbitMQ and waits for publisher confirmation.
6. Successfully published events are marked `PUBLISHED`.
7. If publishing fails, the event is returned to `PENDING` for a later attempt.

### Stale Event Recovery

An event can remain `IN_PROGRESS` if the application stops unexpectedly while processing it.

The recovery service identifies events that have remained `IN_PROGRESS` for more than five minutes and resets them to `PENDING`. The scheduled publisher can then try to publish them again.

This supports recovery after application interruptions and temporary RabbitMQ outages.

**Delivery note:** The outbox improves reliability, but it does not guarantee exactly-once delivery. A message may be delivered more than once, so consumers should implement idempotent processing.

## Idempotent Event Processing

The Inventory API records processed order IDs in PostgreSQL. The order ID is protected by a primary key, and duplicate inserts are ignored.

This prevents the same order-created event from being processed as a new event more than once by the database-backed listener.

The database record and event processing are handled transactionally so a failed database operation can be retried rather than incorrectly recording an event as successfully processed.

## Retry and Failure Recovery

The system includes retry and recovery mechanisms for temporary failures:

- **Inventory API failures:** The Order API retries eligible temporary downstream failures.
- **RabbitMQ publishing failures:** Pending outbox events can be retried by the scheduled publisher.
- **Application interruptions:** Stale `IN_PROGRESS` outbox events are returned to `PENDING`.
- **Duplicate events:** The Inventory API uses database-backed idempotency to ignore duplicate order IDs.

These mechanisms improve resilience, but permanent failures and repeated delivery still require appropriate monitoring and operational handling.

## Running Tests

Run the automated tests using the Maven Wrapper from the `order-api` project directory.

### Windows

```powershell
.\mvnw.cmd test
```

### macOS/Linux

```bash
./mvnw test
```

The test suite includes unit tests and PostgreSQL integration tests using Testcontainers.

Coverage includes:

- Outbox publisher success and failure handling
- Outbox event claiming and status changes
- Preventing duplicate claims
- Skipping rows locked by another transaction
- Recovery of stale outbox claims

Docker must be available for tests that use Testcontainers.

## Future Improvements

Potential enhancements include:

- Dead-letter queue monitoring and replay tooling
- Metrics and dashboards for pending or failed outbox events
- Configurable retry and recovery thresholds
- Improved operational alerting
- Additional end-to-end tests for failure scenarios

## License

This project is intended for learning and demonstration purposes.
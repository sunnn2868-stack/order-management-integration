# Order Management Integration

A learning project demonstrating REST API development and service-to-service integration using Spring Boot.

## Features

* Create and retrieve orders
* PostgreSQL persistence using Spring Data JPA
* Inventory availability checks through a separate REST API
* Request validation and centralized exception handling
* Retry handling for temporary inventory service failures
* Idempotency keys to help prevent duplicate orders
* Correlation IDs for tracing requests across services
* Structured application logging
* Automated tests

## Technology Stack

* Java 21
* Spring Boot
* Maven
* PostgreSQL
* REST APIs
* JUnit and Mockito
* Git and GitHub

## Architecture

Postman → Order API → PostgreSQL
                  ↘ Inventory API

* Order API: `http://localhost:8080`
* Inventory API: `http://localhost:8081`

## Configuration

Database credentials are stored in a local configuration file and are not committed to Git.

Create `src/main/resources/application-local.properties` with your local PostgreSQL connection settings. Configure the database name, username, password, and JPA settings to match your environment.

Never commit passwords, API keys, or other secrets.

## Running the Project

1. Start PostgreSQL and ensure the `orderdb` database exists.
2. Configure the local database properties.
3. Start the Inventory API on port `8081`.
4. Start the Order API on port `8080`.
5. Use Postman to send requests to the Order API.

## Running Tests

Run the automated tests using the Maven wrapper:

Windows:

`.\mvnw.cmd test`

macOS/Linux:

`./mvnw test`

## Project Status

This project is being developed incrementally as part of an Integration Developer learning roadmap.

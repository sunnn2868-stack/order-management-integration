
package com.example.orderapi.client;

import com.example.orderapi.dto.InventoryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:8081")
                .build();
    }

    @CircuitBreaker(
            name = "inventory",
            fallbackMethod = "inventoryFallback"
    )
    public InventoryResponse getInventory(
            int productId,
            String correlationId) {

        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {
                return restClient.get()
                        .uri("/inventory/{productId}", productId)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve()
                        .body(InventoryResponse.class);

            } catch (HttpServerErrorException.ServiceUnavailable ex) {

                System.out.println(
                        "Inventory returned 503. Attempt "
                                + attempt + " of " + maxAttempts
                );

                if (attempt == maxAttempts) {
                    throw ex;
                }

                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Retry interrupted", e
                    );
                }

            } catch (ResourceAccessException ex) {

                System.err.println(
                        "Inventory service connection or read timeout: "
                                + ex.getMessage()
                );

                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Inventory service is currently unavailable",
                        ex
                );
            }
        }

        throw new IllegalStateException("Inventory request failed");
    }

    public void reserveStock(
            int productId,
            Long orderId,
            int quantity,
            String correlationId) {

        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/inventory/{productId}/reserve")
                            .queryParam("orderId", orderId)
                            .queryParam("quantity", quantity)
                            .build(productId))
                    .header("X-Correlation-ID", correlationId)
                    .retrieve()
                    .toBodilessEntity();

        } catch (ResourceAccessException ex) {

            System.err.println(
                    "Inventory reservation connection failure: "
                            + ex.getMessage()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Inventory service is currently unavailable",
                    ex
            );
        }
    }

    public void releaseStock(
            int productId,
            Long orderId,
            String correlationId) {

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/inventory/{productId}/release")
                        .queryParam("orderId", orderId)
                        .build(productId))
                .header("X-Correlation-ID", correlationId)
                .retrieve()
                .toBodilessEntity();
    }

    public InventoryResponse inventoryFallback(
            int productId,
            String correlationId,
            Throwable ex) {

        System.err.println(
                "Inventory circuit breaker activated: "
                        + ex.getMessage()
        );

        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Inventory service is temporarily unavailable",
                ex
        );
    }
}

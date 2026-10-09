package com.example.orderapi.client;

import com.example.orderapi.dto.InventoryResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.springframework.web.client.HttpServerErrorException;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient() {
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();
    }

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
                    throw new IllegalStateException("Retry interrupted", e);
                }
            }
        }

        throw new IllegalStateException("Inventory request failed");
    }
}
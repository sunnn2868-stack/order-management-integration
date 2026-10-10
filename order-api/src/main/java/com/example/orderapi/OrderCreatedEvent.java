package com.example.orderapi;

public record OrderCreatedEvent(
        Integer orderId,
        Integer customerId,
        Integer productId,
        Integer quantity,
        String status
) {
}
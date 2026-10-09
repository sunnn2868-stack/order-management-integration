package com.example.orderapi.controller;

import com.example.orderapi.client.InventoryClient;
import com.example.orderapi.dto.CreateOrderRequest;
import com.example.orderapi.dto.CreateOrderResponse;
import com.example.orderapi.entity.Order;
import com.example.orderapi.service.IdempotencyService;
import com.example.orderapi.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class OrderController {

    private final OrderService orderService;
    private final InventoryClient inventoryClient;
    private final IdempotencyService idempotencyService;

    private static final Logger logger =
            LoggerFactory.getLogger(OrderController.class);

    public OrderController(
            OrderService orderService,
            InventoryClient inventoryClient,
            IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.inventoryClient = inventoryClient;
        this.idempotencyService = idempotencyService;
    }

    @GetMapping("/hello")
    public String hello() {
        return "Order API is working!";
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false)
            String correlationId,
            @Valid @RequestBody CreateOrderRequest request) {

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        logger.info("Received request with correlationId={}", correlationId);

        Optional<CreateOrderResponse> existingResponse =
                idempotencyService.findResponse(idempotencyKey);

        if (existingResponse.isPresent()) {
            return existingResponse.get();
        }

        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus("CREATED");

        Order savedOrder = orderService.createOrder(
                order,
                idempotencyKey,
                correlationId
        );

        CreateOrderResponse response = new CreateOrderResponse();
        response.setOrderId(savedOrder.getOrderId());
        response.setCustomerId(savedOrder.getCustomerId());
        response.setProductId(savedOrder.getProductId());
        response.setQuantity(savedOrder.getQuantity());
        response.setStatus(savedOrder.getStatus());

        return response;
    }

    @GetMapping("/orders")
    public List<Order> getAllOrders() {
        return orderService.getAllOrders();
    }

    @GetMapping("/orders/{id}")
    public Order getOrderById(@PathVariable int id) {
        return orderService.getOrderById(id);
    }
}
package com.example.orderapi.service;

import com.example.orderapi.dto.InventoryResponse;
import com.example.orderapi.entity.Order;
import com.example.orderapi.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.example.orderapi.client.InventoryClient;

import org.springframework.transaction.annotation.Transactional;

import com.example.orderapi.dto.CreateOrderResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.orderapi.OrderCreatedEvent;

import com.example.orderapi.entity.OutboxEvent;
import com.example.orderapi.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    private final InventoryClient inventoryClient;

    private final IdempotencyService idempotencyService;

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);


    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            InventoryClient inventoryClient,
            IdempotencyService idempotencyService,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.idempotencyService = idempotencyService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createOrder(
            Order order,
            String idempotencyKey,
            String correlationId) {
        logger.info(
                "Starting order creation: productId={}, quantity={}, idempotencyKey={}",
                order.getProductId(),
                order.getQuantity(),
                idempotencyKey
        );

        InventoryResponse inventory =
                inventoryClient.getInventory(
                        order.getProductId(),
                        correlationId
                );

        logger.info(
                "Inventory check completed: productId={}, available={}",
                order.getProductId(),
                inventory.isAvailable()
        );

        if (!inventory.isAvailable()) {
            logger.warn(
                    "Order rejected: productId={} is unavailable",
                    order.getProductId()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Product is not available"
            );
        }

        Order savedOrder = orderRepository.save(order);

        logger.info(
                "Order saved successfully: orderId={}, productId={}",
                savedOrder.getOrderId(),
                savedOrder.getProductId()
        );

        CreateOrderResponse response = new CreateOrderResponse();
        response.setOrderId(savedOrder.getOrderId());
        response.setCustomerId(savedOrder.getCustomerId());
        response.setProductId(savedOrder.getProductId());
        response.setQuantity(savedOrder.getQuantity());
        response.setStatus(savedOrder.getStatus());

        idempotencyService.saveResponse(idempotencyKey, response);

        try {
            OrderCreatedEvent event = new OrderCreatedEvent(
                    savedOrder.getOrderId(),
                    savedOrder.getCustomerId(),
                    savedOrder.getProductId(),
                    savedOrder.getQuantity(),
                    savedOrder.getStatus()
            );

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = new OutboxEvent(
                    savedOrder.getOrderId(),
                    "OrderCreatedEvent",
                    payload
            );

            outboxEventRepository.save(outboxEvent);

            logger.info(
                    "Order-created event saved to outbox: orderId={}",
                    savedOrder.getOrderId()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to save order-created event to outbox",
                    exception
            );
        }

        return savedOrder;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(int id) {
        return orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found"
                        )
                );
    }
}
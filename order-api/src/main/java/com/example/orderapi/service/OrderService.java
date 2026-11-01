
package com.example.orderapi.service;

import com.example.orderapi.dto.CreateOrderResponse;
import com.example.orderapi.entity.Order;
import com.example.orderapi.entity.OutboxEvent;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.repository.OutboxEventRepository;
import com.example.orderapi.client.InventoryClient;
import com.example.orderapi.OrderCreatedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final IdempotencyService idempotencyService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    private static final Logger logger =
            LoggerFactory.getLogger(OrderService.class);

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

        Order savedOrder = orderRepository.save(order);

        logger.info(
                "Order saved successfully: orderId={}, productId={}",
                savedOrder.getOrderId(),
                savedOrder.getProductId()
        );

        boolean inventoryReserved = false;

        try {

            // Step 1: Reserve inventory
            logger.info(
                    "Attempting inventory reservation: orderId={}, productId={}, quantity={}",
                    savedOrder.getOrderId(),
                    savedOrder.getProductId(),
                    savedOrder.getQuantity()
            );

            inventoryClient.reserveStock(
                    savedOrder.getProductId(),
                    (long) savedOrder.getOrderId(),
                    savedOrder.getQuantity(),
                    correlationId
            );

            inventoryReserved = true;

            logger.info(
                    "Inventory successfully reserved for orderId={}",
                    savedOrder.getOrderId()
            );

            /* TEMPORARY TEST: Simulate a failure after reservation
            if (inventoryReserved) {
                throw new RuntimeException(
                        "TEST: Simulated failure after inventory reservation"
                );
            }*/

            // Step 2: Build the idempotency response
            CreateOrderResponse response = new CreateOrderResponse();

            response.setOrderId(savedOrder.getOrderId());
            response.setCustomerId(savedOrder.getCustomerId());
            response.setProductId(savedOrder.getProductId());
            response.setQuantity(savedOrder.getQuantity());
            response.setStatus(savedOrder.getStatus());

            idempotencyService.saveResponse(
                    idempotencyKey,
                    response
            );

            // Step 3: Create the order event
            OrderCreatedEvent event = new OrderCreatedEvent(
                    savedOrder.getOrderId(),
                    savedOrder.getCustomerId(),
                    savedOrder.getProductId(),
                    savedOrder.getQuantity(),
                    savedOrder.getStatus()
            );

            String payload = objectMapper.writeValueAsString(event);

            // Step 4: Save event to the outbox
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

            return savedOrder;

        } catch (Exception exception) {

            logger.error(
                    "Order creation failed for orderId={}",
                    savedOrder.getOrderId(),
                    exception
            );

            // Step 5: Compensate only if inventory was reserved
            if (inventoryReserved) {

                logger.info(
                        "Attempting inventory compensation for orderId={}",
                        savedOrder.getOrderId()
                );

                try {

                    inventoryClient.releaseStock(
                            savedOrder.getProductId(),
                            (long) savedOrder.getOrderId(),
                            correlationId
                    );

                    logger.info(
                            "Inventory compensation successful for orderId={}",
                            savedOrder.getOrderId()
                    );

                } catch (Exception compensationException) {

                    logger.error(
                            "Inventory compensation failed for orderId={}",
                            savedOrder.getOrderId(),
                            compensationException
                    );

                    exception.addSuppressed(compensationException);
                }

            } else {

                logger.info(
                        "Inventory was not reserved. Compensation skipped for orderId={}",
                        savedOrder.getOrderId()
                );
            }

            throw new IllegalStateException(
                    "Order creation failed; inventory compensation was handled",
                    exception
            );
        }
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
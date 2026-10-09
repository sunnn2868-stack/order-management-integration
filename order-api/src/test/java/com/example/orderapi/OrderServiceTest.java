package com.example.orderapi;

import com.example.orderapi.client.InventoryClient;
import com.example.orderapi.dto.InventoryResponse;
import com.example.orderapi.entity.Order;
import com.example.orderapi.repository.OrderRepository;
import com.example.orderapi.service.IdempotencyService;
import com.example.orderapi.service.OrderService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void shouldRejectOrderWhenProductIsUnavailable() {
        InventoryResponse inventory = new InventoryResponse();
        inventory.setAvailable(false);

        when(inventoryClient.getInventory(5002, "test-correlation"))
                .thenReturn(inventory);

        Order order = new Order();
        order.setCustomerId(101);
        order.setProductId(5002);
        order.setQuantity(2);
        order.setStatus("CREATED");

        assertThrows(
                ResponseStatusException.class,
                () -> orderService.createOrder(
                        order,
                        "test-idempotency-key",
                        "test-correlation"
                )
        );

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldSaveOrderWhenProductIsAvailable() {
        InventoryResponse inventory = new InventoryResponse();
        inventory.setAvailable(true);
        inventory.setQuantity(10);

        when(inventoryClient.getInventory(5001, "test-correlation"))
                .thenReturn(inventory);

        Order order = new Order();
        order.setCustomerId(101);
        order.setProductId(5001);
        order.setQuantity(2);
        order.setStatus("CREATED");

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order savedOrder = invocation.getArgument(0);
                    savedOrder.setOrderId(1);
                    return savedOrder;
                });

        orderService.createOrder(
                order,
                "test-success-key",
                "test-correlation"
        );

        verify(orderRepository).save(
                argThat(savedOrder ->
                        savedOrder.getProductId() == 5001
                                && savedOrder.getQuantity() == 2
                                && savedOrder.getCustomerId() == 101
                )
        );

        verify(idempotencyService).saveResponse(
                eq("test-success-key"),
                any()
        );
    }

    @Test
    void shouldNotSaveOrderWhenInventoryServiceFails() {
        Order order = new Order();
        order.setCustomerId(101);
        order.setProductId(5001);
        order.setQuantity(2);
        order.setStatus("CREATED");

        when(inventoryClient.getInventory(5001, "test-correlation"))
                .thenThrow(
                        new RuntimeException("Inventory service unavailable")
                );

        assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(
                        order,
                        "test-failure-key",
                        "test-correlation"
                )
        );

        verify(orderRepository, never()).save(any(Order.class));
        verify(idempotencyService, never()).saveResponse(any(), any());
    }
}
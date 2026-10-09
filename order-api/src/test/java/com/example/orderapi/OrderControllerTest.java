package com.example.orderapi;

import com.example.orderapi.controller.OrderController;
import com.example.orderapi.dto.CreateOrderResponse;
import com.example.orderapi.service.IdempotencyService;
import com.example.orderapi.service.OrderService;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderapi.client.InventoryClient;

import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @MockitoBean
    private InventoryClient inventoryClient;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @Test
    void shouldReturnExistingResponseWhenIdempotencyKeyIsReused()
            throws Exception {

        CreateOrderResponse existingResponse = new CreateOrderResponse();
        existingResponse.setOrderId(1);
        existingResponse.setCustomerId(101);
        existingResponse.setProductId(5001);
        existingResponse.setQuantity(2);
        existingResponse.setStatus("CREATED");

        when(idempotencyService.findResponse("test-key"))
                .thenReturn(Optional.of(existingResponse));

        mockMvc.perform(post("/orders")
                        .header("Idempotency-Key", "test-key")
                        .contentType("application/json")
                        .content("""
                            {
                              "customerId": 101,
                              "productId": 5001,
                              "quantity": 2
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.productId").value(5001));

        verify(orderService, never())
                .createOrder(any(), any(), any());

        verify(idempotencyService)
                .findResponse(eq("test-key"));
    }
}
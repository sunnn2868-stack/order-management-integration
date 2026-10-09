package com.example.orderapi.service;

import com.example.orderapi.dto.CreateOrderResponse;
import com.example.orderapi.entity.IdempotencyRecord;
import com.example.orderapi.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<CreateOrderResponse> findResponse(String key) {
        return repository.findByIdempotencyKey(key)
                .map(record -> {
                    try {
                        return objectMapper.readValue(
                                record.getResponseBody(),
                                CreateOrderResponse.class
                        );
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "Could not read saved idempotency response", e);
                    }
                });
    }

    public void saveResponse(String key, CreateOrderResponse response) {
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(key);
            record.setResponseBody(objectMapper.writeValueAsString(response));
            record.setCreatedAt(LocalDateTime.now());

            repository.save(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not serialize idempotency response", e);
        }
    }
}
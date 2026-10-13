package com.example.orderapi.service;

import com.example.orderapi.entity.OutboxEvent;
import com.example.orderapi.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxEventStatusService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventStatusService(
            OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void markAsPublished(OutboxEvent event) {
        event.markPublished();
        outboxEventRepository.save(event);
    }
}

package com.example.orderapi;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCreated(String payload) {
        CorrelationData correlationData = new CorrelationData();

        try {
            rabbitTemplate.convertAndSend(
                    "",
                    "order.created",
                    payload,
                    correlationData
            );

            CorrelationData.Confirm confirm =
                    correlationData.getFuture().get(5, TimeUnit.SECONDS);

            if (!confirm.ack()) {
                throw new IllegalStateException(
                        "RabbitMQ negatively acknowledged the message: "
                                + confirm.reason()
                );
            }

            if (correlationData.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned the message because it could not "
                                + "be routed to a queue."
                );
            }
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Timed out waiting for RabbitMQ publisher confirmation",
                    exception
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to publish order-created event",
                    exception
            );
        }
    }
}
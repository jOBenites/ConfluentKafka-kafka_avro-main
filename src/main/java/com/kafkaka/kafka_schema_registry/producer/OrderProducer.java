package com.kafkaka.kafka_schema_registry.producer;

import com.kafkaka.kafka_schema_registry.dto.OrderMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class OrderProducer {

    private static final Logger LOG = Logger.getLogger(OrderProducer.class);

    @Channel("orders-out")
    Emitter<OrderMessage> emitter;

    public CompletionStage<Void> send(OrderMessage message) {
        LOG.infof("Enviando orden %d a RabbitMQ con messageId=%s", message.getOrderId(), message.getMessageId());
        return emitter.send(message);
    }
}

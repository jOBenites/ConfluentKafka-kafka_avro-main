package com.kafkaka.kafka_schema_registry.consumer;

import com.kafkaka.kafka_schema_registry.dto.OrderMessage;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OrderConsumer {

    private static final Logger LOG = Logger.getLogger(OrderConsumer.class);

    @Incoming("orders-in")
    public void consume(OrderMessage order) {
        LOG.infof("Mensaje recibido de RabbitMQ: orderId=%d, messageId=%s", order.getOrderId(), order.getMessageId());
        processOrder(order);
        LOG.infof("Orden %d procesada exitosamente", order.getOrderId());
    }

    private void processOrder(OrderMessage order) {
        LOG.infof("Procesando orden: ID=%d, Descripción=%s, Dirección=%s",
                order.getOrderId(), order.getOrderDescription(), order.getOrderAddress());
    }
}

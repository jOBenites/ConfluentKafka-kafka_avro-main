package com.kafkaka.kafka_schema_registry.producer;

import com.kafkaka.kafka_schema_registry.dto.orderRecord;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class KafkaAvroProducer21 {

    private static final Logger LOG = Logger.getLogger(KafkaAvroProducer21.class);

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "kafka.schema.registry.url")
    String schemaRegistryUrl;

    @ConfigProperty(name = "topic.name", defaultValue = "order-topic")
    String topicName;

    @ConfigProperty(name = "kafka.partition.default", defaultValue = "1")
    int defaultPartition;

    private KafkaProducer<String, orderRecord> producer;

    void start(@Observes StartupEvent event) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
        properties.put("schema.registry.url", schemaRegistryUrl);
        producer = new KafkaProducer<>(properties);
    }

    public CompletableFuture<String> send(orderRecord order) {
        String messageKey = UUID.randomUUID().toString();
        ProducerRecord<String, orderRecord> record =
                new ProducerRecord<>(topicName, defaultPartition, messageKey, order);
        CompletableFuture<String> result = new CompletableFuture<>();
        producer.send(record, (metadata, exception) -> completeSend(result, order, metadata, exception));
        return result;
    }

    private void completeSend(CompletableFuture<String> result, orderRecord order,
                              RecordMetadata metadata, Exception exception) {
        if (exception != null) {
            LOG.errorf(exception, "Error al enviar la orden %d a Kafka", order.getOrderId());
            result.completeExceptionally(exception);
            return;
        }
        LOG.infof("Orden %d enviada a particion %d, offset %d", order.getOrderId(),
                metadata.partition(), metadata.offset());
        result.complete(String.format("Orden #%d enviada exitosamente a Kafka | Partition: %d | Offset: %d",
                order.getOrderId(), metadata.partition(), metadata.offset()));
    }

    void stop(@Observes ShutdownEvent event) {
        if (producer != null) {
            producer.close();
        }
    }
}
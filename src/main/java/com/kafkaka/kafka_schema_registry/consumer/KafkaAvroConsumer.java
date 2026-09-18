package com.kafkaka.kafka_schema_registry.consumer;

import com.kafkaka.kafka_schema_registry.dto.orderRecord;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class KafkaAvroConsumer {

        private static final Logger LOG = Logger.getLogger(KafkaAvroConsumer.class);

        @ConfigProperty(name = "kafka.bootstrap.servers")
        String bootstrapServers;

        @ConfigProperty(name = "kafka.schema.registry.url")
        String schemaRegistryUrl;

        @ConfigProperty(name = "kafka.consumer.group-id", defaultValue = "prueba-new")
        String groupId;

        @ConfigProperty(name = "topic.name", defaultValue = "order-topic")
        String topicName;

        private KafkaConsumer<String, orderRecord> consumer;
        private ExecutorService executor;

        @PostConstruct
        void start() {
                Properties properties = new Properties();
                properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
                properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
                properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
                properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
                properties.put("schema.registry.url", schemaRegistryUrl);
                properties.put("specific.avro.reader", "true");

                consumer = new KafkaConsumer<>(properties);
                consumer.assign(List.of(new TopicPartition(topicName, 0),
                                new TopicPartition(topicName, 1), new TopicPartition(topicName, 2)));
                consumer.seekToBeginning(consumer.assignment());
                executor = Executors.newVirtualThreadPerTaskExecutor();
                executor.submit(this::poll);
        }

        private void poll() {
                try {
                        while (!Thread.currentThread().isInterrupted()) {
                                for (ConsumerRecord<String, orderRecord> record : consumer.poll(Duration.ofMillis(500))) {
                                        LOG.infof("Mensaje Avro recibido: key=%s, value=%s, partition=%d, offset=%d",
                                                        record.key(), record.value(), record.partition(), record.offset());
                                }
                        }
                } catch (WakeupException ignored) {
                }
        }

        @PreDestroy
        void stop() {
                if (consumer != null) {
                        consumer.wakeup();
                }
                if (executor != null) {
                        executor.shutdownNow();
                }
                if (consumer != null) {
                        consumer.close();
                }
        }
}
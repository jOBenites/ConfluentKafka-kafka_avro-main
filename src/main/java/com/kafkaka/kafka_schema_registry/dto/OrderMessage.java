package com.kafkaka.kafka_schema_registry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public class OrderMessage {

    @JsonProperty("messageId")
    private String messageId;

    @JsonProperty("orderId")
    private int orderId;

    @JsonProperty("orderDescription")
    private String orderDescription;

    @JsonProperty("orderAddress")
    private String orderAddress;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public OrderMessage() {
        this.messageId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public OrderMessage(int orderId, String orderDescription, String orderAddress) {
        this();
        this.orderId = orderId;
        this.orderDescription = orderDescription;
        this.orderAddress = orderAddress;
    }

    public static OrderMessage fromOrderRecord(orderRecord record) {
        return new OrderMessage(
                record.getOrderId(),
                record.getOrderDescription().toString(),
                record.getOrderAddress().toString()
        );
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getOrderDescription() {
        return orderDescription;
    }

    public void setOrderDescription(String orderDescription) {
        this.orderDescription = orderDescription;
    }

    public String getOrderAddress() {
        return orderAddress;
    }

    public void setOrderAddress(String orderAddress) {
        this.orderAddress = orderAddress;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "OrderMessage{" +
                "messageId='" + messageId + '\'' +
                ", orderId=" + orderId +
                ", orderDescription='" + orderDescription + '\'' +
                ", orderAddress='" + orderAddress + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

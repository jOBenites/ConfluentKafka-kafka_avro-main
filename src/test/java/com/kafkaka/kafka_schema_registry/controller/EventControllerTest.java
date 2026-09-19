package com.kafkaka.kafka_schema_registry.controller;

import com.kafkaka.kafka_schema_registry.dto.OrderMessage;
import com.kafkaka.kafka_schema_registry.dto.orderRecord;
import com.kafkaka.kafka_schema_registry.producer.OrderProducer;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EventControllerTest {

    @InjectMock
    OrderProducer producer;

    @Test
    void sendsValidOrder() {
        when(producer.send(any(OrderMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        given()
                .contentType("application/json")
                .body("""
                        {
                          "orderId": 10,
                          "orderDescription": "Pedido valido",
                          "orderAddress": "Calle 10"
                        }
                        """)
                .when()
                .post("/api/events")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("message", containsString("Orden #10 enviada exitosamente a RabbitMQ"));

        verify(producer).send(any(OrderMessage.class));
    }

    @Test
    void rejectsInvalidOrder() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "orderId": 0,
                          "orderDescription": "",
                          "orderAddress": "x"
                        }
                        """)
                .when()
                .post("/api/events")
                .then()
                .statusCode(400)
                .body("success", equalTo(false))
                .body("error", equalTo("Validacion fallida"))
                .body("validationErrors", hasItem(containsString("orderId")));
    }

    @Test
    void returnsServerErrorWhenPublishingFails() {
        when(producer.send(any(OrderMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("RabbitMQ unavailable")));

        given()
                .contentType("application/json")
                .body("""
                        {
                          "orderId": 11,
                          "orderDescription": "Pedido con error",
                          "orderAddress": "Calle 11"
                        }
                        """)
                .when()
                .post("/api/events")
                .then()
                .statusCode(500)
                .body("success", equalTo(false))
                .body("error", equalTo("Error interno del servidor"));
    }
}

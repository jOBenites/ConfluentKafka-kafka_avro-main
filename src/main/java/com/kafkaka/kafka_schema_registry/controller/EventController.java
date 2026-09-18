package com.kafkaka.kafka_schema_registry.controller;

import com.kafkaka.kafka_schema_registry.dto.orderRecord;
import com.kafkaka.kafka_schema_registry.producer.KafkaAvroProducer21;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Path("/api")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EventController {

    private static final Logger LOG = Logger.getLogger(EventController.class);

    @Inject
    KafkaAvroProducer21 producer;

    @POST
    @Path("/events")
    public CompletionStage<Response> sendMessage(orderRecord order) {
        List<String> validationErrors = validateOrder(order);
        if (!validationErrors.isEmpty()) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity(createValidationErrorResponse(validationErrors, order))
                            .build());
        }

        return producer.send(order)
                .thenApply(message -> Response.ok(createSuccessResponse(message, order)).build())
                .exceptionally(exception -> {
                    LOG.error("Error al procesar la orden", exception);
                    return Response.serverError().entity(createServerErrorResponse(exception, order)).build();
                });
    }

    private List<String> validateOrder(orderRecord order) {
        List<String> errors = new ArrayList<>();
        if (order == null || order.getOrderId() == null || order.getOrderId() <= 0) {
            errors.add("orderId debe ser un numero positivo mayor a 0");
        }
        if (order == null || order.getOrderDescription() == null
                || order.getOrderDescription().toString().trim().isEmpty()) {
            errors.add("orderDescription es obligatorio y no puede estar vacio");
        } else if (order.getOrderDescription().toString().length() < 5) {
            errors.add("orderDescription debe tener al menos 5 caracteres");
        }
        if (order == null || order.getOrderAddress() == null
                || order.getOrderAddress().toString().trim().isEmpty()) {
            errors.add("orderAddress es obligatorio y no puede estar vacio");
        } else if (order.getOrderAddress().toString().length() < 5) {
            errors.add("orderAddress debe tener al menos 5 caracteres");
        }
        return errors;
    }

    private Map<String, Object> createSuccessResponse(String message, orderRecord order) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("timestamp", getCurrentTimestamp());
        response.put("message", message);
        response.put("data", Map.of(
                "orderId", order.getOrderId(),
                "orderDescription", order.getOrderDescription().toString(),
                "orderAddress", order.getOrderAddress().toString()));
        return response;
    }

    private Map<String, Object> createValidationErrorResponse(List<String> errors, orderRecord order) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("timestamp", getCurrentTimestamp());
        response.put("error", "Validacion fallida");
        response.put("validationErrors", errors);
        response.put("errorCount", errors.size());
        Map<String, Object> receivedData = new LinkedHashMap<>();
        receivedData.put("orderId", order == null ? null : order.getOrderId());
        receivedData.put("orderDescription", order == null || order.getOrderDescription() == null
            ? null : order.getOrderDescription().toString());
        receivedData.put("orderAddress", order == null || order.getOrderAddress() == null
            ? null : order.getOrderAddress().toString());
        response.put("receivedData", receivedData);
        return response;
    }

    private Map<String, Object> createServerErrorResponse(Throwable exception, orderRecord order) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("timestamp", getCurrentTimestamp());
        response.put("error", "Error interno del servidor");
        response.put("message", "Ocurrio un error al procesar su solicitud");
        response.put("errorType", exception.getClass().getSimpleName());
        response.put("errorDetail", exception.getMessage());
        if (order != null) {
            response.put("orderId", order.getOrderId());
        }
        return response;
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

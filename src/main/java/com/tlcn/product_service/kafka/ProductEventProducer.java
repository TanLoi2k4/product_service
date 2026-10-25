package com.tlcn.product_service.kafka;

import com.tlcn.product_service.dto.kafka.StockReservationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topics.stock-reservation-response:stock-reservation-response}")
    private String stockReservationResponseTopic;

    @Value("${spring.kafka.topics.inventory-update:inventory-update}")
    private String inventoryUpdateTopic;

    @Value("${spring.kafka.topics.product-price-update:product-price-update}")
    private String productPriceUpdateTopic;

    public void sendStockReservationResponse(StockReservationResponse response) {
        if (response == null) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        long timestampMs = Instant.now().toEpochMilli();

        String key = response.getOrderId() != null ? String.valueOf(response.getOrderId()) : null;

        ProducerRecord<String, Object> record = new ProducerRecord<>(
            stockReservationResponseTopic,
            key,
            response
        );
        record.headers().add("messageId", messageId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(timestampMs).getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
        log.info("Sent stock-reservation-response: messageId={}, orderId={}, productId={}, success={} -> topic={}",
            messageId,
            response.getOrderId(),
            response.getProductId(),
            response.isSuccess(),
            stockReservationResponseTopic
        );
    }

    public void sendInventoryUpdate(Long productId, int newStock, String source) {
        if (productId == null) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        long timestampMs = Instant.now().toEpochMilli();

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("newStock", newStock);
        payload.put("source", source);
        payload.put("timestamp", timestampMs);

        ProducerRecord<String, Object> record = new ProducerRecord<>(
            inventoryUpdateTopic,
            String.valueOf(productId),
            payload
        );
        record.headers().add("messageId", messageId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(timestampMs).getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
        log.info("Sent inventory-update: messageId={}, productId={}, newStock={}, source={} -> topic={}",
            messageId,
            productId,
            newStock,
            source,
            inventoryUpdateTopic
        );
    }

    public void sendPriceUpdate(Long productId, Double newPrice, String source) {
        if (productId == null) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        long timestampMs = Instant.now().toEpochMilli();

        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("newPrice", newPrice);
        payload.put("source", source);
        payload.put("timestamp", timestampMs);

        ProducerRecord<String, Object> record = new ProducerRecord<>(
            productPriceUpdateTopic,
            String.valueOf(productId),
            payload
        );
        record.headers().add("messageId", messageId.getBytes(StandardCharsets.UTF_8));
        record.headers().add("timestamp", String.valueOf(timestampMs).getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record);
        log.info("Sent product-price-update: messageId={}, productId={}, newPrice={} -> topic={}",
            messageId,
            productId,
            newPrice,
            productPriceUpdateTopic
        );
    }
}

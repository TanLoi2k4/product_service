package com.tlcn.product_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ProductEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventProducer.class);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public void sendStockReservationResponse(Long orderId, Long productId, int newStock, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("productId", productId);
        data.put("newStock", newStock);
        data.put("status", status); // SUCCESS hoặc FAILED
        try {
            String message = objectMapper.writeValueAsString(data);
            // Sử dụng sendAndForget, log lỗi nếu thất bại.
            kafkaTemplate.send(new ProducerRecord<>("stock-reservation-response", message));
            log.info("Sent stock-reservation-response: orderId={}, status={}", orderId, status);
        } catch (Exception e) {
            log.error("Failed to send stock reservation response for orderId={}: {}", orderId, e.getMessage());
        }
    }

    public void sendInventoryUpdate(Long productId, int newStock, String source) {
        Map<String, Object> data = new HashMap<>();
        data.put("productId", productId);
        data.put("newStock", newStock);
        data.put("source", source);
        try {
            String message = objectMapper.writeValueAsString(data);
            // Sử dụng sendAndForget, log lỗi nếu thất bại.
            kafkaTemplate.send(new ProducerRecord<>("inventory-update", message));
            log.info("Sent inventory-update: productId={}, newStock={}, source={}", productId, newStock, source);
        } catch (Exception e) {
            log.error("Failed to send inventory update for productId={}: {}", productId, e.getMessage());
        }
    }
}
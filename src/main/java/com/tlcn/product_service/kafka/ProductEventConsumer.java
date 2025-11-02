package com.tlcn.product_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlcn.product_service.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "stock-reservation-request", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeStockReservationRequest(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Long orderId = ((Number) data.get("orderId")).longValue();
            Long productId = ((Number) data.get("productId")).longValue();
            int quantity = ((Number) data.get("quantity")).intValue();

            log.info("Received stock-reservation-request: orderId={}, productId={}, quantity={}", orderId, productId, quantity);
            productService.reserveStock(orderId, productId, quantity);
        } catch (Exception e) {
            log.error("Failed to process stock reservation request: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "stock-rollback-request", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeStockRollbackRequest(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            Long productId = ((Number) data.get("productId")).longValue();
            int quantity = ((Number) data.get("quantity")).intValue();

            log.info("Received stock-rollback-request: productId={}, quantity={}", productId, quantity);
            productService.rollbackStock(productId, quantity);
        } catch (Exception e) {
            log.error("Failed to process stock rollback request: {}", e.getMessage());
        }
    }
}
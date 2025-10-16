package com.tlcn.product_service.kafka;

import com.tlcn.product_service.service.ProductService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryConsumer {

    private final ProductService productService;

    @KafkaListener(topics = "inventory-updates", groupId = "product-service-absolute-stock")
    public void consumeInventoryUpdate(Map<String, Object> message, Acknowledgment acknowledgment) {

        try {
            log.info("Received inventory update (Absolute Stock): {}", message);

            if (!validateMessage(message)) {
                log.warn("Invalid message received. Acknowledging and skipping.");
                acknowledgment.acknowledge();
                return;
            }

            Object source = message.get("source");
            // Lọc các tin nhắn từ Order Service (vì chúng được xử lý bởi StockReservationConsumer)
            if (source != null && "order-service".equals(source)) {
                 log.debug("Skipping message from Order Service. It should be handled by StockReservationConsumer.");
                 acknowledgment.acknowledge();
                 return;
            }
            
            // Lọc tin nhắn từ chính Product Service (đã gửi đi khi reserve thành công)
            if (source != null && "product-service".equals(source)) {
                 log.debug("Skipping message originated from Product Service.");
                 acknowledgment.acknowledge();
                 return;
            }

            Long productId = ((Number) message.get("productId")).longValue();
            Integer stock = ((Number) message.get("stock")).intValue();
            
            // Xử lý cập nhật Stock tuyệt đối
            productService.updateStock(productId, stock); 
            log.info("Successfully updated absolute stock for productId={} to {}", productId, stock);

            acknowledgment.acknowledge();
        } catch (EntityNotFoundException e) {

            log.error("Non-retryable error: Product not found (ID: {}) for update. Acknowledging.", message.get("productId"));
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error while consuming inventory update: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private boolean validateMessage(Map<String, Object> message) {
        if (message == null || !message.containsKey("productId") || !message.containsKey("stock")) {
            log.warn("Invalid message format: {}", message);
            return false;
        }

        Object productIdObj = message.get("productId");
        Object stockObj = message.get("stock");
        if (!(productIdObj instanceof Number) || !(stockObj instanceof Number)) {
            log.warn("Invalid types in message: productId={}, stock={}", productIdObj, stockObj);
            return false;
        }

        return true;
    }
}
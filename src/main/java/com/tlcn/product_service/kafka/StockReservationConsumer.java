package com.tlcn.product_service.kafka;

import com.tlcn.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockReservationConsumer {

    private final ProductService productService;

    @KafkaListener(topics = "stock-reservation-requests", groupId = "product-service-reservation")
    public void consumeStockReservationRequest(Map<String, Object> message, Acknowledgment acknowledgment) {
        try {
            log.info("Received stock reservation request: {}", message);
            
            // Kiểm tra tính hợp lệ cơ bản
            if (!validateReservationMessage(message)) {
                acknowledgment.acknowledge();
                return;
            }

            // Trích xuất thông tin đặt chỗ
            Long orderId = ((Number) message.get("orderId")).longValue();
            Long productId = ((Number) message.get("productId")).longValue();
            Integer quantity = ((Number) message.get("quantity")).intValue(); // Số lượng cần TRỪ

            if (quantity <= 0) {
                 log.warn("Invalid quantity in reservation: productId={}, quantity={}", productId, quantity);
                 acknowledgment.acknowledge();
                 return;
            }
            
            // Gọi logic đặt chỗ an toàn (Saga Step)
            productService.reserveStock(orderId, productId, quantity); 
            
            acknowledgment.acknowledge();
        } catch (ProductService.CustomException e) {
            // Ném lỗi để DefaultErrorHandler retry (đặc biệt là OptimisticLockFailure)
            log.error("Stock reservation failed (retryable?): {}", e.getMessage());
            throw e; 
        } catch (Exception e) {
            log.error("Critical error during stock reservation: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private boolean validateReservationMessage(Map<String, Object> message) {
        if (message == null || !message.containsKey("orderId") || !message.containsKey("productId") || !message.containsKey("quantity")) {
            log.warn("Invalid reservation message format: {}", message);
            return false;
        }

        Object orderIdObj = message.get("orderId");
        Object productIdObj = message.get("productId");
        Object quantityObj = message.get("quantity");
        
        if (!(orderIdObj instanceof Number) || !(productIdObj instanceof Number) || !(quantityObj instanceof Number)) {
            log.warn("Invalid types in reservation message: orderId={}, productId={}, quantity={}", orderIdObj, productIdObj, quantityObj);
            return false;
        }

        return true;
    }
}
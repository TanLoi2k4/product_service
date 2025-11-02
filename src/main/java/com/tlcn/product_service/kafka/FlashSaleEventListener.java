package com.tlcn.product_service.kafka;

import com.tlcn.product_service.dto.FlashSaleEvent;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import com.tlcn.product_service.repository.ProductRepository;
import com.tlcn.product_service.service.ProductService; 

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleEventListener {

    private final ProductRepository productRepository;
    private final ProductDocumentRepository productDocumentRepository;
    private final ProductEventProducer productEventProducer; // Cần dùng để gửi Price Update

    // Đảm bảo bạn đã có 'kafkaListenerContainerFactory' để sử dụng Manual Acknowledge
    @KafkaListener(topics = "${kafka.topic.flash-sale:flash-sale}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleFlashSaleEvent(FlashSaleEvent event, Acknowledgment ack) {
        log.info("Received Flash Sale Event: Type={}, Product ID={}", event.getEventType(), event.getProductId());
        
        try {
            // Lấy Product từ DB. Sẽ ném ngoại lệ nếu không tìm thấy.
            Product product = productRepository.findById(event.getProductId())
                .orElseThrow(() -> new ProductService.CustomException("Product not found for Flash Sale event: " + event.getProductId()));
            
            // Lấy ProductDocument (Elasticsearch)
            ProductDocument document = productDocumentRepository.findById(event.getProductId()).orElse(null);

            switch (event.getEventType()) {
                case "START":
                    handleStartEvent(product, document, event);
                    break;
                case "END":
                case "CANCELLED":
                    handleEndOrCancelledEvent(product, document, event);
                    break;
                default:
                    log.warn("Unknown Flash Sale Event Type: {}", event.getEventType());
                    break;
            }

            // Acknowledge the message chỉ khi giao dịch thành công.
            ack.acknowledge();
            
        } catch (OptimisticLockException e) {
            // Xử lý xung đột phiên bản (@Version). KHÔNG ack, để Kafka tự động Re-delivery.
            log.warn("Optimistic Lock conflict occurred for Product ID {}. Transaction will be retried.", event.getProductId());
            throw new RuntimeException("Optimistic Lock conflict: Kafka message re-delivery expected.", e);
        } catch (Exception e) {
            // Lỗi DB/ES hoặc lỗi nghiệp vụ. KHÔNG ack, để đảm bảo không mất dữ liệu.
            log.error("Critical error processing Flash Sale event for Product ID {}. Transaction will be rolled back.", event.getProductId(), e);
            throw new RuntimeException("Failed to process Flash Sale event.", e);
        }
    }

    // --- Xử lý START Event ---
    private void handleStartEvent(Product product, ProductDocument document, FlashSaleEvent event) {
        // Kiểm tra tránh ghi đè nếu sản phẩm đã active FS (trường hợp tin nhắn Kafka bị duplicate)
        if (product.isFlashSale()) {
            log.warn("Product ID {} is already in Flash Sale status. Skipping START update.", product.getId());
            return;
        }

        // 1. Cập nhật PostgreSQL
        product.setOriginalPriceBeforeFs(product.getPrice()); // **Lưu giá gốc**
        product.setPrice(event.getSalePrice());             // Đặt giá Flash Sale
        product.setFlashSale(true);
        product.setFlashSaleEndTime(event.getEndTime());
        productRepository.save(product);
        
        log.info("Product {} price updated to {} (Flash Sale START). Original price saved: {}.", 
                 product.getId(), event.getSalePrice(), product.getOriginalPriceBeforeFs());

        // 2. Cập nhật Elasticsearch (Nếu cần đồng bộ giá cho tìm kiếm)
        if (document != null) {
            document.setPrice(event.getSalePrice());
            productDocumentRepository.save(document);
        }
    }

    // --- Xử lý END/CANCELLED Event ---
    private void handleEndOrCancelledEvent(Product product, ProductDocument document, FlashSaleEvent event) {
        if (!product.isFlashSale()) {
             log.warn("Product ID {} is not currently in Flash Sale status. Skipping {} update.", product.getId(), event.getEventType());
             return;
        }
        
        // 1. Cập nhật PostgreSQL: Khôi phục
        Double priceToRestore = product.getOriginalPriceBeforeFs();

        if (priceToRestore == null) {
            log.error("CRITICAL ERROR: No original price found for Product ID {} after Flash Sale {}. Restoring default price (if any) or requires manual check.", product.getId(), event.getEventType());
            // Giữ nguyên giá nếu không thể khôi phục, hoặc đặt về giá mặc định nếu có logic đó.
            // Ở đây, tôi sẽ giữ nguyên giá hiện tại để tránh lỗi NULL.
        } else {
            product.setPrice(priceToRestore); // **Khôi phục giá gốc**
        }
        
        product.setFlashSale(false);
        product.setOriginalPriceBeforeFs(null); // Xóa giá gốc đã lưu sau khi khôi phục
        product.setFlashSaleEndTime(null);
        productRepository.save(product);
        
        log.info("Product {} price restored to {} (Flash Sale {}).", 
                 product.getId(), product.getPrice(), event.getEventType());

        // 2. Cập nhật Elasticsearch (Nếu cần đồng bộ giá cho tìm kiếm)
        if (document != null) {
            document.setPrice(product.getPrice());
            productDocumentRepository.save(document);
        }
    }
}
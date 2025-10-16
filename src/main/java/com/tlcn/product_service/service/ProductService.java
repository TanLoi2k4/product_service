package com.tlcn.product_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tlcn.product_service.dto.ProductDTO;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import com.tlcn.product_service.repository.ProductRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductDocumentRepository productDocumentRepository;

    @Autowired
    private Cloudinary cloudinary;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${cloudinary.folder}")
    private String cloudinaryFolder;

    public static class CustomException extends RuntimeException {
        public CustomException(String message) {
            super(message);
        }
    }
    
    @Transactional
    public Product createProduct(ProductDTO productDTO, String keycloakId, MultipartFile image) {
        String imagePublicId = null;
        String imageUrl = null;

        try {
            if (image != null && !image.isEmpty()) {
                validateImage(image);
                Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.asMap("folder", cloudinaryFolder));
                imagePublicId = (String) uploadResult.get("public_id");
                imageUrl = (String) uploadResult.get("secure_url");
            }

            Product product = new Product();
            product.setName(productDTO.getName());
            product.setDescription(productDTO.getDescription());
            product.setCategory(productDTO.getCategory());
            product.setPrice(productDTO.getPrice());
            product.setStock(productDTO.getStock());
            product.setKeycloakId(keycloakId);
            product.setImageUrl(imageUrl);
            product = productRepository.save(product);

            ProductDocument document = new ProductDocument();
            document.setId(product.getId());
            document.setName(product.getName());
            document.setDescription(product.getDescription());
            document.setCategory(product.getCategory());
            document.setPrice(product.getPrice());
            document.setStock(product.getStock());
            document.setImageUrl(product.getImageUrl());
            document.setKeycloakId(keycloakId);
            productDocumentRepository.save(document);

            // Gửi cập nhật tồn kho ban đầu (source: product-service)
            sendInventoryUpdate(product.getId(), product.getStock(), "product-service");

            log.info("Product created: id={}, keycloakId={}", product.getId(), keycloakId);
            return product;
        } catch (Exception e) {
            if (imagePublicId != null) {
                try {
                    cloudinary.uploader().destroy(imagePublicId, ObjectUtils.emptyMap());
                } catch (IOException ex) {
                    log.error("Failed to delete image from Cloudinary during cleanup: {}", ex.getMessage());
                }
            }
            log.error("Product creation failed: {}", e.getMessage());
            throw new CustomException("Product creation failed: " + e.getMessage());
        }
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CustomException("Product not found with ID: " + productId));
    }
    
    public Page<Product> getProductsByKeycloakIdWithoutAuth(String keycloakId, Pageable pageable) {
        return productRepository.findByKeycloakId(keycloakId, pageable);
    }

    public Page<ProductDocument> searchProductsByKeycloakIdWithoutAuth(String keycloakId, String query, Pageable pageable) {
        return productDocumentRepository.findByKeycloakIdAndNameContainingOrKeycloakIdAndDescriptionContainingOrKeycloakIdAndCategoryContaining(
                keycloakId, query,
                keycloakId, query,
                keycloakId, query,
                pageable);
    }

    @Transactional
    public Product updateProduct(Long id, ProductDTO productDTO, String keycloakId, MultipartFile image) {
        Product product = getProduct(id);
        if (!product.getKeycloakId().equals(keycloakId)) {
            log.warn("Unauthorized update attempt: keycloakId={}, productKeycloakId={}", keycloakId, product.getKeycloakId());
            throw new CustomException("Unauthorized: Vendor mismatch");
        }

        String imagePublicId = null;
        String imageUrl = product.getImageUrl();

        try {
            if (image != null && !image.isEmpty()) {
                validateImage(image);
                if (product.getImageUrl() != null) {
                    try {
                        cloudinary.uploader().destroy(extractPublicIdFromUrl(product.getImageUrl()), ObjectUtils.emptyMap());
                    } catch (IOException e) {
                        log.error("Failed to delete old image from Cloudinary: {}", e.getMessage());
                    }
                }
                Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.asMap("folder", cloudinaryFolder));
                imagePublicId = (String) uploadResult.get("public_id");
                imageUrl = (String) uploadResult.get("secure_url");
            }

            product.setName(productDTO.getName());
            product.setDescription(productDTO.getDescription());
            product.setCategory(productDTO.getCategory());
            product.setPrice(productDTO.getPrice());
            product.setStock(productDTO.getStock());
            product.setImageUrl(imageUrl);
            product = productRepository.save(product);

            ProductDocument document = productDocumentRepository.findById(id)
                    .orElseThrow(() -> new CustomException("Product not found in Elasticsearch"));
            document.setName(product.getName());
            document.setDescription(product.getDescription());
            document.setCategory(product.getCategory());
            document.setPrice(product.getPrice());
            document.setStock(product.getStock());
            document.setImageUrl(product.getImageUrl());
            document.setKeycloakId(keycloakId);
            productDocumentRepository.save(document);

            sendInventoryUpdate(product.getId(), product.getStock(), "product-service");

            log.info("Product updated: id={}, keycloakId={}", id, keycloakId);
            return product;
        } catch (Exception e) {
            if (imagePublicId != null) {
                try {
                    cloudinary.uploader().destroy(imagePublicId, ObjectUtils.emptyMap());
                } catch (IOException ex) {
                    log.error("Failed to delete image from Cloudinary during cleanup: {}", ex.getMessage());
                }
            }
            log.error("Product update failed: {}", e.getMessage());
            throw new CustomException("Product update failed: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteProduct(Long id, String keycloakId) {
        Product product = getProduct(id);
        if (!product.getKeycloakId().equals(keycloakId)) {
            log.warn("Unauthorized delete attempt: keycloakId={}, productKeycloakId={}", keycloakId, product.getKeycloakId());
            throw new CustomException("Unauthorized: Vendor mismatch");
        }

        if (product.getImageUrl() != null) {
            try {
                cloudinary.uploader().destroy(extractPublicIdFromUrl(product.getImageUrl()), ObjectUtils.emptyMap());
            } catch (IOException e) {
                log.error("Failed to delete image from Cloudinary: {}", e.getMessage());
            }
        }

        productRepository.delete(product);
        productDocumentRepository.deleteById(id);
        log.info("Product deleted: id={}, keycloakId={}", id, keycloakId);
    }

    @Transactional
    public void updateStock(Long productId, int newStock) {
        if (newStock < 0) {
            throw new CustomException("Stock cannot be negative");
        }

        Product product = getProduct(productId);
        product.setStock(newStock);
        productRepository.save(product);

        ProductDocument document = productDocumentRepository.findById(productId)
                .orElseThrow(() -> new CustomException("Product not found in Elasticsearch"));
        document.setStock(newStock);
        productDocumentRepository.save(document);

        log.info("Stock updated for productId={}, newStock={}", productId, newStock);

        sendInventoryUpdate(productId, newStock, "product-service");
    }

    @Transactional
    public void reserveStock(Long orderId, Long productId, int quantity) {
        // 1. Lấy sản phẩm (DB lock/version check)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException("Product not found with ID: " + productId));
        
        // 2. Kiểm tra tồn kho
        if (product.getStock() >= quantity) {
            // ĐỦ: Thực hiện trừ tồn kho
            int newStock = product.getStock() - quantity;
            product.setStock(newStock);
            
            try {
                // 3. Lưu DB (Optimistic Locking sẽ kiểm tra version)
                productRepository.save(product);
                
                // 4. Cập nhật Elasticsearch
                ProductDocument document = productDocumentRepository.findById(productId)
                        .orElseThrow(() -> new CustomException("Product not found in Elasticsearch"));
                document.setStock(newStock);
                productDocumentRepository.save(document);
                
                log.info("Stock reserved: orderId={}, productId={}, newStock={}", orderId, productId, newStock);

                // 5. Gửi PHẢN HỒI THÀNH CÔNG (SAGA)
                sendStockReservationResponse(orderId, productId, newStock, "SUCCESS");
                
                // 6. Gửi cập nhật tồn kho để Order Service/Redis cache cập nhật
                sendInventoryUpdate(productId, newStock, "product-service");
                
            } catch (OptimisticLockingFailureException e) {
                log.warn("OptimisticLockingFailureException for productId={}. Throwing to Kafka DefaultErrorHandler for retry.", productId);
                // Ném lỗi để DefaultErrorHandler retry.
                throw new CustomException("Optimistic lock failed, retry required.");
            }
            
        } else {
            // KHÔNG ĐỦ: Gửi PHẢN HỒI THẤT BẠI (SAGA)
            log.warn("Insufficient stock for reservation: orderId={}, productId={}, required={}, current={}", orderId, productId, quantity, product.getStock());
            sendStockReservationResponse(orderId, productId, product.getStock(), "FAILED");
            // KHÔNG ném Exception để không retry, vì lỗi tồn kho không phải là lỗi tạm thời.
        }
    }

    public Page<Product> getProductsByVendorId(String keycloakId, Pageable pageable) {
        return productRepository.findByKeycloakId(keycloakId, pageable);
    }

    public Page<ProductDocument> searchProductsByVendor(String keycloakId, String query, Pageable pageable) {
        return productDocumentRepository.findByKeycloakIdAndNameContainingOrKeycloakIdAndDescriptionContainingOrKeycloakIdAndCategoryContaining(
                keycloakId, query,
                keycloakId, query,
                keycloakId, query,
                pageable);
    }

    public Page<ProductDocument> searchProducts(String query, Pageable pageable) {
        return productDocumentRepository.findByNameContainingOrDescriptionContainingOrCategoryContaining(
                query, query, query, pageable);
    }

    private void sendInventoryUpdate(Long productId, int stock, String source) {
        Map<String, Object> stockUpdate = new HashMap<>();
        stockUpdate.put("productId", productId);
        stockUpdate.put("stock", stock);
        stockUpdate.put("source", source);

        try {
            kafkaTemplate.send("inventory-updates", String.valueOf(productId), stockUpdate);
            log.info("Sent inventory update to Kafka: productId={}, stock={}, source={}", productId, stock, source);
        } catch (Exception e) {
            log.error("Failed to send inventory update to Kafka: {}", e.getMessage(), e);
            throw new CustomException("Failed to send inventory update to Kafka: " + e.getMessage());
        }
    }
    
    private void sendStockReservationResponse(Long orderId, Long productId, int currentStock, String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("productId", productId);
        response.put("status", status); // SUCCESS hoặc FAILED
        response.put("currentStock", currentStock);
        response.put("source", "product-service");
        
        // Gửi đến topic mà Order Service lắng nghe
        kafkaTemplate.send("stock-reservation-responses", String.valueOf(orderId), response); 
        log.info("Sent stock reservation response: orderId={}, status={}", orderId, status);
    }

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (!Arrays.asList("image/jpeg", "image/png").contains(contentType)) {
            log.warn("Invalid image format: {}", contentType);
            throw new CustomException("Image must be JPEG or PNG");
        }

        long maxSize = 2 * 1024 * 1024; // 2MB
        if (image.getSize() > maxSize) {
            log.warn("Image size exceeds limit: {} bytes", image.getSize());
            throw new CustomException("Image size must not exceed 2MB");
        }
    }

    private String extractPublicIdFromUrl(String url) {
        String[] parts = url.split("/");
        String fileName = parts[parts.length - 1];
        return cloudinaryFolder + "/" + fileName.split("\\.")[0];
    }
}
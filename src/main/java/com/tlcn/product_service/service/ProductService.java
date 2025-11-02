package com.tlcn.product_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tlcn.product_service.dto.ProductDTO;
import com.tlcn.product_service.kafka.ProductEventProducer;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import com.tlcn.product_service.repository.ProductRepository;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductDocumentRepository productDocumentRepository;

    @Autowired
    private Cloudinary cloudinary;

    @Autowired
    private ProductEventProducer productEventProducer;

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
                imageUrl = cloudinary.url().generate(imagePublicId);
            }

            Product product = new Product();
            product.setName(productDTO.getName());
            product.setDescription(productDTO.getDescription());
            product.setCategory(productDTO.getCategory());
            product.setPrice(productDTO.getPrice());
            product.setStock(productDTO.getStock());
            product.setKeycloakId(keycloakId);
            product.setImageUrl(imageUrl);
            product.setDeleted(false);
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
            document.setDeleted(false);
            productDocumentRepository.save(document);

            productEventProducer.sendInventoryUpdate(product.getId(), product.getStock(), "product-service-create");
            log.info("Product created: id={}, keycloakId={}", product.getId(), keycloakId);
            return product;
        } catch (Exception e) {
            if (imagePublicId != null) {
                try {
                    cloudinary.uploader().destroy(imagePublicId, ObjectUtils.emptyMap());
                } catch (IOException ex) {
                    log.error("Failed to delete image from Cloudinary: {}", ex.getMessage());
                }
            }
            log.error("Product creation failed: {}", e.getMessage());
            throw new CustomException("Product creation failed: " + e.getMessage());
        }
    }

    public Product getProduct(Long productId) {
        Product product = productRepository.findByIdAndIsDeletedFalse(productId)
                .orElseThrow(() -> new CustomException("Product not found with ID: " + productId));
        log.info("Retrieved product: id={}", productId);
        return product;
    }

    @Transactional
    public Product updateProduct(Long id, ProductDTO productDTO, String keycloakId, MultipartFile image) {
        Product product = getProduct(id);
        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new CustomException("Unauthorized: Vendor mismatch");
        }

        String imagePublicId = null;
        String imageUrl = product.getImageUrl();
        int oldStock = product.getStock();

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
                imageUrl = cloudinary.url().generate(imagePublicId);
            }

            product.setName(productDTO.getName());
            product.setDescription(productDTO.getDescription());
            product.setCategory(productDTO.getCategory());
            product.setPrice(productDTO.getPrice());
            product.setStock(productDTO.getStock());
            product.setImageUrl(imageUrl);
            product.setDeleted(false);
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
            document.setDeleted(false);
            productDocumentRepository.save(document);

            if (oldStock != product.getStock()) {
                productEventProducer.sendInventoryUpdate(product.getId(), product.getStock(), "product-service-update");
            }
            log.info("Product updated: id={}, keycloakId={}", id, keycloakId);
            return product;
        } catch (Exception e) {
            if (imagePublicId != null) {
                try {
                    cloudinary.uploader().destroy(imagePublicId, ObjectUtils.emptyMap());
                } catch (IOException ex) {
                    log.error("Failed to delete image from Cloudinary: {}", ex.getMessage());
                }
            }
            log.error("Product update failed: {}", e.getMessage());
            throw new CustomException("Product update failed: " + e.getMessage());
        }
    }

    @Transactional
    public void softDeleteProduct(Long id, String keycloakId) {
        Product product = productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new CustomException("Product not found or already deleted"));

        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new CustomException("Unauthorized: You can only delete your own products");
        }

        // Đánh dấu xóa mềm
        product.setDeleted(true);
        productRepository.save(product);

        // Xóa khỏi Elasticsearch
        productDocumentRepository.deleteById(id);

        log.info("Product soft-deleted: id={}, keycloakId={}", id, keycloakId);
    }

    public Page<Product> getProductsByKeycloakIdWithoutAuth(String keycloakId, Pageable pageable) {
        Page<Product> products = productRepository.findByKeycloakIdAndIsDeletedFalse(keycloakId, pageable);
        log.info("Retrieved {} active products for vendor: keycloakId={}", products.getTotalElements(), keycloakId);
        return products;
    }

    public Page<ProductDocument> searchProductsByKeycloakIdWithoutAuth(String keycloakId, String query, Pageable pageable) {
        Page<ProductDocument> products = productDocumentRepository
                .findByKeycloakIdAndIsDeletedFalseAndNameContainingOrKeycloakIdAndIsDeletedFalseAndDescriptionContainingOrKeycloakIdAndIsDeletedFalseAndCategoryContaining(
                        keycloakId, query, keycloakId, query, keycloakId, query, pageable);
        log.info("Search returned {} active products for vendor: keycloakId={}, query={}", products.getTotalElements(), keycloakId, query);
        return products;
    }

    public Page<ProductDocument> searchProducts(String query, Pageable pageable) {
        Page<ProductDocument> products = productDocumentRepository
                .findByIsDeletedFalseAndNameContainingOrIsDeletedFalseAndDescriptionContainingOrIsDeletedFalseAndCategoryContaining(
                        query, query, query, pageable);
        log.info("Search returned {} active products: query={}", products.getTotalElements(), query);
        return products;
    }

    public Page<Product> getDeletedProductsByVendor(String keycloakId, Pageable pageable) {
        Page<Product> deletedProducts = productRepository.findByKeycloakIdAndIsDeletedTrue(keycloakId, pageable);
        log.info("Retrieved {} deleted products for vendor: keycloakId={}", deletedProducts.getTotalElements(), keycloakId);
        return deletedProducts;
    }

    public Page<ProductDocument> getFlashSaleProducts(Pageable pageable) {
        Page<ProductDocument> products = productDocumentRepository.findByIsDeletedFalseAndIsFlashSaleTrue(pageable);
        log.info("Retrieved {} active Flash Sale products.", products.getTotalElements());
        return products;
    }

    public Page<ProductDocument> searchFlashSaleProducts(String query, Pageable pageable) {
        Page<ProductDocument> products = productDocumentRepository.findByIsDeletedFalseAndIsFlashSaleTrueAndNameContainingOrIsDeletedFalseAndIsFlashSaleTrueAndDescriptionContainingOrIsDeletedFalseAndIsFlashSaleTrueAndCategoryContaining(
                query, query, query, pageable);
        log.info("Search returned {} active Flash Sale products: query={}", products.getTotalElements(), query);
        return products;
    }
    
    @Transactional
    public Product restoreProduct(Long id, String keycloakId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CustomException("Product not found with ID: " + id));

        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new CustomException("Unauthorized: You can only restore your own products");
        }

        if (!product.isDeleted()) {
            throw new CustomException("Product is not deleted");
        }

        // Khôi phục
        product.setDeleted(false);
        product = productRepository.save(product);

        // Đồng bộ lại Elasticsearch
        ProductDocument document = ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(product.getCategory())
                .price(product.getPrice())
                .stock(product.getStock())
                .imageUrl(product.getImageUrl())
                .keycloakId(product.getKeycloakId())
                .isDeleted(false)
                .build();
        productDocumentRepository.save(document);

        log.info("Product restored: id={}, keycloakId={}", id, keycloakId);
        return product;
    }

    @Transactional
    public void reserveStock(Long orderId, Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException("Product not found with ID: " + productId));

        if (product.getStock() >= quantity) {
            int newStock = product.getStock() - quantity;
            product.setStock(newStock);

            try {
                productRepository.save(product);

                ProductDocument document = productDocumentRepository.findById(productId)
                        .orElseThrow(() -> new CustomException("Product not found in Elasticsearch"));
                document.setStock(newStock);
                productDocumentRepository.save(document);

                // GỬI PHẢN HỒI THÀNH CÔNG BẰNG PRODUCER
                productEventProducer.sendStockReservationResponse(orderId, productId, newStock, "SUCCESS");

                // GỬI CẬP NHẬT TỒN KHO CHO CÁC SERVICE KHÁC
                productEventProducer.sendInventoryUpdate(productId, newStock, "product-service-reservation");
                log.info("Stock reserved: orderId={}, productId={}, newStock={}", orderId, productId, newStock);
            } catch (Exception e) {
                // Nếu lỗi DB/ES, gửi phản hồi FAILED
                productEventProducer.sendStockReservationResponse(orderId, productId, product.getStock(), "FAILED");
                log.error("Failed to reserve stock (DB/ES error): {}", e.getMessage());
                throw new CustomException("Failed to reserve stock: " + e.getMessage());
            }
        } else {
            // GỬI PHẢN HỒI THẤT BẠI BẰNG PRODUCER
            productEventProducer.sendStockReservationResponse(orderId, productId, product.getStock(), "FAILED");
            
            log.warn("Insufficient stock: orderId={}, productId={}, required={}, current={}", orderId, productId, quantity, product.getStock());
            throw new CustomException("Insufficient stock for product ID: " + productId);
        }
    }

    @Transactional
    public void rollbackStock(Long productId, int quantity) {
        if (quantity < 0) {
            throw new CustomException("Rollback quantity must be positive");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException("Product not found with ID: " + productId));

        int newStock = product.getStock() + quantity;
        product.setStock(newStock);

        try {
            productRepository.save(product);

            ProductDocument document = productDocumentRepository.findById(productId)
                    .orElseThrow(() -> new CustomException("Product not found in Elasticsearch"));
            document.setStock(newStock);
            productDocumentRepository.save(document);

            productEventProducer.sendInventoryUpdate(productId, newStock, "product-service-rollback");
            log.info("Stock rolled back: productId={}, quantity={}, newStock={}", productId, quantity, newStock);
        } catch (Exception e) {
            log.error("Failed to rollback stock: {}", e.getMessage());
            throw new CustomException("Failed to rollback stock: " + e.getMessage());
        }
    }

    

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (!Arrays.asList("image/jpeg", "image/png").contains(contentType)) {
            throw new CustomException("Image must be JPEG or PNG");
        }
        long maxSize = 2 * 1024 * 1024; // 2MB
        if (image.getSize() > maxSize) {
            throw new CustomException("Image size must not exceed 2MB");
        }
    }

    private String extractPublicIdFromUrl(String url) {
        String[] parts = url.split("/");
        String lastPart = parts[parts.length - 1].split("\\.")[0];
        return cloudinaryFolder + "/" + lastPart;
    }
}
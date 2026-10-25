package com.tlcn.product_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tlcn.product_service.dto.ProductRequest;
import com.tlcn.product_service.dto.kafka.FlashSaleEvent;
import com.tlcn.product_service.dto.kafka.StockReservationResponse;
import com.tlcn.product_service.exception.BusinessException;
import com.tlcn.product_service.exception.ForbiddenException;
import com.tlcn.product_service.exception.ResourceNotFoundException;
import com.tlcn.product_service.kafka.ProductEventProducer;
import com.tlcn.product_service.model.Category;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.model.ProductImage;
import com.tlcn.product_service.repository.CategoryRepository;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import com.tlcn.product_service.repository.ProductImageRepository;
import com.tlcn.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor 
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final ProductDocumentRepository productDocumentRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final Cloudinary cloudinary;
    private final ProductEventProducer productEventProducer;

    @Value("${cloudinary.folder}")
    private String cloudinaryFolder;

    // ==================== CUD OPERATIONS (COMMANDS) ====================

    @Transactional
    public Product createProduct(ProductRequest productDTO, String keycloakId, MultipartFile image, MultipartFile[] images) {
        String imageUrl = null;
        String imagePublicId = null;
        List<String> uploadedPublicIds = new ArrayList<>();
        Category category = resolveCategory(productDTO.getCategoryId());

        try {
            if (image != null && !image.isEmpty()) {
                validateImage(image);
                Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.asMap("folder", cloudinaryFolder));
                imagePublicId = (String) uploadResult.get("public_id");
                imageUrl = (String) uploadResult.get("secure_url");
                uploadedPublicIds.add(imagePublicId);
            }

            Product product = Product.builder()
                    .name(productDTO.getName())
                    .description(productDTO.getDescription())
                    .category(category)
                    .price(productDTO.getPrice())
                    .stock(productDTO.getStock())
                    .soldCount(0)
                    .rating(0.0)
                    .keycloakId(keycloakId)
                    .publicId(imagePublicId) 
                    .imageUrl(imageUrl)
                    .isDeleted(false)
                    .isFlashSale(false) 
                    .build();

            upsertMainProductImage(product, imageUrl, imagePublicId);

            if (images != null) {
                for (MultipartFile extraImage : images) {
                    if (extraImage == null || extraImage.isEmpty()) {
                        continue;
                    }
                    validateImage(extraImage);
                    Map uploadResult = cloudinary.uploader().upload(extraImage.getBytes(), ObjectUtils.asMap("folder", cloudinaryFolder));
                    String publicId = (String) uploadResult.get("public_id");
                    String url = (String) uploadResult.get("secure_url");
                    uploadedPublicIds.add(publicId);

                    ProductImage productImage = new ProductImage();
                    productImage.setImageUrl(url);
                    productImage.setPublicId(publicId);
                    productImage.setMain(false);
                    productImage.setProduct(product);
                    product.getImages().add(productImage);
                }
            }

            Product savedProduct = productRepository.save(product);
            registerSyncAfterCommit(savedProduct);
            productEventProducer.sendInventoryUpdate(savedProduct.getId(), savedProduct.getStock(), "create");
            
            return savedProduct;

        } catch (Exception e) {
            cleanupUploadedImages(uploadedPublicIds);
            throw new BusinessException("Failed to create product: " + e.getMessage());
        }
    }

    @Transactional
    public Product updateProduct(Long id, ProductRequest productDTO, String keycloakId, MultipartFile image, MultipartFile[] images) {
        Product product = findActiveProduct(id);
        
        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("Unauthorized to update this product");
        }

        List<String> uploadedPublicIds = new ArrayList<>();
        Category category = resolveCategory(productDTO.getCategoryId());

        try {
            if (image != null && !image.isEmpty()) {
                validateImage(image);
                // Keep the previous cover in the gallery as a regular photo instead of
                // destroying it on Cloudinary or overwriting its ProductImage row.
                ensureCoverInGallery(product);

                Map uploadResult = cloudinary.uploader().upload(image.getBytes(), ObjectUtils.asMap("folder", cloudinaryFolder));
                product.setPublicId((String) uploadResult.get("public_id"));
                product.setImageUrl((String) uploadResult.get("secure_url"));
                uploadedPublicIds.add(product.getPublicId());
            }

            if (images != null) {
                for (MultipartFile extraImage : images) {
                    if (extraImage == null || extraImage.isEmpty()) {
                        continue;
                    }
                    validateImage(extraImage);
                    Map uploadResult = cloudinary.uploader().upload(extraImage.getBytes(), ObjectUtils.asMap("folder", cloudinaryFolder));
                    String publicId = (String) uploadResult.get("public_id");
                    String url = (String) uploadResult.get("secure_url");
                    uploadedPublicIds.add(publicId);

                    ProductImage productImage = new ProductImage();
                    productImage.setImageUrl(url);
                    productImage.setPublicId(publicId);
                    productImage.setMain(false);
                    productImage.setProduct(product);
                    product.getImages().add(productImage);
                }
            }

            upsertMainProductImage(product, product.getImageUrl(), product.getPublicId());

            product.setName(productDTO.getName());
            product.setDescription(productDTO.getDescription());
            product.setCategory(category);
            product.setPrice(productDTO.getPrice());
            product.setStock(productDTO.getStock());

            Product updatedProduct = productRepository.save(product);
            registerSyncAfterCommit(updatedProduct);
            
            productEventProducer.sendInventoryUpdate(id, updatedProduct.getStock(), "update");
            return updatedProduct;
        } catch (Exception e) {
            cleanupUploadedImages(uploadedPublicIds);
            throw new BusinessException("Failed to update product: " + e.getMessage());
        }
    }

    @Transactional
    public void softDeleteProduct(Long id, String keycloakId) {
        Product product = findActiveProduct(id);

        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("Unauthorized to delete this product");
        }

        product.setDeleted(true);
        productRepository.save(product);
        
        registerSyncAfterCommit(product);
        log.info("Command: Product soft-deleted [ID: {}]", id);
    }

    @Transactional
    public void adminSoftDeleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.isDeleted()) {
            throw new BusinessException("Product is already deleted");
        }

        product.setDeleted(true);
        productRepository.save(product);

        registerSyncAfterCommit(product);
        log.info("Command: Product soft-deleted by admin [ID: {}]", id);
    }

    @Transactional
    public Product setMainImage(Long productId, Long imageId, String keycloakId) {
        Product product = findActiveProduct(productId);
        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("Unauthorized to manage this product images");
        }

        ProductImage requested = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        if (requested.getProduct() == null || !requested.getProduct().getId().equals(productId)) {
            throw new BusinessException("Image does not belong to this product");
        }

        ensureCoverInGallery(product);

        ProductImage targetImage = product.getImages().stream()
                .filter(image -> imageId.equals(image.getId()))
                .findFirst()
                .orElse(requested);

        for (ProductImage image : product.getImages()) {
            image.setMain(imageId.equals(image.getId()));
        }
        targetImage.setMain(true);
        product.setPublicId(targetImage.getPublicId());
        product.setImageUrl(targetImage.getImageUrl());

        Product saved = productRepository.save(product);
        registerSyncAfterCommit(saved);
        return saved;
    }

    @Transactional
    public Product setGarmentFlatImage(Long productId, Long imageId, String keycloakId) {
        Product product = findOwnedProduct(productId, keycloakId);
        ProductImage targetImage = findOwnedImage(product, imageId);

        for (ProductImage image : product.getImages()) {
            image.setGarmentFlat(imageId.equals(image.getId()));
        }
        targetImage.setGarmentFlat(true);

        Product saved = productRepository.save(product);
        registerSyncAfterCommit(saved);
        return saved;
    }

    @Transactional
    public Product clearGarmentFlatImage(Long productId, Long imageId, String keycloakId) {
        Product product = findOwnedProduct(productId, keycloakId);
        ProductImage targetImage = findOwnedImage(product, imageId);
        targetImage.setGarmentFlat(false);

        Product saved = productRepository.save(product);
        registerSyncAfterCommit(saved);
        return saved;
    }

    @Transactional
    public Product deleteProductImage(Long productId, Long imageId, String keycloakId) {
        Product product = findActiveProduct(productId);
        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("Unauthorized to manage this product images");
        }

        ProductImage targetImage = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));

        if (!targetImage.getProduct().getId().equals(productId)) {
            throw new BusinessException("Image does not belong to this product");
        }

        if (targetImage.isMain()) {
            throw new BusinessException("Cannot delete the main image. Set another image as main first");
        }

        try {
            if (targetImage.getPublicId() != null) {
                cloudinary.uploader().destroy(targetImage.getPublicId(), ObjectUtils.emptyMap());
            }
        } catch (IOException e) {
            log.warn("Failed to delete image from Cloudinary: {}", e.getMessage());
        }

        product.getImages().remove(targetImage);
        productImageRepository.delete(targetImage);
        return productRepository.save(product);
    }

    @Transactional
    public Product restoreProduct(Long id, String keycloakId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("Unauthorized to restore this product");
        }

        if (!product.isDeleted()) throw new BusinessException("Product is already active");

        product.setDeleted(false);
        Product restoredProduct = productRepository.save(product);
        registerSyncAfterCommit(restoredProduct);

        return restoredProduct;
    }

    // ==================== STOCK COMMANDS ====================

    @Transactional
    public boolean reserveStock(String orderId, Long productId, int quantity) {
        log.info("Processing reservation for Order: {}, Product: {}, Quantity: {}", orderId, productId, quantity);
        
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Integer currentStock = product.getStock();
        if (product.isDeleted() || currentStock == null || currentStock < quantity) {
            log.warn("Reservation failed: orderId={} productId={} quantity={} currentStock={} deleted={}",
                orderId,
                productId,
                quantity,
                currentStock,
                product.isDeleted()
            );
            return false;
        }

        int newStock = currentStock - quantity;
        product.setStock(newStock);
        product.increaseSoldCount(quantity);
        productRepository.save(product);

        log.info("Reservation success: orderId={} productId={} quantity={} stock {} -> {}",
            orderId,
            productId,
            quantity,
            currentStock,
            newStock
        );

        registerSyncAfterCommit(product);
        productEventProducer.sendInventoryUpdate(productId, newStock, "reservation");
        return true;
    }

    @Transactional
    public void rollbackStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Integer currentStock = product.getStock();
        int newStock = (currentStock == null ? 0 : currentStock) + quantity;
        product.setStock(newStock);
        product.decreaseSoldCount(quantity);
        productRepository.save(product);

        log.info("Rollback success: productId={} quantity={} stock {} -> {}",
            productId,
            quantity,
            currentStock,
            newStock
        );

        registerSyncAfterCommit(product);
        productEventProducer.sendInventoryUpdate(productId, newStock, "rollback");
    }

    public void sendStockReservationResponse(StockReservationResponse response) {
        productEventProducer.sendStockReservationResponse(response);
    }

    // ==================== FLASH SALE ====================

    @Transactional
    public void applyFlashSaleEvent(FlashSaleEvent event) {
        if (event == null || event.getProductId() == null) {
            throw new BusinessException("Invalid flash sale event");
        }

        Product product = productRepository.findByIdWithLock(event.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (product.isDeleted()) {
            log.warn("Ignore flash sale event for deleted product: productId={}", product.getId());
            return;
        }

        String type = event.getEventType() != null ? event.getEventType().trim().toUpperCase() : "";

        switch (type) {
            case "START":
                if (!Boolean.TRUE.equals(product.isFlashSale())) {
                    product.setOriginalPriceBeforeFs(product.getPrice());
                }
                if (event.getSalePrice() != null) {
                    product.setPrice(event.getSalePrice());
                }
                product.setFlashSale(true);
                product.setFlashSaleEndTime(event.getEndTime());
                break;

            case "END":
            case "CANCELLED":
                if (Boolean.TRUE.equals(product.isFlashSale())) {
                    Double original = product.getOriginalPriceBeforeFs();
                    if (original != null) {
                        product.setPrice(original);
                    }
                }
                product.setFlashSale(false);
                product.setFlashSaleEndTime(null);
                product.setOriginalPriceBeforeFs(null);
                break;

            default:
                log.warn("Unknown flash sale eventType: {} (productId={})", event.getEventType(), event.getProductId());
                return;
        }

        Product saved = productRepository.save(product);
        registerSyncAfterCommit(saved);

        productEventProducer.sendPriceUpdate(saved.getId(), saved.getPrice(), "flash-sale");
        log.info("Applied flash sale event: type={} productId={} price={} isFlashSale={} endTime={}",
            type,
            saved.getId(),
            saved.getPrice(),
            saved.isFlashSale(),
            saved.getFlashSaleEndTime()
        );
    }

    // ==================== SYNC LOGIC (ES) ====================

    /** Đồng bộ document ES sau khi commit (dùng khi rating/stock đổi ngoài luồng CUD sản phẩm). */
    public void syncToSearchIndexAfterCommit(Product product) {
        registerSyncAfterCommit(product);
    }

    private void registerSyncAfterCommit(Product product) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncToElasticsearch(product);
                }
            });
        } else {
            syncToElasticsearch(product);
        }
    }

    @Async
    protected void syncToElasticsearch(Product product) {
        try {
            ProductDocument document = ProductDocument.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .description(product.getDescription())
                    .category(product.getCategory() == null ? null : product.getCategory().getSlug())
                    .price(product.getPrice())
                    .stock(product.getStock())
                    .soldCount(product.soldCountValue())
                    .rating(product.getRating())
                    .imageUrl(product.getImageUrl())
                    .keycloakId(product.getKeycloakId())
                    .isDeleted(product.isDeleted())
                    // Cập nhật các trường Flash Sale mới vào ES
                    .isFlashSale(product.isFlashSale())
                    .originalPriceBeforeFs(product.getOriginalPriceBeforeFs())
                    .flashSaleEndTime(product.getFlashSaleEndTime())
                    .build();
            productDocumentRepository.save(document);
        } catch (Exception e) {
            log.error("ES Sync Failed: {}", e.getMessage());
        }
    }

    // ==================== HELPERS ====================

    private Product findActiveProduct(Long id) {
        return productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found or inactive"));
    }

    private Product findOwnedProduct(Long productId, String keycloakId) {
        Product product = findActiveProduct(productId);
        if (!product.getKeycloakId().equals(keycloakId)) {
            throw new ForbiddenException("Unauthorized to manage this product images");
        }
        return product;
    }

    private ProductImage findOwnedImage(Product product, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (image.getProduct() == null || !product.getId().equals(image.getProduct().getId())) {
            throw new BusinessException("Image does not belong to this product");
        }
        return image;
    }

    private void rollbackCloudinaryImage(String publicId) {
        if (publicId != null) {
            try {
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            } catch (IOException ex) {
                log.error("Cloudinary rollback failed: {}", ex.getMessage());
            }
        }
    }

    private void cleanupUploadedImages(List<String> publicIds) {
        if (publicIds == null) {
            return;
        }
        for (String publicId : publicIds) {
            if (publicId != null && !publicId.isBlank()) {
                rollbackCloudinaryImage(publicId);
            }
        }
    }

    private void validateImage(MultipartFile image) {
        String type = image.getContentType();
        if (type == null || !Arrays.asList("image/jpeg", "image/png", "image/webp").contains(type)) {
            throw new BusinessException("Supported formats: JPEG, PNG, WEBP");
        }
        if (image.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("Image size must be under 2MB");
        }
    }

    /**
     * Persist Product.imageUrl into the gallery when the cover exists only on the
     * product row (legacy). The previous cover can then be demoted to a regular photo.
     */
    private void ensureCoverInGallery(Product product) {
        String imageUrl = product.getImageUrl();
        String publicId = product.getPublicId();
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (product.getImages() == null) {
            product.setImages(new ArrayList<>());
        }

        boolean exists = product.getImages().stream()
                .anyMatch(img -> imageUrl.equals(img.getImageUrl())
                        || (publicId != null && publicId.equals(img.getPublicId())));
        if (exists) {
            return;
        }

        ProductImage cover = new ProductImage();
        cover.setImageUrl(imageUrl);
        cover.setPublicId(publicId);
        cover.setMain(true);
        cover.setProduct(product);
        product.getImages().add(cover);
    }

    /**
     * Keep the cover photo on Product.imageUrl and as a ProductImage with is_main=true.
     * Never overwrite another gallery row's URL — demote the previous main instead.
     */
    private void upsertMainProductImage(Product product, String imageUrl, String publicId) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        if (product.getImages() == null) {
            product.setImages(new ArrayList<>());
        }

        ProductImage mainImage = product.getImages().stream()
                .filter(img -> imageUrl.equals(img.getImageUrl())
                        || (publicId != null && publicId.equals(img.getPublicId())))
                .findFirst()
                .orElse(null);

        if (mainImage == null) {
            mainImage = new ProductImage();
            mainImage.setProduct(product);
            product.getImages().add(mainImage);
            mainImage.setImageUrl(imageUrl);
            mainImage.setPublicId(publicId);
        }

        mainImage.setMain(true);
        for (ProductImage img : product.getImages()) {
            if (img != mainImage) {
                img.setMain(false);
            }
        }
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException("Category is required");
        }

        return categoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Active category not found with id: " + categoryId));
    }
}
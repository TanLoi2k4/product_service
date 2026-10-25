package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ProductRequest;
import com.tlcn.product_service.dto.ProductResponse;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.dto.VendorProductReviewSummary;
import com.tlcn.product_service.dto.VendorReviewResponse;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.model.ProductImage;
import com.tlcn.product_service.service.ProductCommandService;
import com.tlcn.product_service.service.ProductQueryService;
import com.tlcn.product_service.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/products/vendor")
@RequiredArgsConstructor
public class ProductVendorController {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;
    private final ReviewService reviewService;

    @GetMapping("/reviews")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<VendorReviewResponse>>> getVendorReviews(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<VendorReviewResponse> reviews = reviewService.getReviewsByVendor(keycloakId, query, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor reviews retrieved", reviews));
    }

    @GetMapping("/reviews/products")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<VendorProductReviewSummary>>> getVendorReviewProducts(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size);
        Page<VendorProductReviewSummary> products = reviewService.getProductReviewSummariesByVendor(
                keycloakId,
                query,
                pageable
        );
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor review products retrieved", products));
    }

    @GetMapping
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getMyProducts(
            Authentication authentication,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        
        Page<ProductDocument> result = queryService.getProductsByVendor(keycloakId, query, pageable);
        
        return ResponseEntity.ok(new ResponseDTO<>(true, "Fetched vendor products successfully", result));
    }

    @GetMapping("/deleted")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getMyDeletedProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy
    ) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());

        Page<ProductDocument> result = queryService.getDeletedProductsByVendor(keycloakId, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Fetched deleted products successfully", result));
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> create(
            Authentication authentication,
            @RequestPart("product") ProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "images", required = false) MultipartFile[] images) {
        
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(commandService.createProduct(request, keycloakId, image, images));
        
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product created successfully", response));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> update(
            Authentication authentication,
            @PathVariable Long id,
            @RequestPart("product") ProductRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "images", required = false) MultipartFile[] images) {
        
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(commandService.updateProduct(id, request, keycloakId, image, images));
        
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<String>> delete(
            Authentication authentication,
            @PathVariable Long id) {
        
        String keycloakId = authentication.getName();
        commandService.softDeleteProduct(id, keycloakId);
        
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product deleted successfully", "Deleted ID: " + id));
    }

    @PutMapping("/{productId}/images/{imageId}/main")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> setMainImage(
            Authentication authentication,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(commandService.setMainImage(productId, imageId, keycloakId));
        return ResponseEntity.ok(new ResponseDTO<>(true, "Main image updated successfully", response));
    }

    @PutMapping("/{productId}/images/{imageId}/garment")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> setGarmentFlatImage(
            Authentication authentication,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(
                commandService.setGarmentFlatImage(productId, imageId, keycloakId));
        return ResponseEntity.ok(new ResponseDTO<>(true, "Garment image updated successfully", response));
    }

    @DeleteMapping("/{productId}/images/{imageId}/garment")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> clearGarmentFlatImage(
            Authentication authentication,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(
                commandService.clearGarmentFlatImage(productId, imageId, keycloakId));
        return ResponseEntity.ok(new ResponseDTO<>(true, "Garment image cleared successfully", response));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> deleteImage(
            Authentication authentication,
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(commandService.deleteProductImage(productId, imageId, keycloakId));
        return ResponseEntity.ok(new ResponseDTO<>(true, "Image deleted successfully", response));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ProductResponse>> restore(
            Authentication authentication,
            @PathVariable Long id) {
        
        String keycloakId = authentication.getName();
        ProductResponse response = ProductResponse.fromProduct(commandService.restoreProduct(id, keycloakId));
        
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product restored successfully", response));
    }
}
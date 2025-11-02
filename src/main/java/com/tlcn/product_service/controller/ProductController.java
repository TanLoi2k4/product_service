package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ProductDTO;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping(value = "/vendor", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Product>> createProduct(
            @Valid @RequestPart("product") ProductDTO productDTO,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        String keycloakId = authentication.getName();
        Product product = productService.createProduct(productDTO, keycloakId, image);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product created successfully", product));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<Product>> getProduct(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product retrieved successfully", product));
    }

    @PutMapping(value = "/vendor/{id}", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Product>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestPart("product") ProductDTO productDTO,
            @RequestPart(value = "image", required = false) MultipartFile image,
            Authentication authentication) {
        String keycloakId = authentication.getName();
        Product product = productService.updateProduct(id, productDTO, keycloakId, image);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product updated successfully", product));
    }

    @DeleteMapping("/vendor/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Void>> deleteProduct(@PathVariable Long id, Authentication authentication) {
        String keycloakId = authentication.getName();
        productService.softDeleteProduct(id, keycloakId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product deleted successfully", null));
    }

    @GetMapping("/vendor_id")
    public ResponseEntity<ResponseDTO<Page<Product>>> getProductsByKeycloakId(
            @RequestParam String keycloakId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductsByKeycloakIdWithoutAuth(keycloakId, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Products retrieved successfully", products));
    }

    @GetMapping("/vendor_id/search")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> searchProductsByKeycloakId(
            @RequestParam String keycloakId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDocument> products = productService.searchProductsByKeycloakIdWithoutAuth(keycloakId, query, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor products retrieved successfully", products));
    }
    
    @GetMapping("/vendor")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<Product>>> getProductsByVendor(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductsByKeycloakIdWithoutAuth(keycloakId, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor products retrieved successfully", products));
    }

    @GetMapping("/vendor/search")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> searchProductsByVendor(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDocument> products = productService.searchProductsByKeycloakIdWithoutAuth(keycloakId, query, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor products retrieved successfully", products));
    }
    
    @GetMapping("/search")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> searchProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDocument> products = productService.searchProducts(query, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Products retrieved successfully", products));
    }

    @PostMapping("/vendor/restore/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Product>> restoreProduct(
            @PathVariable Long id,
            Authentication authentication) {
        String keycloakId = authentication.getName();
        Product product = productService.restoreProduct(id, keycloakId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product restored successfully", product));
    }

    @GetMapping("/vendor/deleted")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<Product>>> getDeletedProductsByVendor(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        String keycloakId = authentication.getName();
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> deletedProducts = productService.getDeletedProductsByVendor(keycloakId, pageable);

        return ResponseEntity.ok(
            new ResponseDTO<>(true, "Deleted products retrieved successfully", deletedProducts)
        );
    }

    @GetMapping("/flash_sale")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getFlashSaleProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDocument> products = productService.getFlashSaleProducts(pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Flash Sale products retrieved successfully", products));
    }
    
    @GetMapping("/flash_sale/search")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> searchFlashSaleProducts(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDocument> products = productService.searchFlashSaleProducts(query, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Flash Sale products search retrieved successfully", products));
    }
}
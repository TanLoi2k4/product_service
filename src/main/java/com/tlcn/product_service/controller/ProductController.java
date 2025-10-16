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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping(consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Product>> createProduct(
            @Valid @RequestPart("product") ProductDTO productDTO,
            @RequestPart(value = "image", required = false) MultipartFile image,
            JwtAuthenticationToken authToken) {
        Jwt jwt = authToken.getToken();
        String keycloakId = jwt.getClaimAsString("sub"); 
        Product product = productService.createProduct(productDTO, keycloakId, image);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product created successfully", product));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<Product>> getProduct(@PathVariable Long id) {
        Product product = productService.getProduct(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product retrieved successfully", product));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Product>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestPart("product") ProductDTO productDTO,
            @RequestPart(value = "image", required = false) MultipartFile image,
            JwtAuthenticationToken authToken) {
        Jwt jwt = authToken.getToken();
        String keycloakId = jwt.getClaimAsString("sub");
        Product product = productService.updateProduct(id, productDTO, keycloakId, image);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product updated successfully", product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Void>> deleteProduct(@PathVariable Long id, JwtAuthenticationToken authToken) {
        Jwt jwt = authToken.getToken();
        String keycloakId = jwt.getClaimAsString("sub"); 
        productService.deleteProduct(id, keycloakId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product deleted successfully", null));
    }

    @GetMapping("/by-keycloak-id")
    public ResponseEntity<ResponseDTO<Page<Product>>> getProductsByKeycloakId(
            @RequestParam String keycloakId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductsByKeycloakIdWithoutAuth(keycloakId, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Products retrieved successfully", products));
    }

    @GetMapping("/by-keycloak-id/search")
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
            JwtAuthenticationToken authToken) {
        Jwt jwt = authToken.getToken();
        String keycloakId = jwt.getClaimAsString("sub"); 
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getProductsByVendorId(keycloakId, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor products retrieved successfully", products));
    }

    @GetMapping("/vendor/search")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> searchProductsByVendor(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            JwtAuthenticationToken authToken) {
        Jwt jwt = authToken.getToken();
        String keycloakId = jwt.getClaimAsString("sub"); 
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDocument> products = productService.searchProductsByVendor(keycloakId, query, pageable);
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
}
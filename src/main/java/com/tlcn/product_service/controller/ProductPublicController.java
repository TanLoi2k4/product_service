package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ProductResponse;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.dto.VendorProductSearchRequest;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductPublicController {

    private final ProductQueryService queryService;

    @GetMapping
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> search(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Products fetched successfully",
                queryService.searchProducts(query, pageable)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Product fetched successfully",
                ProductResponse.fromProduct(queryService.getProductById(id))
        ));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getByCategory(
            @PathVariable String category,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Products by category fetched successfully",
                queryService.getProductsByCategory(category, pageable)
        ));
    }

    @GetMapping("/flash-sale")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getFlashSales(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double min,
            @RequestParam(required = false) Double max,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Flash sale products fetched successfully",
                queryService.getFlashSaleProducts(query, category, min, max, pageable)
        ));
    }

    @GetMapping("/price-range")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getByPriceRange(
            @RequestParam(required = false) Double min,
            @RequestParam(required = false) Double max,
            @PageableDefault(size = 12) Pageable pageable) {
        double effectiveMin = (min != null) ? min : 0.0;
        double effectiveMax = (max != null) ? max : Double.MAX_VALUE;
        if (effectiveMin > effectiveMax) {
            throw new IllegalArgumentException("min must be <= max");
        }
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Products by price range fetched successfully",
                queryService.getProductsByPriceRange(effectiveMin, effectiveMax, pageable)
        ));
    }

    @GetMapping("/advanced")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> advancedSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double min,
            @RequestParam(required = false) Double max,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Advanced search completed successfully",
                queryService.advancedSearch(query, category, min, max, pageable)
        ));
    }

    @GetMapping("/{id}/similar")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getSimilar(
            @PathVariable Long id,
            Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Similar products fetched successfully",
                queryService.getSimilarProducts(id, pageable)
        ));
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> autocomplete(
            @RequestParam String prefix,
            Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Autocomplete results fetched successfully",
                queryService.autocomplete(prefix, pageable)
        ));
    }

    @PostMapping("/vendors")
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> getProductsByVendorForCustomer(
            @RequestBody VendorProductSearchRequest request,
            @PageableDefault(size = 12) Pageable pageable) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Vendor products fetched successfully",
                queryService.getProductsByVendorForCustomer(
                        request.getVendorKeycloakId(),
                        request.getQuery(),
                        pageable
                )
        ));
    }
}

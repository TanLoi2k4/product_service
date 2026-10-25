package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ProductResponse;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.service.ProductCommandService;
import com.tlcn.product_service.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/admin")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminProductController {

    private final ProductQueryService queryService;
    private final ProductCommandService commandService;

    @GetMapping
    public ResponseEntity<ResponseDTO<Page<ProductDocument>>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "false") boolean deleted,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<ProductDocument> products = queryService.adminSearchProducts(query, deleted, pageable);
        ResponseDTO<Page<ProductDocument>> body = new ResponseDTO<Page<ProductDocument>>(
                true,
                "Products fetched successfully",
                products
        );
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Product fetched successfully",
                ProductResponse.fromProduct(queryService.getProductByIdForAdmin(id))
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> delete(@PathVariable Long id) {
        commandService.adminSoftDeleteProduct(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product deleted successfully", null));
    }
}

package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.service.ProductViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductViewController {

    private final ProductViewService productViewService;

    @PostMapping("/{id}/views")
    public ResponseEntity<ResponseDTO<Void>> recordView(
            @PathVariable Long id,
            Authentication authentication
    ) {
        productViewService.recordView(authentication.getName(), id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Product view recorded", null));
    }
}

package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.CategoryResponse;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<CategoryResponse>>> getActiveCategories() {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Active categories fetched successfully",
                categoryService.getActiveCategories()
        ));
    }
}

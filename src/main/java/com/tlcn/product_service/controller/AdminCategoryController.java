package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.CategoryRequest;
import com.tlcn.product_service.dto.CategoryResponse;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products/admin/categories")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<CategoryResponse>>> getAllCategories() {
        return ResponseEntity.ok(new ResponseDTO<>(true, "Categories retrieved successfully", categoryService.getAllCategories()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<CategoryResponse>> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(new ResponseDTO<>(true, "Category retrieved successfully", categoryService.getCategoryById(id)));
    }

    @PostMapping
    public ResponseEntity<ResponseDTO<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(new ResponseDTO<>(true, "Category created successfully", categoryService.createCategory(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDTO<CategoryResponse>> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(new ResponseDTO<>(true, "Category updated successfully", categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Category deactivated successfully", null));
    }
}
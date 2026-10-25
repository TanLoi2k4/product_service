package com.tlcn.product_service.service;

import com.tlcn.product_service.dto.CategoryRequest;
import com.tlcn.product_service.dto.CategoryResponse;
import com.tlcn.product_service.exception.BusinessException;
import com.tlcn.product_service.exception.ResourceNotFoundException;
import com.tlcn.product_service.model.Category;
import com.tlcn.product_service.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        return CategoryResponse.fromEntity(findCategory(id));
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        String slug = normalizeSlug(request.getSlug(), request.getName());
        ensureSlugUnique(slug, null);

        Category category = Category.builder()
                .name(request.getName().trim())
                .slug(slug)
                .description(request.getDescription())
                .active(request.isActive())
                .virtualTryOn(request.isVirtualTryOn())
                .build();

        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        String slug = normalizeSlug(request.getSlug(), request.getName());
        ensureSlugUnique(slug, id);

        category.setName(request.getName().trim());
        category.setSlug(slug);
        category.setDescription(request.getDescription());
        category.setActive(request.isActive());
        category.setVirtualTryOn(request.isVirtualTryOn());

        return CategoryResponse.fromEntity(categoryRepository.save(category));
    }

    public void deactivateCategory(Long id) {
        Category category = findCategory(id);
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category findActiveCategory(Long id) {
        return categoryRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Active category not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Category findCategoryBySlug(String slug) {
        return categoryRepository.findBySlugIgnoreCase(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with slug: " + slug));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
    }

    private String normalizeSlug(String slug, String name) {
        String base = (slug == null || slug.isBlank()) ? name : slug;
        if (base == null || base.isBlank()) {
            throw new BusinessException("Category name is required");
        }

        return base.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private void ensureSlugUnique(String slug, Long ignoreId) {
        categoryRepository.findBySlugIgnoreCase(slug).ifPresent(existing -> {
            if (ignoreId == null || !existing.getId().equals(ignoreId)) {
                throw new BusinessException("Category slug already exists: " + slug);
            }
        });
    }
}
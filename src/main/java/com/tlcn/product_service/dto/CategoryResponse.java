package com.tlcn.product_service.dto;

import com.tlcn.product_service.model.Category;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private boolean active;
    private boolean virtualTryOn;

    public static CategoryResponse fromEntity(Category category) {
        if (category == null) {
            return null;
        }

        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .active(category.isActive())
                .virtualTryOn(category.isVirtualTryOn())
                .build();
    }
}
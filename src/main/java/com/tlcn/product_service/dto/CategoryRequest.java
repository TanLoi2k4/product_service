package com.tlcn.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryRequest {

    @NotBlank(message = "Category name is mandatory")
    private String name;

    private String slug;

    private String description;

    private boolean active = true;

    private boolean virtualTryOn = false;
}
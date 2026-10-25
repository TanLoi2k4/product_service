package com.tlcn.product_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotBlank(message = "Name is mandatory")
    private String name;

    private String description;

    @NotNull(message = "Category is mandatory")
    private Long categoryId;

    @NotNull(message = "Price is mandatory")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private Double price;

    @NotNull(message = "Stock is mandatory")
    @Min(value = 0, message = "Stock must be non-negative")
    private Integer stock;
}
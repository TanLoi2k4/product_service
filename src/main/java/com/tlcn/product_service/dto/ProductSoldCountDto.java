package com.tlcn.product_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSoldCountDto {
    private Long productId;
    private Long quantity;
}

package com.tlcn.product_service.dto;

import com.tlcn.product_service.model.ProductDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResult {
    private boolean personalized;
    private List<ProductDocument> products;
    private List<ProductDocument> bestSellers;
    private List<ProductDocument> topRated;
}

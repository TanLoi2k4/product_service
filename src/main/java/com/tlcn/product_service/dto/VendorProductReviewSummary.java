package com.tlcn.product_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class VendorProductReviewSummary {
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Double averageRating;
    private Long reviewCount;
    private Long unrepliedCount;
    private Instant latestReviewAt;
}

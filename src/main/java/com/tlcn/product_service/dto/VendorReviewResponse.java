package com.tlcn.product_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class VendorReviewResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private String customerId;
    private String customerName;
    private Integer rating;
    private String comment;
    private List<String> images;
    private String vendorReply;
    private Instant createdAt;
    private Instant updatedAt;
}

package com.tlcn.product_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long productId;
    private Long orderId;
    private String customerId;
    private String customerName;
    private Integer rating;
    private String comment;
    private List<String> images;
    private String vendorReply;
    private Instant createdAt;
    private Instant updatedAt;
}
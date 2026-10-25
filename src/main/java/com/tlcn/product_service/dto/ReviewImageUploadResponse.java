package com.tlcn.product_service.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewImageUploadResponse {
    private String imageUrl;
    private String publicId;
}

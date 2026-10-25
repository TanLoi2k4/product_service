package com.tlcn.product_service.dto;

import com.tlcn.product_service.model.ProductImage;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductImageResponse {
    private Long id;
    private String imageUrl;
    private String publicId;
    private boolean main;
    private boolean garmentFlat;

    public static ProductImageResponse fromEntity(ProductImage image) {
        if (image == null) {
            return null;
        }

        return ProductImageResponse.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .publicId(image.getPublicId())
                .main(image.isMain())
                .garmentFlat(image.isGarmentFlat())
                .build();
    }
}

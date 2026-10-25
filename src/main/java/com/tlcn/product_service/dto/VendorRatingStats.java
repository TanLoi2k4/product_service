package com.tlcn.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorRatingStats {
    private String keycloakId;
    private Double rating;
    private Long reviewCount;
}

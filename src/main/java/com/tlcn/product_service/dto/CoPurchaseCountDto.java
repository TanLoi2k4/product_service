package com.tlcn.product_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoPurchaseCountDto {
    private Long sourceProductId;
    private Long targetProductId;
    private Long customerCount;
}

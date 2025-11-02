package com.tlcn.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlashSaleEvent {
    // ID của Flash Sale 
    private Long flashSaleId;
    
    // ID sản phẩm
    private Long productId;
    
    // Loại sự kiện: START, END, CANCELLED
    private String eventType; 
    
    // Dữ liệu giảm giá
    private Double salePrice; 
    
    // Thời gian kết thúc (để Product Service theo dõi)
    private Instant endTime; 
}
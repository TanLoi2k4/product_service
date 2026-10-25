package com.tlcn.product_service.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationRequest {
    private String orderId;
    private Long productId;
    private Integer quantity;
    private LocalDateTime timestamp;
    private String eventType = "STOCK_RESERVATION_REQUEST";
}
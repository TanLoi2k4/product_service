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
public class StockReservationResponse {
    private String orderId;
    private Long productId;
    private Integer requestedQuantity;
    private Integer reservedQuantity;
    private boolean success;
    private String errorMessage;
    private LocalDateTime timestamp;
    private String eventType = "STOCK_RESERVATION_RESPONSE";
}
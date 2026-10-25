package com.tlcn.product_service.kafka;

import com.tlcn.product_service.dto.kafka.FlashSaleEvent;
import com.tlcn.product_service.dto.kafka.StockReservationRequest;
import com.tlcn.product_service.dto.kafka.StockReservationResponse;
import com.tlcn.product_service.dto.kafka.StockRollbackRequest;
import com.tlcn.product_service.service.ProductCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private static final String IDEMPOTENCY_KEY_PREFIX = "kafka:product:idempotency:";
    private static final long TTL_HOURS = 24;

    private final ProductCommandService productService;
    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(
        topics = "${spring.kafka.topics.stock-reservation-request:stock-reservation-request}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockReservationRequest(
        StockReservationRequest request,
        @Header(name = "messageId", required = false) byte[] messageIdHeader,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        String messageId = messageIdHeader != null ? new String(messageIdHeader, StandardCharsets.UTF_8) : null;

        try {
            if (messageId != null && isProcessed(messageId)) {
                log.debug("Stock reservation already processed: messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            if (request == null
                || request.getOrderId() == null || request.getOrderId().isBlank()
                || request.getProductId() == null
                || request.getQuantity() == null
                || request.getQuantity() <= 0) {
                log.warn("Ignore invalid stock-reservation-request: messageId={} request={} ({}:{}@{})",
                    messageId,
                    request,
                    topic, partition, offset
                );
                if (messageId != null) {
                    markAsProcessed(messageId);
                }
                ack.acknowledge();
                return;
            }

            log.info("Received stock-reservation-request: messageId={}, orderId={}, productId={}, quantity={} ({}:{}@{})",
                messageId,
                request.getOrderId(),
                request.getProductId(),
                request.getQuantity(),
                topic, partition, offset
            );

            boolean success = productService.reserveStock(
                request.getOrderId(),
                request.getProductId(),
                request.getQuantity()
            );

            StockReservationResponse response = StockReservationResponse.builder()
                .orderId(request.getOrderId())
                .productId(request.getProductId())
                .requestedQuantity(request.getQuantity())
                .reservedQuantity(success ? request.getQuantity() : 0)
                .success(success)
                .errorMessage(success ? null : "Insufficient stock")
                .timestamp(LocalDateTime.now())
                .build();

            productService.sendStockReservationResponse(response);

            if (messageId != null) {
                markAsProcessed(messageId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process stock-reservation-request: messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException("Failed to process stock reservation request", e);
        }
    }

    @KafkaListener(
        topics = "${spring.kafka.topics.stock-rollback-request:stock-rollback-request}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockRollbackRequest(
        StockRollbackRequest request,
        @Header(name = "messageId", required = false) byte[] messageIdHeader,
        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        Acknowledgment ack
    ) {
        String messageId = messageIdHeader != null ? new String(messageIdHeader, StandardCharsets.UTF_8) : null;

        try {
            if (messageId != null && isProcessed(messageId)) {
                log.debug("Stock rollback already processed: messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            if (request == null
                || request.getOrderId() == null || request.getOrderId().isBlank()
                || request.getProductId() == null
                || request.getQuantity() == null
                || request.getQuantity() <= 0) {
                log.warn("Ignore invalid stock-rollback-request: messageId={} request={} ({}:{}@{})",
                    messageId,
                    request,
                    topic, partition, offset
                );
                if (messageId != null) {
                    markAsProcessed(messageId);
                }
                ack.acknowledge();
                return;
            }

            log.info("Received stock-rollback-request: messageId={}, orderId={}, productId={}, quantity={} ({}:{}@{})",
                messageId,
                request.getOrderId(),
                request.getProductId(),
                request.getQuantity(),
                topic, partition, offset
            );

            productService.rollbackStock(request.getProductId(), request.getQuantity());

            if (messageId != null) {
                markAsProcessed(messageId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process stock-rollback-request: messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException("Failed to process stock rollback request", e);
        }
    }

    @KafkaListener(
        topics = "${spring.kafka.topics.flash-sale:flash-sale}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeFlashSaleEvent(
        FlashSaleEvent event,
        @Header(name = "messageId", required = false) String messageId,
        Acknowledgment ack
    ) {
        try {
            if (messageId != null && isProcessed(messageId)) {
                log.debug("Flash sale event already processed: messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            log.info("Received flash-sale event: messageId={}, type={}, flashSaleId={}, productId={}",
                messageId,
                event.getEventType(),
                event.getFlashSaleId(),
                event.getProductId()
            );

            productService.applyFlashSaleEvent(event);

            if (messageId != null) {
                markAsProcessed(messageId);
            }

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process flash-sale event: messageId={}, error={}", messageId, e.getMessage(), e);
            throw new RuntimeException("Failed to process flash-sale event", e);
        }
    }

    private boolean isProcessed(String messageId) {
        String key = IDEMPOTENCY_KEY_PREFIX + messageId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private void markAsProcessed(String messageId) {
        String key = IDEMPOTENCY_KEY_PREFIX + messageId;
        redisTemplate.opsForValue().set(key, "processed", TTL_HOURS, TimeUnit.HOURS);
    }
}
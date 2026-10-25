package com.tlcn.product_service.service;

import com.tlcn.product_service.client.OrderServiceClient;
import com.tlcn.product_service.exception.ResourceNotFoundException;
import com.tlcn.product_service.model.ProductView;
import com.tlcn.product_service.repository.ProductRepository;
import com.tlcn.product_service.repository.ProductViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductViewService {

    private final ProductViewRepository productViewRepository;
    private final ProductRepository productRepository;
    private final OrderServiceClient orderServiceClient;

    /**
     * Ghi một lượt xem. Cùng khách và cùng sản phẩm thì chỉ cập nhật viewedAt.
     */
    @Transactional
    public void recordView(String customerKeycloakId, Long productId) {
        if (customerKeycloakId == null || customerKeycloakId.isBlank() || productId == null) {
            throw new IllegalArgumentException("customer and product are required");
        }
        if (productRepository.findByIdAndIsDeletedFalse(productId).isEmpty()) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        productViewRepository.upsertView(customerKeycloakId, productId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<Long> getViewedProductIds(String customerKeycloakId) {
        if (customerKeycloakId == null || customerKeycloakId.isBlank()) {
            return List.of();
        }
        return productViewRepository.findByCustomerKeycloakIdOrderByViewedAtDesc(customerKeycloakId)
                .stream()
                .map(ProductView::getProductId)
                .toList();
    }

    /**
     * Sản phẩm khách đã mua, bỏ đơn và dòng đã hủy — cùng điều kiện verify-purchase.
     */
    public List<Long> getPurchasedProductIds(String customerKeycloakId) {
        return orderServiceClient.getPurchasedProductIds(customerKeycloakId);
    }
}

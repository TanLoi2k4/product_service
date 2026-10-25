package com.tlcn.product_service.service;

import com.tlcn.product_service.client.OrderServiceClient;
import com.tlcn.product_service.dto.ProductSoldCountDto;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import com.tlcn.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đồng bộ rating/soldCount từ PostgreSQL sang Elasticsearch lúc service khởi động.
 * soldCount của sản phẩm đang = 0 được backfill từ order-service (đơn hàng lịch sử).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSearchIndexReconciler implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductDocumentRepository productDocumentRepository;
    private final OrderServiceClient orderServiceClient;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            backfillSoldCounts();
            reconcileSearchIndex();
        } catch (Exception ex) {
            log.warn("Product search index reconcile skipped: {}", ex.getMessage());
        }
    }

    private void reconcileSearchIndex() {
        int updated = 0;
        for (Product product : productRepository.findAll()) {
            var documentOpt = productDocumentRepository.findById(product.getId());
            if (documentOpt.isEmpty()) {
                continue;
            }
            ProductDocument document = documentOpt.get();
            double dbRating = ratingOf(product.getRating());
            int dbSold = product.soldCountValue();
            boolean ratingChanged = Double.compare(dbRating, ratingOf(document.getRating())) != 0;
            boolean soldChanged = document.getSoldCount() == null || document.getSoldCount() != dbSold;
            if (!ratingChanged && !soldChanged) {
                continue;
            }
            document.setRating(dbRating);
            document.setSoldCount(dbSold);
            productDocumentRepository.save(document);
            updated++;
        }
        if (updated > 0) {
            log.info("Reconciled rating/soldCount on {} Elasticsearch product documents", updated);
        }
    }

    private void backfillSoldCounts() {
        List<ProductSoldCountDto> sales = orderServiceClient.getLifetimeProductSold();
        if (sales.isEmpty()) {
            return;
        }

        Map<Long, Integer> soldByProduct = new HashMap<>();
        for (ProductSoldCountDto row : sales) {
            if (row == null || row.getProductId() == null) {
                continue;
            }
            int quantity = row.getQuantity() == null ? 0 : row.getQuantity().intValue();
            if (quantity > 0) {
                soldByProduct.put(row.getProductId(), quantity);
            }
        }
        if (soldByProduct.isEmpty()) {
            return;
        }

        int updated = 0;
        for (Product product : productRepository.findAll()) {
            if (product.soldCountValue() > 0) {
                continue;
            }
            Integer sold = soldByProduct.get(product.getId());
            if (sold == null || sold <= 0) {
                continue;
            }
            product.setSoldCount(sold);
            productRepository.save(product);
            updated++;
        }
        if (updated > 0) {
            log.info("Backfilled soldCount for {} products from order-service", updated);
        }
    }

    private static double ratingOf(Double value) {
        return value == null ? 0.0 : value;
    }
}

package com.tlcn.product_service.service;

import com.tlcn.product_service.client.OrderServiceClient;
import com.tlcn.product_service.dto.CoPurchaseCountDto;
import com.tlcn.product_service.dto.ScoredProduct;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Hai nguồn điểm gợi ý, mỗi điểm đã chuẩn hóa về [0, 1] trong tập ứng viên.
 */
@Service
@RequiredArgsConstructor
public class RecommendationScoreService {

    static final int CONTENT_CANDIDATE_LIMIT = 50;

    private final ProductViewService productViewService;
    private final ProductDocumentRepository productDocumentRepository;
    private final OrderServiceClient orderServiceClient;

    /**
     * Sản phẩm khách đã xem hoặc đã mua. Xem gần nhất đứng trước.
     */
    public List<Long> seedProductIds(String customerKeycloakId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.addAll(productViewService.getViewedProductIds(customerKeycloakId));
        ids.addAll(productViewService.getPurchasedProductIds(customerKeycloakId));
        return List.copyOf(ids);
    }

    /**
     * Điểm nội dung: _score của more_like_this chia cho điểm cao nhất trong tập ứng viên.
     */
    public Map<Long, Double> contentScores(Collection<Long> seedProductIds) {
        List<ScoredProduct> hits = productDocumentRepository.findMoreLikeProducts(
                seedProductIds,
                CONTENT_CANDIDATE_LIMIT
        );
        Map<Long, Double> raw = new LinkedHashMap<>();
        for (ScoredProduct hit : hits) {
            if (hit.productId() == null) {
                continue;
            }
            raw.put(hit.productId(), hit.score());
        }
        return normalize(raw);
    }

    /**
     * Điểm cùng mua: với mỗi B, cộng số khách đã mua một sản phẩm gốc rồi mua B,
     * rồi chia cho số lớn nhất trong tập ứng viên.
     */
    public Map<Long, Double> coPurchaseScores(Collection<Long> seedProductIds) {
        Map<Long, Double> raw = new HashMap<>();
        for (CoPurchaseCountDto pair : orderServiceClient.getCoPurchases(seedProductIds)) {
            if (pair.getTargetProductId() == null || pair.getCustomerCount() == null) {
                continue;
            }
            raw.merge(pair.getTargetProductId(), pair.getCustomerCount().doubleValue(), Double::sum);
        }
        return normalize(raw);
    }

    private static Map<Long, Double> normalize(Map<Long, Double> raw) {
        double max = 0;
        for (double score : raw.values()) {
            if (score > max) {
                max = score;
            }
        }
        if (max <= 0) {
            return Map.of();
        }
        Map<Long, Double> normalized = new LinkedHashMap<>();
        for (Map.Entry<Long, Double> entry : raw.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / max);
        }
        return normalized;
    }
}

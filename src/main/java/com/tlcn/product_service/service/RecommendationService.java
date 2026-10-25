package com.tlcn.product_service.service;

import com.tlcn.product_service.dto.RecommendationResult;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    static final int CANDIDATE_LIMIT = 50;
    static final int RESULT_LIMIT = 8;
    static final double CONTENT_WEIGHT = 0.5;
    static final double CO_PURCHASE_WEIGHT = 0.3;
    static final double POPULARITY_WEIGHT = 0.2;

    private final RecommendationScoreService recommendationScoreService;
    private final ProductViewService productViewService;
    private final ProductDocumentRepository productDocumentRepository;
    private final ProductQueryService productQueryService;

    public RecommendationResult recommend(String customerKeycloakId) {
        List<Long> seeds = recommendationScoreService.seedProductIds(customerKeycloakId);
        if (seeds.isEmpty()) {
            return RecommendationResult.builder()
                    .personalized(false)
                    .products(List.of())
                    .bestSellers(catalog("soldCount"))
                    .topRated(catalog("rating"))
                    .build();
        }

        Map<Long, Double> content = topScores(
                recommendationScoreService.contentScores(seeds),
                CANDIDATE_LIMIT
        );
        Map<Long, Double> coPurchase = topScores(
                recommendationScoreService.coPurchaseScores(seeds),
                CANDIDATE_LIMIT
        );

        Set<Long> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(content.keySet());
        candidateIds.addAll(coPurchase.keySet());

        Map<Long, ProductDocument> documents = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            productDocumentRepository.findAllById(candidateIds)
                    .forEach(document -> documents.put(document.getId(), document));
        }

        int maxSold = 0;
        for (Long productId : candidateIds) {
            ProductDocument document = documents.get(productId);
            if (document != null) {
                maxSold = Math.max(maxSold, soldCountOf(document));
            }
        }

        Set<Long> purchased = new HashSet<>(productViewService.getPurchasedProductIds(customerKeycloakId));
        List<RankedProduct> ranked = new ArrayList<>();
        for (Long productId : candidateIds) {
            ProductDocument document = documents.get(productId);
            if (document == null) {
                continue;
            }
            double popularity = maxSold <= 0 ? 0 : (double) soldCountOf(document) / maxSold;
            double score = CONTENT_WEIGHT * content.getOrDefault(productId, 0.0)
                    + CO_PURCHASE_WEIGHT * coPurchase.getOrDefault(productId, 0.0)
                    + POPULARITY_WEIGHT * popularity;
            if (!eligible(document, purchased)) {
                continue;
            }
            ranked.add(new RankedProduct(document, score));
        }

        ranked.sort(Comparator.comparingDouble(RankedProduct::score).reversed());
        List<ProductDocument> products = ranked.stream()
                .limit(RESULT_LIMIT)
                .map(RankedProduct::document)
                .toList();

        return RecommendationResult.builder()
                .personalized(true)
                .products(products)
                .bestSellers(List.of())
                .topRated(List.of())
                .build();
    }

    private List<ProductDocument> catalog(String sortProperty) {
        return productQueryService.searchProducts(
                null,
                PageRequest.of(0, RESULT_LIMIT, Sort.by(Sort.Direction.DESC, sortProperty))
        ).getContent();
    }

    private static Map<Long, Double> topScores(Map<Long, Double> scores, int limit) {
        Map<Long, Double> top = new LinkedHashMap<>();
        scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        return top;
    }

    private static boolean eligible(ProductDocument document, Set<Long> purchased) {
        if (document.getId() == null || document.isDeleted() || purchased.contains(document.getId())) {
            return false;
        }
        Integer stock = document.getStock();
        return stock != null && stock > 0;
    }

    private static int soldCountOf(ProductDocument document) {
        return document.getSoldCount() == null ? 0 : document.getSoldCount();
    }

    private record RankedProduct(ProductDocument document, double score) {
    }
}

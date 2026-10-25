package com.tlcn.product_service.service;

import com.tlcn.product_service.dto.VendorRatingStats;
import com.tlcn.product_service.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorRatingService {

    private final ReviewRepository reviewRepository;

    public Map<String, VendorRatingStats> getRatingsForVendors(List<String> keycloakIds) {
        if (keycloakIds == null || keycloakIds.isEmpty()) {
            return Map.of();
        }

        List<String> ids = keycloakIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .map(String::trim)
            .distinct()
            .limit(100)
            .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<String, VendorRatingStats> result = new LinkedHashMap<>();
        for (Object[] row : reviewRepository.aggregateRatingsByVendorKeycloakIds(ids)) {
            VendorRatingStats stats = toStats(row);
            if (stats.getKeycloakId() != null) {
                result.put(stats.getKeycloakId(), stats);
            }
        }
        return result;
    }

    public List<VendorRatingStats> getTopRatedVendors(int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        return reviewRepository.findTopRatedVendors(PageRequest.of(0, limit))
            .stream()
            .map(this::toStats)
            .filter(stats -> stats.getKeycloakId() != null)
            .collect(Collectors.toList());
    }

    private VendorRatingStats toStats(Object[] row) {
        String keycloakId = row[0] != null ? String.valueOf(row[0]) : null;
        double rating = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
        long reviewCount = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        return VendorRatingStats.builder()
            .keycloakId(keycloakId)
            .rating(Math.round(rating * 10.0) / 10.0)
            .reviewCount(reviewCount)
            .build();
    }
}

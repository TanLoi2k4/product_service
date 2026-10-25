package com.tlcn.product_service.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.query.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Elasticsearch từ chối sort nếu field chưa có mapping (index cũ chưa có soldCount).
 * Gắn unmapped_type để sort vẫn chạy; missing được coi như 0.
 */
final class ElasticsearchSorts {

    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "soldCount", "rating", "price", "stock", "id", "version"
    );

    private ElasticsearchSorts() {
    }

    static Pageable withUnmappedNumericSort(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (NUMERIC_FIELDS.contains(order.getProperty())) {
                orders.add(new Order(order.getDirection(), order.getProperty()).withUnmappedType("long"));
            } else {
                orders.add(order);
            }
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    static boolean hasProperty(Pageable pageable, String property) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return false;
        }
        return pageable.getSort().getOrderFor(property) != null;
    }

    static Pageable withoutProperty(Pageable pageable, String property) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }
        List<Sort.Order> remaining = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            if (!property.equals(order.getProperty())) {
                remaining.add(order);
            }
        }
        Sort sort = remaining.isEmpty() ? Sort.unsorted() : Sort.by(remaining);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}

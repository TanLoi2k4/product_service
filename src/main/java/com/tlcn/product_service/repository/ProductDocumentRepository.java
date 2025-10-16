package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, Long> {
    Page<ProductDocument> findByNameContainingOrDescriptionContainingOrCategoryContaining(
            String name, String description, String category, Pageable pageable);

    Page<ProductDocument> findByKeycloakIdAndNameContainingOrKeycloakIdAndDescriptionContainingOrKeycloakIdAndCategoryContaining(
            String keycloakId1, String name,
            String keycloakId2, String description,
            String keycloakId3, String category,
            Pageable pageable);
}
package com.tlcn.product_service.config;

import com.tlcn.product_service.model.ProductDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import org.springframework.boot.context.event.ApplicationReadyEvent;

@Configuration
@Slf4j
public class ElasticsearchConfig {

    private static final String PRODUCT_INDEX = "products";

    private final ObjectProvider<ElasticsearchOperations> operationsProvider;

    public ElasticsearchConfig(ObjectProvider<ElasticsearchOperations> operationsProvider) {
        this.operationsProvider = operationsProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeElasticsearchIndex() {
        ElasticsearchOperations elasticsearchOperations = operationsProvider.getIfAvailable();
        if (elasticsearchOperations == null) {
            log.warn("ElasticsearchOperations not available; skipping index initialization");
            return;
        }

        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(PRODUCT_INDEX));

        try {
            if (!indexOps.exists()) {
                log.info("Creating Elasticsearch index: {}", PRODUCT_INDEX);
                indexOps.create();
                indexOps.putMapping(ProductDocument.class);
                log.info("Successfully created and mapped Elasticsearch index: {}", PRODUCT_INDEX);
            } else {
                log.info("Elasticsearch index '{}' already exists, updating mapping for new fields", PRODUCT_INDEX);
                try {
                    indexOps.putMapping(ProductDocument.class);
                } catch (Exception mappingError) {
                    log.warn("Could not update Elasticsearch mapping (soldCount may still be missing): {}",
                            mappingError.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize Elasticsearch index: {}", e.getMessage(), e);
            // Don't fail application startup, just log the error
        }
    }
}
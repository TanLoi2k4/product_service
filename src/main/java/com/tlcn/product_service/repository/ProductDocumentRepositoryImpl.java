package com.tlcn.product_service.repository;

import co.elastic.clients.elasticsearch._types.query_dsl.Like;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.tlcn.product_service.dto.ScoredProduct;
import com.tlcn.product_service.model.ProductDocument;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.Collection;
import java.util.List;

public class ProductDocumentRepositoryImpl implements ProductDocumentRepositoryCustom {

    private final ElasticsearchOperations operations;

    public ProductDocumentRepositoryImpl(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public List<ScoredProduct> findMoreLikeProducts(Collection<Long> productIds, int limit) {
        if (productIds == null || productIds.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<String> ids = productIds.stream().map(String::valueOf).distinct().toList();
        List<Like> likes = ids.stream()
                .map(id -> Like.of(like -> like.document(doc -> doc.id(id))))
                .toList();

        Query query = Query.of(q -> q.bool(b -> b
                .must(m -> m.moreLikeThis(mlt -> mlt
                        .fields("name", "description")
                        .like(likes)
                        .minTermFreq(1)
                        .minDocFreq(1)))
                .filter(f -> f.term(t -> t.field("isDeleted").value(false)))
                .mustNot(n -> n.ids(i -> i.values(ids)))
        ));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withMaxResults(limit)
                .build();

        SearchHits<ProductDocument> hits = operations.search(nativeQuery, ProductDocument.class);
        return hits.getSearchHits().stream()
                .map(ProductDocumentRepositoryImpl::toScoredProduct)
                .toList();
    }

    private static ScoredProduct toScoredProduct(SearchHit<ProductDocument> hit) {
        return new ScoredProduct(hit.getContent().getId(), hit.getScore());
    }
}

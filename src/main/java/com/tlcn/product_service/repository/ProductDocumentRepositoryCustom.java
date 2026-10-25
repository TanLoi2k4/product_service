package com.tlcn.product_service.repository;

import com.tlcn.product_service.dto.ScoredProduct;

import java.util.Collection;
import java.util.List;

public interface ProductDocumentRepositoryCustom {

    /**
     * more_like_this trên name và description theo nhiều sản phẩm gốc.
     * Không lọc danh mục. Không gồm chính các id gốc.
     */
    List<ScoredProduct> findMoreLikeProducts(Collection<Long> productIds, int limit);
}

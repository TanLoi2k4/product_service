package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, Long>, ProductDocumentRepositoryCustom {

    // --- FULL-TEXT SEARCH (Sử dụng @Query cho các logic phức tạp như Weighting & Fuzziness) ---
    // Lưu ý: "name"/"category" là điều kiện BẮT BUỘC khớp (must) để tránh việc chỉ cần từ khóa
    // xuất hiện đâu đó trong "description" (vd: gợi ý phối đồ) là đã trả về sản phẩm không liên quan.
    // "description" chỉ được dùng để TĂNG ĐIỂM xếp hạng (should), không đủ để tự nó tạo ra kết quả.
    // operator "and": mọi token trong từ khóa phải khớp (tìm "áo polo" không được ra "áo gió").
    // Mặc định multi_match là OR nên chỉ cần chữ "áo" là đủ để lẫn áo gió, áo thun, áo sơ mi.

    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchAll(String query, Pageable pageable);

    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"keycloakId\": \"?0\"}}, {\"term\": {\"isDeleted\": false}}, " +
           "{\"multi_match\": {\"query\": \"?1\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?1\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchByVendor(String keycloakId, String query, Pageable pageable);

    // Customer-facing vendor storefront: active products (not deleted), including out-of-stock

    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"keycloakId\": \"?0\"}}, {\"term\": {\"isDeleted\": false}}, " +
           "{\"multi_match\": {\"query\": \"?1\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?1\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchActiveByVendorForCustomer(String keycloakId, String query, Pageable pageable);

    Page<ProductDocument> findByKeycloakIdAndIsDeletedFalseAndStockGreaterThan(String keycloakId, Integer stock, Pageable pageable);

       Page<ProductDocument> findByKeycloakIdAndIsDeletedFalse(String keycloakId, Pageable pageable);

    Page<ProductDocument> findByKeycloakIdAndIsDeletedTrue(String keycloakId, Pageable pageable);

    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"isFlashSale\": true}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchFlashSaleWithQuery(String query, Pageable pageable);

    // --- FILTER & RANGE (Tận dụng Query Methods cực clean) ---

    Page<ProductDocument> findByIsDeletedFalse(Pageable pageable);

    Page<ProductDocument> findByIsDeletedTrue(Pageable pageable);

    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": true}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchDeleted(String query, Pageable pageable);

    Page<ProductDocument> findByCategoryAndIsDeletedFalse(String category, Pageable pageable);

       Page<ProductDocument> findByCategoryAndStockGreaterThanAndIsDeletedFalse(String category, Integer stock, Pageable pageable);

    Page<ProductDocument> findByPriceBetweenAndIsDeletedFalse(Double min, Double max, Pageable pageable);

       Page<ProductDocument> findByPriceBetweenAndStockGreaterThanAndIsDeletedFalse(Double min, Double max, Integer stock, Pageable pageable);

    Page<ProductDocument> findByStockGreaterThanAndIsDeletedFalse(Integer stock, Pageable pageable);

    Page<ProductDocument> findByIsFlashSaleTrueAndIsDeletedFalse(Pageable pageable);

       Page<ProductDocument> findByIsFlashSaleTrueAndStockGreaterThanAndIsDeletedFalse(Integer stock, Pageable pageable);

    Page<ProductDocument> findByIsFlashSaleTrueAndPriceBetweenAndIsDeletedFalse(Double min, Double max, Pageable pageable);

    Page<ProductDocument> findByIsFlashSaleTrueAndCategoryAndPriceBetweenAndIsDeletedFalse(
            String category, Double min, Double max, Pageable pageable);

    // --- SEARCH NÂNG CAO (Multiple Filters) ---

    @Query("{\"bool\": {\"must\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"category\": \"?0\"}}, " +
           "{\"range\": {\"price\": {\"gte\": ?1, \"lte\": ?2}}}]}}")
    Page<ProductDocument> searchWithFilters(String category, Double minPrice, Double maxPrice, Pageable pageable);

    // Kết hợp từ khóa (name/category, fuzzy) + khoảng giá — tránh trả về sản phẩm không khớp
    // từ khóa chỉ vì nằm trong khoảng giá (ví dụ: tìm "quần" trong 100k-300k không được ra "áo").
    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"filter\": [{\"range\": {\"price\": {\"gte\": ?1, \"lte\": ?2}}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchWithQueryAndPriceRange(String query, Double minPrice, Double maxPrice, Pageable pageable);

    // Kết hợp từ khóa + danh mục (chính xác) + khoảng giá
    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"category\": \"?1\"}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"filter\": [{\"range\": {\"price\": {\"gte\": ?2, \"lte\": ?3}}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchWithQueryCategoryAndPriceRange(
            String query, String category, Double minPrice, Double maxPrice, Pageable pageable);

    // Flash Sale + từ khóa + khoảng giá (dùng cho trang Flash Sale riêng)
    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"isFlashSale\": true}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"filter\": [{\"range\": {\"price\": {\"gte\": ?1, \"lte\": ?2}}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchFlashSaleWithQueryAndPriceRange(
            String query, Double minPrice, Double maxPrice, Pageable pageable);

    // Flash Sale + từ khóa + danh mục (chính xác) + khoảng giá
    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"isFlashSale\": true}}, " +
           "{\"term\": {\"category\": \"?1\"}}, " +
           "{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"name^3\", \"category^2\"], \"operator\": \"and\", \"fuzziness\": \"AUTO\"}}], " +
           "\"filter\": [{\"range\": {\"price\": {\"gte\": ?2, \"lte\": ?3}}}], " +
           "\"should\": [{\"match\": {\"description\": {\"query\": \"?0\", \"boost\": 1}}}]}}")
    Page<ProductDocument> searchFlashSaleWithQueryCategoryAndPriceRange(
            String query, String category, Double minPrice, Double maxPrice, Pageable pageable);

    // --- AUTOCOMPLETE & SIMILAR ---

    // Tìm kiếm gợi ý theo prefix
    Page<ProductDocument> findByNameStartingWithAndIsDeletedFalse(String prefix, Pageable pageable);

       Page<ProductDocument> findByNameStartingWithAndStockGreaterThanAndIsDeletedFalse(String prefix, Integer stock, Pageable pageable);

    // more_like_this chỉ xếp hạng trong CÙNG danh mục. Không lọc category sẽ trả về
    // sản phẩm lệch loại (sách → áo) vì mô tả tiếng Việt dùng nhiều từ chung.
    @Query("{\"bool\": {" +
           "\"must\": [{\"more_like_this\": {\"fields\": [\"name\", \"description\"], \"like\": [{\"_id\": \"?0\"}], \"min_term_freq\": 1, \"min_doc_freq\": 1}}], " +
           "\"filter\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"category\": \"?1\"}}], " +
           "\"must_not\": [{\"ids\": {\"values\": [\"?0\"]}}]}}")
    Page<ProductDocument> findSimilarProducts(Long productId, String category, Pageable pageable);

    @Query("{\"bool\": {" +
           "\"must\": [{\"term\": {\"isDeleted\": false}}, {\"term\": {\"category\": \"?0\"}}], " +
           "\"must_not\": [{\"ids\": {\"values\": [\"?1\"]}}]}}")
    Page<ProductDocument> findByCategoryExcludingId(String category, Long productId, Pageable pageable);
}
package com.tlcn.product_service.service;

import com.tlcn.product_service.exception.ResourceNotFoundException;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.ProductDocument;
import com.tlcn.product_service.repository.ProductDocumentRepository;
import com.tlcn.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductDocumentRepository productDocumentRepository;
    private final ProductRepository productRepository;

    /**
     * Tìm kiếm sản phẩm tổng quát (Full-text search)
     */
    public Page<ProductDocument> searchProducts(String query, Pageable pageable) {
        log.debug("Querying ES for products with term: {}", query);
        return executeSearch(pageable, safe -> {
            if (query == null || query.trim().isEmpty()) {
                return productDocumentRepository.findByIsDeletedFalse(safe);
            }
            return productDocumentRepository.searchAll(query, safe);
        });
    }

    /**
     * Lấy danh sách sản phẩm của một Vendor cụ thể
     */
    public Page<ProductDocument> getProductsByVendor(String keycloakId, String query, Pageable pageable) {
        return executeSearch(pageable, safe -> {
            if (query == null || query.trim().isEmpty()) {
                return productDocumentRepository.findByKeycloakIdAndIsDeletedFalse(keycloakId, safe);
            }
            return productDocumentRepository.searchByVendor(keycloakId, query, safe);
        });
    }

    /**
     * Lấy danh sách sản phẩm của vendor cho Customer (không xóa, bao gồm cả hết hàng)
     */
    public Page<ProductDocument> getProductsByVendorForCustomer(String vendorKeycloakId, String query, Pageable pageable) {
        return executeSearch(pageable, safe -> {
            if (query == null || query.trim().isEmpty()) {
                return productDocumentRepository.findByKeycloakIdAndIsDeletedFalse(vendorKeycloakId, safe);
            }
            return productDocumentRepository.searchActiveByVendorForCustomer(vendorKeycloakId, query, safe);
        });
    }

    /**
     * Lấy danh sách sản phẩm đã xóa của vendor (phục vụ vendor dashboard/restore)
     */
    public Page<ProductDocument> getDeletedProductsByVendor(String vendorKeycloakId, Pageable pageable) {
        return executeSearch(pageable, safe ->
                productDocumentRepository.findByKeycloakIdAndIsDeletedTrue(vendorKeycloakId, safe));
    }

    /**
     * Tìm kiếm sản phẩm đang Flash Sale
     */
    public Page<ProductDocument> getFlashSaleProducts(String query, Pageable pageable) {
        return executeSearch(pageable, safe -> {
            if (query == null || query.trim().isEmpty()) {
                return productDocumentRepository.findByIsFlashSaleTrueAndIsDeletedFalse(safe);
            }
            return productDocumentRepository.searchFlashSaleWithQuery(query, safe);
        });
    }

    /**
     * Tìm kiếm sản phẩm Flash Sale kết hợp từ khóa, danh mục và khoảng giá
     * (phục vụ trang Flash Sale riêng biệt với sidebar Danh mục / Khoảng giá).
     */
    public Page<ProductDocument> getFlashSaleProducts(String query, String category, Double min, Double max, Pageable pageable) {
        double effectiveMin = (min != null) ? min : 0.0;
        double effectiveMax = (max != null) ? max : Double.MAX_VALUE;
        if (effectiveMin > effectiveMax) {
            throw new IllegalArgumentException("min must be <= max");
        }

        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty();

        return executeSearch(pageable, safe -> {
            if (hasQuery && hasCategory) {
                return productDocumentRepository.searchFlashSaleWithQueryCategoryAndPriceRange(
                    query, category, effectiveMin, effectiveMax, safe
                );
            }
            if (hasQuery) {
                return productDocumentRepository.searchFlashSaleWithQueryAndPriceRange(query, effectiveMin, effectiveMax, safe);
            }
            if (hasCategory) {
                return productDocumentRepository.findByIsFlashSaleTrueAndCategoryAndPriceBetweenAndIsDeletedFalse(
                    category, effectiveMin, effectiveMax, safe
                );
            }
            return productDocumentRepository.findByIsFlashSaleTrueAndPriceBetweenAndIsDeletedFalse(
                effectiveMin, effectiveMax, safe
            );
        });
    }

    /**
     * Lọc sản phẩm theo danh mục
     */
    public Page<ProductDocument> getProductsByCategory(String category, Pageable pageable) {
        return executeSearch(pageable, safe ->
                productDocumentRepository.findByCategoryAndIsDeletedFalse(category, safe));
    }

    /**
     * Lọc theo khoảng giá
     */
    public Page<ProductDocument> getProductsByPriceRange(Double min, Double max, Pageable pageable) {
        return executeSearch(pageable, safe ->
                productDocumentRepository.findByPriceBetweenAndIsDeletedFalse(min, max, safe));
    }

    /**
     * Search nâng cao kết hợp từ khóa (Query), Category, Price và Stock.
     * Khi có từ khóa, kết quả BẮT BUỘC khớp tên/danh mục (không chỉ nằm trong khoảng giá),
     * tránh tình trạng tìm "quần" + lọc giá lại trả về "áo" chỉ vì cùng khoảng giá.
     */
    public Page<ProductDocument> advancedSearch(String query, String category, Double min, Double max, Pageable pageable) {
        double effectiveMin = (min != null) ? min : 0.0;
        double effectiveMax = (max != null) ? max : Double.MAX_VALUE;
        if (effectiveMin > effectiveMax) {
            throw new IllegalArgumentException("min must be <= max");
        }

        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty();

        return executeSearch(pageable, safe -> {
            if (hasQuery && hasCategory) {
                return productDocumentRepository.searchWithQueryCategoryAndPriceRange(
                    query, category, effectiveMin, effectiveMax, safe
                );
            }
            if (hasQuery) {
                return productDocumentRepository.searchWithQueryAndPriceRange(query, effectiveMin, effectiveMax, safe);
            }
            if (hasCategory) {
                return productDocumentRepository.searchWithFilters(category, effectiveMin, effectiveMax, safe);
            }
            return productDocumentRepository.findByPriceBetweenAndIsDeletedFalse(
                effectiveMin,
                effectiveMax,
                safe
            );
        });
    }

    /**
     * Danh sách sản phẩm cho admin (có thể lọc sản phẩm đã xóa)
     */
    public Page<ProductDocument> adminSearchProducts(String query, boolean deletedOnly, Pageable pageable) {
        if (deletedOnly) {
            return executeSearch(pageable, safe -> {
                if (query == null || query.trim().isEmpty()) {
                    return productDocumentRepository.findByIsDeletedTrue(safe);
                }
                return productDocumentRepository.searchDeleted(query, safe);
            });
        }
        return searchProducts(query, pageable);
    }

    /**
     * Lấy chi tiết sản phẩm từ Database (admin — bao gồm sản phẩm đã xóa)
     */
    public Product getProductByIdForAdmin(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    /**
     * Lấy chi tiết sản phẩm từ Database (để đảm bảo dữ liệu chính xác nhất cho trang Detail)
     */
    public Product getProductById(Long id) {
        return productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    /**
     * Gợi ý sản phẩm liên quan: bắt buộc cùng danh mục với sản phẩm đang xem.
     * more_like_this chỉ dùng để xếp hạng trong danh mục đó; nếu không đủ kết quả
     * thì lấy các sản phẩm cùng danh mục (trừ chính sản phẩm hiện tại).
     */
    public Page<ProductDocument> getSimilarProducts(Long id, Pageable pageable) {
        Product product = getProductById(id);
        String categorySlug = product.getCategory() != null ? product.getCategory().getSlug() : null;
        if (categorySlug == null || categorySlug.isBlank()) {
            return Page.empty(pageable);
        }

        return executeSearch(pageable, safe -> {
            Page<ProductDocument> similar = productDocumentRepository.findSimilarProducts(id, categorySlug, safe);
            if (!similar.isEmpty()) {
                return similar;
            }
            return productDocumentRepository.findByCategoryExcludingId(categorySlug, id, safe);
        });
    }

    /**
     * Gợi ý tên sản phẩm khi người dùng đang gõ (Autocomplete)
     */
    public Page<ProductDocument> autocomplete(String prefix, Pageable pageable) {
        return executeSearch(pageable, safe ->
                productDocumentRepository.findByNameStartingWithAndIsDeletedFalse(prefix, safe));
    }

    /**
     * Sort numeric fields with unmapped_type so an old ES index (thiếu soldCount) không 500.
     * Nếu vẫn lỗi, bỏ sort soldCount rồi thử lại.
     */
    private Page<ProductDocument> executeSearch(
            Pageable pageable,
            Function<Pageable, Page<ProductDocument>> search
    ) {
        Pageable safe = ElasticsearchSorts.withUnmappedNumericSort(pageable);
        try {
            return search.apply(safe);
        } catch (RuntimeException ex) {
            if (!ElasticsearchSorts.hasProperty(pageable, "soldCount")) {
                throw ex;
            }
            log.warn("Elasticsearch sort by soldCount failed, retrying without it: {}", ex.getMessage());
            return search.apply(ElasticsearchSorts.withoutProperty(safe, "soldCount"));
        }
    }
}
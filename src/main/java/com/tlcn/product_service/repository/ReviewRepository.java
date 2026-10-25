package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Tìm tất cả các review của một sản phẩm, có phân trang.
     */
    Page<Review> findByProductId(Long productId, Pageable pageable);

    @Query("""
        SELECT r FROM Review r
        WHERE r.productId = :productId
          AND r.rating = :rating
        """)
    Page<Review> findByProductIdAndRating(
            @Param("productId") Long productId,
            @Param("rating") Integer rating,
            Pageable pageable);

    /**
     * Kiểm tra review đã tồn tại cho một sản phẩm trong một đơn hàng cụ thể chưa.
     */
    Optional<Review> findByProductIdAndOrderId(Long productId, Long orderId);

    Optional<Review> findByProductIdAndCustomerId(Long productId, String customerId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.productId = :productId")
    Double calculateAverageRatingByProductId(@Param("productId") Long productId);

    @Query("""
        SELECT p.keycloakId, AVG(r.rating), COUNT(r)
        FROM Review r
        JOIN Product p ON r.productId = p.id
        WHERE p.keycloakId IN :keycloakIds
        GROUP BY p.keycloakId
        """)
    List<Object[]> aggregateRatingsByVendorKeycloakIds(@Param("keycloakIds") List<String> keycloakIds);

    @Query("""
        SELECT p.keycloakId, AVG(r.rating), COUNT(r)
        FROM Review r
        JOIN Product p ON r.productId = p.id
        WHERE p.keycloakId IS NOT NULL AND TRIM(p.keycloakId) <> ''
        GROUP BY p.keycloakId
        ORDER BY AVG(r.rating) DESC, COUNT(r) DESC
        """)
    List<Object[]> findTopRatedVendors(org.springframework.data.domain.Pageable pageable);

    @Query("""
        SELECT r FROM Review r, Product p
        WHERE r.productId = p.id
        AND p.keycloakId = :vendorKeycloakId
        AND p.isDeleted = false
        """)
    Page<Review> findAllByVendorKeycloakId(
            @Param("vendorKeycloakId") String vendorKeycloakId,
            Pageable pageable);

    @Query("""
        SELECT r FROM Review r, Product p
        WHERE r.productId = p.id
        AND p.keycloakId = :vendorKeycloakId
        AND p.isDeleted = false
        AND (
            LOWER(r.comment) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(r.customerId) LIKE LOWER(CONCAT('%', :query, '%'))
        )
        """)
    Page<Review> searchByVendorKeycloakId(
            @Param("vendorKeycloakId") String vendorKeycloakId,
            @Param("query") String query,
            Pageable pageable);

    @Query(
            value = """
                SELECT r.productId AS productId,
                       p.name AS productName,
                       p.imageUrl AS productImageUrl,
                       AVG(r.rating) AS averageRating,
                       COUNT(r) AS reviewCount,
                       SUM(CASE WHEN r.vendorReply IS NULL OR TRIM(r.vendorReply) = '' THEN 1 ELSE 0 END) AS unrepliedCount,
                       MAX(r.createdAt) AS latestReviewAt
                FROM Review r, Product p
                WHERE r.productId = p.id
                  AND p.keycloakId = :vendorKeycloakId
                  AND p.isDeleted = false
                  AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
                GROUP BY r.productId, p.name, p.imageUrl
                ORDER BY MAX(r.createdAt) DESC
                """,
            countQuery = """
                SELECT COUNT(DISTINCT r.productId)
                FROM Review r, Product p
                WHERE r.productId = p.id
                  AND p.keycloakId = :vendorKeycloakId
                  AND p.isDeleted = false
                  AND (:query IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')))
                """
    )
    Page<VendorProductReviewSummaryView> findProductReviewSummariesByVendor(
            @Param("vendorKeycloakId") String vendorKeycloakId,
            @Param("query") String query,
            Pageable pageable);

    interface VendorProductReviewSummaryView {
        Long getProductId();
        String getProductName();
        String getProductImageUrl();
        Number getAverageRating();
        Number getReviewCount();
        Number getUnrepliedCount();
        Instant getLatestReviewAt();
    }
}
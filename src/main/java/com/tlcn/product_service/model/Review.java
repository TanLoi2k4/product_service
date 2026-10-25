package com.tlcn.product_service.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

/**
 * Đại diện cho một đánh giá (review) của khách hàng về một sản phẩm.
 */
@Entity
@Table(name = "reviews", uniqueConstraints = {
        // Đảm bảo một khách hàng chỉ có thể đánh giá một sản phẩm một lần cho mỗi đơn hàng.
        @UniqueConstraint(columnNames = {"productId", "orderId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    /**
     * Khóa chính, tự động tăng.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID của sản phẩm được đánh giá. Không thể cập nhật sau khi tạo.
     */
    @Column(nullable = false, updatable = false)
    private Long productId;

    /**
     * ID đơn hàng mà khách đã mua sản phẩm. Không thể cập nhật sau khi tạo.
     */
    @Column(updatable = false)
    private Long orderId;

    /**
     * ID của khách hàng (Keycloak ID). Không thể cập nhật sau khi tạo.
     */
    @Column(nullable = false, updatable = false)
    private String customerId;

    /**
     * Điểm đánh giá, từ 1 đến 5 sao.
     */
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    @Column(nullable = false)
    private Integer rating;

    /**
     * Nội dung bình luận chi tiết của khách hàng.
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

    /**
     * Danh sách các URL hình ảnh đính kèm.
     * Sử dụng @ElementCollection để lưu danh sách chuỗi vào một bảng phụ (review_images).
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "image_url", columnDefinition = "TEXT")
    private List<String> images;

    /**
     * Nội dung phản hồi từ nhà bán hàng (vendor).
     */
    @Column(columnDefinition = "TEXT")
    private String vendorReply;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}

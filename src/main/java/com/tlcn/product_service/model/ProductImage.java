package com.tlcn.product_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;
    private String publicId;

    @Column(name = "is_main", nullable = false)
    private boolean main = false;

    /** Ảnh phẳng, thấy rõ trang phục — đầu vào của thử đồ. Mỗi sản phẩm chỉ một ảnh. */
    @Column(name = "garment_flat", nullable = false, columnDefinition = "boolean default false")
    private boolean garmentFlat = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
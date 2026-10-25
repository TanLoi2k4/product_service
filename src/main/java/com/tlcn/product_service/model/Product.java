package com.tlcn.product_service.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;
    private Double price;
    private Integer stock;

    /** Số lượng đã bán (net: đặt hàng trừ hủy/hoàn). Dùng để sort "Bán chạy". */
    @Column(nullable = false, columnDefinition = "integer default 0")
    @Builder.Default
    private Integer soldCount = 0;

    private Double rating = 0.0;
    private String keycloakId;
    private String publicId;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;
    
    @Version
    private Long version;

    private Double originalPriceBeforeFs; 

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isFlashSale = false; 

    private Instant flashSaleEndTime;

    public int soldCountValue() {
        return soldCount == null ? 0 : soldCount;
    }

    public void increaseSoldCount(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.soldCount = soldCountValue() + quantity;
    }

    public void decreaseSoldCount(int quantity) {
        if (quantity <= 0) {
            return;
        }
        this.soldCount = Math.max(0, soldCountValue() - quantity);
    }
}
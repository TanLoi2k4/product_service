package com.tlcn.product_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một dòng cho mỗi cặp khách–sản phẩm. Xem lại thì cập nhật viewedAt.
 */
@Entity
@Table(
        name = "product_view",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_view_customer_product",
                columnNames = {"customer_keycloak_id", "product_id"}
        ),
        indexes = @Index(name = "idx_product_view_customer", columnList = "customer_keycloak_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_keycloak_id", nullable = false, updatable = false)
    private String customerKeycloakId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "viewed_at", nullable = false)
    private Instant viewedAt;
}

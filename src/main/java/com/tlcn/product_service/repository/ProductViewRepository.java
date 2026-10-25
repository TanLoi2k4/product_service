package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.ProductView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductViewRepository extends JpaRepository<ProductView, Long> {

    List<ProductView> findByCustomerKeycloakIdOrderByViewedAtDesc(String customerKeycloakId);

    @Modifying
    @Query(value = """
            INSERT INTO product_view (customer_keycloak_id, product_id, viewed_at)
            VALUES (:customerKeycloakId, :productId, :viewedAt)
            ON CONFLICT (customer_keycloak_id, product_id)
            DO UPDATE SET viewed_at = EXCLUDED.viewed_at
            """, nativeQuery = true)
    void upsertView(
            @Param("customerKeycloakId") String customerKeycloakId,
            @Param("productId") Long productId,
            @Param("viewedAt") Instant viewedAt
    );
}

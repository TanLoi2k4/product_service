package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    Optional<ProductImage> findByIdAndProduct_KeycloakId(Long id, String keycloakId);
}

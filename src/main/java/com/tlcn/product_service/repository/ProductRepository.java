package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.Product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByKeycloakIdAndIsDeletedFalse(String keycloakId, Pageable pageable);
    Optional<Product> findByIdAndIsDeletedFalse(Long id);
    Page<Product> findByKeycloakIdAndIsDeletedTrue(String keycloakId, Pageable pageable);
    List<Product> findByIsDeletedFalse();
}
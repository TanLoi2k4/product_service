package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Page<Product> findByKeycloakId(String keycloakId, Pageable pageable);
}
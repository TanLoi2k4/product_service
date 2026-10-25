package com.tlcn.product_service.repository;

import com.tlcn.product_service.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByActiveTrueOrderByNameAsc();

    Optional<Category> findByIdAndActiveTrue(Long id);

    Optional<Category> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);
}
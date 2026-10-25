package com.tlcn.product_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    /** Danh mục thời trang được phép thử đồ ảo. */
    @Column(name = "virtual_try_on", nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean virtualTryOn = false;

    @OneToMany(mappedBy = "category")
    @Builder.Default
    private List<Product> products = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void normalizeSlug() {
        if (slug == null || slug.isBlank()) {
            slug = toSlug(name);
        } else {
            slug = toSlug(slug);
        }
    }

    private String toSlug(String value) {
        if (value == null) {
            return null;
        }
        return value.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}

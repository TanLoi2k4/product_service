package com.tlcn.product_service.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    private String description;
    private String category;
    private Double price;
    private Integer stock;
    private String keycloakId; 
    private String imageUrl;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isDeleted = false;
    
    @Version
    private Long version;

    private Double originalPriceBeforeFs; 

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isFlashSale = false; 

    private Instant flashSaleEndTime;
}
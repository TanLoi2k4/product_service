package com.tlcn.product_service.model;

import lombok.*;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String category;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Text)
    private String imageUrl;

    @Field(type = FieldType.Text) 
    private String keycloakId;

    @Field(type = FieldType.Boolean)
    private boolean isDeleted = false;

    @Field(type = FieldType.Double)
    private Double originalPriceBeforeFs; 

    @Field(type = FieldType.Boolean)
    private boolean isFlashSale = false; 

    @Field(type = FieldType.Date) 
    private Instant flashSaleEndTime;
}
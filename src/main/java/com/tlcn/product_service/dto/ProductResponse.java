package com.tlcn.product_service.dto;

import com.tlcn.product_service.model.Product;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String description;
    private CategoryResponse category;
    private Double price;
    private Integer stock;
    private Integer soldCount;
    private Double rating;
    private String keycloakId;
    private String publicId;
    private String imageUrl;
    private List<ProductImageResponse> images;
    private boolean deleted;
    private Long version;
    private Double originalPriceBeforeFs;
    private boolean flashSale;
    private Instant flashSaleEndTime;
    /** Danh mục bật thử đồ và sản phẩm có đúng một ảnh phẳng. */
    private boolean tryOnEnabled;
    private String garmentImageUrl;

    public static ProductResponse fromProduct(Product product) {
        if (product == null) {
            return null;
        }

        List<ProductImageResponse> images = buildImageResponses(product);
        String garmentImageUrl = images.stream()
                .filter(ProductImageResponse::isGarmentFlat)
                .map(ProductImageResponse::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
        boolean categoryAllowsTryOn = product.getCategory() != null && product.getCategory().isVirtualTryOn();

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .category(CategoryResponse.fromEntity(product.getCategory()))
                .price(product.getPrice())
                .stock(product.getStock())
                .soldCount(product.soldCountValue())
                .rating(product.getRating())
                .keycloakId(product.getKeycloakId())
                .publicId(product.getPublicId())
                .imageUrl(product.getImageUrl())
                .images(images)
                .deleted(product.isDeleted())
                .version(product.getVersion())
                .originalPriceBeforeFs(product.getOriginalPriceBeforeFs())
                .flashSale(product.isFlashSale())
                .flashSaleEndTime(product.getFlashSaleEndTime())
                .garmentImageUrl(garmentImageUrl)
                .tryOnEnabled(categoryAllowsTryOn && garmentImageUrl != null)
                .build();
    }

    /**
     * Gallery must include the product-level cover photo and mark exactly one image as main.
     * Older products stored the cover only on Product.imageUrl, while extras live in
     * {@code images} — sometimes with {@code main=true} as well.
     */
    static List<ProductImageResponse> buildImageResponses(Product product) {
        List<ProductImageResponse> images = new ArrayList<>();
        String mainUrl = product.getImageUrl() == null ? "" : product.getImageUrl().trim();

        if (product.getImages() != null) {
            for (var image : product.getImages()) {
                ProductImageResponse mapped = ProductImageResponse.fromEntity(image);
                if (mapped == null || mapped.getImageUrl() == null || mapped.getImageUrl().isBlank()) {
                    continue;
                }
                mapped.setImageUrl(mapped.getImageUrl().trim());
                boolean duplicate = images.stream().anyMatch(img -> mapped.getImageUrl().equals(img.getImageUrl()));
                if (!duplicate) {
                    images.add(mapped);
                }
            }
        }

        if (!mainUrl.isBlank()) {
            boolean present = images.stream().anyMatch(img -> mainUrl.equals(img.getImageUrl()));
            if (!present) {
                images.add(0, ProductImageResponse.builder()
                        .imageUrl(mainUrl)
                        .publicId(product.getPublicId())
                        .main(true)
                        .build());
            }
        }

        int mainIndex = -1;
        if (!mainUrl.isBlank()) {
            for (int i = 0; i < images.size(); i++) {
                if (mainUrl.equals(images.get(i).getImageUrl())) {
                    mainIndex = i;
                    break;
                }
            }
        }
        if (mainIndex < 0) {
            for (int i = 0; i < images.size(); i++) {
                if (images.get(i).isMain()) {
                    mainIndex = i;
                    break;
                }
            }
        }
        if (mainIndex < 0 && !images.isEmpty()) {
            mainIndex = 0;
        }

        for (int i = 0; i < images.size(); i++) {
            images.get(i).setMain(i == mainIndex);
        }

        if (mainIndex > 0) {
            ProductImageResponse main = images.remove(mainIndex);
            images.add(0, main);
        }
        return images;
    }
}

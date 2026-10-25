package com.tlcn.product_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.tlcn.product_service.client.CustomerServiceClient;
import com.tlcn.product_service.client.OrderServiceClient;
import com.tlcn.product_service.dto.ReviewRequest;
import com.tlcn.product_service.dto.ReviewResponse;
import com.tlcn.product_service.dto.VendorProductReviewSummary;
import com.tlcn.product_service.dto.VendorReplyRequest;
import com.tlcn.product_service.dto.VendorReviewResponse;
import com.tlcn.product_service.exception.BusinessException;
import com.tlcn.product_service.exception.PermissionDeniedException;
import com.tlcn.product_service.exception.ResourceNotFoundException;
import com.tlcn.product_service.exception.ReviewAlreadyExistsException;
import com.tlcn.product_service.model.Product;
import com.tlcn.product_service.model.Review;
import com.tlcn.product_service.repository.ProductRepository;
import com.tlcn.product_service.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderServiceClient orderServiceClient;
    private final CustomerServiceClient customerServiceClient;
    private final Cloudinary cloudinary;
    private final ProductRepository productRepository;
    private final ProductCommandService productCommandService;

    @Value("${cloudinary.review-folder:reviews}")
    private String reviewFolder;

    /**
     * Lấy danh sách review của một sản phẩm (public).
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByProductId(Long productId, Integer rating, Pageable pageable) {
        log.info("Fetching reviews for product ID: {} rating: {}", productId, rating);
        Page<Review> reviewPage = rating == null
                ? reviewRepository.findByProductId(productId, pageable)
                : reviewRepository.findByProductIdAndRating(productId, rating, pageable);
        Map<String, String> customerNames = resolveCustomerNames(reviewPage.getContent());
        return reviewPage.map(review -> toReviewResponse(review, customerNames.get(review.getCustomerId())));
    }

    /**
     * Tạo một review mới (dành cho CUSTOMER).
     */
    @Transactional
    public ReviewResponse createReview(ReviewRequest reviewRequest, String customerId, String authorizationHeader) {
        return createReview(reviewRequest, customerId, authorizationHeader, null);
    }

    @Transactional
    public ReviewResponse createReview(ReviewRequest reviewRequest, String customerId, String authorizationHeader, List<MultipartFile> images) {
        log.info("Attempting to create review for product ID: {} by customer ID: {}", reviewRequest.getProductId(), customerId);

        boolean hasPurchased = orderServiceClient.verifyPurchase(customerId, reviewRequest.getProductId(), authorizationHeader);
        if (!hasPurchased) {
            throw new PermissionDeniedException("Customer has not purchased this product in the specified order.");
        }

        reviewRepository.findByProductIdAndCustomerId(reviewRequest.getProductId(), customerId)
                .ifPresent(r -> {
                    throw new ReviewAlreadyExistsException("A review for this product already exists.");
                });
        if (reviewRequest.getOrderId() != null) {
            reviewRepository.findByProductIdAndOrderId(reviewRequest.getProductId(), reviewRequest.getOrderId())
                    .ifPresent(r -> {
                        throw new ReviewAlreadyExistsException("A review for this product already exists.");
                    });
        }

        List<String> uploadedImageUrls = new ArrayList<>();
        List<String> uploadedPublicIds = new ArrayList<>();
        try {
            if (images != null) {
                for (MultipartFile image : images) {
                    if (image == null || image.isEmpty()) {
                        continue;
                    }

                    validateImage(image);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> uploadResult = (Map<String, Object>) cloudinary.uploader().upload(image.getBytes(), ObjectUtils.asMap("folder", reviewFolder));
                    uploadedImageUrls.add((String) uploadResult.get("secure_url"));
                    uploadedPublicIds.add((String) uploadResult.get("public_id"));
                }
            }

            List<String> finalImages = new ArrayList<>();
            if (reviewRequest.getImages() != null) {
                finalImages.addAll(reviewRequest.getImages());
            }
            finalImages.addAll(uploadedImageUrls);

            Review review = toReview(reviewRequest, customerId, finalImages.isEmpty() ? null : finalImages);
            Review savedReview = reviewRepository.save(review);
            updateProductAverageRating(reviewRequest.getProductId());
            log.info("Successfully created review with ID: {}", savedReview.getId());
            return toReviewResponse(savedReview, customerServiceClient.getDisplayName(customerId));
        } catch (IOException e) {
            cleanupCloudinaryUploads(uploadedPublicIds);
            throw new BusinessException("Failed to upload review images: " + e.getMessage());
        } catch (Exception e) {
            cleanupCloudinaryUploads(uploadedPublicIds);
            throw new BusinessException("Failed to create review: " + e.getMessage());
        }
    }

    /**
     * Cập nhật review (chỉ chủ sở hữu review mới có quyền).
     */
    @Transactional
    public ReviewResponse updateReview(Long reviewId, ReviewRequest reviewRequest, String customerId) {
        log.info("Attempting to update review ID: {} by customer ID: {}", reviewId, customerId);
        Review review = getReviewById(reviewId);

        if (!Objects.equals(review.getCustomerId(), customerId)) {
            throw new PermissionDeniedException("You are not authorized to update this review.");
        }

        review.setRating(reviewRequest.getRating());
        review.setComment(reviewRequest.getComment());
        review.setImages(reviewRequest.getImages());

        Review updatedReview = reviewRepository.save(review);
        updateProductAverageRating(review.getProductId());
        log.info("Successfully updated review ID: {}", updatedReview.getId());

        return toReviewResponse(updatedReview, customerServiceClient.getDisplayName(customerId));
    }

    /**
     * Xóa một review (chủ sở hữu hoặc ADMIN).
     */
    @Transactional
    public void deleteReview(Long reviewId, String userId, boolean isAdmin) {
        log.info("Attempting to delete review ID: {} by user ID: {}", reviewId, userId);
        Review review = getReviewById(reviewId);

        if (!isAdmin && !Objects.equals(review.getCustomerId(), userId)) {
            throw new PermissionDeniedException("You are not authorized to delete this review.");
        }

        reviewRepository.delete(review);
        updateProductAverageRating(review.getProductId());
        log.info("Successfully deleted review ID: {}", reviewId);

    }

    /**
     * Vendor phản hồi một review.
     */
    @Transactional
    public ReviewResponse addVendorReply(Long reviewId, VendorReplyRequest replyRequest, String vendorId) {
        log.info("Vendor {} attempting to reply to review ID: {}", vendorId, reviewId);
        Review review = getReviewById(reviewId);

        Product product = productRepository.findById(review.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for review: " + reviewId));

        if (!Objects.equals(vendorId, product.getKeycloakId())) {
            throw new PermissionDeniedException("You are not the owner of the product associated with this review.");
        }

        review.setVendorReply(replyRequest.getReply());
        Review updatedReview = reviewRepository.save(review);
        log.info("Vendor {} successfully replied to review ID: {}", vendorId, reviewId);

        return toReviewResponse(updatedReview, customerServiceClient.getDisplayName(review.getCustomerId()));
    }

    /**
     * Lấy danh sách review của tất cả sản phẩm thuộc vendor (dashboard vendor).
     */
    @Transactional(readOnly = true)
    public Page<VendorReviewResponse> getReviewsByVendor(String vendorKeycloakId, String query, Pageable pageable) {
        log.info("Fetching reviews for vendor: {}", vendorKeycloakId);
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();

        Page<Review> reviews = normalizedQuery == null
                ? reviewRepository.findAllByVendorKeycloakId(vendorKeycloakId, pageable)
                : reviewRepository.searchByVendorKeycloakId(vendorKeycloakId, normalizedQuery, pageable);

        Map<String, String> customerNames = resolveCustomerNames(reviews.getContent());
        return reviews.map(review -> toVendorReviewResponse(review, customerNames.get(review.getCustomerId())));
    }

    /**
     * Gom đánh giá theo từng sản phẩm của vendor (dashboard).
     */
    @Transactional(readOnly = true)
    public Page<VendorProductReviewSummary> getProductReviewSummariesByVendor(
            String vendorKeycloakId,
            String query,
            Pageable pageable
    ) {
        log.info("Fetching review products for vendor: {}", vendorKeycloakId);
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
        Page<ReviewRepository.VendorProductReviewSummaryView> summaries = reviewRepository.findProductReviewSummariesByVendor(
                vendorKeycloakId,
                normalizedQuery,
                pageable
        );
        return summaries.map(this::toProductReviewSummary);
    }

    private VendorProductReviewSummary toProductReviewSummary(ReviewRepository.VendorProductReviewSummaryView view) {
        return VendorProductReviewSummary.builder()
                .productId(view.getProductId())
                .productName(view.getProductName() != null ? view.getProductName() : "Sản phẩm không xác định")
                .productImageUrl(view.getProductImageUrl())
                .averageRating(roundRating(view.getAverageRating()))
                .reviewCount(toLong(view.getReviewCount()))
                .unrepliedCount(toLong(view.getUnrepliedCount()))
                .latestReviewAt(view.getLatestReviewAt())
                .build();
    }

    private static double roundRating(Number rating) {
        if (rating == null) {
            return 0.0;
        }
        return Math.round(rating.doubleValue() * 10.0) / 10.0;
    }

    private static long toLong(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private VendorReviewResponse toVendorReviewResponse(Review review, String customerName) {
        Product product = productRepository.findById(review.getProductId()).orElse(null);

        return VendorReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .productName(product != null ? product.getName() : "Unknown product")
                .productImageUrl(product != null ? product.getImageUrl() : null)
                .customerId(review.getCustomerId())
                .customerName(customerName != null && !customerName.isBlank()
                        ? customerName
                        : CustomerServiceClient.DEFAULT_DISPLAY_NAME)
                .rating(review.getRating())
                .comment(review.getComment())
                .images(review.getImages())
                .vendorReply(review.getVendorReply())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private Review toReview(ReviewRequest request, String customerId, List<String> images) {
        if (request == null) {
            return null;
        }

        return Review.builder()
                .productId(request.getProductId())
                .orderId(request.getOrderId())
                .customerId(customerId)
                .rating(request.getRating())
                .comment(request.getComment())
                .images(images)
                .build();
    }

    private ReviewResponse toReviewResponse(Review review, String customerName) {
        if (review == null) {
            return null;
        }

        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProductId())
                .orderId(review.getOrderId())
                .customerId(review.getCustomerId())
                .customerName(customerName != null && !customerName.isBlank()
                        ? customerName
                        : CustomerServiceClient.DEFAULT_DISPLAY_NAME)
                .rating(review.getRating())
                .comment(review.getComment())
                .images(review.getImages())
                .vendorReply(review.getVendorReply())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }

    private Map<String, String> resolveCustomerNames(List<Review> reviews) {
        return customerServiceClient.getDisplayNames(
                reviews.stream()
                        .map(Review::getCustomerId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(Collectors.toList())
        );
    }

    private void validateImage(MultipartFile image) {
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("Each review image must be at most 5MB");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException("Only image files are allowed for review uploads");
        }
    }

    private void cleanupCloudinaryUploads(List<String> publicIds) {
        for (String publicId : publicIds) {
            if (publicId != null && !publicId.isBlank()) {
                try {
                    cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                } catch (Exception ignored) {
                    log.warn("Failed to cleanup Cloudinary image with public ID: {}", publicId);
                }
            }
        }
    }

    private void updateProductAverageRating(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return;
        }

        Double averageRating = reviewRepository.calculateAverageRatingByProductId(productId);
        product.setRating(averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : 0.0);
        Product saved = productRepository.save(product);
        // Card/list đọc Elasticsearch — phải sync rating, không chỉ Postgres.
        productCommandService.syncToSearchIndexAfterCommit(saved);
        log.info("Updated average rating for product {}: {}", productId, saved.getRating());
    }

    private Review getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found with id: " + reviewId));
    }
}
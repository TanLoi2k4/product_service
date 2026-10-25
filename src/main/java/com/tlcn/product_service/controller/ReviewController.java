package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.dto.ReviewRequest;
import com.tlcn.product_service.dto.ReviewResponse;
import com.tlcn.product_service.dto.VendorReplyRequest;
import com.tlcn.product_service.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{productId}/reviews")
    public ResponseEntity<ResponseDTO<Page<ReviewResponse>>> getReviewsByProductId(
            @PathVariable Long productId,
            @RequestParam(name = "rating", required = false) Integer rating,
            @RequestParam(name = "stars", required = false) Integer stars,
            Pageable pageable) {
        Integer requested = stars != null ? stars : rating;
        Integer normalizedRating = (requested != null && requested >= 1 && requested <= 5) ? requested : null;
        Page<ReviewResponse> reviews = reviewService.getReviewsByProductId(productId, normalizedRating, pageable);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Fetched reviews successfully", reviews));
    }

    @PostMapping(value = "/reviews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<ReviewResponse>> createReviewWithImages(
            @RequestPart("reviewRequest") @Valid ReviewRequest reviewRequest,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            Authentication authentication,
            HttpServletRequest request) {
        String customerId = authentication.getName();
        String authorizationHeader = request.getHeader("Authorization");
        ReviewResponse createdReview = reviewService.createReview(reviewRequest, customerId, authorizationHeader, images);
        return new ResponseEntity<>(new ResponseDTO<>(true, "Review created successfully", createdReview), HttpStatus.CREATED);
    }

    @PutMapping("/reviews/{reviewId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ResponseDTO<ReviewResponse>> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequest reviewRequest,
            Authentication authentication) {
        String customerId = authentication.getName();
        ReviewResponse updatedReview = reviewService.updateReview(reviewId, reviewRequest, customerId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Review updated successfully", updatedReview));
    }

    @DeleteMapping("/reviews/{reviewId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ResponseDTO<Void>> deleteReview(
            @PathVariable Long reviewId,
            Authentication authentication) {
        String userId = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPER_ADMIN"));
        reviewService.deleteReview(reviewId, userId, isAdmin);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Review deleted successfully", null));
    }

    @PostMapping("/reviews/{reviewId}/reply")
    @PreAuthorize("hasRole('VENDOR')")
    public ResponseEntity<ResponseDTO<ReviewResponse>> addVendorReply(
            @PathVariable Long reviewId,
            @Valid @RequestBody VendorReplyRequest replyRequest,
            Authentication authentication) {
        String vendorId = authentication.getName();
        ReviewResponse reviewWithReply = reviewService.addVendorReply(reviewId, replyRequest, vendorId);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor reply added successfully", reviewWithReply));
    }
}

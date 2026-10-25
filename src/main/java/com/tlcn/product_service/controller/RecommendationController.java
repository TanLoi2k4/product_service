package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.RecommendationResult;
import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/me")
    public ResponseEntity<ResponseDTO<RecommendationResult>> me(Authentication authentication) {
        return ResponseEntity.ok(new ResponseDTO<>(
                true,
                "Recommendations loaded",
                recommendationService.recommend(authentication.getName())
        ));
    }
}

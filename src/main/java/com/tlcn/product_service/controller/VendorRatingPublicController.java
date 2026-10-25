package com.tlcn.product_service.controller;

import com.tlcn.product_service.dto.ResponseDTO;
import com.tlcn.product_service.dto.VendorRatingStats;
import com.tlcn.product_service.service.VendorRatingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/products/vendors")
@RequiredArgsConstructor
public class VendorRatingPublicController {

    private final VendorRatingService vendorRatingService;

    @GetMapping("/ratings")
    public ResponseEntity<ResponseDTO<Map<String, VendorRatingStats>>> getVendorRatings(
        @RequestParam("ids") String idsParam
    ) {
        List<String> ids = Arrays.stream(idsParam.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());

        Map<String, VendorRatingStats> ratings = vendorRatingService.getRatingsForVendors(ids);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Vendor ratings loaded", ratings));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<ResponseDTO<List<VendorRatingStats>>> getTopRatedVendors(
        @RequestParam(defaultValue = "6") int size
    ) {
        List<VendorRatingStats> topRated = vendorRatingService.getTopRatedVendors(size);
        return ResponseEntity.ok(new ResponseDTO<>(true, "Top rated vendors loaded", topRated));
    }
}

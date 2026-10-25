package com.tlcn.product_service.client;

import com.tlcn.product_service.dto.CoPurchaseCountDto;
import com.tlcn.product_service.dto.ProductSoldCountDto;
import com.tlcn.product_service.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.order-service.base-url:http://localhost:8084}")
    private String orderServiceBaseUrl;

    public boolean verifyPurchase(String customerId, Long productId, String authorizationHeader) {
        if (customerId == null || customerId.isBlank() || productId == null) {
            return false;
        }

        String url = orderServiceBaseUrl + "/orders/verify-purchase?productId=" + productId;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.hasText(authorizationHeader)) {
                headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<ResponseDTO<Boolean>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<ResponseDTO<Boolean>>() {}
            );
            ResponseDTO<Boolean> body = response.getBody();
            return body != null && Boolean.TRUE.equals(body.getData());
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Số lượng đã bán (đơn chưa hủy/hoàn) theo từng sản phẩm — dùng để backfill soldCount.
     */
    public List<ProductSoldCountDto> getLifetimeProductSold() {
        String url = orderServiceBaseUrl + "/orders/internal/product-sold-counts";
        try {
            ResponseEntity<ResponseDTO<List<ProductSoldCountDto>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ResponseDTO<List<ProductSoldCountDto>>>() {}
            );
            ResponseDTO<List<ProductSoldCountDto>> body = response.getBody();
            if (body == null || body.getData() == null) {
                return List.of();
            }
            return body.getData();
        } catch (Exception ex) {
            log.warn("Could not fetch lifetime product sold counts from order-service: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Sản phẩm khách đã mua. order-service bỏ đơn hủy, dòng hủy và dòng hoàn.
     */
    public List<Long> getPurchasedProductIds(String customerKeycloakId) {
        if (customerKeycloakId == null || customerKeycloakId.isBlank()) {
            return List.of();
        }
        String url = orderServiceBaseUrl + "/orders/internal/customers/" + customerKeycloakId + "/purchased-product-ids";
        try {
            ResponseEntity<ResponseDTO<List<Long>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ResponseDTO<List<Long>>>() {}
            );
            ResponseDTO<List<Long>> body = response.getBody();
            if (body == null || body.getData() == null) {
                return List.of();
            }
            return body.getData();
        } catch (Exception ex) {
            log.warn("Could not fetch purchased product ids from order-service: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Số khách đã mua từng sản phẩm gốc rồi mới mua sản phẩm kia.
     */
    public List<CoPurchaseCountDto> getCoPurchases(Collection<Long> sourceProductIds) {
        if (sourceProductIds == null || sourceProductIds.isEmpty()) {
            return List.of();
        }
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(orderServiceBaseUrl + "/orders/internal/co-purchases");
        for (Long productId : sourceProductIds) {
            if (productId != null) {
                builder.queryParam("productIds", productId);
            }
        }
        if (builder.build().getQueryParams().isEmpty()) {
            return List.of();
        }
        try {
            ResponseEntity<ResponseDTO<List<CoPurchaseCountDto>>> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ResponseDTO<List<CoPurchaseCountDto>>>() {}
            );
            ResponseDTO<List<CoPurchaseCountDto>> body = response.getBody();
            if (body == null || body.getData() == null) {
                return List.of();
            }
            return body.getData();
        } catch (Exception ex) {
            log.warn("Could not fetch co-purchases from order-service: {}", ex.getMessage());
            return List.of();
        }
    }
}

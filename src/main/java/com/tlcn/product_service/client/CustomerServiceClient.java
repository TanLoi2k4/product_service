package com.tlcn.product_service.client;

import com.tlcn.product_service.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceClient {

    public static final String DEFAULT_DISPLAY_NAME = "Khách hàng";

    private final RestTemplate restTemplate;

    @Value("${services.customer-service.base-url:http://localhost:8083}")
    private String customerServiceBaseUrl;

    public Map<String, String> getDisplayNames(Collection<String> keycloakIds) {
        if (keycloakIds == null || keycloakIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> names = new HashMap<>();
        keycloakIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .forEach(id -> names.put(id, getDisplayName(id)));
        return names;
    }

    public String getDisplayName(String keycloakId) {
        CustomerInternalProfile profile = getProfile(keycloakId);
        if (profile == null || profile.displayName() == null || profile.displayName().isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return profile.displayName().trim();
    }

    private CustomerInternalProfile getProfile(String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            return null;
        }
        try {
            ResponseEntity<ResponseDTO<CustomerInternalProfile>> response = restTemplate.exchange(
                    customerServiceBaseUrl + "/customers/internal/{keycloakId}",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ResponseDTO<CustomerInternalProfile>>() {},
                    keycloakId
            );
            ResponseDTO<CustomerInternalProfile> body = response.getBody();
            if (body == null || !body.isSuccess()) {
                return null;
            }
            return body.getData();
        } catch (RestClientException ex) {
            log.warn("Could not fetch customer profile for keycloakId={}: {}", keycloakId, ex.getMessage());
            return null;
        }
    }

    private record CustomerInternalProfile(String keycloakId, String displayName, String phone) {}
}

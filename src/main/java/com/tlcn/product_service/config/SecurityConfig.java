package com.tlcn.product_service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //1 Cấu hình JwtAuthenticationConverter với KeycloakConverter
        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(new KeycloakConverter());

        http
            .csrf(csrf -> csrf.disable())

            //2 Cấu hình quyền truy cập (Authorization rules)
            .authorizeHttpRequests(auth -> auth
                // Public product browsing endpoints
                .requestMatchers(HttpMethod.GET,
                    "/categories",
                    "/categories/**",
                    "/products",
                    "/products/*",
                    "/products/*/similar",
                    "/products/category/**",
                    "/products/flash-sale",
                    "/products/autocomplete",
                    "/products/price-range",
                    "/products/advanced",
                    "/products/*/reviews",
                    "/products/vendors/ratings",
                    "/products/vendors/top-rated"
                ).permitAll()

                // Browse products by vendor (customer storefront) — POST body, not GET
                .requestMatchers(HttpMethod.POST, "/products/vendors").permitAll()

                // Admin product & category management endpoints
                .requestMatchers("/products/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                .requestMatchers(HttpMethod.POST, "/products/*/views").authenticated()

                // Review creation/update/delete/reply require auth and role checks in controller
                .requestMatchers(HttpMethod.POST, "/products/reviews", "/products/reviews/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/products/reviews/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/products/reviews/**").authenticated()

                // Vendor-only product management endpoints
                .requestMatchers("/products/vendor/**").hasRole("VENDOR")

                // All other product endpoints require authentication
                .requestMatchers("/products/**").authenticated()

                .requestMatchers(HttpMethod.GET, "/recommendations/me").authenticated()

                .anyRequest().authenticated()
            )

            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))

            )
            
            // 5. Set session management to stateless (Crucial for JWT/REST API)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 6. CRITICAL FIX: Handle exceptions to return 401 JSON instead of searching for a static page
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            
            // 7. Add custom JWT blacklist filter
            .addFilterBefore(new JwtBlacklistFilter(redisTemplate), BasicAuthenticationFilter.class);
            
        return http.build();
    }
}
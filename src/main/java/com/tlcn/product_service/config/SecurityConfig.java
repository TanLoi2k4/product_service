package com.tlcn.product_service.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
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
                // Public endpoints (cho người dùng xem sản phẩm)
                .requestMatchers(
                    "/api/products/{id}",
                    "/api/products/search",
                    "/api/products/vendor_id",
                    "/api/products/vendor_id/search",
                    "/actuator/**"
                ).permitAll()

                // Các endpoint còn lại yêu cầu authentication
                // Role check sẽ thực hiện bằng @PreAuthorize trong Controller
                .requestMatchers("/api/products/**").authenticated()

                // Mọi request khác ngoài /api/products/** cũng yêu cầu xác thực
                .anyRequest().authenticated()
            )

            //3 Cấu hình Keycloak JWT Resource Server
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
            )

            //4 Stateless session (chuẩn REST API)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            //5 Exception Handling (trả 401 thay vì redirect)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )

            //2 Filter chặn token trong blacklist (logout)
            .addFilterBefore(new JwtBlacklistFilter(redisTemplate), BasicAuthenticationFilter.class);

        return http.build();
    }
}
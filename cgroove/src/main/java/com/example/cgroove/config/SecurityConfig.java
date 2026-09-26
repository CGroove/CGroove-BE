package com.example.cgroove.config;

import com.example.cgroove.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // CSRF 비활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 설정
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션 사용 안 함 (Stateless)
                )
                .authorizeHttpRequests(auth -> auth // URL별 권한 설정
                        .requestMatchers( // 인증 없이 접근 가능
                                "/auth/login",
                                "/auth/signup",
                                "/auth/refresh", // Access Token 만료 상태에서 호출되므로 인증 없이 허용
                                "/auth/logout",
                                "/api/uploads/**",
                                "/uploads/**",
                                "/swagger-ui/**",
                                "/actuator/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated() // 나머지는 인증 필요
                )
                .addFilterBefore( // JWT 필터 추가 (UsernamePasswordAuthenticationFilter 앞에)
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            String jsonResponse = String.format(
                                    "{\"message\":\"%s\",\"data\":null}",
                                    "인증이 필요합니다"
                            );
                            response.getWriter().write(jsonResponse);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            String jsonResponse = String.format(
                                    "{\"message\":\"%s\",\"data\":null}",
                                    "권한이 없습니다"
                            );
                            response.getWriter().write(jsonResponse);
                        })
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
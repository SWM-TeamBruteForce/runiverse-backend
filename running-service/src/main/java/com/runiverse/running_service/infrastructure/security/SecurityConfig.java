package com.runiverse.running_service.infrastructure.security;

import com.runiverse.running_service.presentation.common.security.JwtAccessDeniedHandler;
import com.runiverse.running_service.presentation.common.security.JwtAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
// @PreAuthorize 사용처는 현재 없다. 지우면 나중에 애노테이션을 붙여도 조용히 무시돼
// 인가가 걸린 것처럼 보이는 무증상 우회가 되므로 남겨둔다. (나중에 사용가능성 있음)
@EnableMethodSecurity
public class SecurityConfig {

    // 토큰 없이 접근 할 수 있는 경로
    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/signup",
            "/auth/login",
            "/auth/refresh",
            "/auth/oauth/**",
            "/auth/email/**",
            "/users/nickname/availability"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder accessTokenDecoder, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers(RegexRequestMatcher.regexMatcher(
                                HttpMethod.GET,
                                "/users/[0-9a-fA-F-]{36}/profile-image")).permitAll() // uuid일때만 jwt 검증 생략
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(accessTokenDecoder))
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }

    // cors 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(corsProperties.allowedMethods());
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}

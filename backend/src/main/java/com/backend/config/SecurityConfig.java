package com.backend.config;

import com.backend.exception.ApiError;
import com.backend.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Configuration de Spring Security.
 * - CSRF désactivé (API stateless JWT).
 * - CORS activé pour autoriser les origines frontend (dont Angular 4200).
 * - Session STATELESS.
 * - Filtre JWT inséré avant UsernamePasswordAuthenticationFilter.
 * - AuthenticationEntryPoint (401) et AccessDeniedHandler (403) personnalisés.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS
            .cors(Customizer.withDefaults())
            // Désactiver CSRF (API REST stateless)
            .csrf(AbstractHttpConfigurer::disable)

            // Session STATELESS
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Autorisation des routes
            .authorizeHttpRequests(auth -> auth
                // Endpoints publics d'authentification
                .requestMatchers("POST", "/auth/register").permitAll()
                .requestMatchers("POST", "/auth/login").permitAll()
                .requestMatchers("POST", "/auth/refresh").permitAll()
                // Endpoints publics
                .requestMatchers("/public/**").permitAll()
                // Swagger/OpenAPI (développement)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Administration
                .requestMatchers("/admin/**").hasRole("ADMINISTRATEUR")
                // Agents
                .requestMatchers("/agent/**").hasRole("AGENT")
                // Tout le reste nécessite une authentification (inclut /auth/logout)
                .anyRequest().authenticated()
            )

            // Handlers d'erreur personnalisés
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )

            // Filtre JWT avant le filtre username/password standard
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Handler 401 : token absent, expiré ou invalide.
     * Écrit un ApiError en JSON.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.core.AuthenticationException authException) -> {
            ApiError error = new ApiError(401, "UNAUTHORIZED",
                    "Authentification requise : jeton absent, expiré ou invalide.",
                    request.getRequestURI());
            writeJsonError(response, 401, error);
        };
    }

    /**
     * Handler 403 : authentifié mais rôle insuffisant.
     * Écrit un ApiError en JSON.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            ApiError error = new ApiError(403, "FORBIDDEN",
                    "Accès refusé : rôle ou droits insuffisants.",
                    request.getRequestURI());
            writeJsonError(response, 403, error);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://127.0.0.1:4200",
                "http://localhost:3000",
                "http://localhost:5500",
                "http://127.0.0.1:5500"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "Link", "X-Total-Count"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeJsonError(HttpServletResponse response, int status, ApiError error) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {

        }
    }
}
package com.executionos.config;

import com.executionos.security.JwtAuthenticationFilter;
import com.executionos.security.RateLimitFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // ------------------------------------------------------------------
    // Chain 1 (evaluated first): Swagger UI + OpenAPI docs.
    //
    // These are static HTML/JSON pages a person opens directly in a
    // browser — a browser never attaches a JWT Bearer header to a plain
    // page navigation, so gating them behind the same stateless-JWT rule
    // as the API (hasRole("ADMIN")) would make them permanently
    // unreachable: the page itself would 403 before Swagger's UI ever
    // got a chance to render an "Authorize" button.
    //
    // HTTP Basic gives a real browser login prompt for exactly these two
    // path patterns, decoupled from the JWT flow used everywhere else.
    // ------------------------------------------------------------------
    @Bean
    @Order(1)
    SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .securityMatcher("/swagger-ui/**", "/v3/api-docs/**", "/actuator/prometheus")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(basic -> {})
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    UserDetailsService swaggerUserDetailsService(
            PasswordEncoder encoder,
            @Value("${executionos.swagger.username:admin}") String username,
            @Value("${executionos.swagger.password}") String rawPassword) {
        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(User.withUsername(username)
                .password(encoder.encode(rawPassword))
                .roles("SWAGGER")
                .build());
        return manager;
    }

    // ------------------------------------------------------------------
    // Chain 2: everything else — the stateless JWT-secured REST API,
    // unchanged in behavior from before except that swagger/api-docs
    // paths are no longer matched here at all (chain 1 claims them
    // first), so their old permitAll()/hasRole() entry is gone rather
    // than dead code.
    // ------------------------------------------------------------------
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, RateLimitFilter rateLimitFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/skillforge/auth/register-organization", "/api/v1/skillforge/auth/login",
                                "/api/v1/skillforge/auth/forgot-password", "/api/v1/skillforge/auth/reset-password",
                                "/api/v1/skillforge/auth/google", "/api/v1/skillforge/auth/refresh",
                                "/api/v1/skillforge/auth/verify-email").permitAll()
                        .requestMatchers("/api/v1/skillforge/demo/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/skillforge/catalog/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/skillforge/organizations/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/skillforge/questions/*/full", "/api/v1/skillforge/questions/import/csv").hasAnyRole("ADMIN", "TRAINER", "EVALUATOR")
                        .requestMatchers("/api/v1/skillforge/candidate/link/validate").permitAll()
                        .requestMatchers("/api/v1/skillforge/candidate/attempts/**").permitAll()
                        // Everything else under /skillforge is staff tooling (org
                        // structure, question bank, assessment authoring/publishing,
                        // candidate roster management, reports). A CANDIDATE-role
                        // token should never be able to call any of these, even
                        // though the frontend never shows them the UI for it.
                        .requestMatchers(
                                "/api/v1/skillforge/departments/**",
                                "/api/v1/skillforge/batches/**",
                                "/api/v1/skillforge/users/**",
                                "/api/v1/skillforge/trainers/**",
                                "/api/v1/skillforge/candidates/**",
                                "/api/v1/skillforge/questions/**",
                                "/api/v1/skillforge/assessments/**",
                                "/api/v1/skillforge/trainer/**",
                                "/api/v1/skillforge/reports/**",
                                "/api/v1/skillforge/health-dashboard",
                                "/api/v1/skillforge/search",
                                "/api/v1/skillforge/exports",
                                "/api/v1/skillforge/webhooks")
                                .hasAnyRole("ADMIN", "TRAINER", "EVALUATOR")
                        .requestMatchers("/actuator/health", "/actuator/info", "/ws/**").permitAll()
                        .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${executionos.cors-origins}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

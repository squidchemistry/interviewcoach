package com.aicoach.common.security;

import com.aicoach.common.config.AppProperties;
import com.aicoach.common.error.ErrorCode;
import com.aicoach.common.error.ProblemDetailFactory;
import com.aicoach.common.error.ProblemDetailWriter;
import com.aicoach.common.ratelimit.RateLimitFilter;
import com.aicoach.common.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security: stateless JWT bearer authentication, authenticated-by-default API, admin-only
 * {@code /api/v1/admin/**}, CORS whitelist, security headers, rate limiting, and problem+json for 401/403.
 * <p>
 * CSRF tokens are unnecessary: API calls authenticate with an {@code Authorization} header that browsers never
 * attach automatically. The only cookie (refresh token) is {@code SameSite=Strict}, path-scoped to the auth
 * endpoints, and those endpoints additionally verify the {@code Origin} header.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";
    private static final String DOCS_CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self'; frame-ancestors 'none'; base-uri 'self'";

    /** Swagger UI needs to load its own scripts and styles, so it gets a relaxed (but still same-origin) CSP. */
    @Bean
    @Order(1)
    SecurityFilterChain docsSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
            ProblemDetailWriter problemWriter, JwtAuthenticationConverter jwtAuthenticationConverter,
            RateLimiter rateLimiter, AppProperties properties) throws Exception {
        AuthenticationEntryPoint entryPoint = problemEntryPoint(problemWriter);
        http.cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                        .contentTypeOptions(Customizer.withDefaults()))
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .addFilterAfter(new RateLimitFilter(rateLimiter, properties.rateLimit(), problemWriter),
                        BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Actuator is served only on the management port, which is never published outside
                        // the container network (docker-compose / platform private networking).
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // async dispatches (SSE completion) re-enter the chain without a token; the original request
                        // was already authenticated
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/reports/*", "/api/v1/billing/plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/razorpay").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/companies",
                                "/api/v1/auth/providers", "/api/v1/auth/google", "/api/v1/auth/google/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler((request, response, ex) -> problemWriter.write(response,
                                ProblemDetailFactory.create(ErrorCode.FORBIDDEN,
                                        "You do not have permission to perform this action", request.getRequestURI()))));
        return http.build();
    }

    /**
     * Bearer tokens are ignored on the auth endpoints: an expired access token sent along with
     * {@code POST /auth/refresh} must not cause a 401 on the very call meant to replace it.
     */
    static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> request.getRequestURI().startsWith(RefreshTokenCookies.PATH + "/")
                ? null
                : delegate.resolve(request);
    }

    /** 401 as problem+json; expired tokens get {@code TOKEN_EXPIRED} so the client knows to refresh. */
    static AuthenticationEntryPoint problemEntryPoint(ProblemDetailWriter problemWriter) {
        return (request, response, ex) -> {
            boolean expired = isExpired(ex);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    ex instanceof OAuth2AuthenticationException ? "Bearer error=\"invalid_token\"" : "Bearer");
            problemWriter.write(response, ProblemDetailFactory.create(
                    expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED,
                    expired ? "The access token has expired" : "Authentication is required",
                    request.getRequestURI()));
        };
    }

    private static boolean isExpired(AuthenticationException ex) {
        if (ex instanceof OAuth2AuthenticationException oauth) {
            String description = oauth.getError().getDescription();
            return description != null && description.toLowerCase().contains("expired");
        }
        return false;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Last-Event-ID"));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After",
                "RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset"));
        config.setAllowCredentials(true);
        config.setMaxAge(properties.cors().maxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

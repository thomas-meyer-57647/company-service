package de.innologic.companyservice.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class SecurityConfig {

    private static final List<String> COMPANY_SCOPES = List.of(
            "SCOPE_company:read",
            "SCOPE_company:write",
            "SCOPE_company:admin",
            "SCOPE_company:create"
    );

    @Value("${security.jwt.audience:${SECURITY_JWT_AUDIENCE:company-service}}")
    private String jwtAudience;

    @Value("${security.jwt.clock-skew-seconds:${SECURITY_JWT_CLOCK_SKEW_SECONDS:60}}")
    private long jwtClockSkewSeconds;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ClaimsValidationFilter claimsValidationFilter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/companies")
                        .access((authentication, context) -> {
                            Authentication token = authentication.get();
                            if (!(token.getPrincipal() instanceof Jwt jwt)) {
                                return new AuthorizationDecision(false);
                            }
                            boolean hasScope = token.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .anyMatch("SCOPE_company:create"::equals);
                            boolean isBootstrapService = isBootstrapService(jwt);
                            return new AuthorizationDecision(hasScope && isBootstrapService);
                        })
                        .requestMatchers(HttpMethod.GET, "/companies")
                        .access(scopeAuth("SCOPE_company:read"))
                        .requestMatchers(HttpMethod.GET, "/companies/*")
                        .access(scopeAuth("SCOPE_company:read"))
                        .requestMatchers(HttpMethod.GET, "/companies/*/locations")
                        .access(scopeAuth("SCOPE_company:read"))
                        .requestMatchers(HttpMethod.PUT, "/companies/*")
                        .access(scopeAuth("SCOPE_company:write"))
                        .requestMatchers(HttpMethod.PUT, "/companies/*/logo")
                        .access(scopeAuth("SCOPE_company:write"))
                        .requestMatchers(HttpMethod.DELETE, "/companies/*/logo")
                        .access(scopeAuth("SCOPE_company:write"))
                        .requestMatchers(HttpMethod.DELETE, "/companies/*")
                        .access(scopeAuth("SCOPE_company:admin"))
                        .requestMatchers(HttpMethod.GET, "/location/*")
                        .access(scopeAuth("SCOPE_company:read"))
                        .requestMatchers(HttpMethod.PUT, "/location/*")
                        .access(scopeAuth("SCOPE_company:write"))
                        .requestMatchers(HttpMethod.PATCH, "/location/*")
                        .access(scopeAuth("SCOPE_company:write"))
                        .requestMatchers("/**")
                        .access((authentication, context) -> {
                            Authentication token = authentication.get();
                            if (!(token.getPrincipal() instanceof Jwt jwt)) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }
                            boolean hasScope = hasAnyCompanyScope(token.getAuthorities());
                            boolean hasTenant = StringUtils.hasText(jwt.getClaimAsString("tenant_id"));
                            return new org.springframework.security.authorization.AuthorizationDecision(hasScope && hasTenant);
                        })
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(claimsValidationFilter, BearerTokenAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    @Bean
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return new ApiAuthenticationEntryPoint();
    }

    @Bean
    ApiAccessDeniedHandler apiAccessDeniedHandler() {
        return new ApiAccessDeniedHandler();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder;
        if (StringUtils.hasText(jwkSetUri)) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        } else if (StringUtils.hasText(issuerUri)) {
            JwtDecoder issuerDecoder = JwtDecoders.fromIssuerLocation(issuerUri);
            if (!(issuerDecoder instanceof NimbusJwtDecoder nimbusJwtDecoder)) {
                throw new IllegalStateException("Unsupported JwtDecoder implementation for issuer-uri setup");
            }
            decoder = nimbusJwtDecoder;
        } else {
            throw new IllegalStateException("Either issuer-uri or jwk-set-uri must be configured for JWT validation");
        }

        OAuth2TokenValidator<Jwt> validator = buildTokenValidator();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> buildTokenValidator() {
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            if (jwt.getAudience() != null && jwt.getAudience().contains(jwtAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "wrong audience",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ofSeconds(jwtClockSkewSeconds));
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(timestampValidator);
        if (StringUtils.hasText(issuerUri)) {
            validators.add(new JwtIssuerValidator(issuerUri));
        }
        validators.add(audienceValidator);
        return new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(validators);
    }

    private boolean hasAnyCompanyScope(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority authority : authorities) {
            if (COMPANY_SCOPES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean isBootstrapService(Jwt jwt) {
        return "SERVICE".equals(jwt.getClaimAsString("subject_type"))
                && "auth-service".equals(jwt.getSubject());
    }

    private ScopeAuthorizationManager scopeAuth(String authority) {
        return new ScopeAuthorizationManager(authority);
    }

    public static final class ScopeAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

        private final String requiredAuthority;

        ScopeAuthorizationManager(String requiredAuthority) {
            this.requiredAuthority = Objects.requireNonNull(requiredAuthority, "requiredAuthority");
        }

        @Override
        public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
            Authentication auth = authentication.get();
            if (auth == null || !auth.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }

            boolean hasAuthority = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(requiredAuthority::equals);

            if (hasAuthority) {
                return new AuthorizationDecision(true);
            }

            HttpServletRequest request = context.getRequest();
            String method = request != null ? request.getMethod() : "UNKNOWN";
            String uri = request != null ? request.getRequestURI() : "UNKNOWN";
            throw new ScopeMissingException(requiredAuthority, method, uri);
        }

        public static final class ScopeMissingException extends AccessDeniedException {

            ScopeMissingException(String authority, String method, String uri) {
                super(String.format("Missing required scope %s for %s %s", authority, method, uri));
            }
        }
    }
}

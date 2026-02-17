package de.innologic.companyservice.config;

import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityConfig {

    private static final List<String> COMPANY_SCOPES = List.of(
            "SCOPE_company:read",
            "SCOPE_company:write",
            "SCOPE_company:admin",
            "SCOPE_company:create"
    );

    @Value("${app.security.required-audience:company-service}")
    private String requiredAudience;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
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
                        .requestMatchers(HttpMethod.POST, "/api/v1/companies")
                        .hasAuthority("SCOPE_company:create")
                        .requestMatchers(HttpMethod.GET, "/api/v1/companies/*")
                        .hasAuthority("SCOPE_company:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/companies/*/locations")
                        .hasAuthority("SCOPE_company:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/companies/*")
                        .hasAuthority("SCOPE_company:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/companies/*/logo")
                        .hasAuthority("SCOPE_company:write")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/companies/*/logo")
                        .hasAuthority("SCOPE_company:write")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/companies/*/main-location")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/companies/*")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers(HttpMethod.POST, "/api/v1/companies/*/restore")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers(HttpMethod.POST, "/api/v1/companies/*/deletion-ack")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers(HttpMethod.GET, "/api/v1/location/*")
                        .hasAuthority("SCOPE_company:read")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/location/*")
                        .hasAuthority("SCOPE_company:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/location/*/reopen")
                        .hasAuthority("SCOPE_company:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/location/*/close")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/location/*")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers(HttpMethod.POST, "/api/v1/location/*/restore")
                        .hasAuthority("SCOPE_company:admin")
                        .requestMatchers("/api/v1/**")
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
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
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
            if (jwt.getAudience() != null && jwt.getAudience().contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Required audience '" + requiredAudience + "' is missing",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };

        if (StringUtils.hasText(issuerUri)) {
            return new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuerUri),
                    audienceValidator
            );
        }
        return new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                audienceValidator
        );
    }

    private boolean hasAnyCompanyScope(Collection<? extends GrantedAuthority> authorities) {
        for (GrantedAuthority authority : authorities) {
            if (COMPANY_SCOPES.contains(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}

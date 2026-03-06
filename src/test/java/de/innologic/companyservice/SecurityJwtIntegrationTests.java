package de.innologic.companyservice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.innologic.companyservice.api.CompanyController;
import de.innologic.companyservice.api.LocationController;
import de.innologic.companyservice.config.RequestContext;
import de.innologic.companyservice.config.SecurityConfig;
import de.innologic.companyservice.service.CompanyCommandService;
import de.innologic.companyservice.service.CompanyDeletionWorkflowService;
import de.innologic.companyservice.service.CompanyQueryService;
import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.service.LocationCommandService;
import de.innologic.companyservice.service.LocationQueryService;

@WebMvcTest(controllers = { CompanyController.class, LocationController.class })
@Import(SecurityConfig.class)
@ImportAutoConfiguration(SecurityAutoConfiguration.class)
class SecurityJwtIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    // verhindert issuer/jwk-set Pflicht und Netzwerkanfragen
    @MockitoBean
    private JwtDecoder jwtDecoder;

    // WICHTIG: weil @EnableCaching (aus CompanyServiceApplication) aktiv ist, aber @WebMvcTest keinen CacheManager erstellt.
    // Ein Mock reicht hier komplett aus, damit der ApplicationContext startet.
    @MockitoBean
    private CacheManager cacheManager;

    // CompanyController deps
    @MockitoBean private CompanyCommandService companyCommandService;
    @MockitoBean private CompanyQueryService companyQueryService;
    @MockitoBean private CompanyDeletionWorkflowService companyDeletionWorkflowService;

    // LocationController deps
    @MockitoBean private LocationQueryService locationQueryService;
    @MockitoBean private LocationCommandService locationCommandService;

    // beide Controller brauchen RequestContext
    @MockitoBean private RequestContext requestContext;

    private static final String COMPANY_CREATE_PAYLOAD = """
            {
              "name": "Acme Corporation",
              "displayName": "ACME",
              "timezone": "Europe/Berlin",
              "locale": "de-DE",
              "initialLocation": {
                "name": "Headquarters",
                "locationCode": "HQ-BER",
                "timezone": "Europe/Berlin",
                "countryCode": "DE",
                "regionCode": "BE"
              }
            }
            """;

    private final CompanyEntity companyEntity = createCompanyEntity();

    @BeforeEach
    void setupBootstrap() {
        when(requestContext.subjectId()).thenReturn("auth-service");
        when(requestContext.tenantIdFromJwt()).thenReturn(Optional.empty());
        when(companyCommandService.createCompany(
                        any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(companyEntity);
    }

    @Test
    void missingJwtReturnsUnauthorized_company() throws Exception {
        mockMvc.perform(get("/companies/{companyId}", "company-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void missingJwtReturnsUnauthorized_location() throws Exception {
        mockMvc.perform(get("/location/{locationId}", "loc-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void wrongAudienceTokenReturnsUnauthorized() throws Exception {
        when(jwtDecoder.decode("bad-aud-token"))
                .thenThrow(new BadJwtException("wrong audience"));

        mockMvc.perform(get("/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer bad-aud-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("wrong audience"));
    }

    @Test
    void missingScopeReturnsForbidden_companyEndpoint() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("no-scope-token")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("tenant_id", "company-1")
                .claim("subject_type", "USER")
                .claim("aud", List.of("company-service"))
        // bewusst KEIN scope/scp claim -> soll forbidden ergeben
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("no-scope-token")).thenReturn(jwt);

        mockMvc.perform(get("/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer no-scope-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("missing claim: scp"));
    }

    @Test
    void missingScopeReturnsForbidden_locationEndpoint() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("no-scope-token-2")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("tenant_id", "company-1")
                .claim("subject_type", "USER")
                .claim("aud", List.of("company-service"))
                // bewusst KEIN scope/scp claim -> soll forbidden ergeben
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("no-scope-token-2")).thenReturn(jwt);

        mockMvc.perform(get("/location/{locationId}", "loc-1")
                        .header("Authorization", "Bearer no-scope-token-2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("missing claim: scp"));
    }

    @Test
    void missingRequiredScopeReturnsScopeMissing() throws Exception {
        mockMvc.perform(get("/companies/{companyId}", "company-1")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("user-1")
                                        .claim("tenant_id", "company-1")
                                        .claim("subject_type", "USER")
                                        .claim("aud", List.of("company-service"))
                                        .claim("scp", List.of("company:write"))
                                        .claim("scope", List.of("company:write")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_company:write"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_MISSING"))
                .andExpect(jsonPath("$.message").value("Required scope is missing"));
    }

    @Test
    void missingTenantIdReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("no-tenant")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("subject_type", "USER")
                .claim("scp", List.of("company:read"))
                .claim("aud", List.of("company-service"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("no-tenant")).thenReturn(jwt);

        mockMvc.perform(get("/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer no-tenant"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("missing claim: tenant_id"));
    }

    @Test
    void invalidSubjectTypeReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("bad-subject")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("subject_type", "UNKNOWN")
                .claim("tenant_id", "company-1")
                .claim("scp", List.of("company:read"))
                .claim("aud", List.of("company-service"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("bad-subject")).thenReturn(jwt);

        mockMvc.perform(get("/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer bad-subject"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("invalid subject_type"));
    }

    @Test
    void missingSubjectTypeReturnsUnauthorized() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("no-subject-type")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("tenant_id", "company-1")
                .claim("scp", List.of("company:read"))
                .claim("aud", List.of("company-service"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("no-subject-type")).thenReturn(jwt);

        mockMvc.perform(get("/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer no-subject-type"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("invalid subject_type"));
    }

    @Test
    void bootstrapRequestWithProperClaimsIsCreated() throws Exception {
        mockMvc.perform(post("/companies")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("auth-service")
                                        .claim("subject_type", "SERVICE"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_company:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_CREATE_PAYLOAD))
                .andExpect(status().isCreated());
    }

    @Test
    void bootstrapRequestWithWrongSubjectTypeIsForbidden() throws Exception {
        mockMvc.perform(post("/companies")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("auth-service")
                                        .claim("subject_type", "USER"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_company:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_CREATE_PAYLOAD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void bootstrapRequestWithWrongSubjectIsForbidden() throws Exception {
        mockMvc.perform(post("/companies")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("other")
                                        .claim("subject_type", "SERVICE"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_company:create")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(COMPANY_CREATE_PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantMismatchFromServiceReturnsTenantMismatchCode() throws Exception {
        when(requestContext.tenantIdFromJwt()).thenReturn(Optional.of("company-1"));
        when(locationCommandService.trashLocation(any(), any(), any()))
                .thenThrow(new AccessDeniedException("tenant_id does not match location.companyId"));

        mockMvc.perform(delete("/location/{locationId}", "loc-1")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .subject("user-1")
                                        .claim("tenant_id", "company-1")
                                        .claim("subject_type", "USER")
                                        .claim("aud", List.of("company-service"))
                                        .claim("scp", List.of("company:admin"))
                                        .claim("scope", List.of("company:admin")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_company:admin")))
                        .header("X-Company-Id", "company-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("tenant mismatch"));
    }

    private CompanyEntity createCompanyEntity() {
        Instant now = Instant.now();
        CompanyEntity entity = new CompanyEntity();
        entity.setCompanyId("company-1");
        entity.setName("Acme Corporation");
        entity.setDisplayName("ACME");
        entity.setTimezone("Europe/Berlin");
        entity.setLocale("de-DE");
        entity.setMainLocationId("loc-1");
        entity.setCreatedAt(now);
        entity.setCreatedBy("auth-service");
        entity.setModifiedAt(now);
        entity.setModifiedBy("auth-service");
        entity.setVersion(1L);
        return entity;
    }
}

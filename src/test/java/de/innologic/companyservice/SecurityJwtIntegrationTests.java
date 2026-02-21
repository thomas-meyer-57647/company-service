package de.innologic.companyservice;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
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

    @Test
    void missingJwtReturnsUnauthorized_company() throws Exception {
        mockMvc.perform(get("/api/v1/companies/{companyId}", "company-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingJwtReturnsUnauthorized_location() throws Exception {
        mockMvc.perform(get("/api/v1/location/{locationId}", "loc-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceTokenReturnsUnauthorized() throws Exception {
        when(jwtDecoder.decode("bad-aud-token"))
                .thenThrow(new BadJwtException("Required audience 'company-service' is missing"));

        mockMvc.perform(get("/api/v1/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer bad-aud-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingScopeReturnsForbidden_companyEndpoint() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("no-scope-token")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("tenant_id", "company-1")
                .claim("aud", List.of("company-service"))
                // bewusst KEIN scope/scp claim -> soll forbidden ergeben
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("no-scope-token")).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/companies/{companyId}", "company-1")
                        .header("Authorization", "Bearer no-scope-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingScopeReturnsForbidden_locationEndpoint() throws Exception {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("no-scope-token-2")
                .header("alg", "RS256")
                .claim("sub", "user-1")
                .claim("tenant_id", "company-1")
                .claim("aud", List.of("company-service"))
                // bewusst KEIN scope/scp claim -> soll forbidden ergeben
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();

        when(jwtDecoder.decode("no-scope-token-2")).thenReturn(jwt);

        mockMvc.perform(get("/api/v1/location/{locationId}", "loc-1")
                        .header("Authorization", "Bearer no-scope-token-2"))
                .andExpect(status().isForbidden());
    }
}
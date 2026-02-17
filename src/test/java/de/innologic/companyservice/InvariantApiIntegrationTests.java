package de.innologic.companyservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.entity.TrashedCause;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InvariantApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Test
    void closeMainReturnsConflictCannotCloseMainLocation() throws Exception {
        Fixture f = fixtureWithMainAndSecondaryOpen();

        mockMvc.perform(post("/api/v1/location/{locationId}/close", f.mainLocationId)
                        .with(jwtForCompany(f.companyId, "company:admin"))
                        .header("X-Company-Id", f.companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"maintenance\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_CLOSE_MAIN_LOCATION"));
    }

    @Test
    void trashMainReturnsConflictCannotTrashMainLocation() throws Exception {
        Fixture f = fixtureWithMainAndSecondaryOpen();

        mockMvc.perform(delete("/api/v1/location/{locationId}", f.mainLocationId)
                        .with(jwtForCompany(f.companyId, "company:admin"))
                        .header("X-Company-Id", f.companyId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_TRASH_MAIN_LOCATION"));
    }

    @Test
    void closeLastOpenReturnsConflictLastOpenLocationRequired() throws Exception {
        Fixture f = fixtureWithOnlyMainOpen();

        mockMvc.perform(post("/api/v1/location/{locationId}/close", f.mainLocationId)
                        .with(jwtForCompany(f.companyId, "company:admin"))
                        .header("X-Company-Id", f.companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"maintenance\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LAST_OPEN_LOCATION_REQUIRED"));
    }

    @Test
    void setMainToClosedReturnsConflictMainLocationMustBeOpen() throws Exception {
        Fixture f = fixtureWithClosedSecondary();

        mockMvc.perform(put("/api/v1/companies/{companyId}/main-location", f.companyId)
                        .with(jwtForCompany(f.companyId, "company:admin"))
                        .header("X-Company-Id", f.companyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":\"" + f.secondaryLocationId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MAIN_LOCATION_MUST_BE_OPEN"));
    }

    @Test
    void trashCompanyCascadesLocationsToTrashedCauseCascade() throws Exception {
        Fixture f = fixtureWithMainAndSecondaryOpen();

        mockMvc.perform(delete("/api/v1/companies/{companyId}", f.companyId)
                        .with(jwtForCompany(f.companyId, "company:admin"))
                        .header("X-Company-Id", f.companyId))
                .andExpect(status().isNoContent()); // falls dein Endpoint 202 liefert: hier anpassen

        CompanyEntity company = companyRepository.findById(f.companyId).orElseThrow();
        assertThat(company.getTrashedAt()).isNotNull();

        assertThat(locationRepository.findAllByCompanyId(f.companyId))
                .hasSize(2)
                .allSatisfy(location -> {
                    assertThat(location.getTrashedAt()).isNotNull();
                    assertThat(location.getTrashedCause()).isEqualTo(TrashedCause.CASCADE);
                });
    }

    @Test
    void apiDocsEndpointIsReachable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    /**
     * Erzeugt ein Mock-JWT passend für deine Tenant-/Scope-Checks:
     * - tenant_id = companyId
     * - Authorities als SCOPE_... (z.B. "SCOPE_company:admin")
     */
    private RequestPostProcessor jwtForCompany(String companyId, String... scopes) {
        GrantedAuthority[] auths = Arrays.stream(scopes)
                .map(s -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + s))
                .toArray(GrantedAuthority[]::new);

        return jwt()
                .jwt(j -> j
                        .subject("tester")
                        .claim("tenant_id", companyId))
                .authorities(auths);
    }

    private Fixture fixtureWithMainAndSecondaryOpen() {
        String companyId = UUID.randomUUID().toString();
        String mainLocationId = UUID.randomUUID().toString();
        String secondaryLocationId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        persistCompany(companyId, mainLocationId, now);
        persistLocation(mainLocationId, companyId, "Main", LocationStatus.OPEN, now);
        persistLocation(secondaryLocationId, companyId, "Secondary", LocationStatus.OPEN, now);
        return new Fixture(companyId, mainLocationId, secondaryLocationId);
    }

    private Fixture fixtureWithOnlyMainOpen() {
        String companyId = UUID.randomUUID().toString();
        String mainLocationId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        persistCompany(companyId, mainLocationId, now);
        persistLocation(mainLocationId, companyId, "Main", LocationStatus.OPEN, now);
        return new Fixture(companyId, mainLocationId, null);
    }

    private Fixture fixtureWithClosedSecondary() {
        String companyId = UUID.randomUUID().toString();
        String mainLocationId = UUID.randomUUID().toString();
        String secondaryLocationId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        persistCompany(companyId, mainLocationId, now);
        persistLocation(mainLocationId, companyId, "Main", LocationStatus.OPEN, now);
        persistLocation(secondaryLocationId, companyId, "Secondary", LocationStatus.CLOSED, now);
        return new Fixture(companyId, mainLocationId, secondaryLocationId);
    }

    private void persistCompany(String companyId, String mainLocationId, Instant now) {
        CompanyEntity company = new CompanyEntity();
        company.setCompanyId(companyId);
        company.setName("Company " + companyId);
        company.setMainLocationId(mainLocationId);
        company.setCreatedAt(now);
        companyRepository.save(company);
    }

    private void persistLocation(String locationId, String companyId, String name, LocationStatus status, Instant now) {
        LocationEntity location = new LocationEntity();
        location.setLocationId(locationId);
        location.setCompanyId(companyId);
        location.setName(name);
        location.setStatus(status);
        location.setCreatedAt(now);
        if (status == LocationStatus.CLOSED) {
            location.setClosedAt(now);
            location.setClosedBy("test");
            location.setClosedReason("setup");
        }
        locationRepository.save(location);
    }

    private record Fixture(String companyId, String mainLocationId, String secondaryLocationId) {
    }
}

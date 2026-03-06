package de.innologic.companyservice;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.innologic.companyservice.domain.LocationNotFoundException;
import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

abstract class LocationTenantMismatchIntegrationTestsBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected LocationRepository locationRepository;

    @Autowired
    protected CompanyRepository companyRepository;

    @BeforeEach
    void cleanDatabase() {
        locationRepository.deleteAll();
        companyRepository.deleteAll();
    }

    protected void persistCompanyWithLocation(String companyId, String locationId) {
        Instant now = Instant.now();

        CompanyEntity company = new CompanyEntity();
        company.setCompanyId(companyId);
        company.setName("Company " + companyId);
        company.setMainLocationId(locationId);
        company.setCreatedAt(now);
        company.setCreatedBy("seed");
        company.setModifiedAt(now);
        company.setModifiedBy("seed");
        companyRepository.save(company);

        LocationEntity location = new LocationEntity();
        location.setLocationId(locationId);
        location.setCompanyId(companyId);
        location.setName("HQ");
        location.setStatus(LocationStatus.OPEN);
        location.setCreatedAt(now);
        location.setCreatedBy("seed");
        location.setModifiedAt(now);
        location.setModifiedBy("seed");
        locationRepository.save(location);
    }

    protected JwtRequestPostProcessor tenantJwt(String tenantId) {
        return jwt().jwt(jwt -> jwt
                        .claim("sub", "user-" + UUID.randomUUID())
                        .claim("tenant_id", tenantId)
                        .claim("subject_type", "USER"))
                .authorities(() -> "SCOPE_company:read");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.deletion.required-services=template-service")
class LocationTenantMismatchDefaultModeIntegrationTests extends LocationTenantMismatchIntegrationTestsBase {

    @Test
    void crossTenantAccessReturnsTenantMismatch() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(get("/location/{locationId}", locationId)
                        .with(tenantJwt(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_MISMATCH"))
                .andExpect(jsonPath("$.message").value("tenant mismatch"));
    }
}

@SpringBootTest(properties = {
        "app.deletion.required-services=template-service",
        "COMPANY_NON_LEAK_404=true"
})
@AutoConfigureMockMvc
class LocationTenantMismatchNonLeakModeIntegrationTests extends LocationTenantMismatchIntegrationTestsBase {

    @Test
    void crossTenantAccessReportsLocationNotFound() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(get("/location/{locationId}", locationId)
                        .with(tenantJwt(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOCATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(LocationNotFoundException.MESSAGE));
    }
}

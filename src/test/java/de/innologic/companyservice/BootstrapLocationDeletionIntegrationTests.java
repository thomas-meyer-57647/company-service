package de.innologic.companyservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.DeletionState;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.BootstrapIdempotencyRepository;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.DeletionAckRepository;
import de.innologic.companyservice.persistence.repository.DeletionTombstoneRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.deletion.required-services=template-service")
class BootstrapLocationDeletionIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private BootstrapIdempotencyRepository bootstrapIdempotencyRepository;

    @Autowired
    private DeletionTombstoneRepository deletionTombstoneRepository;

    @Autowired
    private DeletionAckRepository deletionAckRepository;

    @BeforeEach
    void cleanDatabase() {
        deletionAckRepository.deleteAll();
        deletionTombstoneRepository.deleteAll();
        bootstrapIdempotencyRepository.deleteAll();
        locationRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void bootstrapCreateWithScopeCreateWithoutTenantIsAllowed() throws Exception {
        mockMvc.perform(post("/companies")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "auth-service")
                                        .claim("subject_type", "SERVICE"))
                                .authorities(() -> "SCOPE_company:create"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Bootstrap Company",
                                  "displayName":"Bootstrap",
                                  "timezone":"Europe/Berlin",
                                  "locale":"de-DE",
                                  "initialLocation":{"name":"HQ","locationCode":"HQ-1","timezone":"Europe/Berlin"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").isNotEmpty())
                .andExpect(jsonPath("$.mainLocationId").isNotEmpty());
    }

    @Test
    void bootstrapIdempotencyRetryDoesNotCreateDuplicates() throws Exception {
        String payload = """
                {
                  "name":"Idempotent Company",
                  "displayName":"IdemCo",
                  "timezone":"Europe/Berlin",
                  "locale":"de-DE",
                  "initialLocation":{"name":"HQ","locationCode":"HQ-1","timezone":"Europe/Berlin"}
                }
                """;

        MvcResult first = mockMvc.perform(post("/companies")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "auth-service")
                                        .claim("subject_type", "SERVICE"))
                                .authorities(() -> "SCOPE_company:create"))
                        .header("Idempotency-Key", "bootstrap-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/companies")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "auth-service")
                                        .claim("subject_type", "SERVICE"))
                                .authorities(() -> "SCOPE_company:create"))
                        .header("Idempotency-Key", "bootstrap-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondJson = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(secondJson.get("companyId").asText()).isEqualTo(firstJson.get("companyId").asText());
        assertThat(companyRepository.count()).isEqualTo(1);
    }

    @Test
    void locationIdOnlyTenantCheckRejectsCrossTenantAccess() throws Exception {
        String ownerCompany = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(ownerCompany, locationId);

        mockMvc.perform(get("/location/{locationId}", locationId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "user-1")
                                        .claim("tenant_id", UUID.randomUUID().toString())
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchCompanyUpdatesFieldsWhenVersionMatches() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);
        CompanyEntity company = companyRepository.findById(companyId).orElseThrow();

        mockMvc.perform(patch("/companies/{companyId}", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "editor-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName":"Patched",
                                  "timezone":"Europe/London",
                                  "version":%d
                                }
                                """.formatted(company.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Patched"))
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.companyId").value(companyId));
    }

    @Test
    void patchCompanyVersionMismatchReturnsConflict() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);
        CompanyEntity company = companyRepository.findById(companyId).orElseThrow();

        mockMvc.perform(patch("/companies/{companyId}", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "editor-2")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "locale":"en-GB",
                                  "version":%d
                                }
                                """.formatted(company.getVersion() + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Version conflict"));
    }

    @Test
    void listCompaniesReturnsPage() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(get("/companies")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyId").value(companyId))
                .andExpect(jsonPath("$.content[0].mainLocationId").value(locationId))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listCompaniesWithoutScopeReturnsScopeMissing() throws Exception {
        mockMvc.perform(get("/companies")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-2")
                                        .claim("tenant_id", UUID.randomUUID().toString())
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_MISSING"))
                .andExpect(jsonPath("$.message").value("Required scope is missing"));
    }

    @Test
    void listCompanyLocationsFiltersByNameContains() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String firstLocationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, firstLocationId);
        String secondLocationId = UUID.randomUUID().toString();
        persistAdditionalLocation(companyId, secondLocationId, "Remote Office");

        mockMvc.perform(get("/companies/{companyId}/locations", companyId)
                        .param("nameContains", "Remote")
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-3")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].locationId").value(secondLocationId))
                .andExpect(jsonPath("$.content[0].name").value("Remote Office"));
    }

    @Test
    void listCompanyLocationsWithoutScopeReturnsScopeMissing() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(get("/companies/{companyId}/locations", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-4")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SCOPE_MISSING"))
                .andExpect(jsonPath("$.message").value("Required scope is missing"));
    }

    @Test
    void updateLocationNormalizesCountryAndRegionCodes() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(put("/location/{locationId}", locationId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "editor-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"HQ Updated",
                                  "locationCode":"HQ-1",
                                  "timezone":"Europe/Berlin",
                                  "countryCode":"de",
                                  "regionCode":"de-hb"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(jsonPath("$.regionCode").value("DE-HB"));
    }

    @Test
    void updateLocationWithoutCountryAndRegionKeepsExistingValues() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId, "DE", "DE-HB");

        mockMvc.perform(put("/location/{locationId}", locationId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "editor-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"HQ Updated",
                                  "locationCode":"HQ-1",
                                  "timezone":"Europe/Berlin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").value("DE"))
                .andExpect(jsonPath("$.regionCode").value("DE-HB"));

        LocationEntity updated = locationRepository.findById(locationId).orElseThrow();
        assertThat(updated.getCountryCode()).isEqualTo("DE");
        assertThat(updated.getRegionCode()).isEqualTo("DE-HB");
    }

    @Test
    void updateLocationWithEmptyCountryAndRegionClearsValues() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId, "DE", "DE-HB");

        mockMvc.perform(put("/location/{locationId}", locationId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "editor-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"HQ Updated",
                                  "locationCode":"HQ-1",
                                  "timezone":"Europe/Berlin",
                                  "countryCode":"",
                                  "regionCode":""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode").isEmpty())
                .andExpect(jsonPath("$.regionCode").isEmpty());

        LocationEntity updated = locationRepository.findById(locationId).orElseThrow();
        assertThat(updated.getCountryCode()).isNull();
        assertThat(updated.getRegionCode()).isNull();
    }

    @Test
    void updateLocationWithInvalidCountryCodeReturnsBadRequest() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId, "DE", "DE-HB");

        mockMvc.perform(put("/location/{locationId}", locationId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "editor-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"HQ Updated",
                                  "locationCode":"HQ-1",
                                  "timezone":"Europe/Berlin",
                                  "countryCode":"D",
                                  "regionCode":"DE-HB"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCompanyAndLocationResponsesContainContactOwnerFields() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(get("/companies/{companyId}", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactOwnerType").value("COMPANY"))
                .andExpect(jsonPath("$.contactOwnerId").value(companyId));

        mockMvc.perform(get("/location/{locationId}", locationId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactOwnerType").value("LOCATION"))
                .andExpect(jsonPath("$.contactOwnerId").value(locationId));
    }

    @Test
    void deleteWorkflowGoesFromAcceptedToCompletedAndHardDeletesData() throws Exception {
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();
        persistCompanyWithLocation(companyId, locationId);

        mockMvc.perform(delete("/companies/{companyId}", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "admin-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:admin"))
                        .header("Idempotency-Key", "delete-key-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deletionId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));

        mockMvc.perform(get("/companies/{companyId}", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "reader-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:read")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/companies/{companyId}/deletion-ack", companyId)
                        .with(jwt().jwt(jwt -> jwt
                                        .claim("sub", "admin-1")
                                        .claim("tenant_id", companyId)
                                        .claim("subject_type", "USER"))
                                .authorities(() -> "SCOPE_company:admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"template-service\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        assertThat(companyRepository.findById(companyId)).isEmpty();
        assertThat(locationRepository.findById(locationId)).isEmpty();
        assertThat(deletionTombstoneRepository.findByCompanyId(companyId))
                .isPresent()
                .get()
                .extracting(t -> t.getState())
                .isEqualTo(DeletionState.COMPLETED);
    }

    private void persistCompanyWithLocation(String companyId, String locationId) {
        persistCompanyWithLocation(companyId, locationId, null, null);
    }

    private void persistCompanyWithLocation(String companyId, String locationId, String countryCode, String regionCode) {
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
        location.setCountryCode(countryCode);
        location.setRegionCode(regionCode);
        location.setStatus(LocationStatus.OPEN);
        location.setCreatedAt(now);
        location.setCreatedBy("seed");
        location.setModifiedAt(now);
        location.setModifiedBy("seed");
        locationRepository.save(location);
    }

    private void persistAdditionalLocation(String companyId, String locationId, String name) {
        Instant now = Instant.now();
        LocationEntity location = new LocationEntity();
        location.setLocationId(locationId);
        location.setCompanyId(companyId);
        location.setName(name);
        location.setLocationCode("LOC-" + locationId);
        location.setTimezone("Europe/Berlin");
        location.setStatus(LocationStatus.OPEN);
        location.setCreatedAt(now);
        location.setCreatedBy("seed");
        location.setModifiedAt(now);
        location.setModifiedBy("seed");
        locationRepository.save(location);
    }
}

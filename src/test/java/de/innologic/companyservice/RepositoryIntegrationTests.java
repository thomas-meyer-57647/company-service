package de.innologic.companyservice;

import static org.assertj.core.api.Assertions.assertThat;

import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RepositoryIntegrationTests {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Test
    void companyFindByCompanyIdAndTrashedAtIsNullReturnsOnlyActiveCompany() {
        CompanyEntity active = new CompanyEntity();
        active.setCompanyId("company-active-001");
        active.setName("Active Company");
        active.setMainLocationId("loc-active-main-001");
        active.setCreatedAt(Instant.now());
        companyRepository.save(active);

        CompanyEntity trashed = new CompanyEntity();
        trashed.setCompanyId("company-trashed-001");
        trashed.setName("Trashed Company");
        trashed.setMainLocationId("loc-trashed-main-001");
        trashed.setCreatedAt(Instant.now());
        trashed.setTrashedAt(Instant.now());
        companyRepository.save(trashed);

        assertThat(companyRepository.findByCompanyIdAndTrashedAtIsNull("company-active-001")).isPresent();
        assertThat(companyRepository.findByCompanyIdAndTrashedAtIsNull("company-trashed-001")).isEmpty();
    }

    @Test
    void locationQueriesAndOpenCountRespectCompanyScopeAndSoftDelete() {
        CompanyEntity company = new CompanyEntity();
        company.setCompanyId("company-001");
        company.setName("Company");
        company.setMainLocationId("loc-open-001");
        company.setCreatedAt(Instant.now());
        companyRepository.save(company);

        locationRepository.save(newLocation("loc-open-001", "company-001", LocationStatus.OPEN, null));
        locationRepository.save(newLocation("loc-closed-001", "company-001", LocationStatus.CLOSED, null));
        locationRepository.save(newLocation("loc-open-trashed-001", "company-001", LocationStatus.OPEN, Instant.now()));

        assertThat(locationRepository.findByLocationIdAndTrashedAtIsNull("loc-open-001")).isPresent();
        assertThat(locationRepository.findByLocationIdAndTrashedAtIsNull("loc-open-trashed-001")).isEmpty();

        assertThat(locationRepository
                .findByLocationIdAndCompanyIdAndTrashedAtIsNull("loc-open-001", "company-001"))
                .isPresent();
        assertThat(locationRepository
                .findByLocationIdAndCompanyIdAndTrashedAtIsNull("loc-open-001", "other-company"))
                .isEmpty();

        assertThat(locationRepository.findAllByCompanyIdAndTrashedAtIsNull("company-001", PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(2);
        assertThat(locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull("company-001", LocationStatus.OPEN))
                .isEqualTo(1);
    }

    private static LocationEntity newLocation(String locationId, String companyId, LocationStatus status, Instant trashedAt) {
        LocationEntity entity = new LocationEntity();
        entity.setLocationId(locationId);
        entity.setCompanyId(companyId);
        entity.setName("Location " + locationId);
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        entity.setTrashedAt(trashedAt);
        return entity;
    }
}

package de.innologic.companyservice;

import static org.assertj.core.api.Assertions.assertThat;

import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("tc")
class MariaDbSmokeIT {

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void savesAndFindsCompany() {
        CompanyEntity company = new CompanyEntity();
        company.setCompanyId("tc-company-001");
        company.setName("TC Company");
        company.setMainLocationId("tc-main-location");
        company.setCreatedAt(Instant.now());

        companyRepository.save(company);

        Optional<CompanyEntity> loaded = companyRepository.findByCompanyIdAndTrashedAtIsNull(company.getCompanyId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getName()).isEqualTo("TC Company");
    }
}

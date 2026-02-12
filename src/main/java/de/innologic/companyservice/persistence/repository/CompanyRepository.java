package de.innologic.companyservice.persistence.repository;

import de.innologic.companyservice.persistence.entity.CompanyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<CompanyEntity, String> {

    Optional<CompanyEntity> findByCompanyIdAndTrashedAtIsNull(String companyId);
}

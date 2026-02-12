package de.innologic.companyservice.persistence.repository;

import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<LocationEntity, String> {

    Optional<LocationEntity> findByLocationIdAndTrashedAtIsNull(String locationId);

    Optional<LocationEntity> findByLocationIdAndCompanyIdAndTrashedAtIsNull(String locationId, String companyId);

    Page<LocationEntity> findAllByCompanyIdAndTrashedAtIsNull(String companyId, Pageable pageable);

    Page<LocationEntity> findAllByCompanyIdAndStatusAndTrashedAtIsNull(
            String companyId,
            LocationStatus status,
            Pageable pageable
    );

    long countByCompanyIdAndStatusAndTrashedAtIsNull(String companyId, LocationStatus status);

    List<LocationEntity> findAllByCompanyId(String companyId);
}

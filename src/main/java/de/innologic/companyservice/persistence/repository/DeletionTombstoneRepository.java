package de.innologic.companyservice.persistence.repository;

import de.innologic.companyservice.persistence.entity.DeletionState;
import de.innologic.companyservice.persistence.entity.DeletionTombstoneEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletionTombstoneRepository extends JpaRepository<DeletionTombstoneEntity, String> {

    Optional<DeletionTombstoneEntity> findByCompanyId(String companyId);

    Optional<DeletionTombstoneEntity> findByCompanyIdAndState(String companyId, DeletionState state);

    boolean existsByCompanyIdAndState(String companyId, DeletionState state);
}

package de.innologic.companyservice.persistence.repository;

import de.innologic.companyservice.persistence.entity.DeletionAckEntity;
import de.innologic.companyservice.persistence.entity.DeletionAckEntity.DeletionAckId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletionAckRepository extends JpaRepository<DeletionAckEntity, DeletionAckId> {

    List<DeletionAckEntity> findAllByDeletionId(String deletionId);
}

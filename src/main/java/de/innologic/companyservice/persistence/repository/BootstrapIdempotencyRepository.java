package de.innologic.companyservice.persistence.repository;

import de.innologic.companyservice.persistence.entity.BootstrapIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BootstrapIdempotencyRepository extends JpaRepository<BootstrapIdempotencyEntity, String> {
}

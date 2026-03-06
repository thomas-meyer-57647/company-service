package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.DeletionState;
import de.innologic.companyservice.persistence.repository.DeletionTombstoneRepository;
import org.springframework.stereotype.Service;

@Service
public class DeletionGuardService {

    private final DeletionTombstoneRepository deletionTombstoneRepository;

    public DeletionGuardService(DeletionTombstoneRepository deletionTombstoneRepository) {
        this.deletionTombstoneRepository = deletionTombstoneRepository;
    }

    public void assertCompanyAccessible(String companyId) {
        if (deletionTombstoneRepository.existsByCompanyIdAndState(companyId, DeletionState.IN_PROGRESS)) {
            throw new CompanyDeletionInProgressException(companyId);
        }
    }

    public static final class CompanyDeletionInProgressException extends ResourceNotFoundException {
        private CompanyDeletionInProgressException(String companyId) {
            super("Company deletion in progress: " + companyId);
        }
    }
}

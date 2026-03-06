package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanyQueryService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final DeletionGuardService deletionGuardService;

    public CompanyQueryService(
            CompanyRepository companyRepository,
            LocationRepository locationRepository,
            DeletionGuardService deletionGuardService
    ) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.deletionGuardService = deletionGuardService;
    }

    @Cacheable(cacheNames = "companiesById", key = "#companyId")
    @Transactional(readOnly = true)
    public CompanyEntity getActiveCompany(String companyId) {
        deletionGuardService.assertCompanyAccessible(companyId);
        return companyRepository.findByCompanyIdAndTrashedAtIsNull(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
    }

    @Transactional(readOnly = true)
    public Page<CompanyEntity> listActiveCompanies(Pageable pageable) {
        return companyRepository.findAllByTrashedAtIsNull(pageable);
    }

    @Cacheable(
            cacheNames = "locationsByCompany",
            key = "#companyId + '|' + #status + '|' + #pageable.pageNumber + '|' + #pageable.pageSize + '|' + #pageable.sort"
    )
    @Transactional(readOnly = true)
    public Page<LocationEntity> listActiveLocations(String companyId, LocationStatus status, String nameContains, Pageable pageable) {
        deletionGuardService.assertCompanyAccessible(companyId);
        getActiveCompany(companyId);
        boolean hasNameFilter = StringUtils.hasText(nameContains);
        String normalizedName = hasNameFilter ? nameContains.trim() : null;

        if (status == null && !hasNameFilter) {
            return locationRepository.findAllByCompanyIdAndTrashedAtIsNull(companyId, pageable);
        }
        if (status != null && !hasNameFilter) {
            return locationRepository.findAllByCompanyIdAndStatusAndTrashedAtIsNull(companyId, status, pageable);
        }
        if (status == null) {
            return locationRepository.findAllByCompanyIdAndTrashedAtIsNullAndNameContainingIgnoreCase(
                    companyId,
                    normalizedName,
                    pageable
            );
        }
        return locationRepository.findAllByCompanyIdAndStatusAndTrashedAtIsNullAndNameContainingIgnoreCase(
                companyId,
                status,
                normalizedName,
                pageable
        );
    }
}

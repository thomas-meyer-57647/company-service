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

@Service
public class CompanyQueryService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;

    public CompanyQueryService(CompanyRepository companyRepository, LocationRepository locationRepository) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
    }

    @Cacheable(cacheNames = "companiesById", key = "#companyId")
    @Transactional(readOnly = true)
    public CompanyEntity getActiveCompany(String companyId) {
        return companyRepository.findByCompanyIdAndTrashedAtIsNull(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
    }

    @Cacheable(
            cacheNames = "locationsByCompany",
            key = "#companyId + '|' + #status + '|' + #pageable.pageNumber + '|' + #pageable.pageSize + '|' + #pageable.sort"
    )
    @Transactional(readOnly = true)
    public Page<LocationEntity> listActiveLocations(String companyId, LocationStatus status, Pageable pageable) {
        getActiveCompany(companyId);
        if (status == null) {
            return locationRepository.findAllByCompanyIdAndTrashedAtIsNull(companyId, pageable);
        }
        return locationRepository.findAllByCompanyIdAndStatusAndTrashedAtIsNull(companyId, status, pageable);
    }
}

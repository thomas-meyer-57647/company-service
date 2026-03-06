package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.domain.LocationNotFoundException;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationQueryService {

    private final LocationRepository locationRepository;
    private final DeletionGuardService deletionGuardService;
    private final boolean companyNonLeak404;

    public LocationQueryService(
            LocationRepository locationRepository,
            DeletionGuardService deletionGuardService,
            @Value("${COMPANY_NON_LEAK_404:false}") boolean companyNonLeak404
    ) {
        this.locationRepository = locationRepository;
        this.deletionGuardService = deletionGuardService;
        this.companyNonLeak404 = companyNonLeak404;
    }

    @Transactional(readOnly = true)
    public LocationEntity getActiveLocation(String locationId) {
        LocationEntity location = locationRepository.findByLocationIdAndTrashedAtIsNull(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
        deletionGuardService.assertCompanyAccessible(location.getCompanyId());
        return location;
    }

    @Transactional(readOnly = true)
    public LocationEntity getActiveLocationForTenant(String locationId, String tenantId) {
        LocationEntity location = locationRepository.findByLocationIdAndTrashedAtIsNull(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found for company"));
        if (!tenantId.equals(location.getCompanyId())) {
            if (companyNonLeak404) {
                throw new LocationNotFoundException();
            }
            throw new AccessDeniedException("tenant_id does not match location.companyId");
        }
        deletionGuardService.assertCompanyAccessible(location.getCompanyId());
        return location;
    }

    @Transactional(readOnly = true)
    public Page<LocationEntity> listActiveLocations(String companyId, Pageable pageable) {
        deletionGuardService.assertCompanyAccessible(companyId);
        return locationRepository.findAllByCompanyIdAndTrashedAtIsNull(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public long countOpenLocations(String companyId) {
        deletionGuardService.assertCompanyAccessible(companyId);
        return locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull(companyId, LocationStatus.OPEN);
    }
}

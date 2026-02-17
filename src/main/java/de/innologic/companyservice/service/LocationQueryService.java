package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationQueryService {

    private final LocationRepository locationRepository;
    private final DeletionGuardService deletionGuardService;

    public LocationQueryService(LocationRepository locationRepository, DeletionGuardService deletionGuardService) {
        this.locationRepository = locationRepository;
        this.deletionGuardService = deletionGuardService;
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

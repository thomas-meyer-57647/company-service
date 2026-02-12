package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationQueryService {

    private final LocationRepository locationRepository;

    public LocationQueryService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public LocationEntity getActiveLocation(String locationId) {
        return locationRepository.findByLocationIdAndTrashedAtIsNull(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));
    }

    @Transactional(readOnly = true)
    public LocationEntity getActiveLocationForCompany(String locationId, String companyId) {
        return locationRepository.findByLocationIdAndCompanyIdAndTrashedAtIsNull(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found for company"));
    }

    @Transactional(readOnly = true)
    public Page<LocationEntity> listActiveLocations(String companyId, Pageable pageable) {
        return locationRepository.findAllByCompanyIdAndTrashedAtIsNull(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public long countOpenLocations(String companyId) {
        return locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull(companyId, LocationStatus.OPEN);
    }
}

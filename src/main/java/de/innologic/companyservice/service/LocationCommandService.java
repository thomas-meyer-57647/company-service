package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ConflictException;
import de.innologic.companyservice.domain.ErrorCode;
import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.entity.TrashedCause;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.time.Instant;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationCommandService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;

    public LocationCommandService(CompanyRepository companyRepository, LocationRepository locationRepository) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public LocationEntity updateLocation(
            String companyId,
            String locationId,
            String name,
            String locationCode,
            String timezone,
            String modifiedBy
    ) {
        CompanyEntity company = getActiveCompany(companyId);
        ensureMainLocationValid(company);

        LocationEntity location = getActiveLocation(companyId, locationId);
        location.setName(name);
        location.setLocationCode(locationCode);
        location.setTimezone(timezone);
        location.setModifiedAt(Instant.now());
        location.setModifiedBy(modifiedBy);
        return location;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public LocationEntity closeLocation(String companyId, String locationId, String closedBy, String reason) {
        CompanyEntity company = getActiveCompany(companyId);
        ensureMainLocationValid(company);

        LocationEntity location = getActiveLocation(companyId, locationId);
        if (location.getStatus() == LocationStatus.OPEN) {
            long openCount = locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull(companyId, LocationStatus.OPEN);
            if (openCount <= 1) {
                throw new ConflictException(
                        ErrorCode.LAST_OPEN_LOCATION_REQUIRED,
                        "The last OPEN location cannot be closed"
                );
            }
        }
        if (locationId.equals(company.getMainLocationId())) {
            throw new ConflictException(ErrorCode.CANNOT_CLOSE_MAIN_LOCATION, "Main location cannot be closed");
        }
        if (location.getStatus() == LocationStatus.CLOSED) {
            return location;
        }

        Instant now = Instant.now();
        location.setStatus(LocationStatus.CLOSED);
        location.setClosedAt(now);
        location.setClosedBy(closedBy);
        location.setClosedReason(reason);
        location.setModifiedAt(now);
        location.setModifiedBy(closedBy);
        return location;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public LocationEntity reopenLocation(String companyId, String locationId, String reopenedBy) {
        CompanyEntity company = getActiveCompany(companyId);
        ensureMainLocationValid(company);

        LocationEntity location = getActiveLocation(companyId, locationId);
        location.setStatus(LocationStatus.OPEN);
        location.setClosedAt(null);
        location.setClosedBy(null);
        location.setClosedReason(null);
        location.setModifiedAt(Instant.now());
        location.setModifiedBy(reopenedBy);
        return location;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public LocationEntity trashLocation(String companyId, String locationId, String trashedBy) {
        CompanyEntity company = getActiveCompany(companyId);
        ensureMainLocationValid(company);

        LocationEntity location = getActiveLocation(companyId, locationId);
        if (locationId.equals(company.getMainLocationId())) {
            throw new ConflictException(ErrorCode.CANNOT_TRASH_MAIN_LOCATION, "Main location cannot be trashed");
        }

        if (location.getStatus() == LocationStatus.OPEN) {
            long openCount = locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull(companyId, LocationStatus.OPEN);
            if (openCount <= 1) {
                throw new ConflictException(
                        ErrorCode.LAST_OPEN_LOCATION_REQUIRED,
                        "The last OPEN location cannot be trashed"
                );
            }
        }

        Instant now = Instant.now();
        location.setTrashedAt(now);
        location.setTrashedBy(trashedBy);
        location.setTrashedCause(TrashedCause.MANUAL);
        location.setModifiedAt(now);
        location.setModifiedBy(trashedBy);
        return location;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public LocationEntity restoreLocation(String companyId, String locationId, String restoredBy) {
        CompanyEntity company = getActiveCompany(companyId);
        LocationEntity location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));

        if (!companyId.equals(location.getCompanyId())) {
            throw new ConflictException(ErrorCode.LOCATION_NOT_IN_COMPANY, "Location does not belong to company");
        }

        if (location.getTrashedAt() == null) {
            ensureMainLocationValid(company);
            return location;
        }

        Instant now = Instant.now();
        location.setTrashedAt(null);
        location.setTrashedBy(null);
        location.setTrashedCause(null);
        location.setModifiedAt(now);
        location.setModifiedBy(restoredBy);

        ensureMainLocationValid(company);
        ensureAtLeastOneOpenLocation(companyId);
        return location;
    }

    private CompanyEntity getActiveCompany(String companyId) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
        if (company.getTrashedAt() != null) {
            throw new ConflictException(
                    ErrorCode.COMPANY_TRASHED_OPERATION_NOT_ALLOWED,
                    "Operation is not allowed for trashed company: " + companyId
            );
        }
        return company;
    }

    private LocationEntity getActiveLocation(String companyId, String locationId) {
        return locationRepository.findByLocationIdAndCompanyIdAndTrashedAtIsNull(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found for company"));
    }

    private void ensureMainLocationValid(CompanyEntity company) {
        LocationEntity main = locationRepository.findByLocationIdAndCompanyIdAndTrashedAtIsNull(
                        company.getMainLocationId(),
                        company.getCompanyId()
                )
                .orElseThrow(() -> {
                    LocationEntity raw = locationRepository.findById(company.getMainLocationId()).orElse(null);
                    if (raw != null && raw.getTrashedAt() != null) {
                        return new ConflictException(ErrorCode.MAIN_LOCATION_REQUIRED, "Main location must not be trashed");
                    }
                    return new ConflictException(ErrorCode.MAIN_LOCATION_REQUIRED, "Main location must exist");
                });
        if (main.getStatus() != LocationStatus.OPEN) {
            throw new ConflictException(ErrorCode.MAIN_LOCATION_MUST_BE_OPEN, "Main location must be OPEN");
        }
    }

    private void ensureAtLeastOneOpenLocation(String companyId) {
        long open = locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull(companyId, LocationStatus.OPEN);
        if (open <= 0) {
            throw new ConflictException(ErrorCode.LAST_OPEN_LOCATION_REQUIRED, "At least one OPEN location is required");
        }
    }
}

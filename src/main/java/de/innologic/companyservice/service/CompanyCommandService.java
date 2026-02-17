package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ConflictException;
import de.innologic.companyservice.domain.ErrorCode;
import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.BootstrapIdempotencyEntity;
import de.innologic.companyservice.persistence.entity.CompanyEntity;
import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.entity.TrashedCause;
import de.innologic.companyservice.persistence.repository.BootstrapIdempotencyRepository;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyCommandService {

    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final BootstrapIdempotencyRepository bootstrapIdempotencyRepository;
    private final DeletionGuardService deletionGuardService;

    public CompanyCommandService(
            CompanyRepository companyRepository,
            LocationRepository locationRepository,
            BootstrapIdempotencyRepository bootstrapIdempotencyRepository,
            DeletionGuardService deletionGuardService
    ) {
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.bootstrapIdempotencyRepository = bootstrapIdempotencyRepository;
        this.deletionGuardService = deletionGuardService;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", allEntries = true),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public CompanyEntity createCompany(
            String name,
            String displayName,
            String timezone,
            String locale,
            String initialLocationName,
            String initialLocationCode,
            String initialLocationTimezone,
            String createdBy,
            String idempotencyKey
    ) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = null;
        if (normalizedKey != null) {
            requestHash = requestHash(
                    name,
                    displayName,
                    timezone,
                    locale,
                    initialLocationName,
                    initialLocationCode,
                    initialLocationTimezone
            );

            BootstrapIdempotencyEntity existing = bootstrapIdempotencyRepository.findById(normalizedKey).orElse(null);
            if (existing != null) {
                return resolveExistingBootstrapResult(existing, requestHash);
            }
        }

        Instant now = Instant.now();
        String companyId = UUID.randomUUID().toString();
        String locationId = UUID.randomUUID().toString();

        CompanyEntity company = new CompanyEntity();
        company.setCompanyId(companyId);
        company.setName(name);
        company.setDisplayName(displayName);
        company.setTimezone(timezone);
        company.setLocale(locale);
        company.setMainLocationId(locationId);
        company.setCreatedAt(now);
        company.setCreatedBy(createdBy);
        company.setModifiedAt(now);
        company.setModifiedBy(createdBy);

        LocationEntity location = new LocationEntity();
        location.setLocationId(locationId);
        location.setCompanyId(companyId);
        location.setName(initialLocationName);
        location.setLocationCode(initialLocationCode);
        location.setTimezone(initialLocationTimezone);
        location.setStatus(LocationStatus.OPEN);
        location.setCreatedAt(now);
        location.setCreatedBy(createdBy);
        location.setModifiedAt(now);
        location.setModifiedBy(createdBy);

        companyRepository.save(company);
        locationRepository.save(location);

        if (normalizedKey != null) {
            BootstrapIdempotencyEntity idempotency = new BootstrapIdempotencyEntity();
            idempotency.setIdempotencyKey(normalizedKey);
            idempotency.setRequestHash(requestHash);
            idempotency.setCompanyId(companyId);
            idempotency.setCreatedAt(now);
            idempotency.setCreatedBy(createdBy);
            try {
                bootstrapIdempotencyRepository.save(idempotency);
            } catch (DataIntegrityViolationException ex) {
                BootstrapIdempotencyEntity existing = bootstrapIdempotencyRepository.findById(normalizedKey)
                        .orElseThrow(() -> ex);
                return resolveExistingBootstrapResult(existing, requestHash);
            }
        }

        return company;
    }

    private CompanyEntity resolveExistingBootstrapResult(BootstrapIdempotencyEntity existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException(
                    ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                    "Idempotency-Key was already used with a different payload"
            );
        }
        return companyRepository.findById(existing.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Idempotent bootstrap company not found: " + existing.getCompanyId()
                ));
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private String requestHash(
            String name,
            String displayName,
            String timezone,
            String locale,
            String initialLocationName,
            String initialLocationCode,
            String initialLocationTimezone
    ) {
        String canonicalPayload = canonical(name)
                + "|" + canonical(displayName)
                + "|" + canonical(timezone)
                + "|" + canonical(locale)
                + "|" + canonical(initialLocationName)
                + "|" + canonical(initialLocationCode)
                + "|" + canonical(initialLocationTimezone);
        return sha256(canonicalPayload);
    }

    private String canonical(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public CompanyEntity updateCompany(
            String companyId,
            String name,
            String displayName,
            String timezone,
            String locale,
            String modifiedBy
    ) {
        CompanyEntity company = getActiveCompany(companyId);
        Instant now = Instant.now();
        company.setName(name);
        company.setDisplayName(displayName);
        company.setTimezone(timezone);
        company.setLocale(locale);
        company.setModifiedAt(now);
        company.setModifiedBy(modifiedBy);
        ensureMainLocationValid(company);
        return company;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId")
    })
    @Transactional
    public CompanyEntity updateLogo(String companyId, String logoFileRef, String modifiedBy) {
        CompanyEntity company = getActiveCompany(companyId);
        company.setLogoFileRef(logoFileRef);
        company.setModifiedAt(Instant.now());
        company.setModifiedBy(modifiedBy);
        return company;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId")
    })
    @Transactional
    public CompanyEntity removeLogo(String companyId, String modifiedBy) {
        CompanyEntity company = getActiveCompany(companyId);
        company.setLogoFileRef(null);
        company.setModifiedAt(Instant.now());
        company.setModifiedBy(modifiedBy);
        return company;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public CompanyEntity setMainLocation(String companyId, String locationId, String modifiedBy) {
        CompanyEntity company = getActiveCompany(companyId);
        LocationEntity location = locationRepository.findByLocationIdAndCompanyIdAndTrashedAtIsNull(locationId, companyId)
                .orElseThrow(() -> new ConflictException(
                        ErrorCode.LOCATION_NOT_IN_COMPANY,
                        "Location must belong to the company and must not be trashed"
                ));
        if (location.getStatus() != LocationStatus.OPEN) {
            throw new ConflictException(ErrorCode.MAIN_LOCATION_MUST_BE_OPEN, "Main location must be OPEN");
        }
        company.setMainLocationId(locationId);
        company.setModifiedAt(Instant.now());
        company.setModifiedBy(modifiedBy);
        ensureMainLocationValid(company);
        return company;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public CompanyEntity trashCompany(String companyId, String trashedBy) {
        CompanyEntity company = getActiveCompany(companyId);
        Instant now = Instant.now();
        company.setTrashedAt(now);
        company.setTrashedBy(trashedBy);
        company.setModifiedAt(now);
        company.setModifiedBy(trashedBy);

        List<LocationEntity> locations = locationRepository.findAllByCompanyId(companyId);
        for (LocationEntity location : locations) {
            if (location.getTrashedAt() == null) {
                location.setTrashedAt(now);
                location.setTrashedBy(trashedBy);
                location.setTrashedCause(TrashedCause.CASCADE);
                location.setModifiedAt(now);
                location.setModifiedBy(trashedBy);
            }
        }
        return company;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "companiesById", key = "#companyId"),
            @CacheEvict(cacheNames = "locationsByCompany", allEntries = true)
    })
    @Transactional
    public CompanyEntity restoreCompany(String companyId, String restoredBy) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));

        Instant now = Instant.now();
        company.setTrashedAt(null);
        company.setTrashedBy(null);
        company.setModifiedAt(now);
        company.setModifiedBy(restoredBy);

        List<LocationEntity> locations = locationRepository.findAllByCompanyId(companyId);
        if (locations.isEmpty()) {
            throw new ConflictException(ErrorCode.LAST_OPEN_LOCATION_REQUIRED, "Company must have at least one OPEN location");
        }

        for (LocationEntity location : locations) {
            if (location.getTrashedAt() != null && location.getTrashedCause() == TrashedCause.CASCADE) {
                location.setTrashedAt(null);
                location.setTrashedBy(null);
                location.setTrashedCause(null);
                location.setModifiedAt(now);
                location.setModifiedBy(restoredBy);
            }
        }

        ensureAtLeastOneOpenLocation(companyId);
        if (!isMainLocationValid(company)) {
            LocationEntity replacement = locations.stream()
                    .filter(location -> location.getTrashedAt() == null)
                    .filter(location -> location.getStatus() == LocationStatus.OPEN)
                    .findFirst()
                    .orElseThrow(() -> new ConflictException(
                            ErrorCode.LAST_OPEN_LOCATION_REQUIRED,
                            "Company must have at least one OPEN location"
                    ));
            company.setMainLocationId(replacement.getLocationId());
        }
        ensureMainLocationValid(company);
        return company;
    }

    private CompanyEntity getActiveCompany(String companyId) {
        deletionGuardService.assertCompanyAccessible(companyId);
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

    private boolean isMainLocationValid(CompanyEntity company) {
        return locationRepository.findByLocationIdAndCompanyIdAndTrashedAtIsNull(company.getMainLocationId(), company.getCompanyId())
                .filter(location -> location.getStatus() == LocationStatus.OPEN)
                .isPresent();
    }

    private void ensureAtLeastOneOpenLocation(String companyId) {
        long open = locationRepository.countByCompanyIdAndStatusAndTrashedAtIsNull(companyId, LocationStatus.OPEN);
        if (open <= 0) {
            throw new ConflictException(ErrorCode.LAST_OPEN_LOCATION_REQUIRED, "At least one OPEN location is required");
        }
    }
}

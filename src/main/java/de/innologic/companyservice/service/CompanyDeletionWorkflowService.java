package de.innologic.companyservice.service;

import de.innologic.companyservice.domain.ConflictException;
import de.innologic.companyservice.domain.ErrorCode;
import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.persistence.entity.DeletionAckEntity;
import de.innologic.companyservice.persistence.entity.DeletionAckEntity.DeletionAckId;
import de.innologic.companyservice.persistence.entity.DeletionState;
import de.innologic.companyservice.persistence.entity.DeletionTombstoneEntity;
import de.innologic.companyservice.persistence.repository.CompanyRepository;
import de.innologic.companyservice.persistence.repository.DeletionAckRepository;
import de.innologic.companyservice.persistence.repository.DeletionTombstoneRepository;
import de.innologic.companyservice.persistence.repository.LocationRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CompanyDeletionWorkflowService {

    private final DeletionTombstoneRepository deletionTombstoneRepository;
    private final DeletionAckRepository deletionAckRepository;
    private final CompanyRepository companyRepository;
    private final LocationRepository locationRepository;
    private final Set<String> requiredServices;

    public CompanyDeletionWorkflowService(
            DeletionTombstoneRepository deletionTombstoneRepository,
            DeletionAckRepository deletionAckRepository,
            CompanyRepository companyRepository,
            LocationRepository locationRepository,
            @Value("${app.deletion.required-services:}") String requiredServicesRaw
    ) {
        this.deletionTombstoneRepository = deletionTombstoneRepository;
        this.deletionAckRepository = deletionAckRepository;
        this.companyRepository = companyRepository;
        this.locationRepository = locationRepository;
        this.requiredServices = Arrays.stream(requiredServicesRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    @Transactional
    public DeletionTombstoneEntity startDeletion(String companyId, String requestedBySub, String idempotencyKey) {
        DeletionTombstoneEntity existing = deletionTombstoneRepository.findByCompanyId(companyId).orElse(null);
        if (existing != null) {
            if (StringUtils.hasText(idempotencyKey)
                    && StringUtils.hasText(existing.getIdempotencyKey())
                    && !existing.getIdempotencyKey().equals(idempotencyKey.trim())) {
                throw new ConflictException(
                        ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                        "Idempotency-Key was already used with a different delete request"
                );
            }
            return existing;
        }

        if (companyRepository.findById(companyId).isEmpty()) {
            throw new ResourceNotFoundException("Company not found: " + companyId);
        }

        DeletionTombstoneEntity tombstone = new DeletionTombstoneEntity();
        tombstone.setDeletionId(UUID.randomUUID().toString());
        tombstone.setCompanyId(companyId);
        tombstone.setState(DeletionState.IN_PROGRESS);
        tombstone.setRequestedAtUtc(Instant.now());
        tombstone.setRequestedBySub(requestedBySub);
        tombstone.setIdempotencyKey(normalizeIdempotencyKey(idempotencyKey));
        deletionTombstoneRepository.save(tombstone);

        // Transitional mode without messaging: complete immediately if no external acks are configured.
        if (requiredServices.isEmpty()) {
            completeDeletion(tombstone);
        }
        return tombstone;
    }

    @Transactional
    public DeletionTombstoneEntity acknowledgeDeletion(String companyId, String serviceName, String ackedBySub) {
        DeletionTombstoneEntity tombstone = deletionTombstoneRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Deletion workflow not found for company: " + companyId));

        if (tombstone.getState() != DeletionState.IN_PROGRESS) {
            return tombstone;
        }

        String normalizedService = normalizeServiceName(serviceName);
        if (!requiredServices.isEmpty() && !requiredServices.contains(normalizedService)) {
            throw new ConflictException(
                    ErrorCode.VALIDATION_FAILED,
                    "Service is not part of required deletion acknowledgements: " + normalizedService
            );
        }

        DeletionAckId ackId = new DeletionAckId(tombstone.getDeletionId(), normalizedService);
        if (deletionAckRepository.findById(ackId).isEmpty()) {
            DeletionAckEntity ack = new DeletionAckEntity();
            ack.setDeletionId(tombstone.getDeletionId());
            ack.setServiceName(normalizedService);
            ack.setAckedAtUtc(Instant.now());
            ack.setAckedBySub(ackedBySub);
            deletionAckRepository.save(ack);
        }

        if (allRequiredAcksReceived(tombstone.getDeletionId())) {
            completeDeletion(tombstone);
        }
        return tombstone;
    }

    private boolean allRequiredAcksReceived(String deletionId) {
        if (requiredServices.isEmpty()) {
            return true;
        }
        Set<String> received = deletionAckRepository.findAllByDeletionId(deletionId).stream()
                .map(DeletionAckEntity::getServiceName)
                .collect(Collectors.toSet());
        return received.containsAll(requiredServices);
    }

    private void completeDeletion(DeletionTombstoneEntity tombstone) {
        locationRepository.deleteAllByCompanyId(tombstone.getCompanyId());
        companyRepository.deleteById(tombstone.getCompanyId());
        tombstone.setState(DeletionState.COMPLETED);
        tombstone.setCompletedAtUtc(Instant.now());
        tombstone.setFailedAtUtc(null);
        tombstone.setFailureReason(null);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return idempotencyKey.trim();
    }

    private String normalizeServiceName(String serviceName) {
        if (!StringUtils.hasText(serviceName)) {
            throw new ConflictException(ErrorCode.VALIDATION_FAILED, "serviceName must not be blank");
        }
        return serviceName.trim().toLowerCase(Locale.ROOT);
    }
}

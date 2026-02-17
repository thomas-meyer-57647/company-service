package de.innologic.companyservice.api.dto.company;

import de.innologic.companyservice.persistence.entity.DeletionTombstoneEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Deletion workflow status response.")
public record CompanyDeletionResponse(
        @Schema(example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String companyId,
        @Schema(example = "a5f7d1b0-4c7f-4cc7-9e1f-0c2c2b7d9c4a")
        String deletionId,
        @Schema(example = "IN_PROGRESS")
        String state,
        Instant requestedAtUtc,
        Instant completedAtUtc,
        Instant failedAtUtc,
        String failureReason
) {
    public static CompanyDeletionResponse from(DeletionTombstoneEntity entity) {
        return new CompanyDeletionResponse(
                entity.getCompanyId(),
                entity.getDeletionId(),
                entity.getState().name(),
                entity.getRequestedAtUtc(),
                entity.getCompletedAtUtc(),
                entity.getFailedAtUtc(),
                entity.getFailureReason()
        );
    }
}

package de.innologic.companyservice.api.dto.location;

import de.innologic.companyservice.persistence.entity.LocationEntity;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.persistence.entity.TrashedCause;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Location response payload.")
public record LocationResponse(
        @Schema(example = "4f8aa4a4-b4f2-4ec8-87d2-0f7dc4e7b8e2")
        String locationId,
        @Schema(example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String companyId,
        @Schema(example = "Headquarters")
        String name,
        @Schema(example = "HQ-BER")
        String locationCode,
        @Schema(example = "Europe/Berlin")
        String timezone,
        LocationStatus status,
        Instant closedAt,
        String closedBy,
        String closedReason,
        Instant trashedAt,
        String trashedBy,
        TrashedCause trashedCause,
        Instant createdAt,
        String createdBy,
        Instant modifiedAt,
        String modifiedBy,
        Long version
) {
    public static LocationResponse from(LocationEntity entity) {
        return new LocationResponse(
                entity.getLocationId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getLocationCode(),
                entity.getTimezone(),
                entity.getStatus(),
                entity.getClosedAt(),
                entity.getClosedBy(),
                entity.getClosedReason(),
                entity.getTrashedAt(),
                entity.getTrashedBy(),
                entity.getTrashedCause(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getModifiedAt(),
                entity.getModifiedBy(),
                entity.getVersion()
        );
    }
}

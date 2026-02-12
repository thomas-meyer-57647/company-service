package de.innologic.companyservice.api.dto.company;

import de.innologic.companyservice.persistence.entity.CompanyEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Company response payload.")
public record CompanyResponse(
        @Schema(example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String companyId,
        @Schema(example = "Acme Corporation")
        String name,
        @Schema(example = "ACME")
        String displayName,
        @Schema(example = "Europe/Berlin")
        String timezone,
        @Schema(example = "de-DE")
        String locale,
        @Schema(example = "file_01JQ2EGBDPK9X0G9B05RB4VKD8")
        String logoFileRef,
        @Schema(example = "4f8aa4a4-b4f2-4ec8-87d2-0f7dc4e7b8e2")
        String mainLocationId,
        Instant createdAt,
        String createdBy,
        Instant modifiedAt,
        String modifiedBy,
        Instant trashedAt,
        String trashedBy,
        Long version
) {
    public static CompanyResponse from(CompanyEntity entity) {
        return new CompanyResponse(
                entity.getCompanyId(),
                entity.getName(),
                entity.getDisplayName(),
                entity.getTimezone(),
                entity.getLocale(),
                entity.getLogoFileRef(),
                entity.getMainLocationId(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getModifiedAt(),
                entity.getModifiedBy(),
                entity.getTrashedAt(),
                entity.getTrashedBy(),
                entity.getVersion()
        );
    }
}

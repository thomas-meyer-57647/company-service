package de.innologic.companyservice.api.dto.company;

import de.innologic.companyservice.persistence.entity.CompanyEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Company response payload.")
public record CompanyResponse(
        @Schema(description = "Unique identifier of the company", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String companyId,
        @Schema(description = "Resource owner type for linking to other APIs", example = "COMPANY")
        String contactOwnerType,
        @Schema(description = "Resource owner identifier (same as companyId)", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String contactOwnerId,
        @Schema(description = "Official company name", example = "Acme Corporation")
        String name,
        @Schema(description = "Display name used in UIs", example = "ACME")
        String displayName,
        @Schema(description = "Timezone chosen for the company", example = "Europe/Berlin")
        String timezone,
        @Schema(description = "Locale tag for formatting", example = "de-DE")
        String locale,
        @Schema(description = "Reference to the current logo file", example = "file_01JQ2EGBDPK9X0G9B05RB4VKD8")
        String logoFileRef,
        @Schema(description = "Identifier of the headquarter location", example = "4f8aa4a4-b4f2-4ec8-87d2-0f7dc4e7b8e2")
        String mainLocationId,
        @Schema(description = "Creation timestamp", example = "2026-03-05T12:34:56Z")
        Instant createdAt,
        @Schema(description = "User or service who created the company", example = "bootstrap")
        String createdBy,
        @Schema(description = "Last modification timestamp", example = "2026-03-06T08:10:22Z")
        Instant modifiedAt,
        @Schema(description = "Last modifier (user or service)", example = "admin-service")
        String modifiedBy,
        @Schema(description = "Timestamp when the company was trashed", example = "2026-03-10T11:20:30Z")
        Instant trashedAt,
        @Schema(description = "Actor who trashed the company", example = "deletion-service")
        String trashedBy,
        @Schema(description = "Entity version for optimistic locking", example = "3")
        Long version
) {
    public static CompanyResponse from(CompanyEntity entity) {
        return new CompanyResponse(
                entity.getCompanyId(),
                "COMPANY",
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

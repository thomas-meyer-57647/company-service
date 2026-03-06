package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload to update a company.")
public record CompanyUpdateRequest(
        @Schema(example = "Acme Corporation")
        @NotBlank
        String name,
        @Schema(example = "ACME")
        String displayName,
        @Schema(example = "Europe/Berlin")
        String timezone,
        @Schema(example = "de-DE")
        String locale,
        @Schema(description = "Entity version for optimistic locking", example = "3")
        @NotNull
        Long version
) {

    @Schema(description = "Request payload for partial company updates.")
    public record CompanyPatchRequest(
            @Schema(example = "Acme Corporation")
            String name,
            @Schema(example = "ACME")
            String displayName,
            @Schema(example = "Europe/Berlin")
            String timezone,
            @Schema(example = "de-DE")
            String locale,
            @Schema(description = "Entity version for optimistic locking", example = "3")
            @NotNull
            Long version
    ) {
    }
}

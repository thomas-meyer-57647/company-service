package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

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
        String locale
) {
}

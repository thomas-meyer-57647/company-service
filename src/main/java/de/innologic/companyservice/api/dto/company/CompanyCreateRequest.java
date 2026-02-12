package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload to create a company with its initial location.")
public record CompanyCreateRequest(
        @Schema(example = "Acme Corporation")
        @NotBlank
        String name,
        @Schema(example = "ACME")
        String displayName,
        @Schema(example = "Europe/Berlin")
        String timezone,
        @Schema(example = "de-DE")
        String locale,
        @Valid
        @NotNull
        InitialLocationRequest initialLocation
) {
}

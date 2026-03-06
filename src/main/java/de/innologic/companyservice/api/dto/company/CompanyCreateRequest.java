package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload to create a company with its initial location.")
public record CompanyCreateRequest(
    @Schema(description = "Official legal name of the company", example = "Acme Corporation")
    @NotBlank
    String name,
    @Schema(description = "Preferred short display name", example = "ACME")
    String displayName,
    @Schema(description = "Timezone identifier for the company", example = "Europe/Berlin")
    String timezone,
    @Schema(description = "Locale tag used for formatting", example = "de-DE")
    String locale,
    @Schema(description = "Initial location that will serve as headquarter")
    @Valid
    @NotNull
    InitialLocationRequest initialLocation
) {
}

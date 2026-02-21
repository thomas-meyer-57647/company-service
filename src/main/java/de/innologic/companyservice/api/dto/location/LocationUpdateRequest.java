package de.innologic.companyservice.api.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to update a location.")
public record LocationUpdateRequest(
        @Schema(example = "Branch Berlin")
        @NotBlank
        String name,
        @Schema(example = "BER-01")
        String locationCode,
        @Schema(example = "Europe/Berlin")
        String timezone,
        @Schema(example = "DE")
        String countryCode,
        @Schema(example = "BE")
        String regionCode
) {
}

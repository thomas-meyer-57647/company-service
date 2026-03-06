package de.innologic.companyservice.api.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to create a new location.")
public record LocationCreateRequest(
        @Schema(example = "Regional Office")
        @NotBlank
        String name,
        @Schema(example = "REG-1")
        String locationCode,
        @Schema(example = "Europe/Berlin")
        @NotBlank
        String timezone,
        @Schema(description = "ISO-3166 alpha-2 code", example = "DE")
        String countryCode,
        @Schema(description = "Region or state code (optional)", example = "BE")
        String regionCode
) {
}

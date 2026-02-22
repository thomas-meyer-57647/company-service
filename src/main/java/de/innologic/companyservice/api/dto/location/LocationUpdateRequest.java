package de.innologic.companyservice.api.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
        @Pattern(regexp = "^$|^[A-Z]{2}$", flags = Pattern.Flag.CASE_INSENSITIVE)
        String countryCode,
        @Schema(example = "DE-HB")
        @Size(max = 32)
        String regionCode
) {
}

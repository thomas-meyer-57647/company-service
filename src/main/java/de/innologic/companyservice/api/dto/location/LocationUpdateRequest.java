package de.innologic.companyservice.api.dto.location;

import de.innologic.companyservice.persistence.entity.LocationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        String regionCode,
        @Schema(description = "Entity version for optimistic locking", example = "3")
        @NotNull
        Long version
) {

    @Schema(description = "Request payload for partial location updates.")
    public record LocationPatchRequest(
            @Schema(example = "Branch Berlin")
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
            String regionCode,
            @Schema(description = "Optional status transition (CLOSED/OPEN)", example = "CLOSED")
            LocationStatus status,
            @Schema(description = "Entity version for optimistic locking", example = "3")
            @NotNull
            Long version
    ) {
    }
}

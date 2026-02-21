package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Initial main location that is created together with the company.")
public record InitialLocationRequest(
        @Schema(example = "Headquarters")
        @NotBlank
        String name,
        @Schema(example = "HQ-BER")
        String locationCode,
        @Schema(example = "Europe/Berlin")
        String timezone,
        @Schema(example = "DE")
        String countryCode,
        @Schema(example = "BE")
        String regionCode
) {
}

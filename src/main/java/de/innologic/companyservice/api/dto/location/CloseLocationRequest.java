package de.innologic.companyservice.api.dto.location;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload to close a location.")
public record CloseLocationRequest(
        @Schema(example = "Temporarily closed for renovation")
        String reason
) {
}

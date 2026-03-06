package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public final class HeadquarterDto {

    private HeadquarterDto() {
    }

    @Schema(description = "Headquarter response payload containing the currently configured main location.")
    public static record HeadquarterResponse(
            @Schema(description = "Location id of the configured headquarter", example = "4f8aa4a4-b4f2-4ec8-87d2-0f7dc4e7b8e2")
            String locationId
    ) {
    }

    @Schema(description = "Request payload to change the headquarter for a company.")
    public static record SetHeadquarterRequest(
            @Schema(description = "Location id that should become the new headquarter", example = "b43f1ebf-36b4-4a9d-843c-3f5be3eefe54")
            @NotBlank
            String locationId
    ) {
    }
}

package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to set the main location of a company.")
public record SetMainLocationRequest(
        @Schema(example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        @NotBlank
        String locationId
) {
}

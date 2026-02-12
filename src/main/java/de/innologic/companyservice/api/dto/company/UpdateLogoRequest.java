package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload to set a company logo file reference.")
public record UpdateLogoRequest(
        @Schema(example = "file_01JQ2EGBDPK9X0G9B05RB4VKD8")
        @NotBlank
        String logoFileRef
) {
}

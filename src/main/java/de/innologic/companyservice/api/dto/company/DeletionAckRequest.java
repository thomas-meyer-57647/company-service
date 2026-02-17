package de.innologic.companyservice.api.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Ack payload for company deletion workflow.")
public record DeletionAckRequest(
        @Schema(example = "template-service")
        @NotBlank
        String serviceName
) {
}

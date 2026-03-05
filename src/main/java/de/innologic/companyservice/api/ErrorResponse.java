package de.innologic.companyservice.api;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API error response")
public record ErrorResponse(
        @Schema(description = "Timestamp of the error in UTC", example = "2026-03-05T12:34:56Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "Domain-specific error code from the catalog", example = "VALIDATION_FAILED")
        String code,

        @Schema(description = "Human-readable message", example = "Request validation failed")
        String message,

        @Schema(description = "Request path", example = "/api/v1/companies")
        String path,

        @Schema(description = "Correlation id that can be used in support tickets", example = "3b8a97e4-ff76-4c57-a8f4-12dbeccd5e93")
        String correlationId,

        @Schema(description = "Optional list of validation or contextual details", example = "[\"name: must not be blank\"]")
        List<String> details
) {
    public static ErrorResponse of(
            int status,
            String code,
            String message,
            String path,
            String correlationId,
            List<String> details
    ) {
        return new ErrorResponse(Instant.now(), status, code, message, path, correlationId, details);
    }
}

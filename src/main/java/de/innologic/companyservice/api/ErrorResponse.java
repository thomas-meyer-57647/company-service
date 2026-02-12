package de.innologic.companyservice.api;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        String correlationId,
        List<String> details
) {
    public static ErrorResponse of(
            int status,
            String errorCode,
            String message,
            String path,
            String correlationId,
            List<String> details
    ) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, path, correlationId, details);
    }
}

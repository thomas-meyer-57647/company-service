package de.innologic.companyservice.persistence.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Defines whether a location is operational.")
public enum LocationStatus {
    @Schema(description = "Location accepts assignments", example = "OPEN")
    OPEN,
    @Schema(description = "Location is temporarily not accepting work", example = "CLOSED")
    CLOSED
}

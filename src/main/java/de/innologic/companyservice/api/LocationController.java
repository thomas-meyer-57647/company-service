package de.innologic.companyservice.api;

import de.innologic.companyservice.api.dto.location.CloseLocationRequest;
import de.innologic.companyservice.api.dto.location.LocationResponse;
import de.innologic.companyservice.api.dto.location.LocationUpdateRequest;
import de.innologic.companyservice.config.RequestContext;
import de.innologic.companyservice.service.LocationCommandService;
import de.innologic.companyservice.service.LocationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/location")
@Tag(name = "Locations", description = "Location endpoints")
public class LocationController {

    private final LocationQueryService locationQueryService;
    private final LocationCommandService locationCommandService;
    private final RequestContext requestContext;

    public LocationController(
            LocationQueryService locationQueryService,
            LocationCommandService locationCommandService,
            RequestContext requestContext
    ) {
        this.locationQueryService = locationQueryService;
        this.locationCommandService = locationCommandService;
        this.requestContext = requestContext;
    }

    @GetMapping("/{locationId}")
    @Operation(
            summary = "Get location",
            description = "Location read by id. tenant_id is matched against location.companyId. JWT example: {\"sub\":\"user_123\",\"tenant_id\":\"01J3Z4...\",\"aud\":[\"company-service\"],\"scope\":\"company:read\"}",
            security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:read"})}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location found"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse getLocation(
            @PathVariable String locationId,
            @Parameter(description = "Company context for company-scoped checks", required = true,
                    examples = @ExampleObject(value = "d290f1ee-6c54-4b01-90e6-d701748f0851"))
            @RequestHeader(name = "X-Company-Id", required = false) String companyId
    ) {
        return LocationResponse.from(locationQueryService.getActiveLocationForTenant(locationId, resolveTenantId(companyId)));
    }

    @PutMapping("/{locationId}")
    @Operation(summary = "Update location", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location updated"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse updateLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Company-Id", required = false) String companyId,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        return LocationResponse.from(locationCommandService.updateLocation(
                resolveTenantId(companyId),
                locationId,
                request.name(),
                request.locationCode(),
                request.timezone(),
                request.countryCode(),
                request.regionCode(),
                requestContext.subjectId()
        ));
    }

    @PostMapping("/{locationId}/close")
    @Operation(summary = "Close location", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location closed"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse closeLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Company-Id", required = false) String companyId,
            @Valid @RequestBody(required = false) CloseLocationRequest request
    ) {
        String reason = request == null ? null : request.reason();
        return LocationResponse.from(locationCommandService.closeLocation(
                resolveTenantId(companyId),
                locationId,
                requestContext.subjectId(),
                reason
        ));
    }

    @PostMapping("/{locationId}/reopen")
    @Operation(summary = "Reopen location", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location reopened"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse reopenLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Company-Id", required = false) String companyId
    ) {
        return LocationResponse.from(locationCommandService.reopenLocation(
                resolveTenantId(companyId),
                locationId,
                requestContext.subjectId()
        ));
    }

    @DeleteMapping("/{locationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete location", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Location deleted"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void trashLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Company-Id", required = false) String companyId
    ) {
        locationCommandService.trashLocation(resolveTenantId(companyId), locationId, requestContext.subjectId());
    }

    @PostMapping("/{locationId}/restore")
    @Operation(summary = "Restore location", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location restored"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse restoreLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Company-Id", required = false) String companyId
    ) {
        return LocationResponse.from(locationCommandService.restoreLocation(
                resolveTenantId(companyId),
                locationId,
                requestContext.subjectId()
        ));
    }

    private String resolveTenantId(String companyIdHeader) {
        var tenantIdFromJwt = requestContext.tenantIdFromJwt();
        if (tenantIdFromJwt.isPresent()) {
            String tenantId = tenantIdFromJwt.get();
            if (companyIdHeader != null && !companyIdHeader.isBlank() && !tenantId.equals(companyIdHeader)) {
                throw new AccessDeniedException("tenant_id does not match X-Company-Id header");
            }
            return tenantId;
        }

        if (companyIdHeader != null && !companyIdHeader.isBlank()) {
            return companyIdHeader;
        }
        return requestContext.companyIdFromDevHeader()
                .orElseThrow(() -> new IllegalArgumentException("Missing company context. Provide X-Company-Id header."));
    }
}

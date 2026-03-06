package de.innologic.companyservice.api;

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
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/location")
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
            @Parameter(description = "Company context for company-scoped checks via X-Tenant-Id header", required = true,
                    examples = @ExampleObject(value = "d290f1ee-6c54-4b01-90e6-d701748f0851"))
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader
    ) {
        requestContext.assertTenantHeaderMatches(tenantIdHeader);
        return LocationResponse.from(locationQueryService.getActiveLocationForTenant(locationId, resolveTenantId(tenantIdHeader)));
    }

    @PutMapping("/{locationId}")
    @Operation(
            summary = "Update location",
            description = "Partielles Update: Felder, die fehlen oder null sind, bleiben unverändert (null = ignore). Leere Strings bei countryCode/regionCode werden als Löschen interpretiert und zu null normalisiert.",
            security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location updated"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse updateLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @Valid @RequestBody LocationUpdateRequest request
    ) {
        requestContext.assertTenantHeaderMatches(tenantIdHeader);
        return LocationResponse.from(locationCommandService.updateLocation(
                resolveTenantId(tenantIdHeader),
                locationId,
                request.name(),
                request.locationCode(),
                request.timezone(),
                request.countryCode(),
                request.regionCode(),
                request.version(),
                requestContext.subjectId()
        ));
    }

    @PatchMapping("/{locationId}")
    @Operation(
            summary = "Partial update location",
            description = "Allows updating the location fields and optionally toggling the status between OPEN and CLOSED (headquarters cannot be CLOSED and the last OPEN location cannot be shut).",
            security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location updated", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Version conflict or business invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public LocationResponse patchLocation(
            @PathVariable String locationId,
            @RequestHeader(name = "X-Tenant-Id", required = false) String tenantIdHeader,
            @Valid @RequestBody LocationUpdateRequest.LocationPatchRequest request
    ) {
        requestContext.assertTenantHeaderMatches(tenantIdHeader);
        return LocationResponse.from(locationCommandService.patchLocation(
                resolveTenantId(tenantIdHeader),
                locationId,
                request,
                requestContext.subjectId()
        ));
    }

    private String resolveTenantId(String tenantIdHeader) {
        requestContext.assertTenantHeaderMatches(tenantIdHeader);
        return requestContext.tenantId();
    }
}

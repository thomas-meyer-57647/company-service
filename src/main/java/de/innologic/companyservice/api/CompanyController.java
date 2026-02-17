package de.innologic.companyservice.api;

import de.innologic.companyservice.api.dto.company.CompanyCreateRequest;
import de.innologic.companyservice.api.dto.company.CompanyDeletionResponse;
import de.innologic.companyservice.api.dto.company.CompanyResponse;
import de.innologic.companyservice.api.dto.company.CompanyUpdateRequest;
import de.innologic.companyservice.api.dto.company.DeletionAckRequest;
import de.innologic.companyservice.api.dto.company.SetMainLocationRequest;
import de.innologic.companyservice.api.dto.company.UpdateLogoRequest;
import de.innologic.companyservice.api.dto.location.LocationResponse;
import de.innologic.companyservice.config.RequestContext;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.service.CompanyCommandService;
import de.innologic.companyservice.service.CompanyDeletionWorkflowService;
import de.innologic.companyservice.service.CompanyQueryService;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@Tag(name = "Companies", description = "Company endpoints")
public class CompanyController {

    private final CompanyCommandService companyCommandService;
    private final CompanyQueryService companyQueryService;
    private final CompanyDeletionWorkflowService companyDeletionWorkflowService;
    private final RequestContext requestContext;

    public CompanyController(
            CompanyCommandService companyCommandService,
            CompanyQueryService companyQueryService,
            CompanyDeletionWorkflowService companyDeletionWorkflowService,
            RequestContext requestContext
    ) {
        this.companyCommandService = companyCommandService;
        this.companyQueryService = companyQueryService;
        this.companyDeletionWorkflowService = companyDeletionWorkflowService;
        this.requestContext = requestContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Create company (bootstrap)",
            description = "Creates a company with exactly one initial main location. JWT example (bootstrap): {\"sub\":\"auth-service\",\"aud\":[\"company-service\"],\"scope\":\"company:create\"}",
            security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:create"})}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Company created"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse createCompany(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(examples = @ExampleObject(
                            value = "{\"name\":\"Acme Corporation\",\"displayName\":\"ACME\",\"timezone\":\"Europe/Berlin\",\"locale\":\"de-DE\",\"initialLocation\":{\"name\":\"Headquarters\",\"locationCode\":\"HQ-BER\",\"timezone\":\"Europe/Berlin\"}}"
                    )))
            CompanyCreateRequest request
    ) {
        return CompanyResponse.from(companyCommandService.createCompany(
                request.name(),
                request.displayName(),
                request.timezone(),
                request.locale(),
                request.initialLocation().name(),
                request.initialLocation().locationCode(),
                request.initialLocation().timezone(),
                requestContext.subjectId(),
                idempotencyKey
        ));
    }

    @GetMapping("/{companyId}")
    @Operation(
            summary = "Get company",
            description = "JWT tenant call example: {\"sub\":\"user_123\",\"tenant_id\":\"{companyId}\",\"aud\":[\"company-service\"],\"scope\":\"company:read\"}",
            security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:read"})}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company found"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse getCompany(@PathVariable String companyId) {
        requestContext.assertTenantAccess(companyId);
        return CompanyResponse.from(companyQueryService.getActiveCompany(companyId));
    }

    @PutMapping("/{companyId}")
    @Operation(summary = "Update company", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company updated"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse updateCompany(
            @PathVariable String companyId,
            @Valid @RequestBody CompanyUpdateRequest request
    ) {
        requestContext.assertTenantAccess(companyId);
        return CompanyResponse.from(companyCommandService.updateCompany(
                companyId,
                request.name(),
                request.displayName(),
                request.timezone(),
                request.locale(),
                requestContext.subjectId()
        ));
    }

    @PutMapping("/{companyId}/main-location")
    @Operation(summary = "Set main location", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Main location updated"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company/location not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse setMainLocation(
            @PathVariable String companyId,
            @Valid @RequestBody SetMainLocationRequest request
    ) {
        requestContext.assertTenantAccess(companyId);
        return CompanyResponse.from(companyCommandService.setMainLocation(companyId, request.locationId(), requestContext.subjectId()));
    }

    @PutMapping("/{companyId}/logo")
    @Operation(summary = "Update company logo", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logo updated"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse updateLogo(
            @PathVariable String companyId,
            @Valid @RequestBody UpdateLogoRequest request
    ) {
        requestContext.assertTenantAccess(companyId);
        return CompanyResponse.from(companyCommandService.updateLogo(companyId, request.logoFileRef(), requestContext.subjectId()));
    }

    @DeleteMapping("/{companyId}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete company logo", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:write"})})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logo removed"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public void deleteLogo(
            @PathVariable String companyId
    ) {
        requestContext.assertTenantAccess(companyId);
        companyCommandService.removeLogo(companyId, requestContext.subjectId());
    }

    @DeleteMapping("/{companyId}")
    @Operation(
            summary = "Start company deletion workflow",
            description = "Creates a deletion tombstone and starts hard-delete workflow. 202 response example: {\"companyId\":\"01J3Z4...\",\"deletionId\":\"a5f7d1b0-...\",\"state\":\"IN_PROGRESS\",\"requestedAtUtc\":\"2026-02-12T12:34:00Z\"}",
            security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Deletion workflow accepted", content = @Content(schema = @Schema(implementation = CompanyDeletionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CompanyDeletionResponse> deleteCompany(
            @PathVariable String companyId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        requestContext.assertTenantAccess(companyId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CompanyDeletionResponse.from(
                        companyDeletionWorkflowService.startDeletion(companyId, requestContext.subjectId(), idempotencyKey)
                ));
    }

    @PostMapping("/{companyId}/deletion-ack")
    @Operation(summary = "Acknowledge company deletion", description = "Internal transition endpoint for deletion workflow acknowledgements.")
    @SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ack accepted", content = @Content(schema = @Schema(implementation = CompanyDeletionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Deletion workflow not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invalid acknowledgement", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyDeletionResponse acknowledgeDeletion(
            @PathVariable String companyId,
            @Valid @RequestBody DeletionAckRequest request
    ) {
        requestContext.assertTenantAccess(companyId);
        return CompanyDeletionResponse.from(
                companyDeletionWorkflowService.acknowledgeDeletion(companyId, request.serviceName(), requestContext.subjectId())
        );
    }

    @PostMapping("/{companyId}/restore")
    @Operation(summary = "Restore company", description = "Restores company and ensures valid main location plus at least one OPEN location.", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:admin"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company restored"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse restoreCompany(
            @PathVariable String companyId
    ) {
        requestContext.assertTenantAccess(companyId);
        return CompanyResponse.from(companyCommandService.restoreCompany(companyId, requestContext.subjectId()));
    }

    @GetMapping("/{companyId}/locations")
    @Operation(summary = "List active locations for a company", security = {@SecurityRequirement(name = "bearerAuth", scopes = {"company:read"})})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Locations listed"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Missing scope or tenant mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public Page<LocationResponse> listCompanyLocations(
            @PathVariable String companyId,
            @Parameter(description = "Optional status filter, e.g. OPEN or CLOSED") @RequestParam(required = false) LocationStatus status,
            @ParameterObject Pageable pageable
    ) {
        requestContext.assertTenantAccess(companyId);
        return companyQueryService.listActiveLocations(companyId, status, pageable).map(LocationResponse::from);
    }
}

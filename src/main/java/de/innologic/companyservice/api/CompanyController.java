package de.innologic.companyservice.api;

import de.innologic.companyservice.api.dto.company.CompanyCreateRequest;
import de.innologic.companyservice.api.dto.company.CompanyResponse;
import de.innologic.companyservice.api.dto.company.CompanyUpdateRequest;
import de.innologic.companyservice.api.dto.company.SetMainLocationRequest;
import de.innologic.companyservice.api.dto.company.UpdateLogoRequest;
import de.innologic.companyservice.api.dto.location.LocationResponse;
import de.innologic.companyservice.config.RequestContext;
import de.innologic.companyservice.persistence.entity.LocationStatus;
import de.innologic.companyservice.service.CompanyCommandService;
import de.innologic.companyservice.service.CompanyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final RequestContext requestContext;

    public CompanyController(
            CompanyCommandService companyCommandService,
            CompanyQueryService companyQueryService,
            RequestContext requestContext
    ) {
        this.companyCommandService = companyCommandService;
        this.companyQueryService = companyQueryService;
        this.requestContext = requestContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create company", description = "Creates a company with exactly one initial main location.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Company created"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse createCompany(
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
                requestContext.subjectId()
        ));
    }

    @GetMapping("/{companyId}")
    @Operation(summary = "Get company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company found"),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse getCompany(@PathVariable String companyId) {
        return CompanyResponse.from(companyQueryService.getActiveCompany(companyId));
    }

    @PutMapping("/{companyId}")
    @Operation(summary = "Update company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company updated"),
            @ApiResponse(responseCode = "404", description = "Company not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse updateCompany(
            @PathVariable String companyId,
            @Valid @RequestBody CompanyUpdateRequest request
    ) {
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
    @Operation(summary = "Set main location")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Main location updated"),
            @ApiResponse(responseCode = "409", description = "Invariant violation", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public CompanyResponse setMainLocation(
            @PathVariable String companyId,
            @Valid @RequestBody SetMainLocationRequest request
    ) {
        return CompanyResponse.from(companyCommandService.setMainLocation(companyId, request.locationId(), requestContext.subjectId()));
    }

    @PutMapping("/{companyId}/logo")
    @Operation(summary = "Update company logo")
    public CompanyResponse updateLogo(
            @PathVariable String companyId,
            @Valid @RequestBody UpdateLogoRequest request
    ) {
        return CompanyResponse.from(companyCommandService.updateLogo(companyId, request.logoFileRef(), requestContext.subjectId()));
    }

    @DeleteMapping("/{companyId}/logo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete company logo")
    public void deleteLogo(
            @PathVariable String companyId
    ) {
        companyCommandService.removeLogo(companyId, requestContext.subjectId());
    }

    @DeleteMapping("/{companyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Trash company", description = "Soft-deletes company and cascades trash to all locations.")
    public void trashCompany(
            @PathVariable String companyId
    ) {
        companyCommandService.trashCompany(companyId, requestContext.subjectId());
    }

    @PostMapping("/{companyId}/restore")
    @Operation(summary = "Restore company", description = "Restores company and ensures valid main location plus at least one OPEN location.")
    public CompanyResponse restoreCompany(
            @PathVariable String companyId
    ) {
        return CompanyResponse.from(companyCommandService.restoreCompany(companyId, requestContext.subjectId()));
    }

    @GetMapping("/{companyId}/locations")
    @Operation(summary = "List active locations for a company")
    public Page<LocationResponse> listCompanyLocations(
            @PathVariable String companyId,
            @Parameter(description = "Optional status filter, e.g. OPEN or CLOSED") @RequestParam(required = false) LocationStatus status,
            @ParameterObject Pageable pageable
    ) {
        return companyQueryService.listActiveLocations(companyId, status, pageable).map(LocationResponse::from);
    }
}

package de.innologic.companyservice.api;

import de.innologic.companyservice.domain.DomainException;
import de.innologic.companyservice.domain.ErrorCode;
import de.innologic.companyservice.domain.ResourceNotFoundException;
import de.innologic.companyservice.config.CorrelationIdFilter;
import de.innologic.companyservice.config.SecurityConfig.ScopeAuthorizationManager.ScopeMissingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class, BindException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                ex.getMessage(),
                request,
                extractDetails(ex)
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> handleUnauthorized(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        String message = ex.getMessage();

        if (ex instanceof ScopeMissingException) {
            errorCode = ErrorCode.SCOPE_MISSING;
            message = "Required scope is missing";
        } else if (isTenantMismatch(ex)) {
            errorCode = ErrorCode.TENANT_MISMATCH;
            message = "tenant mismatch";
        } else if (!StringUtils.hasText(message)) {
            message = "Access denied";
        }

        return build(HttpStatus.FORBIDDEN, errorCode, message, request, List.of());
    }

    private boolean isTenantMismatch(AccessDeniedException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains("tenant_id")
                || message.toLowerCase(Locale.ROOT).contains("x-company-id");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorResponse> handleDomainConflict(DomainException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getErrorCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCode.OPTIMISTIC_LOCK_FAILED, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleServerError(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, ex.getMessage(), request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<String> details
    ) {
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                errorCode.name(),
                message,
                request.getRequestURI(),
                correlationId(request),
                details
        );
        return ResponseEntity.status(status).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.read(request);
        if (correlationId != null && !correlationId.isBlank()) {
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }

    private List<String> extractDetails(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv) {
            List<String> details = new ArrayList<>();
            manv.getBindingResult().getFieldErrors()
                    .forEach(error -> details.add(error.getField() + ": " + error.getDefaultMessage()));
            manv.getBindingResult().getGlobalErrors()
                    .forEach(error -> details.add(error.getObjectName() + ": " + error.getDefaultMessage()));
            return details;
        }
        if (ex instanceof BindException be) {
            List<String> details = new ArrayList<>();
            be.getBindingResult().getFieldErrors()
                    .forEach(error -> details.add(error.getField() + ": " + error.getDefaultMessage()));
            be.getBindingResult().getGlobalErrors()
                    .forEach(error -> details.add(error.getObjectName() + ": " + error.getDefaultMessage()));
            return details;
        }
        if (ex instanceof ConstraintViolationException cve) {
            return cve.getConstraintViolations().stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .toList();
        }
        return List.of();
    }
}

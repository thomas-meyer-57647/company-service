package de.innologic.companyservice.api;

import de.innologic.companyservice.domain.DomainException;
import de.innologic.companyservice.domain.ErrorCode;
import de.innologic.companyservice.domain.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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
        return build(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, ex.getMessage(), request, List.of());
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
        String header = request.getHeader("X-Correlation-Id");
        if (header != null && !header.isBlank()) {
            return header;
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

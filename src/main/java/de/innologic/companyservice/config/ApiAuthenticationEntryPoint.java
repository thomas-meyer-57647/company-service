package de.innologic.companyservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.innologic.companyservice.api.ErrorResponse;
import de.innologic.companyservice.config.SecurityConfig.ScopeAuthorizationManager.ScopeMissingException;
import de.innologic.companyservice.domain.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;

public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        ApiErrorResponseSupport.writeErrorResponse(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ErrorCode.UNAUTHENTICATED,
                resolveMessage(authException)
        );
    }

    private static final String AUTHENTICATION_REQUIRED_MESSAGE = "Authentication required";

    private static String resolveMessage(AuthenticationException exception) {
        String candidate = exception.getMessage();
        if (StringUtils.hasText(candidate) && !candidate.contains("Full authentication is required")) {
            return candidate;
        }
        return AUTHENTICATION_REQUIRED_MESSAGE;
    }
}

final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ACCESS_DENIED_MESSAGE = "Access denied";
    private static final String SCOPE_MISSING_MESSAGE = "Required scope is missing";
    private static final String TENANT_MISMATCH_MESSAGE = "tenant mismatch";

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        String message = ACCESS_DENIED_MESSAGE;

        if (accessDeniedException instanceof ScopeMissingException) {
            errorCode = ErrorCode.SCOPE_MISSING;
            message = SCOPE_MISSING_MESSAGE;
        } else if (isTenantMismatch(accessDeniedException)) {
            errorCode = ErrorCode.TENANT_MISMATCH;
            message = TENANT_MISMATCH_MESSAGE;
        }

        ApiErrorResponseSupport.writeErrorResponse(
                request,
                response,
                HttpStatus.FORBIDDEN,
                errorCode,
                message
        );
    }

    private static boolean isTenantMismatch(AccessDeniedException ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("tenant_id")
                || normalized.contains("x-company-id")
                || normalized.contains("tenant mismatch");
    }
}

final class ApiErrorResponseSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    static void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ErrorCode errorCode,
            String message
    ) throws IOException {
        String correlationId = resolveCorrelationId(request, response);
        ErrorResponse body = ErrorResponse.of(
                status.value(),
                errorCode.name(),
                message != null ? message : status.getReasonPhrase(),
                request.getRequestURI(),
                correlationId,
                List.of()
        );
        response.setStatus(status.value());
        response.setHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }

    static String resolveCorrelationId(HttpServletRequest request, HttpServletResponse response) {
        String candidate = CorrelationIdFilter.read(request);
        if (StringUtils.hasText(candidate)) {
            response.setHeader(CorrelationIdFilter.HEADER_NAME, candidate);
            return candidate;
        }
        candidate = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (StringUtils.hasText(candidate)) {
            return candidate;
        }
        String generated = UUID.randomUUID().toString();
        response.setHeader(CorrelationIdFilter.HEADER_NAME, generated);
        request.setAttribute(CorrelationIdFilter.ATTRIBUTE_NAME, generated);
        return generated;
    }
}

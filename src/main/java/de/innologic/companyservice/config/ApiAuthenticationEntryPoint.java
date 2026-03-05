package de.innologic.companyservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.innologic.companyservice.api.ErrorResponse;
import de.innologic.companyservice.domain.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
                authException.getMessage()
        );
    }
}

final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        ApiErrorResponseSupport.writeErrorResponse(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN,
                accessDeniedException.getMessage()
        );
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

package de.innologic.companyservice.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class RequestContext {

    private final Environment environment;
    private final HttpServletRequest request;

    public RequestContext(Environment environment, HttpServletRequest request) {
        this.environment = environment;
        this.request = request;
    }

    public String subjectId() {
        if (isDevProfileActive()) {
            String subjectHeader = request.getHeader("X-Subject-Id");
            if (subjectHeader != null && !subjectHeader.isBlank()) {
                return subjectHeader;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system";
    }

    public Optional<String> companyIdFromDevHeader() {
        if (!isDevProfileActive()) {
            return Optional.empty();
        }
        String companyHeader = request.getHeader("X-Company-Id");
        if (companyHeader == null || companyHeader.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(companyHeader);
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("dev"::equalsIgnoreCase);
    }
}

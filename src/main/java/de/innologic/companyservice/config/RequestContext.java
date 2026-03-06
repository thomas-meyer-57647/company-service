package de.innologic.companyservice.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RequestContext {

    private final HttpServletRequest request;

    public RequestContext(HttpServletRequest request) {
        this.request = request;
    }

    public String subjectId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getClaimAsString("sub");
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
            return authentication.getName();
        }
        return "system";
    }

    public Optional<String> tenantIdFromJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        String tenantId = jwt.getClaimAsString("tenant_id");
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(tenantId);
    }

    public String tenantId() {
        return tenantIdFromJwt().orElseThrow(() -> new AccessDeniedException("tenant_id claim is required"));
    }

    public void assertTenantAccess(String companyId) {
        String tenantId = tenantId();
        if (!tenantId.equals(companyId)) {
            throw new AccessDeniedException("tenant_id does not match requested companyId");
        }
    }

    public void assertTenantHeaderMatches(String tenantIdHeader) {
        if (!StringUtils.hasText(tenantIdHeader)) {
            return;
        }
        String tenantId = tenantId();
        if (!tenantId.equals(tenantIdHeader)) {
            throw new AccessDeniedException("tenant mismatch");
        }
    }
}

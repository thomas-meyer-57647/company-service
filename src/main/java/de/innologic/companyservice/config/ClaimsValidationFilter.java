package de.innologic.companyservice.config;

import de.innologic.companyservice.domain.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ClaimsValidationFilter extends OncePerRequestFilter {

    private static final Set<String> VALID_SUBJECT_TYPES = Set.of("USER", "SERVICE");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return !(authentication instanceof JwtAuthenticationToken token && token.isAuthenticated());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        JwtAuthenticationToken token = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = token.getToken();

        if (!hasValidSubjectType(jwt)) {
            ApiErrorResponseSupport.writeErrorResponse(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "invalid subject_type"
            );
            return;
        }

        if (!hasScopeClaim(jwt)) {
            ApiErrorResponseSupport.writeErrorResponse(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "missing claim: scp"
            );
            return;
        }

        if (requiresTenant(request) && !StringUtils.hasText(jwt.getClaimAsString("tenant_id"))) {
            ApiErrorResponseSupport.writeErrorResponse(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.UNAUTHENTICATED,
                    "missing claim: tenant_id"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean hasValidSubjectType(Jwt jwt) {
        String subjectType = jwt.getClaimAsString("subject_type");
        return StringUtils.hasText(subjectType) && VALID_SUBJECT_TYPES.contains(subjectType);
    }

    private boolean hasScopeClaim(Jwt jwt) {
        Object scp = jwt.getClaim("scp");
        if (scp instanceof List list && !CollectionUtils.isEmpty(list)) {
            return true;
        }
        Object scope = jwt.getClaim("scope");
        if (scope instanceof String str && StringUtils.hasText(str)) {
            return true;
        }
        if (scope instanceof List list && !CollectionUtils.isEmpty(list)) {
            return true;
        }
        return scp instanceof String scpStr && StringUtils.hasText(scpStr);
    }

    private boolean requiresTenant(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && request.getRequestURI().endsWith("/companies"));
    }
}

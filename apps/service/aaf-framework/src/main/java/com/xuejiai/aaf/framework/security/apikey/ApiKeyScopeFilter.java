package com.xuejiai.aaf.framework.security.apikey;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** API Key scope 与实体访问范围统一门控。 */
@Component
public class ApiKeyScopeFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_SEGMENTS =
            Set.of("api", "system", "admin", "data", "ingest", "v1");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof ApiKey apiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        var requiredScope =
                HttpMethod.GET.matches(request.getMethod())
                                || HttpMethod.HEAD.matches(request.getMethod())
                                || HttpMethod.OPTIONS.matches(request.getMethod())
                        ? "read"
                        : "write";
        if (!apiKey.hasScope(requiredScope) && !apiKey.hasScope("admin")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "API Key scope insufficient");
            return;
        }

        var resourceSlug = extractResourceSlug(request.getRequestURI());
        if (resourceSlug != null && !apiKey.canAccessTable(resourceSlug)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN, "API Key resource scope insufficient");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String extractResourceSlug(String requestUri) {
        if (requestUri == null) {
            return null;
        }
        for (String segment : requestUri.split("/")) {
            if (!segment.isBlank()
                    && !SAFE_SEGMENTS.contains(segment)
                    && !segment.startsWith("_")) {
                return segment;
            }
        }
        return null;
    }
}

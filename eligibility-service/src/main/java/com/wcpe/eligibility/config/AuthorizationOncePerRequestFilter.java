package com.wcpe.eligibility.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class AuthorizationOncePerRequestFilter extends OncePerRequestFilter {

    private static final String ROLES_HEADER = "X-Roles";
    private static final String EVALUATOR_ROLE = "ELIGIBILITY_EVALUATOR";
    private static final Set<String> PROTECTED_PATHS = Set.of(
        "/api/v1/tenants/",
        "/evaluate",
        "/replay"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/")
                || path.contains("/health")
                || path.contains("/actuator")
                || path.contains("/reason-codes")
                || path.contains("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Only enforce on POST /evaluate and POST /replay
        boolean isProtected = "POST".equalsIgnoreCase(request.getMethod())
                && (path.contains("/evaluate") || path.contains("/replay"));

        if (!isProtected) {
            filterChain.doFilter(request, response);
            return;
        }

        String rolesHeader = request.getHeader(ROLES_HEADER);

        if (rolesHeader == null || rolesHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Missing " + ROLES_HEADER + " header\"}");
            return;
        }

        // Parse comma-separated roles
        List<String> roles = List.of(rolesHeader.split(","))
                .stream()
                .map(String::trim)
                .toList();

        if (!roles.contains(EVALUATOR_ROLE)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"Role " + EVALUATOR_ROLE + " required\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

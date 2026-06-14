package com.wcpe.pricingbff.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(11)
class CrmAuthFilter extends OncePerRequestFilter {
  static final String CRM_SYSTEM_HEADER = "X-CRM-System";
  private static final Set<String> SUPPORTED_SYSTEMS = Set.of("SALESFORCE", "HUBSPOT");
  private final ObjectMapper objectMapper;

  CrmAuthFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    return !uri.startsWith("/api/v1/tenants/") || !uri.contains("/integrations/crm");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String system = resolveSystem(request);
    if (system == null) {
      writeError(response, 400, "CRM_SYSTEM_REQUIRED", "X-CRM-System or CRM path must identify SALESFORCE or HUBSPOT", request);
      return;
    }
    if (!hasBearerToken(request) && !hasApiKey(request)) {
      writeError(response, 401, "CRM_AUTH_REQUIRED", "CRM access requires OAuth2 bearer token or sandbox API key", request);
      return;
    }
    if (!hasScope(request, isReadOnly(request) ? "pricing:read" : "pricing:write")) {
      writeError(response, 403, "CRM_SCOPE_REQUIRED", "CRM pricing access requires pricing:read or pricing:write scope", request);
      return;
    }
    request.setAttribute("crm.system", system);
    chain.doFilter(request, response);
  }

  private String resolveSystem(HttpServletRequest request) {
    String header = request.getHeader(CRM_SYSTEM_HEADER);
    if (header != null && SUPPORTED_SYSTEMS.contains(header.toUpperCase())) {
      return header.toUpperCase();
    }
    String uri = request.getRequestURI().toUpperCase();
    if (uri.contains("/SALESFORCE/")) {
      return "SALESFORCE";
    }
    if (uri.contains("/HUBSPOT/")) {
      return "HUBSPOT";
    }
    return null;
  }

  private boolean isReadOnly(HttpServletRequest request) {
    return "GET".equalsIgnoreCase(request.getMethod());
  }

  private boolean hasBearerToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    return authorization != null && authorization.startsWith("Bearer ") && authorization.length() > "Bearer ".length();
  }

  private boolean hasApiKey(HttpServletRequest request) {
    String apiKey = request.getHeader("X-API-Key");
    return apiKey != null && !apiKey.isBlank();
  }

  private boolean hasScope(HttpServletRequest request, String requiredScope) {
    String scopes = request.getHeader("X-CRM-Scopes");
    if (scopes == null || scopes.isBlank()) {
      return hasApiKey(request);
    }
    return Set.of(scopes.toLowerCase().split("[ ,]+"))
        .contains(requiredScope.toLowerCase());
  }

  private void writeError(HttpServletResponse response, int status, String code, String message, HttpServletRequest request) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), new ErrorBody(code, message, request.getHeader("X-Correlation-ID")));
  }

  private record ErrorBody(String code, String message, String correlationId) {
  }
}

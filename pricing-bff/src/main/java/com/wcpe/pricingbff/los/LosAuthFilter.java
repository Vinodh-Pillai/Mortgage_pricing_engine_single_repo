package com.wcpe.pricingbff.los;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosApiModels.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(10)
class LosAuthFilter extends OncePerRequestFilter {
  static final String LOS_SYSTEM_HEADER = "X-LOS-System";
  static final String LOS_VERSION_HEADER = "X-LOS-Version";
  static final String LOS_SCOPES_HEADER = "X-LOS-Scopes";
  static final String LOS_SERVICE_ACCOUNT_HEADER = "X-LOS-Service-Account";
  static final String LOS_SERVICE_SCOPES_HEADER = "X-LOS-Service-Scopes";
  private static final Set<String> SUPPORTED_SYSTEMS = Set.of("ENCOMPASS", "BYTE", "MERIDIANLINK", "CALYX");
  private static final List<ScopeRule> SCOPE_RULES = List.of(
      new ScopeRule("POST", Pattern.compile("^/api/v1/los/pricing-requests$"), "los:pricing-request:write", false),
      new ScopeRule("GET", Pattern.compile("^/api/v1/los/pricing-requests/[^/]+$"), "los:pricing-request:read", true),
      new ScopeRule("GET", Pattern.compile("^/api/v1/los/pricing-requests/[^/]+/offers$"), "los:pricing-request:read", true),
      new ScopeRule("GET", Pattern.compile("^/api/v1/los/products$"), "los:product-catalog:read", false),
      new ScopeRule("GET", Pattern.compile("^/api/v1/los/products/search$"), "los:product-catalog:read", false),
      new ScopeRule("GET", Pattern.compile("^/api/v1/los/products/[^/]+$"), "los:product-catalog:read", false),
      new ScopeRule("POST", Pattern.compile("^/api/v1/los/product-eligibility$"), "los:product-eligibility:write", false),
      new ScopeRule("POST", Pattern.compile("^/api/v1/los/locks$"), "los:lock:write", false),
      new ScopeRule("GET", Pattern.compile("^/api/v1/los/locks/[^/]+$"), "los:lock:read", true),
      new ScopeRule("POST", Pattern.compile("^/api/v1/los/locks/[^/]+/extend$"), "los:lock:write", false),
      new ScopeRule("POST", Pattern.compile("^/api/v1/los/webhooks$"), "los:webhook:write", true));
  private final ObjectMapper objectMapper;

  LosAuthFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/los");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String losSystem = request.getHeader(LOS_SYSTEM_HEADER);
    if (losSystem == null || !SUPPORTED_SYSTEMS.contains(losSystem.toUpperCase())) {
      writeError(response, 400, "LOS_SYSTEM_REQUIRED", "X-LOS-System must be one of ENCOMPASS, BYTE, MERIDIANLINK, CALYX", request);
      return;
    }
    request.setAttribute("los.system", losSystem.toUpperCase());
    request.setAttribute("los.version", request.getHeader(LOS_VERSION_HEADER));

    if (!hasMtlsClientCertificate(request) && !hasBearerToken(request) && !hasApiKey(request)) {
      writeError(response, 401, "LOS_AUTH_REQUIRED", "mTLS client certificate, OAuth2 bearer token, or sandbox API key is required", request);
      return;
    }

    ScopeRule rule = scopeRuleFor(request.getMethod(), request.getRequestURI());
    if (rule == null) {
      writeError(response, 403, "LOS_SCOPE_DEFINITION_REQUIRED", "LOS endpoint does not have a configured permission scope; request failed closed", request);
      return;
    }
    boolean serviceAccount = isServiceAccount(request);
    Set<String> grantedScopes = grantedScopes(request, serviceAccount);
    if (!rule.permitsServiceAccount() && serviceAccount) {
      writeError(response, 403, "LOS_INTERACTIVE_SCOPE_REQUIRED", rule.requiredScope() + " requires an interactive LOS client scope", request);
      return;
    }
    if (!grantedScopes.contains(rule.requiredScope())) {
      writeError(response, 403, serviceAccount ? "LOS_SERVICE_SCOPE_REQUIRED" : "LOS_SCOPE_REQUIRED",
          rule.requiredScope() + " scope is required for " + request.getMethod() + " " + request.getRequestURI(), request);
      return;
    }
    chain.doFilter(request, response);
  }

  static Set<String> requiredScopesFor(String method, String requestUri) {
    ScopeRule rule = scopeRuleFor(method, requestUri);
    return rule == null ? Set.of() : Set.of(rule.requiredScope());
  }

  static List<String> configuredScopeMatrix() {
    return SCOPE_RULES.stream()
        .map(rule -> rule.method() + " " + rule.pathPattern().pattern() + " -> " + rule.requiredScope())
        .toList();
  }

  private static ScopeRule scopeRuleFor(String method, String requestUri) {
    String normalizedMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);
    String normalizedUri = requestUri == null ? "" : requestUri;
    return SCOPE_RULES.stream()
        .filter(rule -> rule.method().equals(normalizedMethod))
        .filter(rule -> rule.pathPattern().matcher(normalizedUri).matches())
        .findFirst()
        .orElse(null);
  }

  private boolean isServiceAccount(HttpServletRequest request) {
    String serviceAccount = request.getHeader(LOS_SERVICE_ACCOUNT_HEADER);
    return serviceAccount != null && (serviceAccount.equalsIgnoreCase("true") || serviceAccount.equalsIgnoreCase("service"));
  }

  private Set<String> grantedScopes(HttpServletRequest request, boolean serviceAccount) {
    String header = serviceAccount ? request.getHeader(LOS_SERVICE_SCOPES_HEADER) : request.getHeader(LOS_SCOPES_HEADER);
    if (header == null || header.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(header.toLowerCase(Locale.ROOT).split("[ ,]+"))
        .map(String::trim)
        .filter(scope -> !scope.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  private boolean hasMtlsClientCertificate(HttpServletRequest request) {
    Object certs = request.getAttribute("jakarta.servlet.request.X509Certificate");
    if (certs instanceof X509Certificate[] certificateChain) {
      return certificateChain.length > 0;
    }
    Object legacyCerts = request.getAttribute("javax.servlet.request.X509Certificate");
    return legacyCerts instanceof X509Certificate[] certificateChain && certificateChain.length > 0;
  }

  private boolean hasBearerToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    return authorization != null && authorization.startsWith("Bearer ") && authorization.length() > "Bearer ".length();
  }

  private boolean hasApiKey(HttpServletRequest request) {
    String apiKey = request.getHeader("X-API-Key");
    return apiKey != null && !apiKey.isBlank();
  }

  private void writeError(HttpServletResponse response, int status, String code, String message, HttpServletRequest request) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(code, message, request.getHeader("X-Correlation-ID")));
  }

  private record ScopeRule(String method, Pattern pathPattern, String requiredScope, boolean permitsServiceAccount) {}
}

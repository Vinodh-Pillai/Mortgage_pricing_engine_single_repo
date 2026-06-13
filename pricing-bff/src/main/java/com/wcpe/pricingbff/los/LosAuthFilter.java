package com.wcpe.pricingbff.los;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosApiModels.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(10)
class LosAuthFilter extends OncePerRequestFilter {
  static final String LOS_SYSTEM_HEADER = "X-LOS-System";
  static final String LOS_VERSION_HEADER = "X-LOS-Version";
  private static final Set<String> SUPPORTED_SYSTEMS = Set.of("ENCOMPASS", "BYTE", "MERIDIANLINK", "CALYX");
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
    chain.doFilter(request, response);
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
}

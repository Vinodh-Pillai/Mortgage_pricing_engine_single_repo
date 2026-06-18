package com.wcpe.pricingbff.los;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosApiModels.ErrorResponse;
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
@Order(20)
class LosIdempotencyFilter extends OncePerRequestFilter {
  static final String IDEMPOTENCY_HEADER = "X-Request-ID";
  static final String LOANPASS_IDEMPOTENCY_HEADER = "Idempotency-Key";
  private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");
  private final ObjectMapper objectMapper;

  LosIdempotencyFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/los") || !MUTATING_METHODS.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String key = firstPresent(request.getHeader(LOANPASS_IDEMPOTENCY_HEADER), request.getHeader(IDEMPOTENCY_HEADER));
    if (key == null || key.isBlank()) {
      response.setStatus(400);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(), new ErrorResponse("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key or X-Request-ID is required for LOS mutating endpoints", request.getHeader("X-Correlation-ID")));
      return;
    }
    request.setAttribute("los.idempotencyKey", key);
    chain.doFilter(request, response);
  }

  private String firstPresent(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }
}

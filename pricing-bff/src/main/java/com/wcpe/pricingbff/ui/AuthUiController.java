package com.wcpe.pricingbff.ui;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Controller
class AuthUiController {
  private final RestClient restClient;
  private final String tenantContextBaseUrl;

  AuthUiController(
      RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.tenant-context-service.base-url:${TENANT_CONTEXT_URL:}}") String tenantContextBaseUrl) {
    this.restClient = restClientBuilder.build();
    this.tenantContextBaseUrl = tenantContextBaseUrl == null ? "" : tenantContextBaseUrl.trim().replaceAll("/$", "");
  }

  @PostMapping("/api/auth/login")
  ResponseEntity<String> login(@RequestBody String body) {
    return forwardWithBody("/api/auth/login", body, HttpHeaders.EMPTY);
  }

  @PostMapping("/api/auth/register")
  ResponseEntity<String> register(@RequestBody String body) {
    return forwardWithBody("/api/auth/register", body, HttpHeaders.EMPTY);
  }

  @PostMapping("/api/auth/logout")
  ResponseEntity<String> logout(@RequestHeader HttpHeaders headers) {
    return forwardWithoutBody("/api/auth/logout", headers);
  }

  @GetMapping("/api/auth/me")
  ResponseEntity<String> me(@RequestHeader HttpHeaders headers) {
    return forwardWithoutBody("/api/auth/me", headers);
  }

  private ResponseEntity<String> forwardWithBody(String path, String body, HttpHeaders headers) {
    if (tenantContextBaseUrl.isBlank()) return missingTenantContextContract();
    try {
      ResponseEntity<String> response = restClient.post()
          .uri(URI.create(tenantContextBaseUrl + path))
          .contentType(MediaType.APPLICATION_JSON)
          .headers(outbound -> copyAuthHeaders(headers, outbound))
          .body(body)
          .retrieve()
          .toEntity(String.class);
      return copyResponse(response);
    } catch (HttpStatusCodeException exception) {
      return copyErrorResponse(exception);
    }
  }

  private ResponseEntity<String> forwardWithoutBody(String path, HttpHeaders headers) {
    if (tenantContextBaseUrl.isBlank()) return missingTenantContextContract();
    try {
      ResponseEntity<String> response;
      if (path.endsWith("/logout")) {
        response = restClient.post()
            .uri(URI.create(tenantContextBaseUrl + path))
            .headers(outbound -> copyAuthHeaders(headers, outbound))
            .retrieve()
            .toEntity(String.class);
      } else {
        response = restClient.get()
            .uri(URI.create(tenantContextBaseUrl + path))
            .headers(outbound -> copyAuthHeaders(headers, outbound))
            .retrieve()
            .toEntity(String.class);
      }
      return copyResponse(response);
    } catch (HttpStatusCodeException exception) {
      return copyErrorResponse(exception);
    }
  }

  private ResponseEntity<String> missingTenantContextContract() {
    return ResponseEntity.status(503)
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"error\":\"Tenant-context authentication contract is not configured for the BFF\"}");
  }

  private void copyAuthHeaders(HttpHeaders inbound, HttpHeaders outbound) {
    if (inbound.containsKey(HttpHeaders.COOKIE)) outbound.put(HttpHeaders.COOKIE, inbound.get(HttpHeaders.COOKIE));
    if (inbound.containsKey(HttpHeaders.AUTHORIZATION)) outbound.put(HttpHeaders.AUTHORIZATION, inbound.get(HttpHeaders.AUTHORIZATION));
  }

  private ResponseEntity<String> copyResponse(ResponseEntity<String> response) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
    copyResponseHeaders(response.getHeaders(), builder);
    return builder.body(response.getBody());
  }

  private ResponseEntity<String> copyErrorResponse(HttpStatusCodeException exception) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.getStatusCode());
    copyResponseHeaders(exception.getResponseHeaders(), builder);
    return builder.body(exception.getResponseBodyAsString());
  }

  private void copyResponseHeaders(HttpHeaders source, ResponseEntity.BodyBuilder builder) {
    if (source == null) return;
    if (source.containsKey(HttpHeaders.SET_COOKIE)) {
      source.get(HttpHeaders.SET_COOKIE).forEach(cookie -> builder.header(HttpHeaders.SET_COOKIE, cookie));
    }
    MediaType contentType = source.getContentType();
    if (contentType != null) builder.contentType(contentType);
  }
}

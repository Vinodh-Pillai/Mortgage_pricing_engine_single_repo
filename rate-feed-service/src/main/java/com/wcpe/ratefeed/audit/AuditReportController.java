package com.wcpe.ratefeed.audit;

import com.wcpe.ratefeed.domain.RateFeedModels;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RequestContext;
import com.wcpe.ratefeed.role.RateFeedRoles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class AuditReportController {
  private final AuditReportService auditReportService;
  private final boolean trustedDirectHeadersEnabled;

  AuditReportController(AuditReportService auditReportService,
      @Value("${wcpe.auth.trusted-direct-headers-enabled:false}") boolean trustedDirectHeadersEnabled) {
    this.auditReportService = auditReportService;
    this.trustedDirectHeadersEnabled = trustedDirectHeadersEnabled;
  }

  @GetMapping("/rate-feed-audit-events")
  ResponseEntity<RateFeedModels.AuditTimelinePage> auditTimeline(@PathVariable UUID tenantId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) UUID investorId,
      @RequestParam(required = false) UUID channelId,
      @RequestParam(required = false) UUID batchId,
      @RequestParam(required = false) UUID versionId,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String correlationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      HttpServletRequest http) {
    Headers h = headers(http);
    RateFeedModels.AuditTimelineRequest request = new RateFeedModels.AuditTimelineRequest(
        from, to, investorId, channelId, batchId, versionId, actorId, eventType, status, correlationId, page, size);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_AUDIT_VIEW,
        () -> ResponseEntity.ok(auditReportService.queryTimeline(tenantId, request, roles(h.roles()))));
  }

  @GetMapping("/rate-feed-audit-reports/{batchId}")
  ResponseEntity<RateFeedModels.AuditReportResponse> auditReport(@PathVariable UUID tenantId,
      @PathVariable UUID batchId,
      HttpServletRequest http) {
    Headers h = headers(http);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_AUDIT_VIEW,
        () -> ResponseEntity.ok(auditReportService.getBatchReport(tenantId, batchId, h.actorId())));
  }

  @PostMapping("/rate-feed-audit-reports/{batchId}/exports")
  ResponseEntity<RateFeedModels.AuditExportResponse> createAuditExport(@PathVariable UUID tenantId,
      @PathVariable UUID batchId,
      @RequestBody RateFeedModels.AuditExportRequest request,
      HttpServletRequest http) {
    Headers h = headers(http);
    String requiredRole = request != null && request.includeRawValues()
        ? RateFeedRoles.RATE_FEED_AUDIT_EXPORT
        : RateFeedRoles.RATE_FEED_AUDIT_VIEW;
    return withAuthorizedHeaders(h, requiredRole,
        () -> ResponseEntity.status(HttpStatus.CREATED)
            .body(auditReportService.createExport(tenantId, batchId, request, h.actorId(), h.correlationId())));
  }

  @PostMapping("/rate-feed-audit-reports/{batchId}/verify-replay")
  ResponseEntity<RateFeedModels.VerifyReplayResponse> verifyReplay(@PathVariable UUID tenantId,
      @PathVariable UUID batchId,
      @RequestBody RateFeedModels.VerifyReplayRequest request,
      HttpServletRequest http) {
    Headers h = headers(http);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_AUDIT_VIEW,
        () -> ResponseEntity.ok(auditReportService.verifyReplay(tenantId, batchId, request, h.actorId())));
  }

  @ExceptionHandler(RateFeedException.class)
  ResponseEntity<Map<String, Object>> error(RateFeedException ex, HttpServletRequest request) {
    String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id")).filter(v -> !v.isBlank()).orElse(UUID.randomUUID().toString());
    return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage(), "correlationId", correlationId));
  }

  private Headers headers(HttpServletRequest request) {
    return new Headers(request.getHeader("Idempotency-Key"), request.getHeader("X-Actor-Id"), request.getHeader("X-Correlation-Id"), request.getHeader("X-Roles"));
  }

  private <T> T withAuthorizedHeaders(Headers headers, String requiredRole, Supplier<T> action) {
    if (!trustedDirectHeadersEnabled) {
      if (present(headers.roles()) || present(headers.actorId())) {
        throw new RateFeedException(HttpStatus.UNAUTHORIZED, "UNTRUSTED_DIRECT_AUTH_HEADERS", "Direct X-Roles/X-Actor-Id headers are not trusted.");
      }
      throw new RateFeedException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication must be supplied by the approved gateway.");
    }
    try {
      RequestContext.roles(headers.roles());
      String validatedRole = RateFeedRoles.validateRole(requiredRole);
      if (!RequestContext.hasRole(validatedRole)) {
        throw new RateFeedException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", validatedRole + " role is required.");
      }
      return action.get();
    } finally { RequestContext.clear(); }
  }

  private static Set<String> roles(String roles) {
    if (roles == null || roles.isBlank()) return Set.of();
    Set<String> parsed = new HashSet<>();
    for (String role : roles.split(",")) {
      String normalized = role.trim().toUpperCase(Locale.ROOT);
      if (!normalized.isBlank()) parsed.add(normalized);
    }
    return parsed;
  }

  private static boolean present(String value) { return value != null && !value.isBlank(); }

  record Headers(String idempotencyKey, String actorId, String correlationId, String roles) {}
}

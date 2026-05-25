package com.wcpe.ratefeed.domain;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.wcpe.ratefeed.role.RateFeedRoles;
import com.wcpe.ratefeed.domain.RateFeedModels.RateFeedException;
import com.wcpe.ratefeed.domain.RateFeedModels.UploadSessionRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.UploadSessionResponse;
import com.wcpe.ratefeed.domain.RateFeedModels.CompleteUploadRequest;
import com.wcpe.ratefeed.domain.RateFeedModels.CompleteUploadResponse;
import com.wcpe.ratefeed.domain.RateFeedModels.BatchResponse;

/**
 * D-007 fix: All role checks now delegate through RateFeedRoles for canonical names.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
class RateFeedController {
  private final RateFeedService service;
  private final boolean trustedDirectHeadersEnabled;

  RateFeedController(RateFeedService service, @Value("${wcpe.auth.trusted-direct-headers-enabled:false}") boolean trustedDirectHeadersEnabled) {
    this.service = service;
    this.trustedDirectHeadersEnabled = trustedDirectHeadersEnabled;
  }

  // ── Existing endpoints ──

  @PostMapping("/rate-feed-uploads/sessions")
  ResponseEntity<UploadSessionResponse> createSession(@PathVariable UUID tenantId, @RequestBody UploadSessionRequest request, HttpServletRequest http) {
    Headers headers = headers(http);
    return withAuthorizedHeaders(headers, RateFeedRoles.RATE_FEED_UPLOAD, () -> ResponseEntity.status(HttpStatus.CREATED).body(service.createSession(tenantId, request, headers.idempotencyKey(), headers.actorId(), headers.correlationId())));
  }

  @PostMapping("/rate-feed-uploads/sessions/{uploadSessionId}/complete")
  ResponseEntity<CompleteUploadResponse> complete(@PathVariable UUID tenantId, @PathVariable UUID uploadSessionId, @RequestBody CompleteUploadRequest request, HttpServletRequest http) {
    Headers headers = headers(http);
    return withAuthorizedHeaders(headers, RateFeedRoles.RATE_FEED_UPLOAD, () -> ResponseEntity.status(HttpStatus.CREATED).body(service.complete(tenantId, uploadSessionId, request, headers.idempotencyKey(), headers.actorId(), headers.correlationId())));
  }

  @GetMapping("/rate-feed-batches/{batchId}")
  BatchResponse batch(@PathVariable UUID tenantId, @PathVariable UUID batchId, HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW, () -> service.batch(tenantId, batchId));
  }

  // ── G-001: Import rate sheet CSV ──

  @PostMapping(value = "/rate-sheets/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<RateFeedModels.ImportResponse> importRateSheet(@PathVariable UUID tenantId, @RequestParam("file") MultipartFile file, HttpServletRequest http) {
    Headers h = headers(http);
    Map<String, String> metadata = extractFileMetadata(file);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_UPLOAD, () -> ResponseEntity.status(HttpStatus.CREATED).body(
        service.importRateSheet(tenantId, file, metadata, h.idempotencyKey(), h.actorId(), h.correlationId())));
  }

  // ── G-005: Validate rate sheet ──

  @PostMapping("/rate-sheets/{sheetId}/validate")
  ResponseEntity<RateFeedModels.ValidationResultResponse> validate(@PathVariable UUID tenantId, @PathVariable UUID sheetId, @RequestBody RateFeedModels.ValidateRequest request, HttpServletRequest http) {
    Headers h = headers(http);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_UPLOAD, () -> ResponseEntity.ok(service.validateRateSheet(sheetId, request.idempotencyKey(), h.actorId(), h.correlationId())));
  }

  // ── G-002: Activate rate sheet ──

  @PostMapping("/rate-sheets/{sheetId}/activate")
  ResponseEntity<RateFeedModels.ActivateResponse> activate(@PathVariable UUID tenantId, @PathVariable UUID sheetId, @RequestBody RateFeedModels.ActivateRequest request, HttpServletRequest http) {
    Headers h = headers(http);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_ACTIVATE, () -> ResponseEntity.status(HttpStatus.CREATED).body(service.activate(sheetId, request, h.actorId(), h.correlationId())));
  }

  // ── G-003: Resolve active rate sheet ──

  @GetMapping("/rates/resolve")
  ResponseEntity<RateFeedModels.ResolveResponse> resolve(@PathVariable UUID tenantId,
      @RequestParam UUID investorId, @RequestParam UUID channelId,
      @RequestParam String productCode, @RequestParam int lockPeriod,
      @RequestParam Instant resolutionTimestamp, HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.resolve(tenantId, investorId, channelId, productCode, lockPeriod, resolutionTimestamp)));
  }

  // ── G-004: Grid lookup ──

  @GetMapping("/rates/{sheetId}/{version}/grid")
  ResponseEntity<RateFeedModels.GridResponse> grid(@PathVariable UUID tenantId, @PathVariable UUID sheetId, @PathVariable int version, HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.grid(sheetId, version)));
  }

  @GetMapping("/rates/{sheetId}/{version}/price")
  ResponseEntity<RateFeedModels.PriceLookupResponse> price(@PathVariable UUID tenantId, @PathVariable UUID sheetId,
      @PathVariable int version, @RequestParam BigDecimal noteRate, @RequestParam int lockPeriod,
      @RequestParam(defaultValue = "false") boolean interpolate, HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.price(sheetId, version, noteRate, lockPeriod, interpolate)));
  }

  // ── Sheet metadata endpoints ──

  @GetMapping("/rate-sheets/{sheetId}")
  ResponseEntity<RateFeedModels.SheetDetailResponse> sheetDetail(@PathVariable UUID tenantId, @PathVariable UUID sheetId, HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.sheetDetails(sheetId)));
  }

  @GetMapping("/rate-sheets")
  ResponseEntity<RateFeedModels.SheetListResponse> listSheets(@PathVariable UUID tenantId,
      @RequestParam(required = false) UUID investorId,
      @RequestParam(required = false) UUID channelId,
      @RequestParam(required = false) String status,
      HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW, () -> ResponseEntity.ok(service.listSheets(tenantId, investorId, channelId, status)));
  }

  // ── Hardening: Version list endpoint ──

  @GetMapping("/rate-sheets/versions")
  ResponseEntity<RateFeedModels.SheetVersionsResponse> listVersions(
      @RequestParam(required = false) UUID investorId,
      @RequestParam(required = false) UUID channelId,
      @RequestParam(required = false) String productCode,
      HttpServletRequest http) {
    return withAuthorizedHeaders(headers(http), RateFeedRoles.RATE_FEED_VIEW,
        () -> ResponseEntity.ok(service.listVersions(investorId, channelId, productCode)));
  }

  // ── Hardening: Replay endpoint ──

  @PostMapping("/pricing/replay")
  ResponseEntity<RateFeedModels.ReplayResult> replay(@RequestBody RateFeedModels.ReplayRequest request, HttpServletRequest http) {
    Headers h = headers(http);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_VIEW,
        () -> ResponseEntity.ok(service.replay(request, h.actorId(), h.correlationId())));
  }

  // ── G-006: Reject rate sheet ──

  @PostMapping("/rate-sheets/{sheetId}/reject")
  ResponseEntity<RateFeedModels.RejectResponse> reject(@PathVariable UUID tenantId, @PathVariable UUID sheetId, @RequestBody RateFeedModels.RejectRequest request, HttpServletRequest http) {
    Headers h = headers(http);
    return withAuthorizedHeaders(h, RateFeedRoles.RATE_FEED_ACTIVATE, () -> ResponseEntity.ok(service.reject(sheetId, request, h.actorId(), h.correlationId())));
  }

  // ── Shared helpers ──

  @ExceptionHandler(RateFeedException.class)
  ResponseEntity<Map<String, Object>> error(RateFeedException ex, HttpServletRequest request) {
    String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id")).filter(v -> !v.isBlank()).orElse(UUID.randomUUID().toString());
    return ResponseEntity.status(ex.status()).body(Map.of("code", ex.code(), "message", ex.getMessage(), "correlationId", correlationId));
  }

  private Headers headers(HttpServletRequest request) { return new Headers(request.getHeader("Idempotency-Key"), request.getHeader("X-Actor-Id"), request.getHeader("X-Correlation-Id"), request.getHeader("X-Roles")); }

  /** D-007 fix: role parameter comes from RateFeedRoles constants; validated at call site. */
  private <T> T withAuthorizedHeaders(Headers headers, String requiredRole, java.util.function.Supplier<T> action) {
    if (!trustedDirectHeadersEnabled) {
      if (present(headers.roles()) || present(headers.actorId()))
        throw new RateFeedException(HttpStatus.UNAUTHORIZED, "UNTRUSTED_DIRECT_AUTH_HEADERS", "Direct X-Roles/X-Actor-Id headers are not trusted.");
      throw new RateFeedException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication must be supplied by the approved gateway.");
    }
    try {
      RequestContext.roles(headers.roles());
      // D-007: requiredRole is validated by being a constant from RateFeedRoles
      RateFeedRoles.validateRole(requiredRole);
      if (!RequestContext.hasRole(requiredRole)) throw new RateFeedException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", requiredRole + " role is required.");
      return action.get();
    } finally { RequestContext.clear(); }
  }

  private static boolean present(String value) { return value != null && !value.isBlank(); }

  private Map<String, String> extractFileMetadata(org.springframework.web.multipart.MultipartFile file) {
    Map<String, String> m = new LinkedHashMap<>();
    String name = file.getOriginalFilename();
    m.put("fileName", name);
    if (name != null) m.put("fileName", name);
    return m;
  }

  record Headers(String idempotencyKey, String actorId, String correlationId, String roles) {}
}

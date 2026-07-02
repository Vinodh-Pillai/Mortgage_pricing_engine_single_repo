package com.wcpe.pricingbff.ui;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class LockManagementServiceClient {
  private static final ParameterizedTypeReference<List<LockSummary>> LOCK_SUMMARIES = new ParameterizedTypeReference<>() {};
  private static final Set<String> PENDING_STATUSES = Set.of("REQUESTED", "PENDING_APPROVAL", "APPROVED", "PENDING_INVESTOR_CONFIRMATION");

  private final RestClient restClient;
  private final boolean configured;

  LockManagementServiceClient(RestClient.Builder restClientBuilder,
      @Value("${loanweft.integrations.lock-service.base-url:${LOANWEFT_INTEGRATIONS_LOCK_SERVICE_BASE_URL:${LOCK_SERVICE_BASE_URL:}}}") String baseUrl) {
    String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
    this.configured = !normalizedBaseUrl.isBlank();
    this.restClient = configured ? restClientBuilder.baseUrl(normalizedBaseUrl).build() : null;
  }

  private LockManagementServiceClient() {
    this.restClient = null;
    this.configured = false;
  }

  static LockManagementServiceClient notConfigured() {
    return new LockManagementServiceClient();
  }

  PricingBffUiFallbackAdapter.LockManagementView lockManagement(String tenantId, String uiTraceId) {
    String traceId = normalizeTrace(uiTraceId, "lock-management-live-ui");
    if (!configured) {
      return blockedView(tenantId, traceId, "LOCK_SERVICE_BASE_URL_NOT_CONFIGURED",
          "Configure loanweft.integrations.lock-service.base-url, LOANWEFT_INTEGRATIONS_LOCK_SERVICE_BASE_URL, or LOCK_SERVICE_BASE_URL before reading lock-service records.");
    }
    try {
      List<LockSummary> summaries = restClient.get()
          .uri("/api/v1/tenants/{tenantId}/locks", tenantScope(tenantId))
          .header("X-Ui-Trace-Id", traceId)
          .retrieve()
          .body(LOCK_SUMMARIES);
      List<PricingBffUiFallbackAdapter.LockManagementRecord> locks = (summaries == null ? List.<LockSummary>of() : summaries)
          .stream()
          .map(LockManagementServiceClient::toRecord)
          .toList();
      return new PricingBffUiFallbackAdapter.LockManagementView(tenantId, "LOCK_SERVICE_LIVE_READ_READY", locks,
          traceId, List.of("LockManagementReadFromLockService"), List.of(), false,
          (int) locks.stream().filter(lock -> PENDING_STATUSES.contains(lock.status())).count(),
          (int) locks.stream().filter(lock -> "EXPIRING_SOON".equals(lock.expiryStatus())).count());
    } catch (RestClientException exception) {
      return blockedView(tenantId, traceId, "LOCK_SERVICE_READ_UNAVAILABLE",
          "Configured lock-service /api/v1/tenants/{tenantId}/locks endpoint did not return readable records; no lock rows were synthesized.");
    }
  }

  ResponseEntity<PricingBffUiFallbackAdapter.LockManagementDetailResult> lockDetail(String tenantId, String lockId,
      String uiTraceId) {
    String traceId = normalizeTrace(uiTraceId, "lock-management-detail-ui");
    if (!configured) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(blockedDetail(lockId, traceId,
          "LOCK_SERVICE_BASE_URL_NOT_CONFIGURED"));
    }
    try {
      LockDetail detail = restClient.get()
          .uri("/api/v1/tenants/{tenantId}/locks/{lockId}", tenantScope(tenantId), lockId)
          .header("X-Ui-Trace-Id", traceId)
          .retrieve()
          .body(LockDetail.class);
      if (detail == null) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(blockedDetail(lockId, traceId, "LOCK_SERVICE_DETAIL_EMPTY"));
      }
      return ResponseEntity.ok(new PricingBffUiFallbackAdapter.LockManagementDetailResult(detail.lockId(), detail.status(),
          detail.version(), stringValue(detail.createdAt()), stringValue(detail.expiresAt()), String.valueOf(detail.expirationBusinessDays()),
          detail.calendarConfigHash(), traceId, List.of("LockManagementDetailReadFromLockService"), List.of()));
    } catch (RestClientException exception) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(blockedDetail(lockId, traceId, "LOCK_SERVICE_DETAIL_UNAVAILABLE"));
    }
  }

  ResponseEntity<PricingBffUiFallbackAdapter.LockManagementActionResult> requestLockManagementAction(String tenantId,
      String lockId, String action, String uiTraceId) {
    String normalizedAction = action == null ? "" : action.trim().toLowerCase();
    String traceId = normalizeTrace(uiTraceId, "lock-management-action-live-ui");
    if ("read".equals(normalizedAction) || "detail".equals(normalizedAction)) {
      ResponseEntity<PricingBffUiFallbackAdapter.LockManagementDetailResult> detail = lockDetail(tenantId, lockId, traceId);
      if (detail.getStatusCode().is2xxSuccessful()) {
        return ResponseEntity.ok(new PricingBffUiFallbackAdapter.LockManagementActionResult(lockId, normalizedAction, "ACCEPTED",
            "Lock detail was read from lock-service for this record.", "lock-service:/api/v1/tenants/{tenantId}/locks/" + lockId,
            List.of(), traceId, List.of("LockManagementDetailActionRead")));
      }
      return ResponseEntity.status(detail.getStatusCode()).body(new PricingBffUiFallbackAdapter.LockManagementActionResult(lockId,
          normalizedAction, "BLOCKED", "Lock detail could not be read from lock-service.", null,
          detail.getBody() == null ? List.of("LOCK_SERVICE_DETAIL_UNAVAILABLE") : detail.getBody().blockers(), traceId,
          List.of("LockManagementDetailActionBlocked")));
    }
    String blocker = actionBlockers().getOrDefault(normalizedAction, "LOCK_MANAGEMENT_ACTION_NOT_SUPPORTED_BY_BFF");
    return ResponseEntity.unprocessableEntity().body(new PricingBffUiFallbackAdapter.LockManagementActionResult(lockId,
        normalizedAction, "BLOCKED", actionMessage(blocker), null, List.of(blocker), traceId,
        List.of("LockManagementActionDisabled")));
  }

  private static PricingBffUiFallbackAdapter.LockManagementRecord toRecord(LockSummary summary) {
    String status = stringValue(summary.status());
    String expiresAt = stringValue(summary.expiresAt());
    String expiryStatus = expiryStatus(status, expiresAt);
    return new PricingBffUiFallbackAdapter.LockManagementRecord(summary.lockId(), summary.quoteId(), "quote:" + summary.quoteId(), status,
        expiresAt.isBlank() ? null : expiresAt, "not supplied by lock-service list contract",
        List.of("lock-service:/api/v1/tenants/{tenantId}/locks/" + summary.lockId()), List.of(), List.of("read", "detail"),
        actionBlockers(), expiryStatus);
  }

  private static Map<String, String> actionBlockers() {
    Map<String, String> blockers = new LinkedHashMap<>();
    blockers.put("extend", "LOCK_EXTENSION_REQUIRED_FIELDS_NOT_SUPPLIED");
    blockers.put("relock", "LOCK_RELOCK_REQUIRED_FIELDS_NOT_SUPPLIED");
    blockers.put("deliver", "LOCK_INVESTOR_DELIVERY_ROUTE_NOT_EXPOSED_BY_LOCK_SERVICE");
    blockers.put("cancel", "LOCK_CANCELLATION_REQUIRED_FIELDS_NOT_SUPPLIED");
    blockers.put("approve", "LOCK_APPROVAL_RULES_NOT_SUPPLIED_TO_BFF");
    return blockers;
  }

  private static String actionMessage(String blocker) {
    return switch (blocker) {
      case "LOCK_EXTENSION_REQUIRED_FIELDS_NOT_SUPPLIED" -> "Extension stays disabled because lock-service requires requested days, requested expiration, reason, cost snapshot, policy, compliance, and investor-support evidence; the UI/BFF does not invent those fields.";
      case "LOCK_RELOCK_REQUIRED_FIELDS_NOT_SUPPLIED" -> "Relock stays disabled because lock-service relock APIs require selected replacement terms, policy snapshots, and approval evidence not supplied by this UI contract.";
      case "LOCK_INVESTOR_DELIVERY_ROUTE_NOT_EXPOSED_BY_LOCK_SERVICE" -> "Investor delivery stays disabled because the current lock-service contract exposes read/detail, confirmation, extension, relock, sync, cancellation, replay, and evidence routes, but no delivery action route.";
      case "LOCK_CANCELLATION_REQUIRED_FIELDS_NOT_SUPPLIED" -> "Cancellation stays disabled until the UI supplies the lock-service cancellation actor, version, reason, and compliance evidence fields.";
      case "LOCK_APPROVAL_RULES_NOT_SUPPLIED_TO_BFF" -> "Approval stays disabled because approval authority and policy rules are not supplied to the BFF by the current lock-service read contract.";
      default -> "This lock action is not supported by the current BFF-to-lock-service contract.";
    };
  }

  private static String expiryStatus(String status, String expiresAt) {
    if ("EXPIRING_SOON".equals(status)) return "EXPIRING_SOON";
    if (expiresAt == null || expiresAt.isBlank()) return "NOT_SUPPLIED";
    try {
      Instant expiry = Instant.parse(expiresAt);
      Instant now = Instant.now();
      if (expiry.isBefore(now)) return "EXPIRED";
      if (expiry.isBefore(now.plus(7, ChronoUnit.DAYS))) return "EXPIRING_SOON";
      return "SCHEDULED";
    } catch (RuntimeException ignored) {
      return "UNPARSEABLE";
    }
  }

  private static PricingBffUiFallbackAdapter.LockManagementView blockedView(String tenantId, String traceId, String code,
      String blocker) {
    return new PricingBffUiFallbackAdapter.LockManagementView(tenantId, code, List.of(), traceId,
        List.of("LockManagementReadBlocked"), List.of(blocker), false, 0, 0);
  }

  private static PricingBffUiFallbackAdapter.LockManagementDetailResult blockedDetail(String lockId, String traceId,
      String blocker) {
    return new PricingBffUiFallbackAdapter.LockManagementDetailResult(lockId, "BLOCKED", 0, null, null, "not supplied", null,
        traceId, List.of("LockManagementDetailBlocked"), List.of(blocker));
  }

  private static String normalizeTrace(String uiTraceId, String fallback) {
    return uiTraceId == null || uiTraceId.isBlank() ? fallback : uiTraceId;
  }

  private static UUID tenantScope(String tenantId) {
    try {
      return UUID.fromString(tenantId);
    } catch (RuntimeException ignored) {
      return UUID.nameUUIDFromBytes(("ui-tenant:" + tenantId).getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  record LockSummary(String lockId, String quoteId, String status, int version, Instant createdAt, Instant updatedAt,
      Instant expiresAt) {}

  record LockDetail(String lockId, String status, int version, Instant createdAt, Instant expiresAt,
      int expirationBusinessDays, String calendarConfigHash, Object expirationBreakdown) {}
}

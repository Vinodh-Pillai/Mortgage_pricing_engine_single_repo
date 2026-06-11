package com.wcpe.scenarioanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ScenarioAnalysisApiService {
  private final Clock clock;

  public ScenarioAnalysisApiService() {
    this(Clock.systemUTC());
  }

  ScenarioAnalysisApiService(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public ScenarioAnalysisResponse workspace(String tenantId, String runId, String uiTraceId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    String normalizedRunId = requireText(runId, "runId is required");
    Map<String, Object> data = Map.of(
        "dimensions", List.of(),
        "variants", List.of(),
        "batchGrid", Map.of("rows", List.of(), "status", "UNAVAILABLE"),
        "savedAnalyses", List.of(),
        "guardrails", Map.of("status", "UNAVAILABLE", "reason", "eligibility_service_unavailable"),
        "exportRefs", List.of(),
        "replayRefs", List.of(),
        "cacheTtlSeconds", 300);
    return response(
        normalizedTenantId,
        normalizedRunId,
        "PARTIAL",
        data,
        List.of(),
        uiTraceId,
        List.of("workspace.partial.v1"),
        "quote, catalog, eligibility, and pricing dependencies are not connected");
  }

  public ScenarioAnalysisResponse recalculate(
      String tenantId,
      String runId,
      RecalculationRequest request,
      String uiTraceId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    String normalizedRunId = requireText(runId, "runId is required");
    if (request == null) {
      throw new ValidationException("recalculation request is required");
    }
    String changedDimensionId = requireText(request.changedDimensionId(), "changedDimensionId is required");
    Map<String, Object> data = Map.of(
        "status", "QUEUED",
        "message", "Recalculation accepted for orchestration; downstream pricing and eligibility integration is unavailable locally.",
        "changedDimensionId", changedDimensionId,
        "requestedValue", request.requestedValue() == null ? "" : request.requestedValue(),
        "variantFacts", request.variantFacts() == null ? List.of() : request.variantFacts(),
        "backendResultRefs", List.of("scenario-analysis:recalculation:" + UUID.randomUUID()),
        "blockers", List.of("pricing_service_unavailable", "eligibility_service_unavailable"),
        "events", List.of("ScenarioVariantRecalculated.v1:QUEUED"));
    return response(
        normalizedTenantId,
        normalizedRunId,
        "PARTIAL",
        data,
        List.of("scenario-analysis:recalculation:queued"),
        uiTraceId,
        List.of("recalculation.queued.v1"),
        "pricing-service and eligibility-service clients are not available in this service-local slice");
  }

  public ScenarioAnalysisResponse ficoSensitivity(String tenantId, String runId, String uiTraceId) {
    return emptySensitivity(
        tenantId,
        runId,
        "FICO",
        "bands",
        "catalog_metadata_or_tenant_fico_ladder_unavailable",
        uiTraceId);
  }

  public ScenarioAnalysisResponse ltvSensitivity(String tenantId, String runId, String uiTraceId) {
    return emptySensitivity(
        tenantId,
        runId,
        "LTV",
        "bands",
        "catalog_metadata_or_tenant_ltv_ladder_unavailable",
        uiTraceId);
  }

  public ScenarioAnalysisResponse productComparison(String tenantId, String runId, String uiTraceId) {
    return emptySensitivity(
        tenantId,
        runId,
        "PRODUCT",
        "products",
        "catalog_service_product_candidates_unavailable",
        uiTraceId);
  }

  public ScenarioAnalysisResponse lockPeriodComparison(String tenantId, String runId, String uiTraceId) {
    return emptySensitivity(
        tenantId,
        runId,
        "LOCK_PERIOD",
        "periods",
        "catalog_or_lock_policy_periods_unavailable",
        uiTraceId);
  }

  private ScenarioAnalysisResponse emptySensitivity(
      String tenantId,
      String runId,
      String axis,
      String collectionName,
      String fallbackReason,
      String uiTraceId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    String normalizedRunId = requireText(runId, "runId is required");
    Map<String, Object> data = Map.of(
        "axis", axis,
        collectionName, List.of(),
        "currentValue", "UNAVAILABLE",
        "status", "BLOCKED",
        "cacheTtlSeconds", 300);
    return response(
        normalizedTenantId,
        normalizedRunId,
        "BLOCKED",
        data,
        List.of(),
        uiTraceId,
        List.of(axis.toLowerCase() + ".sensitivity.blocked.v1"),
        fallbackReason);
  }

  private ScenarioAnalysisResponse response(
      String tenantId,
      String runId,
      String dependencyStatus,
      Object data,
      List<String> backendResultRefs,
      String uiTraceId,
      List<String> events,
      String fallbackReason) {
    Instant now = Instant.now(clock);
    List<String> versionRefs = List.of("scenario-analysis-service:local", "generated-at:" + now);
    List<String> auditRefs = List.of("audit:scenario-analysis:" + tenantId + ':' + runId);
    return new ScenarioAnalysisResponse(
        tenantId,
        runId,
        dependencyStatus,
        data,
        backendResultRefs,
        "sha256:" + sha256Hex(tenantId + '|' + runId + '|' + dependencyStatus + '|' + data + '|' + fallbackReason),
        versionRefs,
        auditRefs,
        defaultText(uiTraceId, UUID.randomUUID().toString()),
        events,
        fallbackReason);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(message);
    }
    return value.trim();
  }

  private static String defaultText(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  public record RecalculationRequest(
      String changedDimensionId,
      String requestedValue,
      List<Map<String, Object>> variantFacts) {}

  public record ScenarioAnalysisResponse(
      String tenantContext,
      String runId,
      String dependencyStatus,
      Object data,
      List<String> backendResultRefs,
      String replayHash,
      List<String> versionRefs,
      List<String> auditRefs,
      String uiTraceId,
      List<String> events,
      String fallbackReason) {}

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }
}

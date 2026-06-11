package com.wcpe.mladvisory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ml-advisory")
public final class WorkbenchMlAdvisoryApiController {
  private static final String DEFAULT_TENANT = "workbench-context";
  private static final String DEPENDENCY_PARTIAL = "PARTIAL";
  private static final List<CacheMetadata> INSIGHTS_CACHE = List.of(new CacheMetadata("insights", "PT5M", false, "LOCAL_CACHE_NOT_CONFIGURED"));
  private static final List<CacheMetadata> REGISTRY_CACHE = List.of(new CacheMetadata("model-registry", "PT10M", false, "LOCAL_CACHE_NOT_CONFIGURED"));
  private static final List<CacheMetadata> DRIFT_CACHE = List.of(new CacheMetadata("drift", "PT1M", false, "LOCAL_CACHE_NOT_CONFIGURED"));
  private static final List<CacheMetadata> ALERTS_CACHE = List.of(new CacheMetadata("alerts", "PT30S", false, "LOCAL_CACHE_NOT_CONFIGURED"));

  @GetMapping("/insights")
  AdvisoryResponse<InsightsData> insights(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = traceId(uiTraceId, "insights");
    InsightsData data =
        new InsightsData(
            List.of(
                new Recommendation(
                    "recommendation-config-required",
                    "model-version-config-required",
                    "MODEL_CONFIDENCE_UNAVAILABLE",
                    "Advisory evidence is available, but active pricing quote context or model inference is not configured locally.",
                    List.of("VIEW_EXPLANATION", "REQUEST_HUMAN_REVIEW", "EXPORT_AUDIT_EVIDENCE"),
                    List.of("audit:ml-advisory:insights:config-required"),
                    false)),
            true,
            "Pricing-service quote context and production model inference are outside this service-local lock.");
    return response(data, traceId, List.of("MlAdvisoryInsightsViewed.v1"), data.fallbackReason(), INSIGHTS_CACHE);
  }

  @GetMapping("/models")
  AdvisoryResponse<ModelsData> models(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = traceId(uiTraceId, "models");
    ModelsData data =
        new ModelsData(
            List.of(
                new ModelRegistryEntry(
                    "model-registry-config-required",
                    "version-config-required",
                    "PENDING_VALIDATION",
                    "system-config-required",
                    "registered-at-config-required",
                    List.of("artifact-manifest-required"),
                    "CONFIGURATION_REQUIRED")));
    return response(data, traceId, List.of("MlModelRegistryViewed.v1"), "", REGISTRY_CACHE);
  }

  @PostMapping("/models")
  AdvisoryResponse<ModelMutationData> registerModel(
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) ModelRegistrationRequest request) {
    ModelRegistrationRequest safeRequest = request == null ? ModelRegistrationRequest.empty() : request;
    String traceId = traceId(uiTraceId, "register-model");
    ModelMutationData data =
        new ModelMutationData(
            defaulted(safeRequest.modelId(), "model-registration-requested"),
            defaulted(safeRequest.version(), "version-registration-requested"),
            "PENDING_VALIDATION",
            "Validation evidence and artifact checks must complete before approval.");
    return response(data, traceId, List.of("MlModelVersionRegistered.v1"), "");
  }

  @GetMapping("/models/{modelId}/versions/{version}/approval")
  AdvisoryResponse<ModelApprovalData> approval(
      @PathVariable String modelId,
      @PathVariable String version,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = traceId(uiTraceId, "approval");
    ModelApprovalData data =
        new ModelApprovalData(
            modelId,
            version,
            "REQUESTED",
            List.of("performance", "drift", "bias", "security", "documentation"),
            List.of("model-risk-reviewer", "compliance-reviewer"),
            "PENDING_REVIEW");
    return response(data, traceId, List.of("MlModelApprovalViewed.v1"), "");
  }

  @PostMapping("/models/{modelId}/versions/{version}/approval")
  AdvisoryResponse<ModelApprovalData> approve(
      @PathVariable String modelId,
      @PathVariable String version,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) ApprovalDecisionRequest request) {
    ApprovalDecisionRequest safeRequest = request == null ? ApprovalDecisionRequest.empty() : request;
    String traceId = traceId(uiTraceId, "approval-decision");
    String decision = defaulted(safeRequest.decision(), "REVIEW_REQUIRED").toUpperCase();
    boolean approved = decision.equals("APPROVED");
    boolean rejected = decision.equals("REJECTED");
    ModelApprovalData data =
        new ModelApprovalData(
            modelId,
            version,
            approved ? "APPROVED" : rejected ? "REJECTED" : "PENDING_REVIEW",
            List.of("performance", "drift", "bias", "security", "documentation"),
            List.of(defaulted(safeRequest.actorId(), "approver-required")),
            decision);
    return response(data, traceId, List.of(approved ? "MlModelVersionApproved.v1" : "MlModelApprovalReviewRequired.v1"), approved ? "" : "Explicit approval evidence is required before model approval.");
  }

  @PostMapping("/models/{modelId}/versions/{version}/promote")
  AdvisoryResponse<ModelMutationData> promote(
      @PathVariable String modelId,
      @PathVariable String version,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PromotionRequest request) {
    PromotionRequest safeRequest = request == null ? PromotionRequest.empty() : request;
    boolean promotable = safeRequest.approvalSatisfied() && safeRequest.compatibilitySatisfied() && safeRequest.monitoringSatisfied();
    return lifecycle(
        modelId,
        version,
        promotable ? "PROMOTED" : "PROMOTION_BLOCKED",
        promotable
            ? "Promotion recorded after approval, compatibility, and monitoring prerequisites were supplied."
            : "Approval, compatibility, and monitoring configuration must be resolved before promotion.",
        promotable ? "MlModelVersionPromoted.v1" : "MlModelVersionPromotionBlocked.v1",
        uiTraceId);
  }

  @PostMapping("/models/{modelId}/versions/{version}/suspend")
  AdvisoryResponse<ModelMutationData> suspend(
      @PathVariable String modelId,
      @PathVariable String version,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return lifecycle(modelId, version, "SUSPENDED", "Suspension recorded for governance review.", "MlModelVersionSuspended.v1", uiTraceId);
  }

  @PostMapping("/models/{modelId}/versions/{version}/deprecate")
  AdvisoryResponse<ModelMutationData> deprecate(
      @PathVariable String modelId,
      @PathVariable String version,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return lifecycle(modelId, version, "DEPRECATED", "Deprecation requires replacement model and migration timeline evidence.", "MlModelVersionDeprecated.v1", uiTraceId);
  }

  @GetMapping("/models/compatibility")
  AdvisoryResponse<CompatibilityData> compatibility(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = traceId(uiTraceId, "compatibility");
    CompatibilityData data =
        new CompatibilityData(
            List.of(
                new CompatibilityEntry("model-version-config-required", "pricing-service", "UNTESTED"),
                new CompatibilityEntry("model-version-config-required", "eligibility-service", "UNTESTED"),
                new CompatibilityEntry("model-version-config-required", "ml-advisory-service", "COMPATIBLE")));
    return response(data, traceId, List.of("MlModelCompatibilityViewed.v1"), "");
  }

  @PostMapping("/models/compatibility/check")
  AdvisoryResponse<CompatibilityData> compatibilityCheck(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = traceId(uiTraceId, "compatibility-check");
    CompatibilityData data =
        new CompatibilityData(
            List.of(new CompatibilityEntry("model-version-config-required", "ml-advisory-service", "COMPATIBLE")));
    return response(data, traceId, List.of("MlModelCompatibilityChecked.v1"), "External consumer checks require configured service contracts.");
  }

  @GetMapping("/drift/feature")
  AdvisoryResponse<DriftData> featureDrift(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return drift("FEATURE", List.of("psi", "ks", "histogram"), uiTraceId);
  }

  @GetMapping("/drift/prediction")
  AdvisoryResponse<DriftData> predictionDrift(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return drift("PREDICTION", List.of("mean-shift", "distribution", "accuracy"), uiTraceId);
  }

  @GetMapping("/drift/population")
  AdvisoryResponse<DriftData> populationDrift(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return drift("POPULATION", List.of("population-stability-index", "cohort-trend"), uiTraceId);
  }

  @GetMapping("/drift/alerts")
  AdvisoryResponse<AlertsData> alerts(@RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = traceId(uiTraceId, "alerts");
    AlertsData data =
        new AlertsData(
            List.of(
                new AlertData(
                    "alert-config-required",
                    "CONFIGURATION_REQUIRED",
                    "ACTIVE",
                    "Configure monitoring policies and approved aggregate cohorts before alert evaluation.")));
    return response(data, traceId, List.of("MlDriftAlertsViewed.v1"), "", ALERTS_CACHE);
  }

  @PostMapping("/drift/alerts/{alertId}/acknowledge")
  AdvisoryResponse<AlertActionData> acknowledgeAlert(
      @PathVariable String alertId, @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return alertAction(alertId, "ACKNOWLEDGED", uiTraceId);
  }

  @PostMapping("/drift/alerts/{alertId}/resolve")
  AdvisoryResponse<AlertActionData> resolveAlert(
      @PathVariable String alertId, @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return alertAction(alertId, "RESOLVED", uiTraceId);
  }

  @PostMapping("/drift/alerts/{alertId}/suppress")
  AdvisoryResponse<AlertActionData> suppressAlert(
      @PathVariable String alertId, @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return alertAction(alertId, "SUPPRESSED", uiTraceId);
  }

  @PostMapping("/feedback")
  AdvisoryResponse<FeedbackData> feedback(
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) FeedbackRequest request) {
    FeedbackRequest safeRequest = request == null ? FeedbackRequest.empty() : request;
    String traceId = traceId(uiTraceId, "feedback");
    FeedbackData data =
        new FeedbackData(
            deterministicId(defaulted(safeRequest.recommendationId(), "recommendation-required"), traceId, "feedback"),
            defaulted(safeRequest.recommendationId(), "recommendation-required"),
            safeRequest.rating(),
            defaulted(safeRequest.outcome(), "REVIEW_REQUIRED"),
            "Feedback captured for model governance review; retraining infrastructure is outside this story scope.");
    return response(data, traceId, List.of("MlAdvisoryFeedbackCaptured.v1"), "");
  }

  private AdvisoryResponse<ModelMutationData> lifecycle(
      String modelId, String version, String status, String message, String event, String uiTraceId) {
    String traceId = traceId(uiTraceId, status.toLowerCase());
    return response(new ModelMutationData(modelId, version, status, message), traceId, List.of(event), "");
  }

  private AdvisoryResponse<DriftData> drift(String driftType, List<String> metrics, String uiTraceId) {
    String traceId = traceId(uiTraceId, driftType.toLowerCase());
    DriftData data =
        new DriftData(
            driftType,
            "BASELINE_CONFIGURATION_REQUIRED",
            metrics,
            List.of("approved-aggregate-cohorts-required"));
    return response(data, traceId, List.of("MlDriftMonitoringViewed.v1"), "Monitoring baselines are not configured in the service-local environment.", DRIFT_CACHE);
  }

  private AdvisoryResponse<AlertActionData> alertAction(String alertId, String status, String uiTraceId) {
    String traceId = traceId(uiTraceId, status.toLowerCase());
    return response(new AlertActionData(alertId, status), traceId, List.of("MlDriftAlertDispositionChanged.v1"), "");
  }

  private <T> AdvisoryResponse<T> response(T data, String traceId, List<String> events, String fallbackReason) {
    return response(data, traceId, events, fallbackReason, List.of());
  }

  private <T> AdvisoryResponse<T> response(
      T data, String traceId, List<String> events, String fallbackReason, List<CacheMetadata> cacheMetadata) {
    List<String> auditRefs = List.of("audit:ml-advisory:" + traceId);
    List<String> versionRefs = List.of("ml-advisory-api:v1", "PII-24-S36");
    return new AdvisoryResponse<>(
        DEFAULT_TENANT,
        DEPENDENCY_PARTIAL,
        data,
        auditRefs,
        deterministicId(traceId, data.toString(), versionRefs.toString()),
        versionRefs,
        traceId,
        events,
        fallbackReason == null ? "" : fallbackReason,
        cacheMetadata);
  }

  private String traceId(String uiTraceId, String fallback) {
    return uiTraceId == null || uiTraceId.isBlank() ? "ml-advisory-" + fallback + "-trace" : uiTraceId;
  }

  private String defaulted(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String deterministicId(String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public record AdvisoryResponse<T>(
      String tenantContext,
      String dependencyStatus,
      T data,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      String uiTraceId,
      List<String> events,
      String fallbackReason,
      List<CacheMetadata> cacheMetadata) {}

  public record CacheMetadata(String scope, String ttl, boolean cacheAvailable, String status) {}

  public record InsightsData(List<Recommendation> recommendations, boolean advisoryUnavailable, String fallbackReason) {}

  public record Recommendation(
      String recommendationId,
      String modelVersion,
      String confidence,
      String explanation,
      List<String> allowedActions,
      List<String> auditRefs,
      boolean automaticDecisionApplied) {}

  public record ModelsData(List<ModelRegistryEntry> models) {}

  public record ModelRegistryEntry(
      String modelId,
      String version,
      String status,
      String registeredBy,
      String registeredAt,
      List<String> artifacts,
      String validationStatus) {}

  public record ModelRegistrationRequest(String modelId, String version) {
    static ModelRegistrationRequest empty() {
      return new ModelRegistrationRequest("", "");
    }
  }

  public record ModelMutationData(String modelId, String version, String status, String message) {}

  public record PromotionRequest(boolean approvalSatisfied, boolean compatibilitySatisfied, boolean monitoringSatisfied) {
    static PromotionRequest empty() {
      return new PromotionRequest(false, false, false);
    }
  }

  public record ModelApprovalData(
      String modelId, String version, String status, List<String> criteria, List<String> approvers, String decision) {}

  public record ApprovalDecisionRequest(String actorId, String decision, String comments) {
    static ApprovalDecisionRequest empty() {
      return new ApprovalDecisionRequest("", "", "");
    }
  }

  public record CompatibilityData(List<CompatibilityEntry> matrix) {}

  public record CompatibilityEntry(String modelVersion, String consumerService, String status) {}

  public record DriftData(String driftType, String status, List<String> metrics, List<String> evidenceRefs) {}

  public record AlertsData(List<AlertData> alerts) {}

  public record AlertData(String alertId, String severity, String status, String recommendedAction) {}

  public record AlertActionData(String alertId, String status) {}

  public record FeedbackRequest(
      String recommendationId, int rating, String comment, String outcome, Map<String, String> modifiedValues) {
    public FeedbackRequest {
      modifiedValues = modifiedValues == null ? Map.of() : Map.copyOf(modifiedValues);
    }

    static FeedbackRequest empty() {
      return new FeedbackRequest("", 0, "", "", Map.of());
    }
  }

  public record FeedbackData(String feedbackId, String recommendationId, int rating, String outcome, String message) {}
}

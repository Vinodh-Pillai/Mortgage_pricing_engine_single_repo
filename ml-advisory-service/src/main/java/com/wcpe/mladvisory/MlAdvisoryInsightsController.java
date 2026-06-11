package com.wcpe.mladvisory;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/ml-advisory/insights")
public final class MlAdvisoryInsightsController {
  @GetMapping
  MlAdvisoryInsightsView insights(
      @PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "ml-s14-local-trace" : uiTraceId;
    return new MlAdvisoryInsightsView(tenantId, "MODEL_EVIDENCE_PARTIALLY_AVAILABLE", traceId,
        List.of(new AdvisoryRecommendationInsight("advisory-config-required", "model-version-ref-required",
            "confidence-score-from-model-output", "Model explanation is available as backend-owned rationale text only.",
            List.of("VIEW_EXPLANATION", "REQUEST_HUMAN_REVIEW", "EXPORT_AUDIT_EVIDENCE"),
            List.of("audit-ref-required", "replay-hash-required"), false)),
        List.of(
            new ModelVersionGovernanceInsight("model-version-ref-required", "DRIFT_BASELINE_REQUIRED",
                "ALERT_REVIEW_REQUIRED", List.of("feedback-loop-ref-required"),
                List.of("evidence-export-ref-required", "manifest-hash-required")),
            new ModelVersionGovernanceInsight("model-version-unavailable", "ADVISORY_UNAVAILABLE",
                "NO_ACTIVE_ALERT", List.of(), List.of())),
        true,
        "Configured model evidence, drift baselines, and export manifests are incomplete; the advisory module remains visible with explicit unavailable states.",
        List.of("MlAdvisoryInsightsOpened", "MlAdvisoryEvidenceGroupedByModelVersion"));
  }

  record MlAdvisoryInsightsView(String tenantContext, String dependencyStatus, String uiTraceId,
      List<AdvisoryRecommendationInsight> recommendations,
      List<ModelVersionGovernanceInsight> modelVersions, boolean advisoryUnavailable,
      String fallbackReason, List<String> events) {}

  record AdvisoryRecommendationInsight(String recommendationId, String modelVersion, String confidence,
      String explanation, List<String> allowedActions, List<String> auditRefs, boolean automaticDecisionApplied) {}

  record ModelVersionGovernanceInsight(String modelVersion, String driftStatus, String alertState,
      List<String> feedbackLoops, List<String> exportEvidenceRefs) {}
}

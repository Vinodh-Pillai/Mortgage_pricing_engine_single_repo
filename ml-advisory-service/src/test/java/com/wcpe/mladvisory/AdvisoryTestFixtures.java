package com.wcpe.mladvisory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AdvisoryTestFixtures {
  static final String TENANT = "11111111-1111-1111-1111-111111111111";
  static final String OTHER_TENANT = "22222222-2222-2222-2222-222222222222";
  static final Instant NOW = Instant.parse("2026-06-06T21:00:00Z");

  private AdvisoryTestFixtures() {}

  static MlAdvisoryControlService visibleService() {
    MlAdvisoryControlService service = new MlAdvisoryControlService(Clock.fixed(NOW, ZoneOffset.UTC));
    MlAdvisoryResult<MlAdvisoryControlResponse> result = service.createControl(visibleControlCommand());
    if (!result.valid()) {
      throw new IllegalStateException(result.errorCode().orElse("control failed"));
    }
    return service;
  }

  static CreateControlCommand visibleControlCommand() {
    return new CreateControlCommand(
        TENANT,
        "idem-control-visible",
        "ml-admin-1",
        Set.of(MlAdvisoryControlService.ADMIN_ROLE),
        "advisory-interface",
        "pricing-result",
        AdvisoryType.PRICING,
        AdvisoryMode.ADVISORY_VISIBLE,
        Instant.parse("2026-06-06T20:00:00Z"),
        null,
        "Enable advisory interface display",
        "MRM-2026-0003",
        "APPROVED_FOR_ADVISORY",
        "mrm-approver-1",
        "APPROVAL-PII-14-S03",
        "corr-control-PII-14-S03");
  }

  static GenerateAdvisoryCommand command(String idempotencyKey, double confidence) {
    return new GenerateAdvisoryCommand(
        TENANT,
        idempotencyKey,
        "pricing-analyst-1",
        "scenario-123",
        "pricing-result-456",
        "snapshot-789",
        "model-version-approved-1",
        AdvisoryType.PRICING,
        "Review advisory explanation before final pricing decision",
        confidence,
        confidence < 0.50 ? "LOW" : "VISIBLE",
        NOW,
        NOW.plusSeconds(3600),
        List.of(
            new AdvisoryReason(
                "CONFIGURED_MODEL_SIGNAL",
                1,
                "Tenant-configured model signal was present in the approved feature snapshot",
                "INFORMATIONAL",
                "feature-hash:income-stability",
                "NON_PUBLIC")),
        Map.of("snapshot", "snapshot-789", "pricingResult", "pricing-result-456"),
        AdvisoryDisplayPolicy.configured(0.50),
        "corr-advisory-PII-14-S03");
  }

  static ModelArtifactRef approvedArtifact() {
    return new ModelArtifactRef(
        "model-version-approved-1",
        "local://models/ml-advisory/model-version-approved-1.fake",
        "sha256:local-approved-model",
        "sha256:local-approved-model",
        "APPROVED_FOR_ADVISORY",
        "ml-advisory-feature-schema-v1");
  }

  static InferenceRequest inferenceRequest(String snapshotId) {
    return new InferenceRequest(
        TENANT,
        snapshotId,
        AdvisoryType.PRICING,
        "ml-advisory-feature-schema-v1",
        Map.of("incomeStability", "raw-borrower-feature-value"),
        "pricing-analyst-1",
        "corr-runtime-PII-14-S04",
        250,
        false,
        false,
        false);
  }

  static ModelInvocationRequest invocationRequest() {
    return new ModelInvocationRequest(approvedArtifact(), inferenceRequest("snapshot-789"));
  }

  static MlAdvisoryControlService pricingServiceWithSnapshot() {
    MlAdvisoryControlService service = visibleService();
    MlAdvisoryResult<FeatureSnapshotResponse> snapshot = service.captureFeatureSnapshot(pricingSnapshotCommand("idem-snapshot-pricing", false));
    if (!snapshot.valid()) {
      throw new IllegalStateException(snapshot.errorCode().orElse("snapshot failed"));
    }
    return service;
  }

  static MlAdvisoryControlService pricingServiceWithProhibitedProxySnapshot() {
    MlAdvisoryControlService service = visibleService();
    MlAdvisoryResult<FeatureSnapshotResponse> snapshot = service.captureFeatureSnapshot(pricingSnapshotCommand("idem-snapshot-proxy", true));
    if (!snapshot.valid()) {
      throw new IllegalStateException(snapshot.errorCode().orElse("snapshot failed"));
    }
    return service;
  }

  static CaptureFeatureSnapshotCommand pricingSnapshotCommand(String idempotencyKey, boolean prohibitedProxy) {
    return new CaptureFeatureSnapshotCommand(
        TENANT,
        idempotencyKey,
        "pricing-analyst-1",
        "scenario-123",
        "pricing-result-456",
        "eligibility-result-001",
        "ml-advisory-feature-schema-v1",
        CaptureMode.SHADOW_ONLY,
        "TENANT_APPROVED_ADVISORY_FEATURE_USE",
        "MORTGAGE_AUDIT_7Y",
        "corr-snapshot-PII-14-S05",
        List.of(
            new FeatureInput(
                prohibitedProxy ? "protectedClassProxy" : "incomeStabilitySignal",
                "STRING",
                prohibitedProxy ? "raw-prohibited-proxy-value" : "raw-borrower-feature-value",
                prohibitedProxy ? "PROHIBITED_PROXY" : "NON_PUBLIC",
                "pricing-context",
                prohibitedProxy ? "proxyField" : "incomeStability",
                prohibitedProxy ? "Should be suppressed by fair lending guard" : "Approved advisory feature snapshot signal")),
        Map.of("pricingResult", "pricing-result-456", "source", "pricing-context"));
  }

  static EvaluatePricingAdvisoryCommand pricingEvaluationCommand(String idempotencyKey, double confidence) {
    return pricingEvaluationCommand(idempotencyKey, confidence, "snapshot-789", false, false, false);
  }

  static EvaluatePricingAdvisoryCommand pricingEvaluationCommand(
      String idempotencyKey,
      double confidence,
      String snapshotId,
      boolean simulateTimeout,
      boolean simulateFailure,
      boolean simulateAuthoritativeOutput) {
    return new EvaluatePricingAdvisoryCommand(
        TENANT,
        idempotencyKey,
        "pricing-analyst-1",
        "scenario-123",
        "pricing-result-456",
        snapshotId,
        approvedArtifact(),
        confidence,
        AdvisoryDisplayPolicy.configured(0.50),
        List.of(
            new AdvisoryReason(
                "CONFIGURED_MODEL_SIGNAL",
                1,
                "Tenant-approved model signal was present in the approved feature snapshot",
                "INFORMATIONAL",
                "feature-hash:income-stability",
                "NON_PUBLIC")),
        NOW,
        NOW.plusSeconds(3600),
        "corr-pricing-advisory-PII-14-S05",
        250,
        simulateTimeout,
        simulateFailure,
        simulateAuthoritativeOutput);
  }
}

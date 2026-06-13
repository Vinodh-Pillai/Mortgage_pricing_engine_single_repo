package com.wcpe.margin;

import com.wcpe.margin.MarginReplayService.ReplayCommand;
import com.wcpe.margin.MarginReplayService.ReplayFixture;
import com.wcpe.margin.MarginReplayService.VersionManifest;
import com.wcpe.margin.MarginReplayService.WaterfallStep;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

final class MarginReplayTestFixtures {
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  static final String TENANT = "tenant-a";
  static final String FIXTURE_ID = "margin-comp-full-stack-pass";

  private MarginReplayTestFixtures() {}

  static VersionManifest fullStackManifest() {
    List<WaterfallStep> steps = fullStackSteps("company-margin-hash", "channel-margin-hash",
        "srp-hash", "branch-overlay-hash", "lo-comp-hash", "broker-comp-hash", "profitability-floor-hash");
    return new VersionManifest("manifest-margin-comp-full-stack",
        Map.of(
            "COMPANY", "company-policy:company-v1:cfg-company-v1",
            "CHANNEL", "channel-policy:channel-v1:cfg-channel-v1",
            "SRP", "srp-policy:srp-v1:cfg-srp-v1",
            "BRANCH_OVERLAY", "branch-policy:branch-v1:cfg-branch-v1",
            "LO", "lo-plan:lo-v1:cfg-lo-v1",
            "BROKER", "broker-plan:broker-v1:cfg-broker-v1",
            "PROFITABILITY_FLOOR", "profitability-policy:floor-v1:cfg-floor-v1"),
        steps,
        List.of("MarginPolicyPublished.v1", "SrpPolicyPublished.v1", "CompPlanPublished.v1", "DecisionReplayed.v1"),
        Map.of("ci", "local-harness", "seed", "PII-07-S10-governed-symbolic-fixture"));
  }

  static ReplayFixture fullStackFixture() {
    VersionManifest manifest = fullStackManifest();
    return new ReplayFixture(TENANT, FIXTURE_ID, "Full stack margin and compensation replay",
        "projects/margin-service/golden/margin-comp-full-stack-pass.json",
        MarginReplayService.manifestHash(manifest), MarginReplayService.resultHash(manifest.waterfallSteps()),
        expectedStepHashes(manifest), List.of("AUDIT", "MARGIN", "COMPENSATION"), true);
  }

  static ReplayCommand fullStackCommand(VersionManifest manifest) {
    return new ReplayCommand(TENANT, "audit-user", "quote-a", FIXTURE_ID,
        MarginReplayService.EXACT_MARGIN_COMP_MANIFEST,
        List.of(MarginReplayService.REPLAY_PERMISSION, MarginReplayService.SENSITIVE_REPLAY_PERMISSION),
        manifest, "corr-replay");
  }

  static List<WaterfallStep> fullStackSteps(String companyHash, String channelHash, String srpHash, String branchHash,
      String loHash, String brokerHash, String floorHash) {
    return List.of(
        step("BASE_PRICING", "rate-v1", "base-price-ref", "rate-price-conversion", "HALF_UP", "PUBLIC", "BASE", "base-hash", false),
        step("ADJUSTMENTS_OVERLAYS", "adjustment-v1", "adjustment-ref", "adjustment-conversion", "HALF_UP", "PUBLIC", "ADJ", "adjustment-hash", false),
        step("COMPANY_MARGIN", "company-v1", "company-margin-ref", "points-conversion", "HALF_UP", "SENSITIVE", "COMPANY_MARGIN", companyHash, true),
        step("CHANNEL_MARGIN", "channel-v1", "channel-margin-ref", "points-conversion", "HALF_UP", "SENSITIVE", "CHANNEL_MARGIN", channelHash, true),
        step("SRP", "srp-v1", "srp-spread-bps-ref", "bps-to-points", "HALF_UP", "SENSITIVE", "SRP-FNMA-RETAIL", srpHash, true),
        step("BRANCH_OVERLAY", "branch-v1", "branch-overlay-ref", "points-conversion", "HALF_UP", "SENSITIVE", "BRANCH_OVERLAY", branchHash, true),
        step("LO_COMPENSATION", "lo-v1", "lo-comp-ref", "bps-to-points", "HALF_UP", "SENSITIVE", "LO_COMP", loHash, true),
        step("BROKER_COMPENSATION", "broker-v1", "broker-comp-ref", "bps-to-points", "HALF_UP", "SENSITIVE", "BROKER_COMP", brokerHash, true),
        step("PROFITABILITY_FLOOR", "floor-v1", "profitability-floor-ref", "net-margin-bps", "HALF_UP", "SENSITIVE", "PROFITABILITY", floorHash, true),
        step("CONCESSIONS_COMPLIANCE_BEST_EXECUTION", "compliance-v1", "concession-ref", "compliance-conversion", "HALF_UP", "PUBLIC", "COMPLIANCE", "compliance-hash", false));
  }

  static Map<String, String> expectedStepHashes(VersionManifest manifest) {
    return manifest.waterfallSteps().stream()
        .collect(java.util.stream.Collectors.toMap(WaterfallStep::stepType, WaterfallStep::hashContribution));
  }

  private static WaterfallStep step(String stepType, String sourceVersionId, String amountRef, String conversionRef,
      String roundingMode, String visibility, String reasonCode, String hashContribution, boolean sensitive) {
    return new WaterfallStep(stepType, sourceVersionId, amountRef, conversionRef, roundingMode, visibility, reasonCode,
        hashContribution, sensitive);
  }
}

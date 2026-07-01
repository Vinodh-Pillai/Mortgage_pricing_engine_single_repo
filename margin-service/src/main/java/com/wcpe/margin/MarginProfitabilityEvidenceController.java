package com.wcpe.margin;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarginProfitabilityEvidenceController {
  @GetMapping({"/api/v1/margins/profitability-evidence", "/api/v1/margins/profitability"})
  public MarginProfitabilityEvidenceView profitabilityEvidence(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestHeader(value = "X-Margin-Compensation-Permission", required = false) String compensationPermission) {
    String tenant = tenantContext == null || tenantContext.isBlank() ? "ui-preview-tenant" : tenantContext;
    String trace = uiTraceId == null || uiTraceId.isBlank() ? "margin-s16-local-trace" : uiTraceId;
    boolean canViewCompensation = "pricing.margin.compensation.view_sensitive".equals(compensationPermission);

    return new MarginProfitabilityEvidenceView(tenant, "MARGIN_PROFITABILITY_EVIDENCE_VISIBLE", canViewCompensation,
        List.of(
            section("company-margin", "Company margin", "margin-service company margin policy", true,
                List.of("company-policy-version-ref-required", "company-margin-audit-ref-required"), List.of()),
            section("channel-margin", "Channel margin", "margin-service channel margin policy", true,
                List.of("channel-policy-version-ref-required", "channel-margin-audit-ref-required"), List.of()),
            section("srp", "Servicing Release Premium", "margin-service SRP policy", true,
                List.of("srp-policy-version-ref-required", "srp-audit-ref-required"), List.of()),
            section("branch-overlay", "Branch overlay", "margin-service branch hierarchy overlay", true,
                List.of("branch-overlay-version-ref-required", "branch-scope-audit-ref-required"), List.of()),
            section("lo-compensation", "LO compensation", "margin-service LO compensation plan", canViewCompensation,
                List.of("lo-comp-plan-version-ref-required", "lo-comp-audit-ref-required"), redaction(canViewCompensation, "LO compensation")),
            section("broker-compensation", "Broker compensation", "margin-service broker compensation plan", canViewCompensation,
                List.of("broker-comp-plan-version-ref-required", "broker-comp-audit-ref-required"), redaction(canViewCompensation, "Broker compensation")),
            section("profitability-floor", "Profitability floor", "margin-service profitability floor policy", true,
                List.of("profitability-floor-version-ref-required", "profitability-floor-audit-ref-required"), List.of()),
            section("approval-governance", "Approval and governance", "margin-service separation-of-duty controls", true,
                List.of("approval-audit-ref-required", "governance-replay-ref-required"), List.of()),
            section("replay-evidence", "Replay evidence", "margin-service replay hash boundary", true,
                List.of("margin-replay-hash-required", "margin-version-graph-hash-required"), List.of())),
        new ProfitabilityFloorEvidence("quote-option-contract-required", "BLOCKED", "PROFITABILITY_FLOOR_BREACH",
            "profitability-floor-version-ref-required", "profitability-threshold-ref-required",
            "profitability-exception-route-ref-required", List.of("profitability-floor-audit-ref-required"),
            "Backend-owned floor evidence is displayed for exception routing only; floors remain backend-owned."),
        List.of("margin-service.profitability", "margin-service.compensation", "margin-service.replay"),
        List.of("audit:margin-profitability-required", "audit:compensation-redaction-required"),
        "margin-profitability-replay-hash-required", trace, List.of("MarginProfitabilityEvidenceOpened"),
        "Configured margin-service persistence/contracts are not connected in local fallback mode; non-secret evidence refs, redaction reasons, and blocked states are exposed only.");
  }

  private static MarginEvidenceSection section(String id, String label, String sourceRef, boolean permitted,
      List<String> evidenceRefs, List<RedactionEvidence> redactions) {
    return new MarginEvidenceSection(id, label, sourceRef, permitted ? "VISIBLE" : "REDACTED", evidenceRefs, redactions);
  }

  private static List<RedactionEvidence> redaction(boolean permitted, String label) {
    if (permitted) {
      return List.of();
    }
    return List.of(new RedactionEvidence(label, "REDACTED", "pricing.margin.compensation.view_sensitive is required",
        "audit:compensation-redaction-required"));
  }

  public record MarginProfitabilityEvidenceView(String tenantContext, String status, boolean compensationDetailsVisible,
      List<MarginEvidenceSection> sections, ProfitabilityFloorEvidence floorEvidence, List<String> versionRefs,
      List<String> auditRefs, String replayHash, String uiTraceId, List<String> events, String fallbackReason) {}

  public record MarginEvidenceSection(String sectionId, String label, String sourceRef, String permissionState,
      List<String> evidenceRefs, List<RedactionEvidence> redactions) {}

  public record RedactionEvidence(String fieldLabel, String state, String reason, String auditRef) {}

  public record ProfitabilityFloorEvidence(String quoteOptionId, String decision, String decisionCode,
      String floorPolicyVersionRef, String thresholdRef, String exceptionRouteRef, List<String> auditRefs,
      String displayGuidance) {}
}

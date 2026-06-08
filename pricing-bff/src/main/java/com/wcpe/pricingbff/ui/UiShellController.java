package com.wcpe.pricingbff.ui;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
class UiShellController {
  @GetMapping("/api/ui/health")
  UiHealth health(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    return new UiHealth("pricing-bff", "UP", true, "NO_UPSTREAMS_CONFIGURED", correlationId, List.of());
  }

  @GetMapping("/api/v1/ui/menus/{persona}")
  UiMenu menu(@PathVariable String persona) {
    String normalizedPersona = persona == null || persona.isBlank() ? "default" : persona.toLowerCase(Locale.ROOT);
    return new UiMenu(normalizedPersona, List.of(
        new UiMenuItem("status", "Workbench status", "/", "main-content"),
        new UiMenuItem("help", "Help", "#help", "help-panel")));
  }

  @GetMapping("/api/v1/ui/notices")
  UiNotices notices() {
    return new UiNotices(List.of(new UiNotice("shell-baseline", "UI shell baseline is active.", "info", false)));
  }

  @GetMapping("/api/v1/ui/alerts/current")
  UiAlerts alerts() {
    return new UiAlerts(List.of());
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs")
  ResponseEntity<QuoteRunLaunch> launchQuoteRun(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) Map<String, Object> intake) {
    IntakeValidation validation = validateBorrowerIntake(intake);
    String traceId = normalizeTrace(uiTraceId);
    if (!validation.passed()) {
      return ResponseEntity.badRequest().body(QuoteRunLaunch.blocked(traceId, validation));
    }

    String runId = deterministicRunId(tenantId, intake);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new QuoteRunLaunch(runId, "CREATED", "/quote/" + runId + "/offers", validation, traceId,
            List.of("UIFlowOpened", "BorrowerIntakeSubmitted"), false, "UPSTREAM_CONTRACT_NOT_CONFIGURED"));
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/intake/validate")
  IntakeValidation validateQuoteRunIntake(@RequestBody(required = false) Map<String, Object> intake) {
    return validateBorrowerIntake(intake);
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/status")
  QuoteRunStatus quoteRunStatus(@PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return new QuoteRunStatus(runId, "CREATED", "/quote/" + runId + "/offers", normalizeTrace(uiTraceId),
        "UPSTREAM_CONTRACT_NOT_CONFIGURED");
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers")
  OfferComparisonView offerComparison(@PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return OfferComparisonView.upstreamMissing(runId, normalizeTrace(uiTraceId));
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/explain")
  OfferExplanationView offerExplanation(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return OfferExplanationView.missing(runId, offerId, normalizeTrace(uiTraceId));
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/select")
  ResponseEntity<OfferSelectionResult> selectOffer(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(OfferSelectionResult.blocked(runId, offerId, normalizeTrace(uiTraceId)));
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/lock")
  LockWorkflowView lockWorkflow(@PathVariable String runId,
      @RequestParam(value = "selectedOfferId", required = false) String selectedOfferId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    if (selectedOfferId == null || selectedOfferId.isBlank()) {
      return LockWorkflowView.blocked(runId, normalizeTrace(uiTraceId));
    }
    return LockWorkflowView.ready(runId, selectedOfferId, normalizeTrace(uiTraceId));
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/lock/confirm")
  ResponseEntity<LockConfirmationResult> confirmLock(@PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) LockConfirmRequest request) {
    String traceId = normalizeTrace(uiTraceId);
    if (request == null || request.selectedOfferId() == null || request.selectedOfferId().isBlank()) {
      return ResponseEntity.badRequest().body(LockConfirmationResult.blocked(runId, traceId));
    }
    if (request.selectedOfferId().toLowerCase(Locale.ROOT).contains("conflict")) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(LockConfirmationResult.conflict(runId, request.selectedOfferId(), traceId));
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(LockConfirmationResult.confirmed(runId, request.selectedOfferId(), traceId));
  }

  @GetMapping("/api/v1/partners/{partnerId}/quotes")
  PartnerQuoteListView partnerQuotes(@PathVariable String partnerId,
      @RequestParam(value = "status", required = false) String status,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String normalizedStatus = normalized(status).toUpperCase(Locale.ROOT);
    List<PartnerQuoteSummary> quotes = partnerQuoteFallbacks().stream()
        .filter(quote -> normalizedStatus.isBlank() || quote.status().equalsIgnoreCase(normalizedStatus))
        .toList();
    return new PartnerQuoteListView(partnerId, normalizeTenant(tenantContext), normalizedStatus, quotes,
        normalizePartnerTrace(uiTraceId), List.of("PartnerQuoteLoaded"));
  }

  @GetMapping("/api/v1/partners/{partnerId}/quotes/{quoteId}")
  PartnerQuoteDetail partnerQuoteDetail(@PathVariable String partnerId, @PathVariable String quoteId,
      @RequestParam(value = "apiPermit", required = false, defaultValue = "false") boolean apiPermit,
      @RequestHeader(value = "X-Partner-Role", required = false) String partnerRole,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    PartnerQuoteSummary summary = partnerQuoteFallbacks().stream()
        .filter(quote -> quote.quoteId().equals(quoteId))
        .findFirst()
        .orElseGet(() -> PartnerQuoteSummary.blocked(quoteId));
    boolean hasRoleContext = partnerRole != null && !partnerRole.isBlank();
    PartnerQuoteAction reprice = partnerRepriceAction(hasRoleContext, apiPermit);
    return new PartnerQuoteDetail(summary.quoteId(), summary.borrowerLabel(), summary.status(), summary.slaState(),
        summary.lockState(), summary.errorFlags(), normalizeTenant(tenantContext), partnerId,
        List.of("PartnerQuoteLoaded"), Map.of("reprice", reprice), normalizePartnerTrace(uiTraceId));
  }

  @PostMapping("/api/v1/partners/{partnerId}/quotes/{quoteId}/reprice")
  ResponseEntity<PartnerRepriceResult> partnerReprice(@PathVariable String quoteId,
      @RequestParam(value = "apiPermit", required = false, defaultValue = "false") boolean apiPermit,
      @RequestHeader(value = "X-Partner-Role", required = false) String partnerRole,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    boolean hasRoleContext = partnerRole != null && !partnerRole.isBlank();
    PartnerQuoteAction action = partnerRepriceAction(hasRoleContext, apiPermit);
    if (!action.permitted()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(PartnerRepriceResult.blocked(quoteId, action,
          normalizePartnerTrace(uiTraceId)));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(PartnerRepriceResult.accepted(quoteId,
        normalizePartnerTrace(uiTraceId)));
  }

  @GetMapping("/api/v1/ops/cases")
  OpsCaseListView opsCases(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return new OpsCaseListView(normalizeTenant(tenantContext), opsCaseFallbacks(), normalizeOpsTrace(uiTraceId),
        List.of("OpsCaseQueueOpened"));
  }

  @GetMapping("/api/v1/ops/cases/{caseId}")
  OpsCaseDetail opsCaseDetail(@PathVariable String caseId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    OpsCaseSummary summary = opsCaseFallbacks().stream()
        .filter(opsCase -> opsCase.caseId().equals(caseId))
        .findFirst()
        .orElseGet(() -> OpsCaseSummary.blocked(caseId));
    return new OpsCaseDetail(summary.caseId(), summary.priority(), summary.ageLabel(), summary.slaState(),
        summary.owner(), summary.status(), summary.contextSummary(), normalizeTenant(tenantContext),
        List.of(
            new OpsCaseTimelineEvent("timeline-opened", "OpsCaseOpened", "Operations case context opened."),
            new OpsCaseTimelineEvent("timeline-blocker", "LockBlockerContextLinked",
                "Blocked lock context is preserved for triage.")),
        List.of("evidence-packet-required-after-escalation"), normalizeOpsTrace(uiTraceId),
        List.of("OpsCaseOpened"));
  }

  @PostMapping("/api/v1/ops/cases/{caseId}/assign")
  ResponseEntity<OpsCaseAssignmentResult> assignOpsCase(@PathVariable String caseId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) OpsCaseAssignRequest request) {
    String traceId = normalizeOpsTrace(uiTraceId);
    if (request == null || request.owner() == null || request.owner().isBlank()) {
      return ResponseEntity.badRequest().body(new OpsCaseAssignmentResult(caseId, null, "BLOCKED",
          "Assignment requires an owner supplied by the operations user.", traceId, List.of("OpsCaseAssignmentBlocked")));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OpsCaseAssignmentResult(caseId, request.owner(),
        "ASSIGNED", "Ops case assignment recorded by pricing-bff fallback.", traceId, List.of("OpsCaseAssigned")));
  }

  @PostMapping("/api/v1/ops/cases/{caseId}/notes")
  ResponseEntity<OpsCaseNoteResult> addOpsCaseNote(@PathVariable String caseId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) OpsCaseNoteRequest request) {
    String traceId = normalizeOpsTrace(uiTraceId);
    if (request == null || request.note() == null || request.note().isBlank()) {
      return ResponseEntity.badRequest().body(new OpsCaseNoteResult(caseId, "BLOCKED",
          "Note addition requires note text before case context can be updated.", traceId,
          List.of("OpsCaseNoteBlocked")));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OpsCaseNoteResult(caseId, "NOTE_RECORDED",
        "Ops case note recorded by pricing-bff fallback without changing pricing state.", traceId,
        List.of("OpsCaseNoteAdded")));
  }

  @PostMapping("/api/v1/ops/cases/{caseId}/status")
  ResponseEntity<OpsCaseStatusResult> updateOpsCaseStatus(@PathVariable String caseId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) OpsCaseStatusRequest request) {
    String traceId = normalizeOpsTrace(uiTraceId);
    String status = request == null ? "" : normalized(request.status()).toUpperCase(Locale.ROOT);
    String reason = request == null ? "" : normalized(request.reason());
    String resolutionCode = request == null ? "" : normalized(request.resolutionCode()).toUpperCase(Locale.ROOT);

    if (status.equals("ESCALATED") && reason.isBlank()) {
      return ResponseEntity.badRequest().body(OpsCaseStatusResult.blocked(caseId, "ESCALATED",
          "Escalation requires a reason before downstream context can be preserved.", traceId));
    }
    if ((status.equals("RESOLVED") || status.equals("CLOSED")) && resolutionCode.isBlank()) {
      return ResponseEntity.badRequest().body(OpsCaseStatusResult.blocked(caseId, status,
          "Closing case requires an explicit resolution code supplied by the operations user.", traceId));
    }
    if (status.isBlank()) {
      return ResponseEntity.badRequest().body(OpsCaseStatusResult.blocked(caseId, "BLOCKED",
          "Status transition requires a target status.", traceId));
    }

    boolean resolved = status.equals("RESOLVED") || status.equals("CLOSED");
    String immutableSummary = resolved
        ? "Case " + caseId + " closed with resolution code " + resolutionCode + "."
        : "Case " + caseId + " transitioned to " + status + " with original context preserved.";
    List<String> events = status.equals("ESCALATED") ? List.of("OpsCaseEscalated")
        : resolved ? List.of("OpsCaseResolved") : List.of("OpsCaseOpened");
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OpsCaseStatusResult(caseId, status, immutableSummary,
        status.equals("ESCALATED"), false, traceId, events));
  }

  @GetMapping("/api/v1/compliance/evidence")
  ComplianceEvidenceRegistryView complianceEvidenceRegistry(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return new ComplianceEvidenceRegistryView(normalizeTenant(tenantContext), "FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE",
        complianceEvidenceFallbacks(), complianceDecisionFallbacks(), privacyRequestFallbacks(), securityEventFallbacks(),
        complianceAlertFallbacks(), retentionControlFallbacks(), normalizeComplianceTrace(uiTraceId),
        List.of("ComplianceEvidenceRegistryOpened"),
        "Configured compliance, audit-replay, security, privacy, and retention service contracts are unavailable; this response carries non-secret UI fallback records only.");
  }

  @GetMapping("/api/v1/partners/{partnerId}/integrations/webhooks")
  PartnerWebhookHealthView partnerWebhookHealth(@PathVariable String partnerId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return new PartnerWebhookHealthView(partnerId, normalizeTenant(tenantContext), "RETRY_HEALTH_VISIBLE",
        "latest 30 events", "DLQ size requires configured integration-service metrics", "Configured retry window required",
        partnerWebhookAttempts(), partnerSafetyToggles(),
        new PartnerWebhookAction(true,
            "Replay requires request correlation and explicit idempotency confirmation before it can be recorded.",
            "Confirm correlation id and idempotency before replay.", "/partners/support/webhooks"),
        new PartnerWebhookAction(false,
            "Endpoint test requires the configured partner webhook transport contract.",
            "Confirm endpoint ownership before testing.", "/partners/support/webhooks"),
        normalizePartnerTrace(uiTraceId), List.of("WebhookHealthChecked"));
  }

  @PostMapping("/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/test")
  ResponseEntity<PartnerWebhookActionResult> testPartnerWebhook(@PathVariable String webhookId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new PartnerWebhookActionResult(webhookId, null, "BLOCKED",
        "Endpoint test requires the configured partner webhook transport contract.",
        "Confirm endpoint ownership before testing.", false, normalizePartnerTrace(uiTraceId),
        List.of("WebhookActionBlocked")));
  }

  @PostMapping("/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/replay")
  ResponseEntity<PartnerWebhookActionResult> replayPartnerWebhook(@PathVariable String webhookId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PartnerWebhookReplayRequest request) {
    String traceId = normalizePartnerTrace(uiTraceId);
    if (request == null || request.correlationId() == null || request.correlationId().isBlank()
        || !request.idempotencyConfirmed()) {
      return ResponseEntity.badRequest().body(new PartnerWebhookActionResult(webhookId,
          request == null ? null : request.eventId(), "BLOCKED",
          "Replay requires request correlation and explicit idempotency confirmation before it can be recorded.",
          "Provide the observed correlation id and confirm idempotency for this replay request.", false, traceId,
          List.of("WebhookActionBlocked")));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PartnerWebhookActionResult(webhookId, request.eventId(),
        "ACCEPTED", "Webhook replay request recorded by pricing-bff fallback.",
        "Configured upstream replay execution remains outside this UI fallback slice.", false, traceId,
        List.of("WebhookReplayRequested")));
  }

  @PostMapping("/api/v1/partners/{partnerId}/integrations/webhooks/{webhookId}/safety")
  ResponseEntity<PartnerSafetyToggleResult> updatePartnerWebhookSafety(@PathVariable String webhookId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId,
      @RequestBody(required = false) PartnerSafetyToggleRequest request) {
    String traceId = normalizePartnerTrace(uiTraceId);
    if (request == null || !request.confirmed()) {
      return ResponseEntity.badRequest().body(new PartnerSafetyToggleResult(webhookId,
          request == null ? null : request.route(), request != null && request.paused(), "BLOCKED",
          "Safety toggle change requires explicit confirmation.", traceId, List.of("WebhookSafetyToggleBlocked")));
    }
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new PartnerSafetyToggleResult(webhookId, request.route(),
        request.paused(), "VISIBLE", "Safety toggle change is visible in the BFF fallback response.", traceId,
        List.of("WebhookSafetyToggled")));
  }

  @GetMapping("/api/v1/quality/dashboard")
  QualityDashboardView qualityDashboard(@RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Quality-Role", required = false) String qualityRole,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    boolean privileged = qualityRole != null && !qualityRole.isBlank()
        && !qualityRole.equalsIgnoreCase("viewer");
    String traceId = normalizeQualityTrace(uiTraceId);
    return new QualityDashboardView(normalizeTenant(tenantContext), "FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE",
        new QualityValidationRun("validation-run-config-required", "BLOCKED", "RED",
            "Block package closure and route unresolved blockers to rework until configured evidence is linked.",
            List.of(
                new QualityValidationStage("V1", "Preflight", "PASS", "timestamp supplied by configured validator"),
                new QualityValidationStage("V2", "Contract Validation", "FAIL", "contract service conformance required"),
                new QualityValidationStage("V3", "Execution Validation", "PENDING", "execution validator unavailable"),
                new QualityValidationStage("V4", "End-to-End Consistency", "PENDING", "pipeline evidence required"),
                new QualityValidationStage("V5", "Closure Validation", "BLOCKED", "blocker evidence required")),
            List.of(
                new QualityBlocker("blocker-contract-conformance", "P1", "workflow", "Release engineering", "OPEN",
                    "Configured contract conformance evidence is missing."),
                new QualityBlocker("blocker-evidence-completeness", "P2", "data", "Quality operations", "OPEN",
                    "Evidence package completeness must be supplied by upstream quality APIs.")),
            List.of("validation_result.json", "validation_trace.jsonl", "module_evidence_index.json",
                "blocker_register.json")),
        new QualityReadinessStatus("fail", true,
            List.of("P1 contract conformance blocker is open", "Evidence set completeness is incomplete"),
            List.of("Quality owner signature required", "Release owner signature required"),
            List.of("smoke check: configured result required", "schema compatibility: blocked", "config validation: pending",
                "policy signatures: required"),
            "missing required evidence references"),
        new QualityDriftSummary("pricing-quality", "configured analysis window required", "configured baseline required",
            List.of("product set supplied by quality API"), "stale",
            "Comparison controls are locked until baseline and sample-window evidence are supplied.",
            List.of(new QualityDriftMetric("contract_failure_rate", "P2", "deviation value supplied by configured metrics"),
                new QualityDriftMetric("validation_rework_queue", "P3", "trend supplied by configured metrics"))),
        new QualityFairnessSummary(qualityDimensions(privileged), !privileged, "sample counts supplied by fairness API",
            "P1", "Risk and compliance owner", List.of("fairness-evidence-package-required")),
        List.of(
            new QualityIncident("quality-incident-contract", "P1", "Release engineering", "contract", "playbook-required",
                "mitigating", "evidence-package-required", List.of("pricing-bff", "governance-service")),
            new QualityIncident("quality-incident-drift", "P2", "Quality operations", "drift", "playbook-required",
                "acknowledged", "evidence-package-required", List.of("observability-service"))),
        new QualityReplaySummary("policySnapshotId-required", "inputBundleRef-required", "deterministicSeed-required",
            false, "Replay is blocked until configured snapshot, seed, and event payload evidence are supplied.",
            List.of("regression replay", "deterministic quote replay", "webhook/event replay")),
        List.of(
            new QualityContractConformance("pricing-bff-ui-quality", "FAIL", "schema compatibility evidence required",
                List.of("quality-dashboard contract pending upstream conformance")),
            new QualityContractConformance("partner-transport-events", "PENDING", "event envelope evidence required",
                List.of("webhook replay conformance requires integration-service contract"))),
        new QualityEvidenceExport("quality-evidence-package-required", "INCOMPLETE", true,
            List.of("validation_result.json", "validation_trace.jsonl", "module_evidence_index.json",
                "blocker_register.json"),
            List.of("Completeness status is incomplete until configured evidence store is available")),
        traceId, List.of("QualityDashboardOpened"),
        "Configured quality analytics, drift, fairness, replay, and contract services are unavailable; this response carries non-secret UI fallback records only.");
  }

  @GetMapping("/api/v1/quality/evidence/export")
  QualityEvidenceExport qualityEvidenceExport() {
    return new QualityEvidenceExport("quality-evidence-package-required", "INCOMPLETE", true,
        List.of("validation_result.json", "validation_trace.jsonl", "module_evidence_index.json", "blocker_register.json"),
        List.of("Export is redacted and incomplete until configured quality evidence storage is available."));
  }

  @GetMapping("/api/v1/admin/governance")
  AdminGovernanceView adminGovernance(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Admin-Role", required = false) String adminRole,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = normalizeAdminTrace(uiTraceId);
    List<OpenDecisionGate> openDecisions = adminOpenDecisionGates();
    List<String> releaseBlockers = List.of(
        "OD-001 unresolved blocks RBAC source and role-to-privilege ingestion.",
        "OD-002 unresolved blocks exact approver quorum for each environment.",
        "OD-004 unresolved blocks emergency feature-flag disable routing.",
        "OD-005 unresolved blocks retention windows for override and diff artifacts.",
        "Configured governance-service release execution contract is unavailable.");

    return new AdminGovernanceView(normalizeTenant(tenantContext), normalizedAdminRole(adminRole),
        "FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE", traceId,
        new AdminTraceMetadata(traceId, "artifact-admin-governance-fallback", "policy-version-required",
            "environment-config-required", "signer-contract-required"),
        List.of(
            new PolicyVersionSummary("policy-v2.3.1", "Policy owner required", "validation_pending",
                "environment binding supplied by governance-service", "policy-v2.3.0", "hash-placeholder-required",
                List.of("module constraint impact requires configured policy diff service",
                    "validation path impact requires configured policy validation evidence")),
            new PolicyVersionSummary("policy-v2.3.0", "Policy owner required", "approved",
                "environment binding supplied by governance-service", "policy-v2.2.0", "hash-placeholder-required",
                List.of("lineage visible; deployment state requires governance-service contract"))),
        List.of(new FeatureFlagSummary("flag-config-required", "environment target required", false,
            List.of("DEPENDENCY_CONTRACT_REQUIRED", "OD-004_UNRESOLVED"), true,
            "Emergency disable path is blocked until OD-004 is resolved and dual-control evidence is configured.")),
        List.of(new MarketRuleSummary("market-rule-config-required", "state-rule staging", "staged",
            List.of("caps", "disclosures", "usury", "antiRedlining"), true,
            "Completeness gate blocks promotion until configured market-rule evidence supplies required fields.")),
        List.of(new ChangeRequestSummary("CR-release-candidate-config-required", "release_candidate", "blocked", "P2",
            "Release / Governance Manager", List.of("pending_review", "compliance_check", "governance_check", "approved", "deployed"),
            true, openDecisionIds(openDecisions))),
        new ReleaseCandidateReadiness("RC-config-required", "RED", "environment-config-required", true, true,
            "releaseFingerprint-required", "manifestRef-required", "signature-required",
            List.of(
                new ReleaseGateSummary("smoke-tests", "BLOCKED", true, "Configured smoke test evidence is required."),
                new ReleaseGateSummary("schema-compatibility", "BLOCKED", true,
                    "Configured schema compatibility evidence is required."),
                new ReleaseGateSummary("policy-signatures", "BLOCKED", true,
                    "Policy signature evidence is unavailable until governance-service contract exists."),
                new ReleaseGateSummary("config-validation", "BLOCKED", true,
                    "Config validation is blocked by unresolved OD-001 and OD-002."),
                new ReleaseGateSummary("quality-guardrails", "FAIL", true,
                    "Quality guardrails report open blockers from /api/v1/quality/dashboard."),
                new ReleaseGateSummary("rollback-readiness", "BLOCKED", true,
                    "Rollback target and blast-impact thresholds require configured release contract.")),
            releaseBlockers, List.of("pricing", "workflow", "notifications", "disclosures")),
        openDecisions,
        List.of(new DriftAlertSummary("drift-config-required", "HIGH", "environment-config-required", "SRE / Operations Lead",
            "Configured baseline and alert threshold are required; no numeric threshold is inferred.", false)),
        List.of(new IncidentReviewSummary("INC-release-gate-config-required", "active", "rollback-target-required",
            false, false, true,
            "Rollback execution is disabled until configured rollback target, RCA, corrective action, and dual-control evidence exist.")),
        List.of(new OverrideLedgerEntry("override-ledger-config-required", "actor-required", "timestamp-required",
            "fieldPath-required", "old-value-redacted", "new-value-redacted", "policy_ref-required",
            "reason-required", true, "auditRef-required")),
        List.of("AdminGovernanceOpened"),
        "Configured governance, policy, release, drift, incident, and audit services are unavailable; this response carries non-secret UI fallback records only.");
  }

  private List<PartnerQuoteSummary> partnerQuoteFallbacks() {
    return List.of(
        new PartnerQuoteSummary("quote-active", "Borrower context available", "ACTIVE",
            "Awaiting configured SLA contract", "LOCK_NOT_REQUESTED", List.of()),
        new PartnerQuoteSummary("quote-blocked", "Borrower context redacted", "BLOCKED",
            "Awaiting configured SLA contract", "LOCK_BLOCKED", List.of("UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED")));
  }

  private List<OpsCaseSummary> opsCaseFallbacks() {
    return List.of(
        new OpsCaseSummary("ops-lock-blocked", "CRITICAL", "Age supplied by configured ops-case API", "SLA contract required",
            "Unassigned", "OPEN", "Blocked lock workflow requires operations triage."),
        new OpsCaseSummary("ops-evidence-pending", "NORMAL", "Age supplied by configured ops-case API",
            "SLA contract required", "Operations queue", "PENDING_EVIDENCE",
            "Escalation evidence packet is not yet linked."));
  }

  private List<ComplianceEvidenceArtifact> complianceEvidenceFallbacks() {
    return List.of(
        new ComplianceEvidenceArtifact("evidence-ops-lock-blocked", "/ops/cases/ops-lock-blocked", "OPS_ESCALATION",
            "Operations queue", "Configured retention class required", "operations", "v1", "hash-placeholder-required",
            "trace-ops-s06", "policy-version-required", "policy-digest-required", "jurisdiction-config-required",
            "CHAIN_CONTINUITY_UNVERIFIED", List.of("m10-lock", "ops-case-triage"), true,
            List.of("Missing configured compliance evidence store", "Policy version must be supplied by upstream contract")),
        new ComplianceEvidenceArtifact("evidence-partner-webhook-blocked", "/partners/webhooks", "SECURITY_EVENT",
            "Security queue", "Configured retention class required", "partner-transport", "v1",
            "hash-placeholder-required", "trace-ch-s05", "policy-version-required", "policy-digest-required",
            "jurisdiction-config-required", "CHAIN_CONTINUITY_UNVERIFIED", List.of("m16-integration", "security-events"),
            true, List.of("Webhook evidence store unavailable", "Tenant and delegation scope require configured auth contract")));
  }

  private List<ComplianceDecisionRationale> complianceDecisionFallbacks() {
    return List.of(new ComplianceDecisionRationale("decision-explainability-required", "RULE_SOURCE_REQUIRED",
        "Human-readable adverse-action and ranking explanations require configured policy/explainability contracts.",
        "jurisdiction-config-required", List.of("policy", "eligibility"), true, "disclosure-artifact-required"));
  }

  private List<PrivacyRequestSummary> privacyRequestFallbacks() {
    return List.of(new PrivacyRequestSummary("dsar-config-required", "Borrower reference redacted", "restricted",
        "unverified", "SLA deadline supplied by configured privacy service", "consentAuditRef-required",
        List.of("Identity verification contract unavailable", "Legal hold and retention exceptions must be evaluated upstream")));
  }

  private List<SecurityEventSummary> securityEventFallbacks() {
    return List.of(new SecurityEventSummary("security-event-config-required", "vulnerability finding", "P2",
        "Security owner required", "logRecordId-required", "trace-ch-s05", false,
        List.of("Explicit owner acknowledgment required before release handoff when upstream severity contract confirms P1/P2")));
  }

  private List<ComplianceAlertSummary> complianceAlertFallbacks() {
    return List.of(new ComplianceAlertSummary("alert-missing-evidence", "P2", "workflow", "missing_evidence",
        "Owner queue required", false, List.of("Evidence attachment pending", "Deduplication contract unavailable")));
  }

  private List<RetentionControlSummary> retentionControlFallbacks() {
    return List.of(new RetentionControlSummary("retention-rule-config-required", "Configured retention class required",
        "Retention window supplied by configured policy", true, "OD-005 unresolved blocks destructive retention actions",
        "backup inventory supplied by configured evidence store"));
  }

  private PartnerQuoteAction partnerRepriceAction(boolean hasRoleContext, boolean apiPermit) {
    boolean permitted = hasRoleContext && apiPermit;
    if (permitted) {
      return new PartnerQuoteAction(true, true, "API permit is true and partner role context is present.",
          "/partners/support/reprice");
    }
    return new PartnerQuoteAction(false, false,
        "Reprice requires partner role context and an explicit API permit from the configured partner quote contract.",
        "/partners/support/reprice");
  }

  private List<PartnerWebhookDeliveryAttempt> partnerWebhookAttempts() {
    return List.of(
        new PartnerWebhookDeliveryAttempt("webhook-pricing-updates", "event-quote-active", "/partners/quotes",
            "DELIVERED", "NONE", "2026-06-08T07:15:00Z", "No failure recorded in fallback sample.",
            "CONFIRMED_REQUIRED_FOR_REPLAY", "MASKING_INDICATOR_PRESENT", "CONSENT_INDICATOR_PRESENT"),
        new PartnerWebhookDeliveryAttempt("webhook-pricing-updates", "event-quote-blocked", "/partners/quotes",
            "FAILED", "UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED", "2026-06-08T07:15:00Z",
            "Configured partner webhook transport is unavailable at the BFF boundary.",
            "CONFIRMED_REQUIRED_FOR_REPLAY", "MASKING_INDICATOR_PRESENT", "CONSENT_INDICATOR_PRESENT"),
        new PartnerWebhookDeliveryAttempt("webhook-lock-alerts", "event-lock-blocked", "/partners/alerts",
            "DLQ_PENDING", "DLQ_METRICS_CONTRACT_REQUIRED", "2026-06-08T07:10:00Z",
            "DLQ size and retry aging require configured integration-service metrics.",
            "CONFIRMED_REQUIRED_FOR_REPLAY", "MASKING_INDICATOR_PRESENT", "CONSENT_INDICATOR_PRESENT"));
  }

  private List<PartnerSafetyToggle> partnerSafetyToggles() {
    return List.of(
        new PartnerSafetyToggle("webhook-pricing-updates", "/partners/quotes", false,
            "Auto-emit is enabled in the visible BFF fallback state."),
        new PartnerSafetyToggle("webhook-lock-alerts", "/partners/alerts", true,
            "Auto-emit is paused for this route in the visible BFF fallback state."));
  }

  private String normalizeTenant(String tenantContext) {
    return tenantContext == null || tenantContext.isBlank() ? "TENANT_CONTEXT_UNAVAILABLE" : tenantContext;
  }

  private String normalizePartnerTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "ch-s02-local-trace" : uiTraceId;
  }

  private String normalizeOpsTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "ops-s06-local-trace" : uiTraceId;
  }

  private String normalizeComplianceTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "sec-s07-local-trace" : uiTraceId;
  }

  private String normalizeQualityTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "ql-s08-local-trace" : uiTraceId;
  }

  private String normalizeAdminTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "ag-s09-local-trace" : uiTraceId;
  }

  private String normalizedAdminRole(String adminRole) {
    return adminRole == null || adminRole.isBlank() ? "admin-role-context-required" : adminRole;
  }

  private List<OpenDecisionGate> adminOpenDecisionGates() {
    return List.of(
        new OpenDecisionGate("OD-001", "Enterprise identity and RBAC source for role-to-privilege ingestion", "BLOCKING",
            "world-class-pricing-engine/11-assumptions-open-decisions.md"),
        new OpenDecisionGate("OD-002", "Exact approver quorum per environment", "BLOCKING",
            "world-class-pricing-engine/11-assumptions-open-decisions.md"),
        new OpenDecisionGate("OD-004", "Emergency feature-flag disable path", "BLOCKING",
            "world-class-pricing-engine/11-assumptions-open-decisions.md"),
        new OpenDecisionGate("OD-005", "Retention windows for override and diff artifact views", "BLOCKING",
            "market-gap-update-plan.md"));
  }

  private List<String> openDecisionIds(List<OpenDecisionGate> openDecisions) {
    return openDecisions.stream().map(OpenDecisionGate::decisionId).toList();
  }

  private List<String> qualityDimensions(boolean privileged) {
    if (privileged) {
      return List.of("configured-dimension-ref-required", "policy-approved-dimension-ref-required");
    }
    return List.of("masked-class-label", "masked-policy-approved-dimension");
  }

  private IntakeValidation validateBorrowerIntake(Map<String, Object> intake) {
    Map<String, String> blockers = new LinkedHashMap<>();
    if (isBlankText(intake, "borrowerName")) {
      blockers.put("borrowerName", "Borrower name is required before a quote run can start.");
    }
    if (isBlankText(intake, "contactEmail")) {
      blockers.put("contactEmail", "Contact email is required before a quote run can start.");
    }
    if (isBlankText(intake, "quoteGoal")) {
      blockers.put("quoteGoal", "Quote goal is required before a quote run can start.");
    }

    if (!blockers.isEmpty()) {
      return new IntakeValidation(false, "BLOCKED", "Complete the highlighted required fields.", blockers);
    }

    return new IntakeValidation(true, "PASSED", "Required borrower intake fields are present.", Map.of());
  }

  private boolean isBlankText(Map<String, Object> intake, String field) {
    Object value = intake == null ? null : intake.get(field);
    return value == null || value.toString().isBlank();
  }

  private String deterministicRunId(String tenantId, Map<String, Object> intake) {
    String seed = normalized(tenantId) + "|" + normalized(intake.get("borrowerName")) + "|"
        + normalized(intake.get("contactEmail")) + "|" + normalized(intake.get("quoteGoal"));
    return "run-" + Integer.toUnsignedString(seed.hashCode(), 36);
  }

  private String normalizeTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "brw-s01-local-trace" : uiTraceId;
  }

  private String normalized(Object value) {
    return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
  }

  record UiHealth(String service, String status, boolean ready, String dependencyStatus, String correlationId,
      List<String> dependencies) {}

  record UiMenu(String persona, List<UiMenuItem> items) {}

  record UiMenuItem(String id, String label, String href, String focusTarget) {}

  record UiNotices(List<UiNotice> notices) {}

  record UiNotice(String id, String message, String level, boolean dismissible) {}

  record UiAlerts(List<UiAlert> alerts) {}

  record UiAlert(String id, String message, String severity, Instant createdAt) {}

  record IntakeValidation(boolean passed, String status, String message, Map<String, String> blockers) {}

  record QuoteRunLaunch(String runId, String status, String nextRoute, IntakeValidation validationSummary, String uiTraceId,
      List<String> events, boolean fallbackMode, String dependencyStatus) {
    static QuoteRunLaunch blocked(String traceId, IntakeValidation validation) {
      return new QuoteRunLaunch(null, "BLOCKED", null, validation, traceId, List.of("UIFlowOpened"), true,
          "UPSTREAM_NOT_CALLED");
    }
  }

  record QuoteRunStatus(String runId, String status, String nextRoute, String uiTraceId, String dependencyStatus) {}

  record OfferComparisonView(String runId, String status, List<OfferSummary> offers, List<String> sortOptions,
      String selectedOfferId, boolean commitBlocked, String fallbackReason, String uiTraceId, List<String> events) {
    static OfferComparisonView upstreamMissing(String runId, String traceId) {
      return new OfferComparisonView(runId, "UPSTREAM_EXPLAINABILITY_REQUIRED", List.of(),
          List.of("payment", "apr", "confidence"), null, true,
          "Offer comparison requires a configured quote-service offers and explainability contract before commit.", traceId,
          List.of("OfferListRendered"));
    }
  }

  record OfferSummary(String offerId, int rank, String productLabel, String payment, String apr, String confidence,
      List<String> rationaleChips, List<String> scenarioFlags, String explanationStatus, String sourceScenarioId) {}

  record OfferExplanationView(String runId, String offerId, String status, List<String> rationaleLines,
      List<String> scenarioFlags, boolean commitBlocked, String message, String uiTraceId) {
    static OfferExplanationView missing(String runId, String offerId, String traceId) {
      return new OfferExplanationView(runId, offerId, "MISSING", List.of(), List.of(), true,
          "Explanation data is not available from the configured BFF boundary; selection remains blocked.", traceId);
    }
  }

  record OfferSelectionResult(String runId, String selectedOfferId, String status, String nextRoute, String sourceScenarioId,
      String auditRef, String message, String uiTraceId, List<String> events) {
    static OfferSelectionResult blocked(String runId, String offerId, String traceId) {
      return new OfferSelectionResult(runId, null, "BLOCKED", null, null, null,
          "Offer selection is blocked until explanation data is available for offer " + offerId + ".", traceId,
          List.of("OfferSelectionBlocked"));
    }
  }

  record LockWorkflowView(String runId, String selectedOfferId, String status, boolean lockDisabled,
      List<String> blockers, String disclosureText, String nextAction, String uiTraceId, List<String> events,
      String dependencyStatus) {
    static LockWorkflowView blocked(String runId, String traceId) {
      return new LockWorkflowView(runId, null, "BLOCKED", true,
          List.of("Select an offer before requesting a lock.",
              "Lock-service eligibility and pricing-staleness contracts are not configured at this BFF boundary."),
          "Review lock disclosures after an offer is selected. No terms are locked from the blocked state.",
          "Return to offer comparison and select an offer with available explanation context.", traceId,
          List.of("LockBlocked"), "UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED");
    }

    static LockWorkflowView ready(String runId, String selectedOfferId, String traceId) {
      return new LockWorkflowView(runId, selectedOfferId, "READY", false, List.of(),
          "Confirming records the selected offer for lock workflow tracking. Final lock eligibility remains owned by the configured lock-service contract.",
          "Confirm lock request", traceId, List.of("LockAttempted"), "UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED");
    }
  }

  record LockConfirmRequest(String selectedOfferId, boolean disclosuresAccepted) {}

  record LockConfirmationResult(String runId, String selectedOfferId, String status, String lockId, String lockStatus,
      String expiresAt, String statusRoute, String message, String uiTraceId, List<String> events, List<String> blockers) {
    static LockConfirmationResult blocked(String runId, String traceId) {
      return new LockConfirmationResult(runId, null, "BLOCKED", null, null, null, null,
          "Lock confirmation requires a selected offer context.", traceId, List.of("LockBlocked"),
          List.of("Select an offer before confirming lock."));
    }

    static LockConfirmationResult conflict(String runId, String selectedOfferId, String traceId) {
      return new LockConfirmationResult(runId, selectedOfferId, "CONFLICT", null, null, null, null,
          "Lock conflict returned by BFF fallback: refresh status or choose another offer without losing context.", traceId,
          List.of("LockBlocked"), List.of("A competing lock context exists for the selected offer."));
    }

    static LockConfirmationResult confirmed(String runId, String selectedOfferId, String traceId) {
      String lockId = "lock-" + Integer.toUnsignedString((runId + "|" + selectedOfferId).hashCode(), 36);
      return new LockConfirmationResult(runId, selectedOfferId, "CONFIRMED", lockId, "LOCK_REQUEST_RECORDED",
          "Pending configured lock-service response", "/quote/" + runId + "/status",
          "Lock request recorded for selected offer " + selectedOfferId + ".", traceId, List.of("LockSuccess"), List.of());
    }
  }

  record PartnerQuoteSummary(String quoteId, String borrowerLabel, String status, String slaState, String lockState,
      List<String> errorFlags) {
    static PartnerQuoteSummary blocked(String quoteId) {
      return new PartnerQuoteSummary(quoteId, "Borrower context unavailable", "BLOCKED",
          "Awaiting configured SLA contract", "LOCK_BLOCKED", List.of("PARTNER_QUOTE_NOT_FOUND_IN_FALLBACK"));
    }
  }

  record PartnerQuoteAction(boolean visible, boolean permitted, String guidance, String supportHandoffRoute) {}

  record PartnerQuoteListView(String partnerId, String tenantContext, String statusFilter,
      List<PartnerQuoteSummary> quotes, String uiTraceId, List<String> events) {}

  record PartnerQuoteDetail(String quoteId, String borrowerLabel, String status, String slaState, String lockState,
      List<String> errorFlags, String tenantContext, String partnerId, List<String> lifecycleEvents,
      Map<String, PartnerQuoteAction> actions, String uiTraceId) {}

  record PartnerRepriceResult(String quoteId, String status, String message, String guidance, String supportHandoffRoute,
      String uiTraceId, List<String> events) {
    static PartnerRepriceResult blocked(String quoteId, PartnerQuoteAction action, String traceId) {
      return new PartnerRepriceResult(quoteId, "BLOCKED", "Partner reprice is blocked by the BFF fallback contract.",
          action.guidance(), action.supportHandoffRoute(), traceId, List.of("PartnerActionBlocked"));
    }

    static PartnerRepriceResult accepted(String quoteId, String traceId) {
      return new PartnerRepriceResult(quoteId, "ACCEPTED", "Partner reprice request recorded by pricing-bff fallback.",
          "Configured upstream repricing remains outside this UI fallback slice.", "/partners/support/reprice", traceId,
           List.of("PartnerQuoteRepriced"));
    }
  }

  record OpsCaseListView(String tenantContext, List<OpsCaseSummary> cases, String uiTraceId, List<String> events) {}

  record OpsCaseSummary(String caseId, String priority, String ageLabel, String slaState, String owner, String status,
      String contextSummary) {
    static OpsCaseSummary blocked(String caseId) {
      return new OpsCaseSummary(caseId, "UNKNOWN", "Age supplied by configured ops-case API", "SLA contract required",
          "Unassigned", "BLOCKED", "Ops case not found in deterministic BFF fallback.");
    }
  }

  record OpsCaseDetail(String caseId, String priority, String ageLabel, String slaState, String owner, String status,
      String contextSummary, String tenantContext, List<OpsCaseTimelineEvent> timeline, List<String> evidencePacketIds,
      String uiTraceId, List<String> events) {}

  record OpsCaseTimelineEvent(String eventId, String eventType, String summary) {}

  record OpsCaseAssignRequest(String owner) {}

  record OpsCaseAssignmentResult(String caseId, String owner, String status, String message, String uiTraceId,
      List<String> events) {}

  record OpsCaseNoteRequest(String note) {}

  record OpsCaseNoteResult(String caseId, String status, String message, String uiTraceId, List<String> events) {}

  record OpsCaseStatusRequest(String status, String reason, String resolutionCode, String actor) {}

  record OpsCaseStatusResult(String caseId, String status, String immutableSummary, boolean escalationContextPreserved,
      boolean downstreamExecuted, String uiTraceId, List<String> events) {
    static OpsCaseStatusResult blocked(String caseId, String status, String message, String traceId) {
      return new OpsCaseStatusResult(caseId, status, message, false, false, traceId, List.of("OpsCaseActionBlocked"));
    }
  }

  record ComplianceEvidenceRegistryView(String tenantContext, String dependencyStatus,
      List<ComplianceEvidenceArtifact> artifacts, List<ComplianceDecisionRationale> decisions,
      List<PrivacyRequestSummary> privacyRequests, List<SecurityEventSummary> securityEvents,
      List<ComplianceAlertSummary> alerts, List<RetentionControlSummary> retentionControls, String uiTraceId,
      List<String> events, String fallbackReason) {}

  record ComplianceEvidenceArtifact(String artifactId, String path, String artifactType, String owner,
      String retentionClass, String relatedModule, String version, String hash, String traceId, String policyVersion,
      String policyDigest, String jurisdictionCode, String continuityStatus, List<String> moduleLinks,
      boolean progressionBlocked, List<String> blockers) {}

  record ComplianceDecisionRationale(String decisionId, String reasonCode, String humanText, String jurisdictionCode,
      List<String> reasonTiers, boolean exportBlocked, String disclosureArtifactRef) {}

  record PrivacyRequestSummary(String requestId, String borrowerRef, String requestedScope, String identityStatus,
      String slaState, String consentAuditRef, List<String> blockers) {}

  record SecurityEventSummary(String eventId, String category, String severity, String owner, String logRecordId,
      String correlationId, boolean acknowledged, List<String> blockers) {}

  record ComplianceAlertSummary(String alertId, String severity, String alertClass, String triggerType, String routeTarget,
      boolean acknowledged, List<String> blockers) {}

  record RetentionControlSummary(String ruleId, String retentionClass, String retentionWindow, boolean legalHoldActive,
      String deletionGateReason, String backupEvidence) {}

  record QualityDashboardView(String tenantContext, String dependencyStatus, QualityValidationRun validationRun,
      QualityReadinessStatus readiness, QualityDriftSummary drift, QualityFairnessSummary fairness,
      List<QualityIncident> incidents, QualityReplaySummary replay, List<QualityContractConformance> contracts,
      QualityEvidenceExport evidenceExport, String uiTraceId, List<String> events, String fallbackReason) {}

  record QualityValidationRun(String runId, String status, String loopStatus, String nextAction,
      List<QualityValidationStage> stages, List<QualityBlocker> openBlockers, List<String> evidencePaths) {}

  record QualityValidationStage(String stageId, String label, String status, String timestampLabel) {}

  record QualityBlocker(String blockerId, String severity, String reasonClass, String owner, String status,
      String summary) {}

  record QualityReadinessStatus(String readinessStatus, boolean deploymentDisabled, List<String> blockList,
      List<String> signoffRefs, List<String> dependencyChecks, String evidenceSetCompleteness) {}

  record QualityDriftSummary(String metricFamily, String window, String windowBaseline, List<String> affectedProducts,
      String cacheStaleness, String lockoutReason, List<QualityDriftMetric> metrics) {}

  record QualityDriftMetric(String metricName, String severity, String deviationLabel) {}

  record QualityFairnessSummary(List<String> protectedClassDimensions, boolean redacted, String sampleCountsLabel,
      String breachSeverity, String escalationTarget, List<String> evidenceRefs) {}

  record QualityIncident(String incidentId, String severity, String escalationTarget, String incidentClass,
      String playbookRef, String lifecycleStage, String evidencePackageId, List<String> impactedServices) {}

  record QualityReplaySummary(String policySnapshotId, String inputBundleRef, String deterministicSeedRef,
      boolean replayAvailable, String blockedReason, List<String> replayModes) {}

  record QualityContractConformance(String contractId, String status, String summary, List<String> failures) {}

  record QualityEvidenceExport(String packageId, String completenessStatus, boolean redacted, List<String> evidenceRefs,
      List<String> blockers) {}

  record AdminGovernanceView(String tenantContext, String adminRole, String dependencyStatus, String uiTraceId,
      AdminTraceMetadata traceMetadata, List<PolicyVersionSummary> policies, List<FeatureFlagSummary> featureFlags,
      List<MarketRuleSummary> marketRules, List<ChangeRequestSummary> changeRequests,
      ReleaseCandidateReadiness releaseCandidate, List<OpenDecisionGate> openDecisions,
      List<DriftAlertSummary> driftAlerts, List<IncidentReviewSummary> incidents,
      List<OverrideLedgerEntry> overrideLedger, List<String> events, String fallbackReason) {}

  record AdminTraceMetadata(String traceId, String artifactId, String policyVersion, String environment,
      String signerMetadata) {}

  record PolicyVersionSummary(String versionId, String owner, String status, String environmentMapping,
      String parentVersionId, String hashSignature, List<String> diffImpacts) {}

  record FeatureFlagSummary(String flagId, String environmentTarget, boolean enabled, List<String> unresolvedFlags,
      boolean activationDisabled, String emergencyToggleGate) {}

  record MarketRuleSummary(String ruleId, String ruleType, String stagingStatus, List<String> missingRequiredFields,
      boolean promotionDisabled, String completenessGate) {}

  record ChangeRequestSummary(String requestId, String requestType, String state, String riskLevel, String owner,
      List<String> requiredStateSequence, boolean promotionDisabled, List<String> blockers) {}

  record ReleaseCandidateReadiness(String candidateId, String readinessStatus, String environmentTarget,
      boolean deployDisabled, boolean rollbackDisabled, String releaseFingerprint, String manifestRef, String signature,
      List<ReleaseGateSummary> gates, List<String> blockers, List<String> affectedSubsystems) {}

  record ReleaseGateSummary(String gateName, String status, boolean mandatory, String artifactRef) {}

  record OpenDecisionGate(String decisionId, String title, String status, String resolutionRef) {}

  record DriftAlertSummary(String alertId, String severity, String environment, String owner, String summary,
      boolean acknowledged) {}

  record IncidentReviewSummary(String incidentId, String status, String rollbackTarget, boolean rcaLinked,
      boolean correctiveActionDone, boolean closeDisabled, String closureGate) {}

  record OverrideLedgerEntry(String ledgerId, String actor, String timestamp, String fieldPath, String oldValue,
      String newValue, String policyRef, String reason, boolean approvalRequired, String auditRef) {}

  record PartnerWebhookHealthView(String partnerId, String tenantContext, String retryHealthSummary,
      String eventWindow, String dlqSizeStatus, String retryWindowStatus, List<PartnerWebhookDeliveryAttempt> deliveryAttempts,
      List<PartnerSafetyToggle> safetyToggles, PartnerWebhookAction replayAction,
      PartnerWebhookAction endpointTestAction, String uiTraceId, List<String> events) {}

  record PartnerWebhookDeliveryAttempt(String webhookId, String eventId, String route, String status,
      String rootCauseCode, String lastSuccessfulAt, String failureReason, String idempotencyKeyState,
      String maskingIndicator, String consentIndicator) {}

  record PartnerSafetyToggle(String webhookId, String route, boolean paused, String visibleState) {}

  record PartnerWebhookAction(boolean available, String disabledReason, String confirmationRequirement,
      String supportHandoffRoute) {}

  record PartnerWebhookReplayRequest(String eventId, String correlationId, boolean idempotencyConfirmed) {}

  record PartnerSafetyToggleRequest(String route, boolean paused, boolean confirmed) {}

  record PartnerWebhookActionResult(String webhookId, String eventId, String status, String message, String guidance,
      boolean downstreamExecuted, String uiTraceId, List<String> events) {}

  record PartnerSafetyToggleResult(String webhookId, String route, boolean paused, String status, String message,
      String uiTraceId, List<String> events) {}
}

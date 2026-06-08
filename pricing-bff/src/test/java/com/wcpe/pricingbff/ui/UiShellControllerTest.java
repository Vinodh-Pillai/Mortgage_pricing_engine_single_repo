package com.wcpe.pricingbff.ui;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UiShellControllerTest {
  @Autowired MockMvc mvc;

  @Test
  void healthReturnsNonSecretBffBoundaryStatus() throws Exception {
    mvc.perform(get("/api/ui/health").header("X-Correlation-Id", "corr-test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("pricing-bff"))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.dependencyStatus").value("NO_UPSTREAMS_CONFIGURED"))
        .andExpect(jsonPath("$.correlationId").value("corr-test"))
        .andExpect(jsonPath("$.dependencies", empty()));
  }

  @Test
  void shellMetadataEndpointsReturnDeterministicFallbacks() throws Exception {
    mvc.perform(get("/api/v1/ui/menus/Borrower"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.persona").value("borrower"))
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.items[0].focusTarget").value("main-content"));

    mvc.perform(get("/api/v1/ui/notices"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notices[0].id").value("shell-baseline"))
        .andExpect(jsonPath("$.notices[0].level").value("info"));

    mvc.perform(get("/api/v1/ui/alerts/current"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.alerts", empty()));
  }

  @Test
  void borrowerQuoteRunBlocksMissingRequiredIntake() throws Exception {
    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"borrowerName\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.fallbackMode").value(true))
        .andExpect(jsonPath("$.validationSummary.passed").value(false))
        .andExpect(jsonPath("$.validationSummary.blockers.borrowerName")
            .value("Borrower name is required before a quote run can start."))
        .andExpect(jsonPath("$.validationSummary.blockers.contactEmail")
            .value("Contact email is required before a quote run can start."))
        .andExpect(jsonPath("$.validationSummary.blockers.quoteGoal")
            .value("Quote goal is required before a quote run can start."));
  }

  @Test
  void borrowerQuoteRunCreatesDeterministicRunIdWithoutCallingUpstreams() throws Exception {
    String intake = """
        {
          "borrowerName": "Alex Borrower",
          "contactEmail": "alex@example.test",
          "quoteGoal": "Compare available offers"
        }
        """;

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs")
            .header("X-Ui-Trace-Id", "trace-brw-s01")
            .contentType(MediaType.APPLICATION_JSON)
            .content(intake))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").isString())
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.nextRoute").value(org.hamcrest.Matchers.startsWith("/quote/run-")))
        .andExpect(jsonPath("$.validationSummary.passed").value(true))
        .andExpect(jsonPath("$.uiTraceId").value("trace-brw-s01"))
        .andExpect(jsonPath("$.events[0]").value("UIFlowOpened"))
        .andExpect(jsonPath("$.events[1]").value("BorrowerIntakeSubmitted"))
        .andExpect(jsonPath("$.dependencyStatus").value("UPSTREAM_CONTRACT_NOT_CONFIGURED"));
  }

  @Test
  void offerComparisonReturnsBoundedFallbackWhenExplainabilityContractMissing() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers")
            .header("X-Ui-Trace-Id", "trace-brw-s02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value("run-test"))
        .andExpect(jsonPath("$.status").value("UPSTREAM_EXPLAINABILITY_REQUIRED"))
        .andExpect(jsonPath("$.offers", empty()))
        .andExpect(jsonPath("$.sortOptions", hasSize(3)))
        .andExpect(jsonPath("$.commitBlocked").value(true))
        .andExpect(jsonPath("$.fallbackReason")
            .value("Offer comparison requires a configured quote-service offers and explainability contract before commit."))
        .andExpect(jsonPath("$.events[0]").value("OfferListRendered"));
  }

  @Test
  void offerExplanationAndSelectionStayBlockedUntilExplanationExists() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers/offer-1/explain")
            .header("X-Ui-Trace-Id", "trace-brw-s03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MISSING"))
        .andExpect(jsonPath("$.commitBlocked").value(true))
        .andExpect(jsonPath("$.message")
            .value("Explanation data is not available from the configured BFF boundary; selection remains blocked."));

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers/offer-1/select")
            .header("X-Ui-Trace-Id", "trace-brw-s02"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.selectedOfferId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.events[0]").value("OfferSelectionBlocked"));
  }

  @Test
  void lockWorkflowBlocksWhenSelectedOfferContextIsMissing() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/lock")
            .header("X-Ui-Trace-Id", "trace-brw-s04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.lockDisabled").value(true))
        .andExpect(jsonPath("$.blockers", hasSize(2)))
        .andExpect(jsonPath("$.events[0]").value("LockBlocked"));
  }

  @Test
  void lockWorkflowConfirmsWithSelectedOfferAndReturnsDetails() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/lock")
            .param("selectedOfferId", "offer-1")
            .header("X-Ui-Trace-Id", "trace-brw-s04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.lockDisabled").value(false))
        .andExpect(jsonPath("$.selectedOfferId").value("offer-1"))
        .andExpect(jsonPath("$.events[0]").value("LockAttempted"));

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/lock/confirm")
            .header("X-Ui-Trace-Id", "trace-brw-s04")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selectedOfferId\":\"offer-1\",\"disclosuresAccepted\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.lockId").isString())
        .andExpect(jsonPath("$.statusRoute").value("/quote/run-test/status"))
        .andExpect(jsonPath("$.events[0]").value("LockSuccess"));
  }

  @Test
  void lockConflictKeepsSelectedOfferContext() throws Exception {
    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/lock/confirm")
            .header("X-Ui-Trace-Id", "trace-brw-s04")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selectedOfferId\":\"conflict-offer\",\"disclosuresAccepted\":true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value("CONFLICT"))
        .andExpect(jsonPath("$.selectedOfferId").value("conflict-offer"))
        .andExpect(jsonPath("$.blockers[0]").value("A competing lock context exists for the selected offer."))
        .andExpect(jsonPath("$.events[0]").value("LockBlocked"));
  }

  @Test
  void partnerQuotesFilterByStatusAndPreserveTenantContext() throws Exception {
    mvc.perform(get("/api/v1/partners/partner-preview/quotes")
            .param("status", "BLOCKED")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ch-s02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.partnerId").value("partner-preview"))
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.statusFilter").value("BLOCKED"))
        .andExpect(jsonPath("$.quotes", hasSize(1)))
        .andExpect(jsonPath("$.quotes[0].quoteId").value("quote-blocked"))
        .andExpect(jsonPath("$.events[0]").value("PartnerQuoteLoaded"));
  }

  @Test
  void partnerQuoteDetailShowsRepriceWhenRoleAndApiPermitArePresent() throws Exception {
    mvc.perform(get("/api/v1/partners/partner-preview/quotes/quote-active")
            .param("apiPermit", "true")
            .header("X-Partner-Role", "role-context-present")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ch-s02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.actions.reprice.visible").value(true))
        .andExpect(jsonPath("$.actions.reprice.permitted").value(true))
        .andExpect(jsonPath("$.lifecycleEvents[0]").value("PartnerQuoteLoaded"));
  }

  @Test
  void partnerRepriceReturnsBlockedGuidanceAndSupportRouteWhenPermitMissing() throws Exception {
    mvc.perform(post("/api/v1/partners/partner-preview/quotes/quote-active/reprice")
            .header("X-Ui-Trace-Id", "trace-ch-s02")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.guidance")
            .value("Reprice requires partner role context and an explicit API permit from the configured partner quote contract."))
        .andExpect(jsonPath("$.supportHandoffRoute").value("/partners/support/reprice"))
        .andExpect(jsonPath("$.events[0]").value("PartnerActionBlocked"));
  }

  @Test
  void opsCaseQueueRendersSlaOwnerAndStableFallbackContext() throws Exception {
    mvc.perform(get("/api/v1/ops/cases")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ops-s06"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.cases", hasSize(2)))
        .andExpect(jsonPath("$.cases[0].caseId").value("ops-lock-blocked"))
        .andExpect(jsonPath("$.cases[0].slaState").value("SLA contract required"))
        .andExpect(jsonPath("$.cases[0].owner").value("Unassigned"))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseQueueOpened"));

    mvc.perform(get("/api/v1/ops/cases/ops-lock-blocked")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ops-s06"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value("ops-lock-blocked"))
        .andExpect(jsonPath("$.timeline", hasSize(2)))
        .andExpect(jsonPath("$.evidencePacketIds[0]").value("evidence-packet-required-after-escalation"))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseOpened"));
  }

  @Test
  void opsCaseAssignmentEscalationAndResolutionAreGatedByExplicitInputs() throws Exception {
    mvc.perform(post("/api/v1/ops/cases/ops-lock-blocked/assign")
            .header("X-Ui-Trace-Id", "trace-ops-s06")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseAssignmentBlocked"));

    mvc.perform(post("/api/v1/ops/cases/ops-lock-blocked/assign")
            .header("X-Ui-Trace-Id", "trace-ops-s06")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"owner\":\"ops-user\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.owner").value("ops-user"))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseAssigned"));

    mvc.perform(post("/api/v1/ops/cases/ops-lock-blocked/notes")
            .header("X-Ui-Trace-Id", "trace-ops-s06")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"note\":\"Borrower callback captured in operations queue\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("NOTE_RECORDED"))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseNoteAdded"));

    mvc.perform(post("/api/v1/ops/cases/ops-lock-blocked/status")
            .header("X-Ui-Trace-Id", "trace-ops-s06")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"ESCALATED\",\"reason\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.immutableSummary")
            .value("Escalation requires a reason before downstream context can be preserved."));

    mvc.perform(post("/api/v1/ops/cases/ops-lock-blocked/status")
            .header("X-Ui-Trace-Id", "trace-ops-s06")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"ESCALATED\",\"reason\":\"Borrower lock blocker still unresolved\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.escalationContextPreserved").value(true))
        .andExpect(jsonPath("$.downstreamExecuted").value(false))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseEscalated"));

    mvc.perform(post("/api/v1/ops/cases/ops-lock-blocked/status")
            .header("X-Ui-Trace-Id", "trace-ops-s06")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"RESOLVED\",\"resolutionCode\":\"OPS_CONFIRMED\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.immutableSummary")
            .value("Case ops-lock-blocked closed with resolution code OPS_CONFIRMED."))
        .andExpect(jsonPath("$.events[0]").value("OpsCaseResolved"));
  }

  @Test
  void partnerWebhookHealthShowsRetryHealthAndSafetyIndicators() throws Exception {
    mvc.perform(get("/api/v1/partners/partner-preview/integrations/webhooks")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ch-s05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.partnerId").value("partner-preview"))
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.retryHealthSummary").value("RETRY_HEALTH_VISIBLE"))
        .andExpect(jsonPath("$.eventWindow").value("latest 30 events"))
        .andExpect(jsonPath("$.dlqSizeStatus").value("DLQ size requires configured integration-service metrics"))
        .andExpect(jsonPath("$.deliveryAttempts", hasSize(3)))
        .andExpect(jsonPath("$.deliveryAttempts[1].rootCauseCode")
            .value("UPSTREAM_PARTNER_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.deliveryAttempts[1].lastSuccessfulAt").value("2026-06-08T07:15:00Z"))
        .andExpect(jsonPath("$.deliveryAttempts[1].idempotencyKeyState").value("CONFIRMED_REQUIRED_FOR_REPLAY"))
        .andExpect(jsonPath("$.safetyToggles[1].paused").value(true))
        .andExpect(jsonPath("$.replayAction.available").value(true))
        .andExpect(jsonPath("$.events[0]").value("WebhookHealthChecked"));
  }

  @Test
  void partnerWebhookReplayRequiresCorrelationAndIdempotencyConfirmation() throws Exception {
    mvc.perform(post("/api/v1/partners/partner-preview/integrations/webhooks/webhook-pricing-updates/replay")
            .header("X-Ui-Trace-Id", "trace-ch-s05")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventId\":\"event-quote-blocked\",\"correlationId\":\"\",\"idempotencyConfirmed\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.message")
            .value("Replay requires request correlation and explicit idempotency confirmation before it can be recorded."))
        .andExpect(jsonPath("$.events[0]").value("WebhookActionBlocked"));

    mvc.perform(post("/api/v1/partners/partner-preview/integrations/webhooks/webhook-pricing-updates/replay")
            .header("X-Ui-Trace-Id", "trace-ch-s05")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventId\":\"event-quote-blocked\",\"correlationId\":\"corr-123\",\"idempotencyConfirmed\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.eventId").value("event-quote-blocked"))
        .andExpect(jsonPath("$.downstreamExecuted").value(false))
        .andExpect(jsonPath("$.events[0]").value("WebhookReplayRequested"));
  }

  @Test
  void partnerWebhookSafetyToggleRequiresConfirmationAndReturnsVisibleState() throws Exception {
    mvc.perform(post("/api/v1/partners/partner-preview/integrations/webhooks/webhook-lock-alerts/safety")
            .header("X-Ui-Trace-Id", "trace-ch-s05")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"route\":\"/partners/alerts\",\"paused\":false,\"confirmed\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.message").value("Safety toggle change requires explicit confirmation."));

    mvc.perform(post("/api/v1/partners/partner-preview/integrations/webhooks/webhook-lock-alerts/safety")
            .header("X-Ui-Trace-Id", "trace-ch-s05")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"route\":\"/partners/alerts\",\"paused\":false,\"confirmed\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("VISIBLE"))
        .andExpect(jsonPath("$.paused").value(false))
        .andExpect(jsonPath("$.events[0]").value("WebhookSafetyToggled"));
  }

  @Test
  void complianceEvidenceRegistryReturnsNonSecretFallbackAcrossSecurityPrivacyAndRetention() throws Exception {
    mvc.perform(get("/api/v1/compliance/evidence")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-sec-s07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE"))
        .andExpect(jsonPath("$.artifacts", hasSize(2)))
        .andExpect(jsonPath("$.artifacts[0].artifactId").value("evidence-ops-lock-blocked"))
        .andExpect(jsonPath("$.artifacts[0].policyVersion").value("policy-version-required"))
        .andExpect(jsonPath("$.artifacts[0].continuityStatus").value("CHAIN_CONTINUITY_UNVERIFIED"))
        .andExpect(jsonPath("$.artifacts[0].progressionBlocked").value(true))
        .andExpect(jsonPath("$.decisions[0].exportBlocked").value(true))
        .andExpect(jsonPath("$.privacyRequests[0].identityStatus").value("unverified"))
        .andExpect(jsonPath("$.securityEvents[0].logRecordId").value("logRecordId-required"))
        .andExpect(jsonPath("$.alerts[0].triggerType").value("missing_evidence"))
        .andExpect(jsonPath("$.retentionControls[0].legalHoldActive").value(true))
        .andExpect(jsonPath("$.retentionControls[0].deletionGateReason")
            .value("OD-005 unresolved blocks destructive retention actions"))
        .andExpect(jsonPath("$.uiTraceId").value("trace-sec-s07"))
        .andExpect(jsonPath("$.events[0]").value("ComplianceEvidenceRegistryOpened"));
  }

  @Test
  void qualityDashboardReturnsValidationReadinessDriftFairnessReplayAndContractFallbacks() throws Exception {
    mvc.perform(get("/api/v1/quality/dashboard")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Quality-Role", "viewer")
            .header("X-Ui-Trace-Id", "trace-ql-s08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE"))
        .andExpect(jsonPath("$.validationRun.loopStatus").value("RED"))
        .andExpect(jsonPath("$.validationRun.stages", hasSize(5)))
        .andExpect(jsonPath("$.validationRun.openBlockers[0].reasonClass").value("workflow"))
        .andExpect(jsonPath("$.readiness.readinessStatus").value("fail"))
        .andExpect(jsonPath("$.readiness.deploymentDisabled").value(true))
        .andExpect(jsonPath("$.drift.cacheStaleness").value("stale"))
        .andExpect(jsonPath("$.drift.lockoutReason")
            .value("Comparison controls are locked until baseline and sample-window evidence are supplied."))
        .andExpect(jsonPath("$.fairness.redacted").value(true))
        .andExpect(jsonPath("$.fairness.protectedClassDimensions[0]").value("masked-class-label"))
        .andExpect(jsonPath("$.incidents", hasSize(2)))
        .andExpect(jsonPath("$.replay.replayAvailable").value(false))
        .andExpect(jsonPath("$.contracts[0].status").value("FAIL"))
        .andExpect(jsonPath("$.evidenceExport.redacted").value(true))
        .andExpect(jsonPath("$.uiTraceId").value("trace-ql-s08"))
        .andExpect(jsonPath("$.events[0]").value("QualityDashboardOpened"));
  }

  @Test
  void qualityEvidenceExportRemainsRedactedAndIncompleteWithoutConfiguredEvidenceStore() throws Exception {
    mvc.perform(get("/api/v1/quality/evidence/export"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packageId").value("quality-evidence-package-required"))
        .andExpect(jsonPath("$.completenessStatus").value("INCOMPLETE"))
        .andExpect(jsonPath("$.redacted").value(true))
        .andExpect(jsonPath("$.evidenceRefs", hasSize(4)))
        .andExpect(jsonPath("$.blockers[0]")
            .value("Export is redacted and incomplete until configured quality evidence storage is available."));
  }

  @Test
  void adminGovernanceReturnsBlockedReleaseGateDataAndOpenDecisions() throws Exception {
    mvc.perform(get("/api/v1/admin/governance")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Admin-Role", "release-governance-preview")
            .header("X-Ui-Trace-Id", "trace-ag-s09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.adminRole").value("release-governance-preview"))
        .andExpect(jsonPath("$.dependencyStatus").value("FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE"))
        .andExpect(jsonPath("$.traceMetadata.traceId").value("trace-ag-s09"))
        .andExpect(jsonPath("$.policies", hasSize(2)))
        .andExpect(jsonPath("$.featureFlags[0].activationDisabled").value(true))
        .andExpect(jsonPath("$.featureFlags[0].unresolvedFlags[1]").value("OD-004_UNRESOLVED"))
        .andExpect(jsonPath("$.marketRules[0].missingRequiredFields", hasSize(4)))
        .andExpect(jsonPath("$.changeRequests[0].promotionDisabled").value(true))
        .andExpect(jsonPath("$.changeRequests[0].blockers", hasSize(4)))
        .andExpect(jsonPath("$.releaseCandidate.readinessStatus").value("RED"))
        .andExpect(jsonPath("$.releaseCandidate.deployDisabled").value(true))
        .andExpect(jsonPath("$.releaseCandidate.rollbackDisabled").value(true))
        .andExpect(jsonPath("$.releaseCandidate.gates", hasSize(6)))
        .andExpect(jsonPath("$.releaseCandidate.blockers[0]").value(org.hamcrest.Matchers.startsWith("OD-001")))
        .andExpect(jsonPath("$.openDecisions[0].decisionId").value("OD-001"))
        .andExpect(jsonPath("$.openDecisions[1].decisionId").value("OD-002"))
        .andExpect(jsonPath("$.openDecisions[2].decisionId").value("OD-004"))
        .andExpect(jsonPath("$.openDecisions[3].decisionId").value("OD-005"))
        .andExpect(jsonPath("$.driftAlerts[0].summary")
            .value("Configured baseline and alert threshold are required; no numeric threshold is inferred."))
        .andExpect(jsonPath("$.incidents[0].closeDisabled").value(true))
        .andExpect(jsonPath("$.overrideLedger[0].approvalRequired").value(true))
        .andExpect(jsonPath("$.events[0]").value("AdminGovernanceOpened"));
  }
}

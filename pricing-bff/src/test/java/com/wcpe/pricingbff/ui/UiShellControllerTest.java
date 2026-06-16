package com.wcpe.pricingbff.ui;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UiShellControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;

  @Test
  void uiSurfacesAreRegisteredAsSeparateControllersOverOneFallbackAdapter() {
    assertThat(context.getBean(AuthUiController.class)).isNotNull();
    assertThat(context.getBean(ShellUiController.class)).isNotNull();
    assertThat(context.getBean(ProductCatalogUiController.class)).isNotNull();
    assertThat(context.getBean(QuoteRunUiController.class)).isNotNull();
    assertThat(context.getBean(OfferUiController.class)).isNotNull();
    assertThat(context.getBean(PartnerUiController.class)).isNotNull();
    assertThat(context.getBean(OpsUiController.class)).isNotNull();
    assertThat(context.getBean(ComplianceUiController.class)).isNotNull();
    assertThat(context.getBean(QualityUiController.class)).isNotNull();
    assertThat(context.getBean(CustomRuleEvidenceUiController.class)).isNotNull();
    assertThat(context.getBean(TenantPlatformUiController.class)).isNotNull();
    assertThat(context.getBean(AuditReplayUiController.class)).isNotNull();
    assertThat(context.getBean(ScenarioAnalysisUiController.class)).isNotNull();
    assertThat(context.getBean(AdminUiController.class)).isNotNull();
    assertThat(context.getBean(MlAdvisoryUiController.class)).isNotNull();
    assertThat(context.getBean(PricingBffUiFallbackAdapter.class)).isNotNull();
  }

  @Test
  void authLoginFailsClosedWhenTenantContextContractIsMissing() throws Exception {
    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"user@example.test\",\"password\":\"test-password-value\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("Tenant-context authentication contract is not configured for the BFF"));
  }

  @Test
  void mlAdvisoryInsightsExposesModelVersionGovernanceWithoutAutomaticDecisions() throws Exception {
    mvc.perform(get("/api/v1/ml-advisory/insights")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ml-s14"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("ML_ADVISORY_SERVICE_EVIDENCE_INCOMPLETE"))
        .andExpect(jsonPath("$.recommendations[0].modelVersion").value("model-version-ref-required"))
        .andExpect(jsonPath("$.recommendations[0].confidence").value("confidence-score-from-model-output"))
        .andExpect(jsonPath("$.recommendations[0].allowedActions[0]").value("VIEW_EXPLANATION"))
        .andExpect(jsonPath("$.recommendations[0].auditRefs[0]").value("audit-ref-required"))
        .andExpect(jsonPath("$.recommendations[0].automaticDecisionApplied").value(false))
        .andExpect(jsonPath("$.modelVersions[0].driftStatus").value("DRIFT_BASELINE_REQUIRED"))
        .andExpect(jsonPath("$.modelVersions[0].alertState").value("ALERT_REVIEW_REQUIRED"))
        .andExpect(jsonPath("$.modelVersions[0].feedbackLoops[0]").value("feedback-loop-ref-required"))
        .andExpect(jsonPath("$.modelVersions[0].exportEvidenceRefs[0]").value("evidence-export-ref-required"))
        .andExpect(jsonPath("$.advisoryUnavailable").value(true))
        .andExpect(jsonPath("$.events[0]").value("MlAdvisoryInsightsOpened"));
  }

  @Test
  void healthReturnsNonSecretBffBoundaryStatus() throws Exception {
    mvc.perform(get("/api/ui/health").header("X-Correlation-Id", "corr-test"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.service").value("pricing-workbench"))
        .andExpect(jsonPath("$.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.ready").value(true))
        .andExpect(jsonPath("$.dependencyStatus").value("Connected services need setup"))
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
  void tenantWorkspacePlaceholderRecordsLocalSetupWithoutCredentialsOrUpstreams() throws Exception {
    mvc.perform(post("/api/v1/tenants/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantName\":\"Retail workspace\",\"operationsContact\":\"ops@example.test\",\"launchGoal\":\"Prepare guided pricing workflow\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tenantId").value(org.hamcrest.Matchers.startsWith("tenant-")))
        .andExpect(jsonPath("$.status").value("RECORDED"))
        .andExpect(jsonPath("$.message").value("Tenant workspace setup was recorded in local preview mode."))
        .andExpect(jsonPath("$.placeholders", hasSize(2)));

    mvc.perform(post("/api/v1/tenants/workspaces")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.placeholders", hasSize(3)));
  }

  @Test
  void productCatalogPlaceholderRecordsDraftWithoutPricingRules() throws Exception {
    mvc.perform(post("/api/v1/products/catalog")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productName\":\"Standard purchase draft\",\"productOwner\":\"Product manager\",\"borrowerNeed\":\"Compare purchase options\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.productId").value(org.hamcrest.Matchers.startsWith("product-")))
        .andExpect(jsonPath("$.status").value("RECORDED"))
        .andExpect(jsonPath("$.message").value("Product catalog draft was recorded in local preview mode."))
        .andExpect(jsonPath("$.placeholders[0]")
            .value("Product terms, eligibility, rates, thresholds, and regulatory values are not inferred."));

    mvc.perform(post("/api/v1/products/catalog")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.placeholders", hasSize(3)));
  }

  @Test
  void productCatalogManagerExposesBackendOwnedSectionsLifecycleAndBlockedEvidence() throws Exception {
    mvc.perform(get("/api/v1/products/catalog/manager")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-catalog-manager"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("CATALOG_CONTRACTS_UNAVAILABLE"))
        .andExpect(jsonPath("$.areas", hasSize(4)))
        .andExpect(jsonPath("$.areas[0].areaId").value("draft-products"))
        .andExpect(jsonPath("$.areas[1].label").value("Investors, taxonomy, and channels"))
        .andExpect(jsonPath("$.areas[2].fields", hasSize(4)))
        .andExpect(jsonPath("$.lifecycle.actionsDisabled").value(true))
        .andExpect(jsonPath("$.lifecycle.actions", hasSize(3)))
        .andExpect(jsonPath("$.lifecycle.snapshotRefs[0]").value("snapshot-catalog-contract-required"))
        .andExpect(jsonPath("$.lifecycle.auditRefs[1]").value("replay-hash-required"))
        .andExpect(jsonPath("$.events[0]").value("CatalogManagerOpened"))
        .andExpect(jsonPath("$.uiTraceId").value("trace-catalog-manager"));
  }

  @Test
  void quoteDetailExposesPanelsRedactionsWaterfallAndReplayEvidence() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers/quote-option-contract-required/detail")
            .header("X-Ui-Trace-Id", "trace-quote-detail"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("demo-tenant"))
        .andExpect(jsonPath("$.offerId").value("quote-option-contract-required"))
        .andExpect(jsonPath("$.status").value("DETAIL_VISIBLE_WITH_BACKEND_REFS"))
        .andExpect(jsonPath("$.summary.productLabel").value("Backend-ranked offer"))
        .andExpect(jsonPath("$.panels", hasSize(5)))
        .andExpect(jsonPath("$.panels[2].label").value("Pricing waterfall"))
        .andExpect(jsonPath("$.redactions", hasSize(3)))
        .andExpect(jsonPath("$.redactions[0].reason").value("pricing.waterfall.restricted.read permission is required for selected note rate"))
        .andExpect(jsonPath("$.complianceFlags[0]").value("compliance-review-ref-required"))
        .andExpect(jsonPath("$.waterfall.finalPrice.ledger", hasSize(3)))
        .andExpect(jsonPath("$.waterfall.finalPrice.ledger[1].operation").value("BACKEND_OWNED"))
        .andExpect(jsonPath("$.auditRefs[0]").value("audit:quote-detail-opened"))
        .andExpect(jsonPath("$.replayHash").value("quote-detail-replay-hash-required"))
        .andExpect(jsonPath("$.uiTraceId").value("trace-quote-detail"));
  }

  @Test
  void auditReplayWorkbenchExposesAuditRecordsReplayDiffsAndExportDecisions() throws Exception {
    mvc.perform(get("/api/v1/audit-replay/workbench")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-audit-replay"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("AUDIT_REPLAY_SERVICE_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.records", hasSize(2)))
        .andExpect(jsonPath("$.records[0].eventId").value("event-id-required"))
        .andExpect(jsonPath("$.records[0].hashIntegrity").value("INTEGRITY_PENDING"))
        .andExpect(jsonPath("$.records[0].redactionProfile").value("redaction-profile-required"))
        .andExpect(jsonPath("$.records[1].legalHold").value(true))
        .andExpect(jsonPath("$.records[1].exportEligibility").value("EXPORT_LOCKED_BY_LEGAL_HOLD"))
        .andExpect(jsonPath("$.replayRuns[0].replayHash").value("replay-hash-required"))
        .andExpect(jsonPath("$.replayRuns[0].missingDependencyBlockers[1]").value("quote-service replay dependency is unavailable"))
        .andExpect(jsonPath("$.exportSummary.legalHold").value(true))
        .andExpect(jsonPath("$.exportSummary.downloadEligible").value(false))
        .andExpect(jsonPath("$.exportSummary.manifestHash").value("manifest-hash-required"))
        .andExpect(jsonPath("$.contractRefs", hasSize(3)))
        .andExpect(jsonPath("$.events[0]").value("AuditReplayWorkbenchOpened"))
        .andExpect(jsonPath("$.uiTraceId").value("trace-audit-replay"));
  }

  @Test
  void scenarioAnalysisWorkspaceExposesDimensionsGuardrailsBatchSavedAndReplayRefs() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/what-if/workspace")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-scenario-analysis"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.runId").value("run-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("SCENARIO_ANALYSIS_SERVICE_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.dimensions", hasSize(4)))
        .andExpect(jsonPath("$.dimensions[0].dimensionId").value("fico"))
        .andExpect(jsonPath("$.dimensions[0].backendOnly").value(true))
        .andExpect(jsonPath("$.variants[1].status").value("BLOCKED"))
        .andExpect(jsonPath("$.variants[1].guardrailBlockers[0].blockerCode").value("REQUIRED_FACTS_MISSING"))
        .andExpect(jsonPath("$.batchGrid", hasSize(2)))
        .andExpect(jsonPath("$.savedAnalyses[0].exportRef").value("export-ref-required"))
        .andExpect(jsonPath("$.replayRefs[0]").value("replay-hash-required"))
        .andExpect(jsonPath("$.events[0]").value("ScenarioAnalysisWorkspaceOpened"));
  }

  @Test
  void scenarioAnalysisRecalculationPassesVariantFactsWithoutPricingLocally() throws Exception {
    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/what-if/recalculate")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-scenario-analysis")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"changedDimensionId\":\"fico\",\"requestedValue\":\"borrower-fico-ref-updated\",\"variantFacts\":[\"fact:fico-score-ref\",\"fact:ltv-ref\"]}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("BACKEND_RESULT_VISIBLE"))
        .andExpect(jsonPath("$.backendResultRefs[0]").value("scenario-analysis-service.result:fico"))
        .andExpect(jsonPath("$.blockers[0].requiredFacts[0]").value("fact:fico-score-ref"))
        .andExpect(jsonPath("$.events[1]").value("VariantFactsForwarded"));

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/what-if/recalculate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.blockers[0].blockerCode").value("VARIANT_FACTS_REQUIRED"));
  }

  @Test
  void borrowerQuoteRunBlocksMissingRequiredIntake() throws Exception {
    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"quoteIntent\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.fallbackMode").value(true))
        .andExpect(jsonPath("$.validationSummary.passed").value(false))
        .andExpect(jsonPath("$.validationSummary.blockers.quoteIntent")
            .value("Quote intent is required before a quote run can start."))
        .andExpect(jsonPath("$.validationSummary.blockers.channel")
            .value("Channel is required before a quote run can start."));
  }

  @Test
  void borrowerQuoteRunCreatesDeterministicRunIdWithoutCallingUpstreams() throws Exception {
    String intake = """
        {
          "quoteIntent": "purchase",
          "channel": "retail",
          "scenarioName": "Alex purchase scenario"
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
        .andExpect(jsonPath("$.events[1]").value("ProgressiveQuickQuoteReviewed"))
        .andExpect(jsonPath("$.events[2]").value("ScenarioMetadataReviewed"))
        .andExpect(jsonPath("$.events[3]").value("BorrowerIntakeSubmitted"))
        .andExpect(jsonPath("$.dependencyStatus").value("SCENARIO_QUOTE_CATALOG_CONTRACTS_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.auditPackageId").value("audit-package-required-after-scenario-service-create"))
        .andExpect(jsonPath("$.replayHashRef").value("replay-hash-required-after-scenario-service-create"))
        .andExpect(jsonPath("$.validationIssues[0].code").value("SCENARIO_SERVICE_CONTRACT_REQUIRED"))
        .andExpect(jsonPath("$.missingContractBlockers[0]").value(org.hamcrest.Matchers.containsString("scenario id")))
        .andExpect(jsonPath("$.quickQuoteState.minimalFirstStepFields", hasSize(2)))
        .andExpect(jsonPath("$.quickQuoteState.quoteServiceRequiredFacts[0]").value("scenarioId"));
  }

  @Test
  void quickQuoteLaunchCarriesCompletedBackendFactsAndMissingContractBlockers() throws Exception {
    String intake = """
        {
          "quoteIntent": "purchase",
          "channel": "retail",
          "scenarioName": "Alex purchase scenario",
          "scenarioId": "scenario-ref-123",
          "scenarioVersion": "version-ref-7",
          "loanPurpose": "purchase",
          "loanAmount": "loan-amount-ref",
          "requestedLockPeriods": "lock-period-ref-from-catalog",
          "effectiveDate": "effective-date-ref",
          "actorId": "actor-ref",
          "clientContext": "client-context-ref"
        }
        """;

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(intake))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.backendFactRefs[0]").value("fact:scenarioId"))
        .andExpect(jsonPath("$.backendFactRefs[1]").value("fact:scenarioVersion"))
        .andExpect(jsonPath("$.backendFactRefs[2]").value("fact:quoteIntent"))
        .andExpect(jsonPath("$.missingContractBlockers", empty()))
        .andExpect(jsonPath("$.quickQuoteState.progressiveSectionOrder", hasSize(5)));
  }

  @Test
  void scenarioIntakeMetadataReturnsFieldGroupsBlockersAuditAndReplayWithoutPricingRules() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/intake-metadata")
            .header("X-Ui-Trace-Id", "trace-brw-s01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("demo-tenant"))
        .andExpect(jsonPath("$.dependencyStatus").value("PARTIAL"))
        .andExpect(jsonPath("$.fieldGroups", hasSize(6)))
        .andExpect(jsonPath("$.fieldGroups[0].fields[0].fieldId").value("quoteIntent"))
        .andExpect(jsonPath("$.fieldGroups[0].fields[0].required").value(true))
        .andExpect(jsonPath("$.fieldGroups[0].fields[1].fieldId").value("channel"))
        .andExpect(jsonPath("$.fieldGroups[1].fields[2].fieldId").value("creditScore"))
        .andExpect(jsonPath("$.fieldGroups[2].fields[1].fieldId").value("loanAmount"))
        .andExpect(jsonPath("$.fieldGroups[3].fields[2].fieldId").value("propertyType"))
        .andExpect(jsonPath("$.fieldGroups[4].fields[2].fieldId").value("monthlyDebt"))
        .andExpect(jsonPath("$.fieldGroups[5].groupId").value("preferences"))
        .andExpect(jsonPath("$.fieldGroups[5].fields[4].fieldId").value("requestedLockPeriods"))
        .andExpect(jsonPath("$.decisionControls[2]").value("Keep pricing calculations outside the workbench intake surface."))
        .andExpect(jsonPath("$.decisionControls[3]").value("Keep the first step minimal and reveal backend-mapped sections progressively."))
        .andExpect(jsonPath("$.validationIssues[0].severity").value("BLOCKING"))
        .andExpect(jsonPath("$.validationIssues[1].code").value("QUOTE_SERVICE_CONTRACT_REQUIRED"))
        .andExpect(jsonPath("$.quickQuoteState.minimalFirstStepFields[0]").value("quoteIntent"))
        .andExpect(jsonPath("$.quickQuoteState.minimalFirstStepFields[1]").value("channel"))
        .andExpect(jsonPath("$.quickQuoteState.progressiveSectionOrder", hasSize(5)))
        .andExpect(jsonPath("$.quickQuoteState.quoteServiceRequiredFacts[2]").value("quoteIntent"))
        .andExpect(jsonPath("$.quickQuoteState.quoteServiceRequiredFacts[5]").value("loanAmount"))
        .andExpect(jsonPath("$.quickQuoteState.backendOwnedFactSources[0]").value("creditScore"))
        .andExpect(jsonPath("$.quickQuoteState.blockedByContracts", hasSize(5)))
        .andExpect(jsonPath("$.auditPackageId").value("review-package-required-after-scenario-create"))
        .andExpect(jsonPath("$.replayHashRef").value("review-reference-required-after-scenario-create"))
        .andExpect(jsonPath("$.uiTraceId").value("trace-brw-s01"));
  }

  @Test
  void offerComparisonReturnsBackendOwnedRankingEvidenceAndRefs() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers")
            .header("X-Ui-Trace-Id", "trace-brw-s02"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value("run-test"))
        .andExpect(jsonPath("$.status").value("QUOTE_SERVICE_EVIDENCE_VISIBLE"))
        .andExpect(jsonPath("$.offers", hasSize(2)))
        .andExpect(jsonPath("$.offers[0].rank").value(1))
        .andExpect(jsonPath("$.offers[0].rankScore").value("rank-score-ref-required"))
        .andExpect(jsonPath("$.offers[0].upstreamRefs[0]").value("eligibility-service:decision-ref-required"))
        .andExpect(jsonPath("$.offers[0].lockEligibilityRefs[0]").value("lock-eligibility:pending:quote-option-contract-required"))
        .andExpect(jsonPath("$.offers[0].snapshotRefs[0]").value("snapshot:quote-service:run:run-test"))
        .andExpect(jsonPath("$.offers[0].auditIds[0]").value("audit:quote-ready-required"))
        .andExpect(jsonPath("$.offers[0].explanationSections", hasSize(3)))
        .andExpect(jsonPath("$.sortOptions", hasSize(3)))
        .andExpect(jsonPath("$.commitBlocked").value(false))
        .andExpect(jsonPath("$.requiredFacts[0]").value("requestedLockPeriods"))
        .andExpect(jsonPath("$.fallbackReason")
            .value("Quote-service offer evidence is represented with backend-owned refs; UI actions stay blocked only when required facts are missing."))
        .andExpect(jsonPath("$.events[0]").value("OfferListRendered"));
  }

  @Test
  void offerExplanationAndSelectionCarryScenarioSnapshotLockAndAuditRefs() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers/offer-1/explain")
            .header("X-Ui-Trace-Id", "trace-brw-s03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AVAILABLE"))
        .andExpect(jsonPath("$.commitBlocked").value(false))
        .andExpect(jsonPath("$.upstreamRefs[0]").value("quote-service.option:offer-1"))
        .andExpect(jsonPath("$.snapshotRefs[0]").value("snapshot:quote-service:run:run-test"))
        .andExpect(jsonPath("$.auditIds[0]").value("audit:quote-explanation-required"));

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/offers/offer-1/select")
            .header("X-Ui-Trace-Id", "trace-brw-s02"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SELECTED"))
        .andExpect(jsonPath("$.selectedOfferId").value("offer-1"))
        .andExpect(jsonPath("$.scenarioVersion").value(7))
        .andExpect(jsonPath("$.lockEligibilityRef").value("lock-eligibility:pending:offer-1"))
        .andExpect(jsonPath("$.snapshotRef").value("snapshot:quote-service:run:run-test"))
        .andExpect(jsonPath("$.auditIds[0]").value("audit:quote-selection-required"))
        .andExpect(jsonPath("$.events[0]").value("OfferSelectionRecorded"));
  }

  @Test
  void eligibilityModuleReturnsReasonFactsOverlaysCacheAndFailClosedBlockers() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/eligibility")
            .param("quoteOptionId", "option-1")
            .header("X-Ui-Trace-Id", "trace-brw-s04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.runId").value("run-test"))
        .andExpect(jsonPath("$.quoteOptionId").value("option-1"))
        .andExpect(jsonPath("$.decisions", hasSize(3)))
        .andExpect(jsonPath("$.decisions[0].decision").value("ELIGIBLE"))
        .andExpect(jsonPath("$.decisions[0].reasonCodes[0]").value("ELIGIBILITY_CONTRACT_VISIBLE"))
        .andExpect(jsonPath("$.decisions[0].inputFactRefs[0]").value("fact:scenario-version"))
        .andExpect(jsonPath("$.decisions[0].overlayRefs[0]").value("overlay:configured-product"))
        .andExpect(jsonPath("$.decisions[0].cacheFreshness.status").value("FRESHNESS_REQUIRED"))
        .andExpect(jsonPath("$.blockers[0].reasonCode").value("UNKNOWN_REQUIRED_FACT"))
        .andExpect(jsonPath("$.requiredNextFacts[0]").value("fact:income-assets"))
        .andExpect(jsonPath("$.fallbackReason")
            .value("Configured eligibility-service decision, overlay, cache, and explanation contracts are unavailable; this fallback carries references and blockers only."));
  }

  @Test
  void pricingWaterfallReturnsRedactedBackendOwnedEvidenceAndBlockers() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/pricing-waterfall")
            .header("X-Ui-Trace-Id", "trace-pw-s05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("demo-tenant"))
        .andExpect(jsonPath("$.runId").value("run-test"))
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.restrictedValuesVisible").value(false))
        .andExpect(jsonPath("$.dependencyStatus").value("PRICING_SERVICE_WATERFALL_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.baseSelection.gridVersionRef").value("grid-version-ref-required"))
        .andExpect(jsonPath("$.baseSelection.selectedNoteRate.redacted").value(true))
        .andExpect(jsonPath("$.finalPrice.ledger", hasSize(3)))
        .andExpect(jsonPath("$.finalPrice.ledger[2].step").value("ROUND_FINAL_PRICE"))
        .andExpect(jsonPath("$.blockers[0].code").value("PRICING_SERVICE_CONTRACT_REQUIRED"))
        .andExpect(jsonPath("$.auditRefs[1]").value("audit:final-price-required"))
        .andExpect(jsonPath("$.replayHash").value("replay-hash-required"))
        .andExpect(jsonPath("$.evidenceHash").value("waterfall-evidence-hash-required"))
        .andExpect(jsonPath("$.events[0]").value("PricingWaterfallOpened"));
  }

  @Test
  void lockWorkflowBlocksWhenSelectedOfferContextIsMissing() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/lock")
            .header("X-Ui-Trace-Id", "trace-brw-s04"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.lockDisabled").value(true))
        .andExpect(jsonPath("$.blockers", hasSize(2)))
        .andExpect(jsonPath("$.blockerDetails[0].code").value("SELECTED_OFFER_REQUIRED"))
        .andExpect(jsonPath("$.requiredEvidence[0]").value("selected-offer-ref"))
        .andExpect(jsonPath("$.stateTransitions[0].eventId").value("lock.lifecycle.blocked.selection-required"))
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
        .andExpect(jsonPath("$.selectedQuoteRefs[1]").value("selected-offer:offer-1"))
        .andExpect(jsonPath("$.freshnessChecks[0].sourceRef").value("lock-service:freshness-check"))
        .andExpect(jsonPath("$.stateTransitions[0].eventId").value("lock.lifecycle.ready.offer-1"))
        .andExpect(jsonPath("$.auditGroups[0].eventId").value("lock.confirmation.offer-1"))
        .andExpect(jsonPath("$.events[0]").value("LockAttempted"));

    mvc.perform(post("/api/v1/tenants/demo-tenant/quote-runs/run-test/lock/confirm")
            .header("X-Ui-Trace-Id", "trace-brw-s04")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"selectedOfferId\":\"offer-1\",\"disclosuresAccepted\":true}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.lockId").isString())
        .andExpect(jsonPath("$.statusRoute").value("/quote/run-test/status"))
        .andExpect(jsonPath("$.auditGroups[0].eventId").value("lock.confirmation.offer-1"))
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
  void rateFeedOperationsExposeWorkflowBlockersSourceReferencesAndReplayEvidence() throws Exception {
    mvc.perform(get("/api/v1/ops/rate-feeds")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-rf-s03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("RATE_FEED_SERVICE_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.workflowSteps", hasSize(5)))
        .andExpect(jsonPath("$.workflowSteps[0].stepId").value("upload"))
        .andExpect(jsonPath("$.workflowSteps[1].label").value("Parse and normalize"))
        .andExpect(jsonPath("$.workflowSteps[2].sourceBoundary").value("rate-feed-service validation-report endpoint"))
        .andExpect(jsonPath("$.workflowSteps[3].label").value("Activate or reject"))
        .andExpect(jsonPath("$.workflowSteps[4].resultHashRef").value("cache-invalidation-command-required"))
        .andExpect(jsonPath("$.rowBlockers[0].sourceReference").value("source:rate-feed-batch/row/12"))
        .andExpect(jsonPath("$.sourceReferences[1]").value("activation-audit-ref-required"))
        .andExpect(jsonPath("$.replayEvidence[0]").value("cache-invalidation-command-required"))
        .andExpect(jsonPath("$.actionsDisabled").value(true))
        .andExpect(jsonPath("$.events[0]").value("RateFeedOperationsOpened"));
  }

  @Test
  void performanceDashboardGroupsSignalsByServiceTenantCorrelationFreshnessAndBlockedEvidence() throws Exception {
    mvc.perform(get("/api/v1/ops/performance")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-perf-s09"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("OBSERVABILITY_SERVICE_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.signalGroups", hasSize(3)))
        .andExpect(jsonPath("$.signalGroups[0].serviceName").value("pricing-bff"))
        .andExpect(jsonPath("$.signalGroups[0].tenantContext").value("ui-preview-tenant"))
        .andExpect(jsonPath("$.signalGroups[0].correlationId").value("corr-performance-bff"))
        .andExpect(jsonPath("$.signalGroups[1].freshness").value("STALE"))
        .andExpect(jsonPath("$.signalGroups[1].signals[1].evidenceRefs[0]")
            .value(".local-harness/evidence/PII-22-S09/load-test-report-required.json"))
        .andExpect(jsonPath("$.impacts[0].source").value("observability-service"))
        .andExpect(jsonPath("$.impacts[0].recoveryOwner").value("SRE / Operations Lead"))
        .andExpect(jsonPath("$.blockers[0].owner").value("observability-service"))
        .andExpect(jsonPath("$.actionsDisabled").value(true))
        .andExpect(jsonPath("$.uiTraceId").value("trace-perf-s09"))
        .andExpect(jsonPath("$.events[0]").value("PerformanceDashboardOpened"));
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
        .andExpect(jsonPath("$.dlqSizeStatus").value("Exception queue size requires configured integration-service metrics"))
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
  void partnerChannelWorkbenchShowsSeparateModulesFallbackDetailsAndServiceAccountBlockedState() throws Exception {
    mvc.perform(get("/api/v1/partners/partner-preview/integrations/workbench")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-ch-s12"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.partnerId").value("partner-preview"))
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("INTEGRATION_SERVICE_CHANNEL_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.tabs", hasSize(7)))
        .andExpect(jsonPath("$.tabs[0].tabId").value("quote-requests"))
        .andExpect(jsonPath("$.tabs[1].tabId").value("webhook-delivery"))
        .andExpect(jsonPath("$.tabs[2].tabId").value("retries"))
        .andExpect(jsonPath("$.tabs[3].tabId").value("dlq"))
        .andExpect(jsonPath("$.tabs[3].label").value("Exception queue"))
        .andExpect(jsonPath("$.tabs[4].tabId").value("feed-adapters"))
        .andExpect(jsonPath("$.tabs[4].label").value("Investor delivery connections"))
        .andExpect(jsonPath("$.tabs[5].tabId").value("sftp-adapters"))
        .andExpect(jsonPath("$.tabs[5].label").value("Partner file delivery"))
        .andExpect(jsonPath("$.tabs[6].tabId").value("health"))
        .andExpect(jsonPath("$.tabs[3].items[0].dlqReason").value("EXCEPTION_QUEUE_METRICS_REQUIRED"))
        .andExpect(jsonPath("$.tabs[3].items[0].payloadRedactionState").value("payload-redacted"))
        .andExpect(jsonPath("$.tabs[3].items[0].auditRefs[0]").value("audit:partner-dlq-required"))
        .andExpect(jsonPath("$.serviceAccount.blocked").value(true))
        .andExpect(jsonPath("$.serviceAccount.missingCapability")
            .value("integration-service.partner-channel.workbench.read"))
        .andExpect(jsonPath("$.serviceAccount.recoveryOwner").value("integration-platform-owner"))
        .andExpect(jsonPath("$.serviceAccount.credentialExposure").value("credentials-not-rendered"))
        .andExpect(jsonPath("$.events[0]").value("PartnerIntegrationWorkbenchOpened"));
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
        .andExpect(jsonPath("$.dependencyStatus").value("Configuration details need setup"))
        .andExpect(jsonPath("$.artifacts", hasSize(2)))
        .andExpect(jsonPath("$.artifacts[0].artifactId").value("evidence-ops-lock-blocked"))
        .andExpect(jsonPath("$.artifacts[0].policyVersion").value("policy-version-required"))
        .andExpect(jsonPath("$.artifacts[0].continuityStatus").value("CHAIN_CONTINUITY_UNVERIFIED"))
        .andExpect(jsonPath("$.artifacts[0].progressionBlocked").value(true))
        .andExpect(jsonPath("$.decisions[0].exportBlocked").value(true))
        .andExpect(jsonPath("$.advisoryReviews[0].reviewType").value("quote"))
        .andExpect(jsonPath("$.advisoryReviews[0].reasonCodes[0]").value("HIGH_COST_THRESHOLD_UNAVAILABLE"))
        .andExpect(jsonPath("$.advisoryReviews[0].auditSnapshotRefs[0]").value("audit-snapshot-ref-required"))
        .andExpect(jsonPath("$.advisoryReviews[0].regulatoryApprovalState").value("REGULATORY_APPROVAL_PENDING_CONFIG"))
        .andExpect(jsonPath("$.advisoryReviews[0].exportRefs[0]").value("evidence-export-ref-required"))
        .andExpect(jsonPath("$.advisoryReviews[0].blockedByConfiguration").value(true))
        .andExpect(jsonPath("$.fairLendingMonitoring[0].dimensions[0]").value("masked-class-label"))
        .andExpect(jsonPath("$.fairLendingMonitoring[0].redactionState").value("redaction-profile-required"))
        .andExpect(jsonPath("$.fairLendingMonitoring[0].redacted").value(true))
        .andExpect(jsonPath("$.configurationGaps[0]")
            .value("Configured regulatory threshold values are unavailable; the UI records this blocked gap instead of embedding plausible constants."))
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
        .andExpect(jsonPath("$.dependencyStatus").value("Configuration details need setup"))
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
  void tenantPlatformCoverageReturnsTraceControlsReadinessAndBlockedLiveIntegrationWithoutSecrets() throws Exception {
    mvc.perform(get("/api/v1/platform/tenant-context")
            .header("X-Tenant-Context", "tenant-test")
            .header("X-Ui-Trace-Id", "trace-tc-s08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("tenant-test"))
        .andExpect(jsonPath("$.dependencyStatus").value("TENANT_CONTEXT_SERVICE_CONTRACT_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.trace.correlationIdRef").value("correlation-id-required"))
        .andExpect(jsonPath("$.trace.idempotencyKeyRef").value("idempotency-key-required"))
        .andExpect(jsonPath("$.trace.eventEnvelopeRef").value("event-envelope-ref-required"))
        .andExpect(jsonPath("$.trace.auditRef").value("audit:tenant-context-platform-required"))
        .andExpect(jsonPath("$.controls", hasSize(5)))
        .andExpect(jsonPath("$.controls[0].controlId").value("tenant-resolution"))
        .andExpect(jsonPath("$.controls[2].status").value("BLOCKED"))
        .andExpect(jsonPath("$.controls[3].evidenceRefs[1]").value("outbox-event-ref"))
        .andExpect(jsonPath("$.blockers[0].code").value("CONFIGURED_TENANT_CONTEXT_CONTRACT_REQUIRED"))
        .andExpect(jsonPath("$.blockers[1].code").value("NO_SECRET_DIAGNOSTICS"))
        .andExpect(jsonPath("$.events[0]").value("TenantPlatformCoverageOpened"));
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
        .andExpect(jsonPath("$.dependencyStatus").value("Configuration details need setup"))
        .andExpect(jsonPath("$.traceMetadata.traceId").value("trace-ag-s09"))
        .andExpect(jsonPath("$.descriptors[0].stableId").value("config-lifecycle"))
        .andExpect(jsonPath("$.descriptors[0].allowedOperators[0]").value("simulate"))
        .andExpect(jsonPath("$.descriptors[0].valueSources[0]").value("governance-service.config-lifecycle"))
        .andExpect(jsonPath("$.descriptors[0].decisionQualityRequirement").value("CONFIRMED_BACKEND_EVIDENCE"))
        .andExpect(jsonPath("$.descriptors[0].versionRef").value("config-lifecycle-version-ref-required"))
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
        .andExpect(jsonPath("$.pendingReview.simulationVisible").value(true))
        .andExpect(jsonPath("$.pendingReview.publishVisible").value(true))
        .andExpect(jsonPath("$.pendingReview.downstreamConsumers[0]").value("pricing-bff"))
        .andExpect(jsonPath("$.dynamicRuleEvidence.matchedRules[0].outcome").value("MATCHED"))
        .andExpect(jsonPath("$.dynamicRuleEvidence.skippedRules[0].reasonCode").value("UNKNOWN_FACT_FAIL_CLOSED"))
        .andExpect(jsonPath("$.dynamicRuleEvidence.actionOutputs[0]").value("action-output-ref-required"))
        .andExpect(jsonPath("$.dynamicRuleEvidence.factRefs[0]").value("fact:configured-governance-metadata"))
        .andExpect(jsonPath("$.dynamicRuleEvidence.precisionMetadataRef").value("precision-metadata-ref-required"))
        .andExpect(jsonPath("$.dynamicRuleEvidence.replayHashRef").value("replay-hash-ref-required"))
        .andExpect(jsonPath("$.events[0]").value("AdminGovernanceOpened"));
  }

  @Test
  void quoteJourneyMapSurfacesCrossServiceRefsBlockersAndSafeDrilldowns() throws Exception {
    mvc.perform(get("/api/v1/tenants/demo-tenant/quote-runs/run-test/journey")
            .header("X-Ui-Trace-Id", "trace-journey-s19"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantContext").value("demo-tenant"))
        .andExpect(jsonPath("$.runId").value("run-test"))
        .andExpect(jsonPath("$.status").value("BLOCKED_WITH_FALLBACK_FACTS"))
        .andExpect(jsonPath("$.dependencyStatus").value("CROSS_SERVICE_CONTRACTS_PARTIAL_OR_UNAVAILABLE"))
        .andExpect(jsonPath("$.nodes", hasSize(10)))
        .andExpect(jsonPath("$.nodes[0].nodeId").value("scenario-facts"))
        .andExpect(jsonPath("$.nodes[0].freshness.status").value("STALE_OR_UNKNOWN"))
        .andExpect(jsonPath("$.nodes[0].evidenceRefs[0]").value("scenario-version-ref-required"))
        .andExpect(jsonPath("$.nodes[0].replayHash").value("replay-hash-required-after-scenario-service-create"))
        .andExpect(jsonPath("$.nodes[1].status").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.nodes[1].blockers[0]").value("CATALOG_CONTRACT_UNAVAILABLE"))
        .andExpect(jsonPath("$.nodes[4].drilldownRoute").value("/quote/run-test/pricing-waterfall"))
        .andExpect(jsonPath("$.nodes[5].downstreamDependencies[1]").value("lock"))
        .andExpect(jsonPath("$.nodes[7].drilldownRefs.runId").value("run-test"))
        .andExpect(jsonPath("$.nodes[7].drilldownRefs.scenarioRef").value("scenario-ref-required"))
        .andExpect(jsonPath("$.nodes[7].drilldownRefs.quoteRef").value("quote-option-contract-required"))
        .andExpect(jsonPath("$.nodes[7].drilldownRefs.lockRef").value("lock-ref-required"))
        .andExpect(jsonPath("$.nodes[7].drilldownRefs.correlationRef").value("trace-journey-s19"))
        .andExpect(jsonPath("$.blockers[0]").value(org.hamcrest.Matchers.containsString("fallback refs and blocked states")))
        .andExpect(jsonPath("$.events[0]").value("QuoteJourneyMapOpened"));
  }

  @Test
  void customRuleFieldsUiAdapterReturnsBlockedFallbackMetadataContract() throws Exception {
    mvc.perform(get("/api/ui/custom-rules/fields")
            .param("scenarioId", "scenario-custom-rule")
            .header("X-Tenant-Context", "demo-tenant")
            .header("X-Ui-Trace-Id", "trace-cr-s05-fields"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scenarioId").value("scenario-custom-rule"))
        .andExpect(jsonPath("$.tenantContext").value("demo-tenant"))
        .andExpect(jsonPath("$.dependencyStatus").value("BACKEND_CONTRACT_UNAVAILABLE"))
        .andExpect(jsonPath("$.resultStatus").value("BLOCKED_FALLBACK"))
        .andExpect(jsonPath("$.fields", hasSize(3)))
        .andExpect(jsonPath("$.fields[0].sourceService").value("governance-service"))
        .andExpect(jsonPath("$.fields[0].versionRef").value("metadata-version-ref-required"))
        .andExpect(jsonPath("$.fields[1].factQuality").value("CONFLICTING"))
        .andExpect(jsonPath("$.factQualityOptions[1]").value("UNKNOWN"))
        .andExpect(jsonPath("$.validationMessages[0]").value(org.hamcrest.Matchers.startsWith("BACKEND_CONTRACT_UNAVAILABLE")))
        .andExpect(jsonPath("$.metadataVersionRefs[1]").value("typed-fact-version-ref-required"))
        .andExpect(jsonPath("$.errors[1].code").value("METADATA_VERSION_UNAVAILABLE"))
        .andExpect(jsonPath("$.events[0]").value("CustomRuleFieldsFallbackVisible"));
  }

  @Test
  void customRuleEvidenceUiAdapterPreservesBlockedRulesAuditRefsAndReplayHashes() throws Exception {
    mvc.perform(get("/api/ui/custom-rules/evidence")
            .param("quoteId", "quote-custom-rule")
            .header("X-Tenant-Context", "demo-tenant")
            .header("X-Ui-Trace-Id", "trace-cr-s05-evidence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quoteId").value("quote-custom-rule"))
        .andExpect(jsonPath("$.dependencyStatus").value("BACKEND_CONTRACT_UNAVAILABLE"))
        .andExpect(jsonPath("$.resultStatus").value("BLOCKED_FALLBACK"))
        .andExpect(jsonPath("$.matchedRules[0].outcome").value("MATCHED"))
        .andExpect(jsonPath("$.skippedRules[0].reasonCode").value("REQUIRED_FACT_CONFLICTING"))
        .andExpect(jsonPath("$.blockedRules[0].outcome").value("BLOCKED"))
        .andExpect(jsonPath("$.blockedRules[0].auditRefs[0]").value("audit:quote-rule-blocked-required"))
        .andExpect(jsonPath("$.calculationSteps[0].status").value("BLOCKED"))
        .andExpect(jsonPath("$.calculationSteps[0].summary")
            .value("No pricing math is computed by pricing-bff fallback."))
        .andExpect(jsonPath("$.reasonCodes[3]").value("RULE_EVIDENCE_BLOCKED"))
        .andExpect(jsonPath("$.auditRefs[0]").value("audit:custom-rule-evidence-required"))
        .andExpect(jsonPath("$.replayHashRefs[0]").value("replay-hash-ref-required"))
        .andExpect(jsonPath("$.errors[0].code").value("DEPENDENCY_UNAVAILABLE"))
        .andExpect(jsonPath("$.events[0]").value("CustomRuleEvidenceFallbackVisible"));
  }
}

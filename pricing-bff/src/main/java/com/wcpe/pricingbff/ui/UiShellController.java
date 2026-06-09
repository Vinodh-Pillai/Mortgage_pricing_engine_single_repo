package com.wcpe.pricingbff.ui;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Component
class PricingBffUiFallbackAdapter {
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

  @PostMapping("/api/v1/tenants/workspaces")
  ResponseEntity<TenantWorkspaceResult> createTenantWorkspace(
      @RequestBody(required = false) Map<String, Object> setup) {
    Map<String, String> blockers = new LinkedHashMap<>();
    if (isBlankText(setup, "tenantName")) {
      blockers.put("tenantName", "Workspace name is required before tenant setup can be recorded.");
    }
    if (isBlankText(setup, "operationsContact")) {
      blockers.put("operationsContact", "Operations contact is required before tenant setup can be recorded.");
    }
    if (isBlankText(setup, "launchGoal")) {
      blockers.put("launchGoal", "Launch goal is required before tenant setup can be recorded.");
    }
    if (!blockers.isEmpty()) {
      return ResponseEntity.badRequest().body(new TenantWorkspaceResult(null, "BLOCKED",
          "Complete the highlighted workspace fields.", "Finish tenant setup details.", blockers.values().stream().toList()));
    }

    String tenantId = "tenant-" + Integer.toUnsignedString((normalized(setup.get("tenantName")) + "|"
        + normalized(setup.get("operationsContact"))).hashCode(), 36);
    return ResponseEntity.status(HttpStatus.CREATED).body(new TenantWorkspaceResult(tenantId, "RECORDED",
        "Tenant workspace setup was recorded in local preview mode.",
        "Connect configured tenant and identity services before production onboarding.",
        List.of("Tenant service contract is not configured in this local response.",
            "Identity provider and credentials remain external configuration.")));
  }

  @PostMapping("/api/v1/products/catalog")
  ResponseEntity<ProductCatalogResult> createProductCatalogEntry(
      @RequestBody(required = false) Map<String, Object> product) {
    Map<String, String> blockers = new LinkedHashMap<>();
    if (isBlankText(product, "productName")) {
      blockers.put("productName", "Product name is required before a catalog draft can be recorded.");
    }
    if (isBlankText(product, "productOwner")) {
      blockers.put("productOwner", "Product owner is required before a catalog draft can be recorded.");
    }
    if (isBlankText(product, "borrowerNeed")) {
      blockers.put("borrowerNeed", "Borrower need is required before a catalog draft can be recorded.");
    }
    if (!blockers.isEmpty()) {
      return ResponseEntity.badRequest().body(new ProductCatalogResult(null, "BLOCKED",
          "Complete the highlighted product fields.", "Finish product setup details.", blockers.values().stream().toList()));
    }

    String productId = "product-" + Integer.toUnsignedString((normalized(product.get("productName")) + "|"
        + normalized(product.get("productOwner"))).hashCode(), 36);
    return ResponseEntity.status(HttpStatus.CREATED).body(new ProductCatalogResult(productId, "RECORDED",
        "Product catalog draft was recorded in local preview mode.",
        "Connect configured product catalog services before production publishing.",
        List.of("Product terms, eligibility, rates, thresholds, and regulatory values are not inferred.",
            "Catalog publishing remains blocked until a configured product contract is available.")));
  }

  ProductCatalogManagerView productCatalogManager(String tenantContext, String uiTraceId) {
    String tenant = tenantContext == null || tenantContext.isBlank() ? "ui-preview-tenant" : tenantContext;
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "catalog-manager-local-trace" : uiTraceId;
    List<ProductCatalogArea> areas = List.of(
        new ProductCatalogArea("draft-products", "Product drafts", "catalog-service draft metadata",
            "BLOCKED", "Drafts are visible, but configured product draft contracts are required before publish.",
            List.of("Product name", "Product owner", "Borrower need", "Version reference"),
            List.of("Configured draft schema is required before field validation can be marked verified.")),
        new ProductCatalogArea("investors-channels", "Investors, taxonomy, and channels",
            "catalog-service domain lists", "BLOCKED",
            "Domain list labels must come from catalog-service; this fallback does not infer investor behavior.",
            List.of("Investor label", "Taxonomy path", "Channel label"),
            List.of("Investor/channel catalog contracts are unavailable in local fallback mode.")),
        new ProductCatalogArea("terms-property-purpose", "Terms, property, occupancy, and purpose",
            "catalog-service domain lists", "BLOCKED",
            "Term, property, occupancy, and purpose lists remain backend-owned and policy-neutral.",
            List.of("Term label", "Property type", "Occupancy", "Loan purpose"),
            List.of("Configured domain-list metadata is required before options can be selected.")),
        new ProductCatalogArea("market-artifacts", "Market artifacts", "catalog-service market metadata",
            "BLOCKED", "Market artifact evidence is shown as references only; no market rules are inferred.",
            List.of("Market artifact reference", "Effective version", "Audit reference"),
            List.of("Configured market artifact contracts are unavailable.")));
    ProductCatalogLifecycle lifecycle = new ProductCatalogLifecycle("REVIEW_BLOCKED", true,
        List.of("approve", "publish", "rollback"),
        List.of("snapshot-catalog-contract-required", "event-catalog-contract-required"),
        List.of("audit-ref-required", "replay-hash-required"),
        "Approval, publish, rollback, snapshot, event, and audit actions stay disabled until catalog-service contracts are configured.");
    return new ProductCatalogManagerView(tenant, "CATALOG_CONTRACTS_UNAVAILABLE", areas, lifecycle,
        List.of("CatalogManagerOpened"),
        "Configured catalog-service draft, lifecycle, snapshot, event, and audit contracts are unavailable; fallback records non-secret blocked states only.",
        traceId);
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
            List.of("UIFlowOpened", "ScenarioMetadataReviewed", "BorrowerIntakeSubmitted"), false,
            "SCENARIO_SERVICE_CONTRACT_NOT_CONFIGURED", "audit-package-required-after-scenario-service-create",
            "replay-hash-required-after-scenario-service-create", scenarioIntakeMetadata(tenantId, traceId).validationIssues()));
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/intake-metadata")
  ScenarioIntakeMetadata scenarioIntakeMetadata(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = normalizeTrace(uiTraceId);
    return new ScenarioIntakeMetadata(tenantId, "SCENARIO_SERVICE_CONTRACT_NOT_CONFIGURED", List.of(
        new ScenarioIntakeFieldGroup("scenario-identity", "Scenario identity",
            "Capture identifiers and channel context. These fields are passed through as facts and are not pricing inputs.",
            List.of(
                metadataField("scenarioName", "Scenario name", "scenario-identity", "text", false,
                    "Optional local label for support and audit trace review.", "scenario-service metadata contract", "UNKNOWN",
                    List.of("Configured scenario-service metadata is required before this field can be marked verified.")),
                metadataField("channel", "Channel", "scenario-identity", "text", false,
                    "Record the originating channel supplied by the backend-owned scenario profile.", "submission-profile contract", "UNKNOWN",
                    List.of("Channel remains optional until a configured submission profile marks it required.")),
                metadataField("externalLoanId", "External loan id", "scenario-identity", "text", false,
                    "Store a caller-provided loan reference when available; do not infer one in the UI.", "scenario-service create request", "UNKNOWN", List.of()),
                metadataField("sourceSystem", "Source system", "scenario-identity", "text", false,
                    "Optional upstream system reference for replay and audit correlation.", "scenario-service create request", "UNKNOWN", List.of()))),
        new ScenarioIntakeFieldGroup("borrower-loan-property", "Borrower, loan, and property facts",
            "Capture fact fields for downstream validation. The BFF does not calculate eligibility, rates, fees, or pricing.",
            List.of(
                metadataField("borrowerCreditStatus", "Borrower credit status", "borrower-loan-property", "text", false,
                    "Status label supplied by borrower intake or a configured credit source.", "borrower-credit profile", "UNKNOWN", List.of()),
                metadataField("creditScore", "Credit score", "borrower-loan-property", "number", false,
                    "Optional borrower-provided score value; pricing decisions remain downstream.", "borrower-credit profile", "UNKNOWN", List.of()),
                metadataField("loanPurpose", "Loan purpose", "borrower-loan-property", "text", false,
                    "Plain-language purpose captured for scenario completeness only.", "loan-structure metadata", "UNKNOWN", List.of()),
                metadataField("loanAmount", "Loan amount", "borrower-loan-property", "number", false,
                    "Optional requested amount captured as a fact; the UI does not calculate ratios.", "loan-structure metadata", "UNKNOWN", List.of()),
                metadataField("propertyState", "Property state", "borrower-loan-property", "text", false,
                    "State reference captured for configured downstream validation.", "property metadata", "UNKNOWN", List.of()),
                metadataField("occupancyType", "Occupancy type", "borrower-loan-property", "text", false,
                    "Occupancy fact captured for downstream scenario validation only.", "property metadata", "UNKNOWN", List.of()))),
        new ScenarioIntakeFieldGroup("income-assets", "Income and assets",
            "Capture optional borrower-provided income and asset facts without deriving capacity or pricing.",
            List.of(
                metadataField("monthlyIncome", "Monthly income", "income-assets", "number", false,
                    "Optional income fact for downstream scenario-service validation.", "income-asset metadata", "UNKNOWN", List.of()),
                metadataField("liquidAssets", "Liquid assets", "income-assets", "number", false,
                    "Optional asset fact for downstream scenario-service validation.", "income-asset metadata", "UNKNOWN", List.of())))),
        List.of("Disable quote progression when required backend facts are missing.",
            "Surface audit package and replay hash references before downstream quote decisions.",
            "Keep pricing calculations outside the workbench intake surface."),
        List.of(new ScenarioIntakeValidationIssue("SCENARIO_SERVICE_CONTRACT_REQUIRED", "scenarioService", "BLOCKING",
            "Scenario-service metadata, validation issues, audit package id, and replay hash must be configured before downstream quote decisions can mutate.")),
        "audit-package-required-after-scenario-service-create", "replay-hash-required-after-scenario-service-create",
        "Configured scenario-service metadata is unavailable; this BFF response exposes non-secret field metadata, blockers, audit ids, and replay references only.", traceId);
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

  PricingWaterfallView pricingWaterfall(String tenantId, String runId, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "pw-s05-local-trace" : uiTraceId;
    return new PricingWaterfallView(tenantId, runId, "BLOCKED", false,
        "PRICING_SERVICE_WATERFALL_CONTRACT_NOT_CONFIGURED",
        new WaterfallBaseSelection("base-selection-ref-required", "grid-version-ref-required",
            new RedactedWaterfallValue(null, true, "pricing.waterfall.restricted.read permission is required for selected note rate"),
            new RedactedWaterfallValue(null, true, "pricing.waterfall.restricted.read permission is required for base price"),
            List.of("grid-resolution", "candidate-generation", "rate-selection")),
        new WaterfallFinalPrice("final-price-ref-required",
            new RedactedWaterfallValue(null, true, "pricing.waterfall.restricted.read permission is required for rounded final price"),
            List.of(
                new WaterfallLedgerRow(1, "BASE_PRICE", new RedactedWaterfallValue(null, true,
                    "pricing.waterfall.restricted.read permission is required for ledger values"), "START",
                    new RedactedWaterfallValue(null, true,
                        "pricing.waterfall.restricted.read permission is required for ledger values"),
                    "grid-version-ref-required", "BASE_RATE_SELECTED", null),
                new WaterfallLedgerRow(2, "ADJUSTMENTS_AND_MARGINS", new RedactedWaterfallValue(null, true,
                    "pricing.waterfall.restricted.read permission is required for ledger values"), "BACKEND_OWNED",
                    new RedactedWaterfallValue(null, true,
                        "pricing.waterfall.restricted.read permission is required for ledger values"),
                    "adjustment-margin-version-refs-required", "CONFIGURED_PRICING_EVIDENCE_REQUIRED", null),
                new WaterfallLedgerRow(3, "ROUND_FINAL_PRICE", new RedactedWaterfallValue(null, true,
                    "pricing.waterfall.restricted.read permission is required for ledger values"), "ROUND",
                    new RedactedWaterfallValue(null, true,
                        "pricing.waterfall.restricted.read permission is required for ledger values"),
                    "rounding-policy-ref-required", "ROUNDING_TRACE_REQUIRED", "configured-rounding-mode-required")),
            List.of("adjustment-version-refs-required", "margin-version-refs-required"),
            List.of("rounding-policy-ref-required", "configured-rounding-trace-required")),
        List.of(
            new WaterfallBlocker("PRICING_SERVICE_CONTRACT_REQUIRED",
                "Pricing-service waterfall evidence must provide base selection, final price ledger, rounding trace, and replay hashes before values can be shown.",
                "pricing-service.waterfall"),
            new WaterfallBlocker("MISSING_PRICE_POLICY_REQUIRED",
                "Missing-price handling remains fail-closed until pricing-service returns an explicit incident or valid-price result.",
                "pricing-service.missing-price")),
        List.of("grid-version-ref-required", "adjustment-version-refs-required", "rounding-policy-ref-required"),
        List.of("audit:base-selection-required", "audit:final-price-required", "audit:missing-price-required"),
        "replay-hash-required", "version-graph-hash-required", "result-hash-required", "waterfall-evidence-hash-required",
        traceId, List.of("PricingWaterfallOpened"),
        "Configured pricing-service waterfall contract is unavailable; this BFF response exposes non-secret references, redactions, and blockers only.");
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers")
  OfferComparisonView offerComparison(@PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return OfferComparisonView.contractVisible(runId, normalizeTrace(uiTraceId));
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/explain")
  OfferExplanationView offerExplanation(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return OfferExplanationView.available(runId, offerId, normalizeTrace(uiTraceId));
  }

  EligibilityModuleView eligibilityModule(String runId, String quoteOptionId, String uiTraceId) {
    String traceId = normalizeTrace(uiTraceId);
    String optionId = quoteOptionId == null || quoteOptionId.isBlank() ? "quote-option-contract-required" : quoteOptionId;
    List<EligibilityDecisionView> decisions = List.of(
        new EligibilityDecisionView("eligible-contract-path", "ELIGIBLE", List.of("ELIGIBILITY_CONTRACT_VISIBLE"),
            List.of("fact:scenario-version", "fact:loan-structure"), List.of("overlay:configured-product"),
            new CacheFreshnessView("FRESHNESS_REQUIRED", "cache:eligibility:decision", "Cache timestamp supplied by eligibility-service."),
            "Configured eligibility-service explanation text is displayed here when available.",
            List.of("audit-package-required", "rule-version-graph-required")),
        new EligibilityDecisionView("ineligible-contract-path", "INELIGIBLE", List.of("FILTER_OUT_EXPLANATION_REQUIRED"),
            List.of("fact:representative-credit", "fact:property"), List.of("overlay:investor-contract"),
            new CacheFreshnessView("FRESHNESS_REQUIRED", "cache:eligibility:filter-out", "Filter-out cache evidence is backend-owned."),
            "Filter-out explanation must come from eligibility-service; the BFF does not infer policy logic.",
            List.of("evidence-id-required", "replay-hash-required")),
        new EligibilityDecisionView("conditional-contract-path", "CONDITIONAL", List.of("REQUIRED_FACTS_PENDING"),
            List.of("fact:income-assets", "fact:documentation"), List.of("overlay:conditional-review"),
            new CacheFreshnessView("STALE_OR_UNKNOWN", "cache:eligibility:conditional", "Refresh requirement is supplied by eligibility-service."),
            "Conditional explanation is visible only as backend-owned text and references.",
            List.of("condition-audit-ref-required", "source-fact-bundle-required")));
    List<EligibilityBlockerView> blockers = List.of(
        new EligibilityBlockerView("UNKNOWN_REQUIRED_FACT", "fact:income-assets",
            "Required fact is unknown; eligibility stays fail-closed until a configured source supplies it."),
        new EligibilityBlockerView("CONFLICTING_FACT", "fact:representative-credit",
            "Conflicting fact evidence blocks eligibility review without defaulting values."));
    return new EligibilityModuleView(runId, optionId, "FAIL_CLOSED_REVIEW", decisions, blockers,
        List.of("fact:income-assets", "fact:representative-credit"),
        "Configured eligibility-service decision, overlay, cache, and explanation contracts are unavailable; this fallback carries references and blockers only.",
        traceId, List.of("EligibilityModuleOpened"));
  }

  @PostMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/select")
  ResponseEntity<OfferSelectionResult> selectOffer(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String normalizedOfferId = offerId == null || offerId.isBlank() ? "quote-option-contract-required" : offerId;
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(OfferSelectionResult.selected(runId, normalizedOfferId, normalizeTrace(uiTraceId)));
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

  RateFeedOperationsView rateFeedOperations(String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "rf-s03-local-trace" : uiTraceId;
    return new RateFeedOperationsView(normalizeTenant(tenantContext), "RATE_FEED_SERVICE_CONTRACT_NOT_CONFIGURED",
        List.of(
            new RateFeedWorkflowStep("upload", "Upload received", "UPLOAD_READY", "rate-feed-service upload sessions and import endpoints", "batchId-required", "raw-file-ref-required"),
            new RateFeedWorkflowStep("parse", "Parse and normalize", "PARSE_AND_NORMALIZE_VISIBLE", "rate-feed-service parse-results and normalized-entries endpoints", "parse-job-ref-required", "parse-result-hash-required"),
            new RateFeedWorkflowStep("validate", "Validation review", "VALIDATION_BLOCKERS_VISIBLE", "rate-feed-service validation-report endpoint", "validation-job-ref-required", "validation-result-hash-required"),
            new RateFeedWorkflowStep("activate", "Activate or reject", "ACTION_BLOCKED_UNTIL_CONFIGURED_SERVICE", "rate-feed-service publish, rollback, activate, and reject endpoints", "approval-ref-required", "activation-audit-ref-required"),
            new RateFeedWorkflowStep("replay", "Replay and cache evidence", "EVIDENCE_BLOCKED_UNTIL_CONFIGURED_SERVICE", "rate-feed-service replay and cache-invalidation endpoints", "replay-hash-required", "cache-invalidation-command-required")),
        List.of(
            new RateFeedGridBlocker("source-row-12", "noteRate", "BLOCKER", "SOURCE_ROW_VALIDATION_REQUIRED", "source:rate-feed-batch/row/12", "configured validation report required"),
            new RateFeedGridBlocker("source-row-19", "lockPeriod", "WARNING", "SOURCE_REFERENCE_REVIEW_REQUIRED", "source:rate-feed-batch/row/19", "operator review required before publish")),
        List.of("sheet-version-ref-required", "activation-audit-ref-required", "partner-submission-ref-required"),
        List.of("cache-invalidation-command-required", "replay-hash-required", "outbox-event-ref-required"),
        true,
        "Configured rate-feed-service operations contract is unavailable in this local BFF fallback; UI actions show workflow state and blockers only and do not recalculate rates.",
        traceId,
        List.of("RateFeedOperationsOpened"));
  }

  PerformanceDashboardView performanceDashboard(String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "perf-s09-local-trace" : uiTraceId;
    return new PerformanceDashboardView(normalizeTenant(tenantContext), "OBSERVABILITY_SERVICE_CONTRACT_NOT_CONFIGURED",
        List.of(
            new PerformanceSignalGroup("pricing-bff", "ui-preview-tenant", "corr-performance-bff", "PARTIAL",
                List.of(
                    new PerformanceSignal("request-latency", "Latency signal requires configured observability-service metric snapshot.", "NO_DATA", "pricing-bff request metrics", "observability-service.performance_metric_snapshot", List.of(".local-harness/evidence/PII-22-S09/pricing-bff-test.log"))),
                List.of()),
            new PerformanceSignalGroup("observability-service", "ui-preview-tenant", "corr-performance-observability", "STALE",
                List.of(
                    new PerformanceSignal("reference-cache-freshness", "Cache freshness is stale until the configured cache observation read model is linked.", "STALE", "reference data cache observation", "observability-service.cache_observation", List.of(".local-harness/evidence/PII-22-S09/observability-service-test.log")),
                    new PerformanceSignal("load-test-evidence", "Load-test evidence is unavailable until a project-relative report is supplied.", "BLOCKED", "load-test profile", "observability-service.loadtest", List.of(".local-harness/evidence/PII-22-S09/load-test-report-required.json"))),
                List.of(
                    new PerformanceBlocker("LOAD_TEST_EVIDENCE_UNAVAILABLE", "observability-service", "Project-relative load-test report is required before this dashboard can mark load history fresh."))
            ),
            new PerformanceSignalGroup("redis-cache", "ui-preview-tenant", "corr-performance-cache", "PARTIAL",
                List.of(
                    new PerformanceSignal("cache-backpressure", "Backpressure source is visible; runtime configuration is not changed from this dashboard.", "PARTIAL", "cache and backpressure snapshots", "observability-service.backpressure", List.of("runbooks/cache-backpressure"))),
                List.of(
                    new PerformanceBlocker("CACHE_RECOVERY_OWNER_REQUIRED", "SRE / Operations Lead", "Configured recovery ownership is required before operators can close stale cache incidents."))
            )),
        List.of(
            new PerformanceImpact("STALE_CACHE", "Pricing/reference cache freshness may be stale for operators reviewing quote workflow readiness.", "observability-service", "SRE / Operations Lead", "runbooks/cache-backpressure"),
            new PerformanceImpact("BACKPRESSURE_VISIBLE", "Backpressure is shown as an operational signal only; the UI does not change rate-limit or cache settings.", "observability-service", "Platform Operations", "runbooks/backpressure")),
        List.of(".local-harness/evidence/PII-22-S09/observability-service-test.log", ".local-harness/evidence/PII-22-S09/pricing-bff-test.log", ".local-harness/evidence/PII-22-S09/ui-test.log"),
        List.of(
            new PerformanceBlocker("OBSERVABILITY_SERVICE_CONTRACT_NOT_CONFIGURED", "observability-service", "Live performance metric, cache, and alert read models are not configured at the BFF boundary."),
            new PerformanceBlocker("LOAD_TEST_REPORT_REQUIRED", "Performance engineering", "Load-test evidence must be supplied as a project-relative artifact before the report link can be verified.")),
        true,
        "Configured observability-service performance, cache, alert, and load-test contracts are unavailable; this fallback carries backend-owned refs, freshness, blockers, and recovery ownership only.",
        traceId,
        List.of("PerformanceDashboardOpened"));
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

  @GetMapping("/api/v1/custom-rules/evidence")
  CustomRuleEvidenceView customRuleEvidence(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "cr-s01-local-trace" : uiTraceId;
    return new CustomRuleEvidenceView(normalizeTenant(tenantContext), "FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE", traceId,
        List.of(
            new CustomFieldMetadata("custom-field-evidence-source", "Evidence source", "text",
                List.of("backend metadata required"),
                "Source reference supplied by the configured rule evidence contract.", "UNKNOWN",
                "configured-governance-metadata", List.of("Backend metadata marks this required fact as UNKNOWN."), true),
            new CustomFieldMetadata("custom-field-decision-quality", "Decision quality", "enumeration",
                List.of("VERIFIED", "UNKNOWN", "CONFLICTING"),
                "Decision state supplied by typed fact evaluation.", "CONFLICTING", "typed-fact-contract",
                List.of("Conflicting required fact blocks commit until the backend returns a resolved state."), true),
            new CustomFieldMetadata("custom-field-review-note", "Review note", "text", List.of("free text from configured metadata"),
                "Optional operations note; not used for pricing math.", "VERIFIED", "ui-metadata-contract", List.of(), false)),
        new CalculationEvidence(
            List.of(new RuleEvidenceRow("rule-evidence-contract-required", "version-ref-required", "MATCHED",
                "RULE_EVIDENCE_VISIBLE", List.of("custom-field-evidence-source", "custom-field-decision-quality"))),
            List.of(new RuleEvidenceRow("rule-skipped-conflicting-fact", "version-ref-required", "SKIPPED",
                "REQUIRED_FACT_CONFLICTING", List.of("custom-field-decision-quality"))),
            List.of("RULE_EVIDENCE_VISIBLE", "REQUIRED_FACT_UNKNOWN", "REQUIRED_FACT_CONFLICTING"),
            "Precision metadata supplied by configured backend evidence.", "replay-hash-ref-required"),
        List.of("Required fact custom-field-evidence-source is UNKNOWN.",
            "Required fact custom-field-decision-quality is CONFLICTING.",
            "Configured rule evaluation contract must resolve blockers before commit."),
        true,
        new DesignEvidenceStatus("DESIGN_EVIDENCE_BLOCKED",
            "External screenshot/PDF evidence is unavailable until copied into a project-relative evidence path.",
            List.of("Copy approved files under .local-harness/screenshots/PII-21-S01/source/",
                "Use a future consensus-gated external-path ingestion task.")),
        List.of("CustomRuleEvidenceOpened"),
        "Configured typed-fact and rule evidence services are unavailable; this response carries non-secret fallback metadata and blockers only.");
  }

  AuditReplayWorkbenchView auditReplayWorkbench(String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "ar-s10-local-trace" : uiTraceId;
    return new AuditReplayWorkbenchView(normalizeTenant(tenantContext),
        "AUDIT_REPLAY_SERVICE_CONTRACT_NOT_CONFIGURED", traceId,
        List.of(
            new AuditReplayRecordSummary("event-id-required", "quote", "quote-id-required", "QUOTE_REPLAY_REQUESTED",
                "INTEGRITY_PENDING", "redaction-profile-required", "retention-policy-ref-required", false,
                "BLOCKED_UNTIL_CONFIGURED_SERVICE", List.of("audit-record-id-required", "integrity-hash-required")),
            new AuditReplayRecordSummary("event-id-lock-required", "lock", "lock-id-required", "LOCK_REPLAY_REQUESTED",
                "INTEGRITY_PENDING", "legal-hold-redaction-profile-required", "LEGAL_HOLD_ACTIVE", true,
                "EXPORT_LOCKED_BY_LEGAL_HOLD", List.of("audit-record-id-required", "previous-hash-required"))),
        List.of(
            new AuditReplayRunSummary("quote-replay-run-required", "QUOTE", "quote-id-required", "BLOCKED",
                "original-hash-required", "replay-hash-required", List.of("quote snapshot diff supplied by audit-replay-service"),
                List.of("source audit record contract is not configured", "quote-service replay dependency is unavailable"),
                List.of("quote-version-ref-required", "event-sequence-ref-required")),
            new AuditReplayRunSummary("lock-replay-run-required", "LOCK", "lock-id-required", "BLOCKED",
                "original-lock-hash-required", "lock-replay-hash-required", List.of("lock term diff supplied by audit-replay-service"),
                List.of("source lock audit record contract is not configured", "lock-service replay dependency is unavailable"),
                List.of("market-snapshot-ref-required", "extension-policy-ref-required"))),
        new AuditReplayExportSummary("evidence-export-required", "BLOCKED", "redaction-profile-required",
            "retention-until-supplied-by-audit-replay-service", true, false, "manifest-hash-required",
            List.of("legal hold prevents direct download until backend release decision is supplied",
                "configured evidence export storage contract is unavailable")),
        List.of(
            new AuditReplayContractRef("audit-record-search", "/api/v1/tenants/{tenantId}/audit-records",
                "Shows event ids, integrity hashes, redaction profile, retention date, and legal hold flags."),
            new AuditReplayContractRef("quote-replay", "/api/v1/tenants/{tenantId}/quote-replays/{runId}/diff",
                "Shows replay diff, replay hash, version refs, and missing dependency blockers."),
            new AuditReplayContractRef("evidence-export", "/api/v1/tenants/{tenantId}/evidence-exports/{exportId}",
                "Preserves backend-owned redaction, retention, manifest hash, and legal hold decisions.")),
        List.of("Configured audit-replay-service endpoint is not wired to pricing-bff local fallback.",
            "Replay execution remains blocked until quote-service and lock-service dependencies provide source snapshots.",
            "Evidence download remains disabled while legal hold or retention decision is backend-owned."),
        List.of("AuditReplayWorkbenchOpened", "AuditReplayFallbackEvidenceVisible"),
        "Configured audit-replay-service contracts are unavailable; this response carries non-secret fallback evidence refs, blockers, retention states, and redaction states only.");
  }

  TenantPlatformCoverageView tenantPlatformCoverage(String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "tc-s08-local-trace" : uiTraceId;
    return new TenantPlatformCoverageView(normalizeTenant(tenantContext), "TENANT_CONTEXT_SERVICE_CONTRACT_NOT_CONFIGURED",
        traceId,
        new TenantContextTrace("tenant-id-visible-from-request", "correlation-id-required", "idempotency-key-required",
            "event-envelope-ref-required", "audit:tenant-context-platform-required", "replay-hash-required"),
        List.of(
            new TenantPlatformControl("tenant-resolution", "Tenant resolution", "VISIBLE",
                "tenant-context-service resolves request tenant, actor, channel, and permitted tenant scope.",
                List.of("tenant-id", "actor-id", "channel", "permitted-tenant-scope"), List.of()),
            new TenantPlatformControl("cache-scope", "Tenant-scoped cache keys", "VISIBLE",
                "Cache evidence stays tenant-scoped and shows invalidation references instead of cache contents.",
                List.of("cache-scope-ref", "cache-invalidation-event-ref"), List.of()),
            new TenantPlatformControl("rate-limit", "Rate limiting guard", "BLOCKED",
                "Rate limit outcomes are visible only when tenant-context-service supplies a configured policy decision.",
                List.of("rate-limit-policy-ref-required", "rate-limit-event-ref-required"),
                List.of("Configured tenant rate-limit policy contract is unavailable in local fallback mode.")),
            new TenantPlatformControl("audit-outbox", "Audit and event envelope", "VISIBLE",
                "Audit refs, outbox refs, event envelope refs, and replay hashes are displayed as backend-owned evidence.",
                List.of("audit-ref", "outbox-event-ref", "event-envelope-ref", "replay-hash-ref"), List.of()),
            new TenantPlatformControl("readiness", "Service readiness", "BLOCKED",
                "Readiness remains blocked until configured service checks provide current tenant-context status.",
                List.of("tenant-context-service", "pricing-bff", "pricing-workbench-ui"),
                List.of("Configured readiness endpoint is unavailable in this local fallback."))),
        List.of(
            new TenantPlatformBlocker("CONFIGURED_TENANT_CONTEXT_CONTRACT_REQUIRED", "tenant-context-service",
                "Configured tenant-context diagnostics are required before live platform coverage can be marked ready."),
            new TenantPlatformBlocker("NO_SECRET_DIAGNOSTICS", "pricing-bff",
                "Diagnostics show refs and statuses only; credentials, tokens, tenant secrets, and secret transport values are not exposed.")),
        List.of("TenantPlatformCoverageOpened", "TenantContextFallbackEvidenceVisible"),
        "Configured tenant-context-service diagnostics are unavailable; this response carries non-secret platform coverage refs and blocked states only.");
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
            new GovernanceDescriptor("config-lifecycle", "configuration lifecycle", "workflow",
                List.of("simulate", "approve", "publish", "rollback"),
                List.of("governance-service.config-lifecycle", "audit-replay-service.audit-records"),
                "CONFIRMED_BACKEND_EVIDENCE", List.of("Configured lifecycle validation evidence is required before publish."),
                "config-lifecycle-version-ref-required"),
            new GovernanceDescriptor("dynamic-rule-evidence", "dynamic rule evidence", "rule-evidence",
                List.of("match", "skip", "block"),
                List.of("governance-service.rule-builder", "typed-fact-contract"),
                "FAIL_CLOSED_ON_UNKNOWN_FACT", List.of("Unknown or conflicting facts block dynamic rule commit."),
                "rule-evidence-version-ref-required")),
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
        new PendingConfigReview("PCR-config-lifecycle-required", "PENDING_REVIEW", true, true, true, true,
            "auditRef-required", List.of("pricing-bff", "pricing-workbench-ui", "governance-service"),
            List.of("Configured simulation evidence is required before approval.",
                "Downstream consumer impact is shown as refs only until governance-service returns live data.")),
        new DynamicRuleEvidenceSnapshot(
            List.of(new RuleEvidenceRow("rule-evidence-contract-required", "rule-evidence-version-ref-required", "MATCHED",
                "RULE_EVIDENCE_VISIBLE", List.of("fact:configured-governance-metadata"))),
            List.of(new RuleEvidenceRow("rule-skipped-unknown-fact", "rule-evidence-version-ref-required", "SKIPPED",
                "UNKNOWN_FACT_FAIL_CLOSED", List.of("fact:unknown-governance-input"))),
            List.of("action-output-ref-required"), List.of("fact:configured-governance-metadata", "fact:unknown-governance-input"),
            "precision-metadata-ref-required", "replay-hash-ref-required"),
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

  private ScenarioIntakeField metadataField(String fieldId, String label, String groupId, String dataType, boolean required,
      String helpText, String sourceRef, String decisionQuality, List<String> validationMessages) {
    return new ScenarioIntakeField(fieldId, label, groupId, dataType, required, helpText, sourceRef, decisionQuality,
        validationMessages);
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

  record TenantWorkspaceResult(String tenantId, String status, String message, String nextStep,
      List<String> placeholders) {}

  record ProductCatalogResult(String productId, String status, String message, String nextStep,
      List<String> placeholders) {}

  record ProductCatalogManagerView(String tenantContext, String dependencyStatus, List<ProductCatalogArea> areas,
      ProductCatalogLifecycle lifecycle, List<String> events, String fallbackReason, String uiTraceId) {}

  record ProductCatalogArea(String areaId, String label, String sourceRef, String status, String guidance,
      List<String> fields, List<String> validationMessages) {}

  record ProductCatalogLifecycle(String state, boolean actionsDisabled, List<String> actions,
      List<String> snapshotRefs, List<String> auditRefs, String blocker) {}

  record IntakeValidation(boolean passed, String status, String message, Map<String, String> blockers) {}

  record QuoteRunLaunch(String runId, String status, String nextRoute, IntakeValidation validationSummary, String uiTraceId,
      List<String> events, boolean fallbackMode, String dependencyStatus, String auditPackageId, String replayHashRef,
      List<ScenarioIntakeValidationIssue> validationIssues) {
    static QuoteRunLaunch blocked(String traceId, IntakeValidation validation) {
      return new QuoteRunLaunch(null, "BLOCKED", null, validation, traceId, List.of("UIFlowOpened"), true,
          "UPSTREAM_NOT_CALLED", null, null, List.of());
    }
  }

  record ScenarioIntakeMetadata(String tenantContext, String dependencyStatus, List<ScenarioIntakeFieldGroup> fieldGroups,
      List<String> decisionControls, List<ScenarioIntakeValidationIssue> validationIssues, String auditPackageId,
      String replayHashRef, String fallbackReason, String uiTraceId) {}

  record ScenarioIntakeFieldGroup(String groupId, String label, String helpText, List<ScenarioIntakeField> fields) {}

  record ScenarioIntakeField(String fieldId, String label, String groupId, String dataType, boolean required,
      String helpText, String sourceRef, String decisionQuality, List<String> validationMessages) {}

  record ScenarioIntakeValidationIssue(String code, String fieldPath, String severity, String message) {}

  record QuoteRunStatus(String runId, String status, String nextRoute, String uiTraceId, String dependencyStatus) {}

  record PricingWaterfallView(String tenantContext, String runId, String status, boolean restrictedValuesVisible,
      String dependencyStatus, WaterfallBaseSelection baseSelection, WaterfallFinalPrice finalPrice,
      List<WaterfallBlocker> blockers, List<String> versionRefs, List<String> auditRefs, String replayHash,
      String versionGraphHash, String resultHash, String evidenceHash, String uiTraceId, List<String> events,
      String fallbackReason) {}

  record WaterfallBaseSelection(String selectionId, String gridVersionRef, RedactedWaterfallValue selectedNoteRate,
      RedactedWaterfallValue basePrice, List<String> ledgerSteps) {}

  record WaterfallFinalPrice(String finalPriceId, RedactedWaterfallValue roundedFinalPrice,
      List<WaterfallLedgerRow> ledger, List<String> adjustmentRefs, List<String> roundingTraceRefs) {}

  record WaterfallLedgerRow(int ordinal, String step, RedactedWaterfallValue inputValue, String operation,
      RedactedWaterfallValue outputValue, String configRef, String reasonCode, String roundingMode) {}

  record RedactedWaterfallValue(String value, boolean redacted, String reason) {}

  record WaterfallBlocker(String code, String message, String sourceRef) {}

  record OfferComparisonView(String runId, String status, List<OfferSummary> offers, List<String> sortOptions,
      String selectedOfferId, boolean commitBlocked, String fallbackReason, List<String> requiredFacts,
      List<String> backendRefs, String uiTraceId, List<String> events) {
    static OfferComparisonView upstreamMissing(String runId, String traceId) {
      return new OfferComparisonView(runId, "UPSTREAM_EXPLAINABILITY_REQUIRED", List.of(),
          List.of("payment", "apr", "confidence"), null, true,
          "Offer comparison requires a configured quote-service offers and explainability contract before commit.",
          List.of("requestedLockPeriods", "scenarioVersion"), List.of("quote-service.offers"), traceId,
          List.of("OfferListRendered"));
    }

    static OfferComparisonView contractVisible(String runId, String traceId) {
      return new OfferComparisonView(runId, "QUOTE_SERVICE_EVIDENCE_VISIBLE", List.of(
          new OfferSummary("quote-option-contract-required", 1, "Backend-ranked offer", "payment-ref-required",
              "apr-ref-required", "score:backend-owned", "rank-score-ref-required",
              List.of("Rank 1 from quote-service ranking response", "Policy and version refs are displayed without UI-side pricing math"),
              List.of("LOCK_PERIOD_REQUIRED", "FILTER_FACTS_PENDING"), "AVAILABLE", "scenario-ref-required", 7,
              List.of("eligibility-service:decision-ref-required", "pricing-service:waterfall-ref-required"),
              List.of("lock-eligibility:pending:quote-option-contract-required"),
              List.of("snapshot:quote-service:run:" + runId),
              List.of("audit:quote-ready-required", "replay-hash-required"),
              List.of("ranking", "comparison", "detail")),
          new OfferSummary("quote-option-backup-contract", 2, "Alternate backend-ranked offer", "payment-ref-required",
              "apr-ref-required", "score:backend-owned", "rank-score-ref-required-secondary",
              List.of("Rank 2 remains selectable only when configured selection policy permits it"),
              List.of("NON_TOP_RANK_REASON_REQUIRED"), "AVAILABLE", "scenario-ref-required", 7,
              List.of("eligibility-service:alternate-decision-ref-required"),
              List.of("lock-eligibility:pending:quote-option-backup-contract"),
              List.of("snapshot:quote-service:run:" + runId),
              List.of("audit:quote-ready-required", "audit:alternate-option-required"),
              List.of("ranking", "comparison", "detail"))),
          List.of("rank", "score", "confidence"), null, false,
          "Quote-service offer evidence is represented with backend-owned refs; UI actions stay blocked only when required facts are missing.",
          List.of("requestedLockPeriods", "scenarioVersion", "filterFacts"),
          List.of("quote-service.ranking", "quote-service.explanation", "quote-service.selection"), traceId,
          List.of("OfferListRendered", "QuoteServiceEvidenceBound"));
    }
  }

  record OfferSummary(String offerId, int rank, String productLabel, String payment, String apr, String confidence,
      String rankScore, List<String> rationaleChips, List<String> scenarioFlags, String explanationStatus,
      String sourceScenarioId, int scenarioVersion, List<String> upstreamRefs, List<String> lockEligibilityRefs,
      List<String> snapshotRefs, List<String> auditIds, List<String> explanationSections) {}

  record OfferExplanationView(String runId, String offerId, String status, List<String> rationaleLines,
      List<String> scenarioFlags, List<String> upstreamRefs, List<String> snapshotRefs, List<String> auditIds,
      List<String> explanationSections, boolean commitBlocked, String message, String uiTraceId) {
    static OfferExplanationView missing(String runId, String offerId, String traceId) {
      return new OfferExplanationView(runId, offerId, "MISSING", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true,
          "Explanation data is not available from the configured BFF boundary; selection remains blocked.", traceId);
    }

    static OfferExplanationView available(String runId, String offerId, String traceId) {
      return new OfferExplanationView(runId, offerId, "AVAILABLE",
          List.of("quote-service supplied rank, score, warnings, and source refs for this option.",
              "Selection will carry scenario version, lock eligibility, snapshot, replay, and audit refs."),
          List.of("LOCK_PERIOD_REQUIRED", "FILTER_FACTS_PENDING"),
          List.of("quote-service.option:" + offerId, "pricing-service.waterfall-ref-required", "eligibility-service.decision-ref-required"),
          List.of("snapshot:quote-service:run:" + runId),
          List.of("audit:quote-explanation-required", "replay-hash-required"),
          List.of("ranking", "comparison", "detail", "selection-handoff"), false,
          "Explanation data is available from backend-owned refs; no UI-side pricing rules are inferred.", traceId);
    }
  }

  record EligibilityModuleView(String runId, String quoteOptionId, String status, List<EligibilityDecisionView> decisions,
      List<EligibilityBlockerView> blockers, List<String> requiredNextFacts, String fallbackReason, String uiTraceId,
      List<String> events) {}

  record EligibilityDecisionView(String decisionId, String decision, List<String> reasonCodes, List<String> inputFactRefs,
      List<String> overlayRefs, CacheFreshnessView cacheFreshness, String explanationText, List<String> references) {}

  record CacheFreshnessView(String status, String cacheRef, String indicatorText) {}

  record EligibilityBlockerView(String reasonCode, String factRef, String message) {}

  record OfferSelectionResult(String runId, String selectedOfferId, String status, String nextRoute, String sourceScenarioId,
      int scenarioVersion, String lockEligibilityRef, String snapshotRef, List<String> auditIds, String auditRef,
      String message, String uiTraceId, List<String> events) {
    static OfferSelectionResult blocked(String runId, String offerId, String traceId) {
      return new OfferSelectionResult(runId, null, "BLOCKED", null, null, 0, null, null, List.of(), null,
          "Offer selection is blocked until explanation data is available for offer " + offerId + ".", traceId,
          List.of("OfferSelectionBlocked"));
    }

    static OfferSelectionResult selected(String runId, String offerId, String traceId) {
      return new OfferSelectionResult(runId, offerId, "SELECTED", "/quote/" + runId + "/lock",
          "scenario-ref-required", 7, "lock-eligibility:pending:" + offerId,
          "snapshot:quote-service:run:" + runId,
          List.of("audit:quote-selection-required", "replay-hash-required"),
          "audit:quote-selection-required",
          "Offer selection recorded with backend-owned refs for lock handoff.", traceId,
          List.of("OfferSelectionRecorded", "LockEligibilityRefsBound"));
    }
  }

  record LockWorkflowView(String runId, String selectedOfferId, String status, boolean lockDisabled,
      List<String> blockers, List<LockBlockerView> blockerDetails, String disclosureText, String nextAction,
      String uiTraceId, List<String> events, String dependencyStatus, List<String> selectedQuoteRefs,
      List<LockLifecycleCheck> freshnessChecks, List<String> requiredEvidence,
      List<LockStateTransition> stateTransitions, List<LockAuditGroup> auditGroups) {
    static LockWorkflowView blocked(String runId, String traceId) {
      return new LockWorkflowView(runId, null, "BLOCKED", true,
          List.of("Select an offer before requesting a lock.",
              "Lock-service eligibility and pricing-staleness contracts are not configured at this BFF boundary."),
          List.of(
              new LockBlockerView("SELECTED_OFFER_REQUIRED", "Select an offer before requesting a lock.",
                  "Return to offer comparison and bind an offer selection reference."),
              new LockBlockerView("LOCK_SERVICE_CONTRACT_REQUIRED",
                  "Lock-service eligibility and freshness contracts are not configured at this BFF boundary.",
                  "Connect lock-service lifecycle adapters before enabling submission.")),
          "Review lock disclosures after an offer is selected. No terms are locked from the blocked state.",
          "Return to offer comparison and select an offer with available explanation context.", traceId,
          List.of("LockBlocked"), "UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED",
          List.of("quote-run:" + runId, "selected-offer:required", "scenario-version:required"),
          List.of(new LockLifecycleCheck("Selected quote freshness", "BLOCKED", "lock-service:freshness-check",
              "Select an offer and refresh backend freshness before submission.")),
          List.of("selected-offer-ref", "freshness-check-id", "pricing-result-hash", "scenario-version-ref"),
          List.of(new LockStateTransition("NO_SELECTION", "BLOCKED", "lock.lifecycle.blocked.selection-required", "BLOCKED")),
          lifecycleAuditGroups(runId, "blocked"));
    }

    static LockWorkflowView ready(String runId, String selectedOfferId, String traceId) {
      return new LockWorkflowView(runId, selectedOfferId, "READY", false, List.of(), List.of(),
          "Confirming records the selected offer for lock workflow tracking. Final lock eligibility remains owned by the configured lock-service contract.",
          "Confirm lock request", traceId, List.of("LockAttempted"), "UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED",
          List.of("quote-run:" + runId, "selected-offer:" + selectedOfferId,
              "lock-eligibility:pending:" + selectedOfferId, "audit:quote-selection-required"),
          List.of(
              new LockLifecycleCheck("Quote freshness", "PENDING_CONFIGURED_SERVICE", "lock-service:freshness-check",
                  "Lock-service must return the authoritative freshness decision before live submission."),
              new LockLifecycleCheck("Scenario and pricing hashes", "VISIBLE", "quote-service:selected-offer-snapshot",
                  "Compare backend hashes; the UI does not override mismatches.")),
          List.of("selected-offer-ref", "lock-eligibility-ref", "freshness-check-id", "rate-sheet-version-ref",
              "scenario-hash", "pricing-result-hash"),
          List.of(
              new LockStateTransition("OFFER_SELECTED", "READY_FOR_LOCK_REQUEST", "lock.lifecycle.ready." + selectedOfferId,
                  "VISIBLE"),
              new LockStateTransition("READY_FOR_LOCK_REQUEST", "SUBMISSION_PENDING_BACKEND", "lock.lifecycle.submit." + selectedOfferId,
                  "PENDING_CONFIGURED_SERVICE")),
          lifecycleAuditGroups(runId, selectedOfferId));
    }
  }

  static List<LockAuditGroup> lifecycleAuditGroups(String runId, String key) {
    return List.of(
        new LockAuditGroup("lock.confirmation." + key, "Confirmation", List.of("audit:lock-confirmation:" + runId),
            "replay:lock-confirmation:" + key, "export:lock-confirmation:" + runId),
        new LockAuditGroup("lock.extension." + key, "Extension", List.of("audit:lock-extension:" + runId),
            "replay:lock-extension:" + key, "export:lock-extension:" + runId),
        new LockAuditGroup("lock.relock." + key, "Relock and expiration", List.of("audit:lock-relock:" + runId,
            "audit:lock-expiration:" + runId), "replay:lock-relock:" + key, "export:lock-relock:" + runId),
        new LockAuditGroup("lock.sync." + key, "Sync, replay, and export", List.of("audit:lock-sync:" + runId,
            "audit:lock-evidence-export:" + runId), "replay:lock-sync:" + key, "export:lock-lifecycle:" + runId));
  }

  record LockLifecycleCheck(String label, String status, String sourceRef, String remediation) {}

  record LockBlockerView(String code, String message, String remediation) {}

  record LockStateTransition(String fromState, String toState, String eventId, String status) {}

  record LockAuditGroup(String eventId, String label, List<String> evidenceRefs, String replayHash, String exportRef) {}

  record LockConfirmRequest(String selectedOfferId, boolean disclosuresAccepted) {}

  record LockConfirmationResult(String runId, String selectedOfferId, String status, String lockId, String lockStatus,
      String expiresAt, String statusRoute, String message, String uiTraceId, List<String> events, List<String> blockers,
      List<LockAuditGroup> auditGroups) {
    static LockConfirmationResult blocked(String runId, String traceId) {
      return new LockConfirmationResult(runId, null, "BLOCKED", null, null, null, null,
          "Lock confirmation requires a selected offer context.", traceId, List.of("LockBlocked"),
          List.of("Select an offer before confirming lock."), lifecycleAuditGroups(runId, "blocked-confirm"));
    }

    static LockConfirmationResult conflict(String runId, String selectedOfferId, String traceId) {
      return new LockConfirmationResult(runId, selectedOfferId, "CONFLICT", null, null, null, null,
          "Lock conflict returned by BFF fallback: refresh status or choose another offer without losing context.", traceId,
          List.of("LockBlocked"), List.of("A competing lock context exists for the selected offer."),
          lifecycleAuditGroups(runId, "conflict-" + selectedOfferId));
    }

    static LockConfirmationResult confirmed(String runId, String selectedOfferId, String traceId) {
      String lockId = "lock-" + Integer.toUnsignedString((runId + "|" + selectedOfferId).hashCode(), 36);
      return new LockConfirmationResult(runId, selectedOfferId, "CONFIRMED", lockId, "LOCK_REQUEST_RECORDED",
          "Pending configured lock-service response", "/quote/" + runId + "/status",
          "Lock request recorded for selected offer " + selectedOfferId + ".", traceId, List.of("LockSuccess"), List.of(),
          lifecycleAuditGroups(runId, selectedOfferId));
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

  record RateFeedOperationsView(String tenantContext, String dependencyStatus, List<RateFeedWorkflowStep> workflowSteps,
      List<RateFeedGridBlocker> rowBlockers, List<String> sourceReferences, List<String> replayEvidence,
      boolean actionsDisabled, String fallbackReason, String uiTraceId, List<String> events) {}

  record RateFeedWorkflowStep(String stepId, String label, String status, String sourceBoundary, String auditRef,
      String resultHashRef) {}

  record RateFeedGridBlocker(String rowRef, String fieldName, String severity, String blockerCode, String sourceReference,
      String resolutionState) {}

  record PerformanceDashboardView(String tenantContext, String dependencyStatus,
      List<PerformanceSignalGroup> signalGroups, List<PerformanceImpact> impacts, List<String> evidenceLinks,
      List<PerformanceBlocker> blockers, boolean actionsDisabled, String fallbackReason, String uiTraceId,
      List<String> events) {}

  record PerformanceSignalGroup(String serviceName, String tenantContext, String correlationId, String freshness,
      List<PerformanceSignal> signals, List<PerformanceBlocker> blockers) {}

  record PerformanceSignal(String signalId, String label, String freshness, String source, String sourceRef,
      List<String> evidenceRefs) {}

  record PerformanceImpact(String impactCode, String summary, String source, String recoveryOwner, String runbookRef) {}

  record PerformanceBlocker(String code, String owner, String message) {}

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

  record CustomRuleEvidenceView(String tenantContext, String dependencyStatus, String uiTraceId,
      List<CustomFieldMetadata> fields, CalculationEvidence evidence, List<String> commitBlockers,
      boolean commitDisabled, DesignEvidenceStatus designEvidence, List<String> events, String fallbackReason) {}

  record CustomFieldMetadata(String fieldId, String label, String dataType, List<String> allowedValues, String helpText,
      String decisionQuality, String sourceRef, List<String> validationMessages, boolean requiredForRules) {}

  record CalculationEvidence(List<RuleEvidenceRow> matchedRules, List<RuleEvidenceRow> skippedRules,
      List<String> reasonCodes, String precision, String replayHashRef) {}

  record RuleEvidenceRow(String ruleRef, String versionRef, String outcome, String reasonCode, List<String> factRefs) {}

  record DesignEvidenceStatus(String status, String blocker, List<String> safeOptions) {}

  record AuditReplayWorkbenchView(String tenantContext, String dependencyStatus, String uiTraceId,
      List<AuditReplayRecordSummary> records, List<AuditReplayRunSummary> replayRuns,
      AuditReplayExportSummary exportSummary, List<AuditReplayContractRef> contractRefs, List<String> blockers,
      List<String> events, String fallbackReason) {}

  record AuditReplayRecordSummary(String eventId, String subjectType, String subjectId, String action,
      String hashIntegrity, String redactionProfile, String retentionState, boolean legalHold, String exportEligibility,
      List<String> evidenceRefs) {}

  record AuditReplayRunSummary(String runId, String replayType, String subjectId, String status, String originalHash,
      String replayHash, List<String> diffs, List<String> missingDependencyBlockers, List<String> versionRefs) {}

  record AuditReplayExportSummary(String exportId, String status, String redactionProfile, String retentionUntil,
      boolean legalHold, boolean downloadEligible, String manifestHash, List<String> blockers) {}

  record AuditReplayContractRef(String contractId, String route, String preservedDecision) {}

  record TenantPlatformCoverageView(String tenantContext, String dependencyStatus, String uiTraceId,
      TenantContextTrace trace, List<TenantPlatformControl> controls, List<TenantPlatformBlocker> blockers,
      List<String> events, String fallbackReason) {}

  record TenantContextTrace(String tenantIdRef, String correlationIdRef, String idempotencyKeyRef,
      String eventEnvelopeRef, String auditRef, String replayHashRef) {}

  record TenantPlatformControl(String controlId, String label, String status, String guidance,
      List<String> evidenceRefs, List<String> blockers) {}

  record TenantPlatformBlocker(String code, String owner, String message) {}

  record AdminGovernanceView(String tenantContext, String adminRole, String dependencyStatus, String uiTraceId,
      AdminTraceMetadata traceMetadata, List<GovernanceDescriptor> descriptors,
      List<PolicyVersionSummary> policies, List<FeatureFlagSummary> featureFlags,
      List<MarketRuleSummary> marketRules, List<ChangeRequestSummary> changeRequests,
      ReleaseCandidateReadiness releaseCandidate, List<OpenDecisionGate> openDecisions,
      List<DriftAlertSummary> driftAlerts, List<IncidentReviewSummary> incidents,
      List<OverrideLedgerEntry> overrideLedger, PendingConfigReview pendingReview,
      DynamicRuleEvidenceSnapshot dynamicRuleEvidence, List<String> events, String fallbackReason) {}

  record AdminTraceMetadata(String traceId, String artifactId, String policyVersion, String environment,
      String signerMetadata) {}

  record GovernanceDescriptor(String stableId, String label, String type, List<String> allowedOperators,
      List<String> valueSources, String decisionQualityRequirement, List<String> validationMessages, String versionRef) {}

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

  record PendingConfigReview(String reviewId, String state, boolean simulationVisible, boolean approvalVisible,
      boolean publishVisible, boolean rollbackVisible, String auditRef, List<String> downstreamConsumers,
      List<String> blockers) {}

  record DynamicRuleEvidenceSnapshot(List<RuleEvidenceRow> matchedRules, List<RuleEvidenceRow> skippedRules,
      List<String> actionOutputs, List<String> factRefs, String precisionMetadataRef, String replayHashRef) {}

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

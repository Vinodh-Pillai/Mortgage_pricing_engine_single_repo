package com.wcpe.pricingbff.ui;

import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationField;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationValue;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionProductSummary;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassExecutionSummaryResponse;
import com.wcpe.pricingbff.los.LosApiModels.LoanPassProductExecutionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
  private static final String DURABLE_UI_STORE_REQUIRED =
      "Durable UI persistence is not configured; process-local tenant and draft state is disabled.";
  private final PricingBffQuoteServiceLoanPassClient quoteServiceClient;
  private final Map<QuoteRunContextKey, QuoteRunContext> quoteRunContexts = new ConcurrentHashMap<>();

  PricingBffUiFallbackAdapter(PricingBffQuoteServiceLoanPassClient quoteServiceClient) {
    this.quoteServiceClient = quoteServiceClient;
  }

  @GetMapping("/api/ui/health")
  UiHealth health(@RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    return new UiHealth("pricing-workbench", "AVAILABLE", true, "Connected services need setup", correlationId, List.of());
  }

  @GetMapping("/api/v1/ui/menus/{persona}")
  UiMenu menu(@PathVariable String persona) {
    String normalizedPersona = normalizePersona(persona);
    return new UiMenu(normalizedPersona, menuItemsForPersona(normalizedPersona));
  }

  private String normalizePersona(String persona) {
    if (persona == null || persona.isBlank()) {
      return "metadata-unavailable";
    }
    String normalized = persona.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    return switch (normalized) {
      case "lo", "loan-officer" -> "loan-officer";
      case "pricing-analyst" -> "pricing-analyst";
      case "operations", "operations-lead", "ops-lead" -> "operations-lead";
      case "admin", "administrator" -> "admin";
      case "borrower", "consumer" -> "borrower";
      default -> "metadata-unavailable";
    };
  }

  private List<UiMenuItem> menuItemsForPersona(String persona) {
    return switch (persona) {
      case "loan-officer" -> List.of(
          new UiMenuItem("pipeline-intake", "Pipeline Intake", "/pipeline", "main-content"),
          new UiMenuItem("quick-quote", "Quick Quote", "/quote/start", "main-content"),
          new UiMenuItem("draft-scenarios", "Draft Scenarios", "/pipeline?view=drafts", "main-content"),
          new UiMenuItem("lock-workflow", "Lock Workflow", "/quote/run-test/lock", "main-content"));
      case "borrower" -> List.of(
          new UiMenuItem("status", "Workbench status", "/", "main-content"),
          new UiMenuItem("quick-quote", "Quick Quote", "/quote/start", "main-content"));
      case "pricing-analyst" -> List.of(
          new UiMenuItem("product-catalog", "Product Catalog", "/admin/products", "main-content"),
          new UiMenuItem("product-management", "Product Management", "/admin/products/catalog", "main-content"),
          new UiMenuItem("pricing-analysis", "Pricing Analysis", "/pricing/analysis", "main-content"),
          new UiMenuItem("rate-sheet-intake", "Rate Sheet Intake", "/pricing/rate-sheets", "main-content"));
      case "admin" -> List.of(
          new UiMenuItem("product-catalog", "Product Catalog", "/admin/products", "main-content"),
          new UiMenuItem("tenant-management", "Tenant Management", "/admin/tenants", "main-content"),
          new UiMenuItem("user-management", "User Management", "/admin/users", "main-content"),
          new UiMenuItem("feature-flags", "Feature Flags", "/admin/tenants?section=feature-flags", "main-content"));
      case "operations-lead" -> List.of(
          new UiMenuItem("ops-cases", "Ops Cases", "/ops/dashboard", "main-content"),
          new UiMenuItem("rate-feed-ops", "Rate Feed Ops", "/ops/rate-feeds", "main-content"),
          new UiMenuItem("lock-workflow", "Lock Workflow", "/quote/run-test/lock", "main-content"),
          new UiMenuItem("partner-integrations", "Partner Integrations", "/partners/integrations", "main-content"));
      default -> List.of(new UiMenuItem("role-metadata-unavailable", "Role metadata unavailable", "#role-metadata-unavailable", "role-metadata-unavailable"));
    };
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
    if (isBlankText(product, "productId")) {
      blockers.put("productId", "LoanPASS productId is required before a product can be returned to pricing clients.");
    }
    if (isBlankText(product, "mortgageType")) {
      blockers.put("mortgageType", "LoanPASS mortgageType is required before a product can be returned to pricing clients.");
    }
    if (isBlankText(product, "priceGroupId")) {
      blockers.put("priceGroupId", "LoanPASS priceGroupId is required before a product can be returned to pricing clients.");
    }
    if (!blockers.isEmpty()) {
      return ResponseEntity.badRequest().body(new ProductCatalogResult(null, "BLOCKED",
          "Complete the highlighted LoanPASS product fields.", "Provide productId, mortgageType, and priceGroupId.",
          blockers.values().stream().toList(), null, List.of()));
    }

    String productId = normalizedRaw(product.get("productId"));
    LoanPassProduct productContract = new LoanPassProduct(productId, normalizedRaw(product.get("selectedProgramId")),
        normalizedRaw(product.get("priceGroupId")), normalizedRaw(product.get("mortgageType")),
        normalizedRaw(product.get("loanQualificationType")), normalizedRaw(product.get("desiredLoanTerm")),
        normalizedRaw(product.get("desiredAmortizationType")), normalizedRaw(product.get("channelType")));
    return ResponseEntity.status(HttpStatus.CREATED).body(new ProductCatalogResult(productId, "RECORDED",
        "LoanPASS product contract was recorded in local preview mode.",
        "Resolve product authorization and versioned catalog snapshots before production pricing.",
        List.of("Pricing rules, eligibility thresholds, rates, and regulatory values are not inferred.",
            "Product authorization remains blocked until configured catalog mappings are available."),
        productContract,
        List.of("field@desired-mortgage-type", "field@desired-loan-term", "field@desired-amortization-type")));
  }

  PipelineSettingsView pipelineSettings(String tenantId, String uiTraceId) {
    String tenantKey = normalizedTenantKey(tenantId);
    return PipelineSettingsView.unconfigured(tenantKey, normalizeTrace(uiTraceId));
  }

  ResponseEntity<PipelineSettingsView> savePipelineSettings(String tenantId, String uiTraceId,
      PipelineSettingsRequest request) {
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    if (request == null) {
      return ResponseEntity.badRequest().body(PipelineSettingsView.unconfigured(tenantKey, traceId));
    }

    List<PipelineFieldSetting> pipelineFields = safePipelineFields(request.pipelineFields());
    PriceScenarioTableSettings priceScenarioTable = safePriceScenarioTable(request.priceScenarioTable());
    DefaultPricingFilters defaultFilters = safeDefaultPricingFilters(request.defaultPricingFilters());
    LockingFieldSettings lockingFields = safeLockingFields(request.lockingFields());
    PipelineSettingsBindingSummary bindingSummary = new PipelineSettingsBindingSummary(
        pipelineFields.stream().map(PipelineFieldSetting::fieldId).filter(fieldId -> !fieldId.isBlank()).toList(),
        priceScenarioCalculationFieldIds(priceScenarioTable), defaultFilters.fieldIds(), lockingFieldIds(lockingFields));
    List<String> validationMessages = new ArrayList<>();
    if (bindingSummary.pipelineColumnFieldIds().isEmpty()) {
      validationMessages.add("pipelineFields are empty; pipeline grid column binding has no configured field list.");
    }
    if (bindingSummary.priceScenarioCalculationFieldIds().isEmpty()) {
      validationMessages.add("priceScenarioTable has no configured calculation field IDs.");
    }
    if (bindingSummary.defaultFilterFieldIds().isEmpty()) {
      validationMessages.add("defaultPricingFilters has no configured field IDs.");
    }
    if (bindingSummary.lockingFieldIds().isEmpty()) {
      validationMessages.add("lockingFields has no configured lock action field IDs.");
    }
    PipelineSettingsView view = new PipelineSettingsView(tenantKey,
        "BLOCKED", false,
        pipelineFields, priceScenarioTable, defaultFilters, lockingFields, bindingSummary,
        "PIPELINE_SETTINGS_PERSISTENCE_REQUIRED",
        appendPersistenceBlocked(validationMessages), traceId, List.of("PipelineSettingsPersistenceBlocked"),
        DURABLE_UI_STORE_REQUIRED);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(view);
  }

  ClientSettingsView clientSettings(String tenantId, String uiTraceId) {
    String tenantKey = normalizedTenantKey(tenantId);
    return ClientSettingsView.unconfigured(tenantKey, normalizeTrace(uiTraceId));
  }

  ResponseEntity<ClientSettingsView> saveClientSettings(String tenantId, String uiTraceId,
      ClientSettingsRequest request) {
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    if (request == null) {
      return ResponseEntity.badRequest().body(ClientSettingsView.unconfigured(tenantKey, traceId));
    }

    List<ClientSettingsFieldRef> systemFields = safeClientSettingsFields(request.systemFields());
    Map<String, String> fieldValues = safeClientFieldValues(request.clientFieldValues());
    String activeVersion = normalizedRaw(request.activeVersion());
    List<String> validationMessages = clientSettingsValidation(systemFields, fieldValues, activeVersion);
    ClientSettingsView view = new ClientSettingsView(tenantKey,
        "BLOCKED", false, activeVersion,
        systemFields, fieldValues, appendPersistenceBlocked(validationMessages),
        "CLIENT_SETTINGS_PERSISTENCE_REQUIRED",
        traceId, List.of("ClientSettingsPersistenceBlocked"),
        DURABLE_UI_STORE_REQUIRED);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(view);
  }

  ResponseEntity<ClientSettingsView> publishClientSettings(String tenantId, String uiTraceId) {
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    ClientSettingsView existing = ClientSettingsView.unconfigured(tenantKey, traceId);
    List<String> validationMessages = clientSettingsValidation(existing.systemFields(), existing.clientFieldValues(),
        existing.activeVersion());
    if (!validationMessages.isEmpty()) {
      ClientSettingsView blocked = existing.withStatus("BLOCKED", "CLIENT_SETTINGS_PUBLISH_BLOCKED", traceId,
          validationMessages, List.of("ClientSettingsPublishBlocked"));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(blocked);
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(existing.withStatus("BLOCKED",
        "CLIENT_SETTINGS_PERSISTENCE_REQUIRED", traceId, List.of(DURABLE_UI_STORE_REQUIRED),
        List.of("ClientSettingsPersistenceBlocked")));
  }

  NotificationSettingsView notificationSettings(String tenantId, String uiTraceId) {
    String tenantKey = normalizedTenantKey(tenantId);
    return NotificationSettingsView.unconfigured(tenantKey, normalizeTrace(uiTraceId));
  }

  ResponseEntity<NotificationSettingsView> saveNotificationSettings(String tenantId, String uiTraceId,
      NotificationSettingsRequest request) {
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    if (request == null) {
      return ResponseEntity.badRequest().body(NotificationSettingsView.unconfigured(tenantKey, traceId));
    }

    List<ClientSettingsFieldRef> activeFieldLibrary = effectiveActiveFieldLibrary(tenantKey, request.activeFieldLibrary());
    List<PricingNotificationFieldView> notificationFields = safeNotificationFields(request.notificationFields(),
        activeFieldLibrary);
    String activeVersion = normalizedRaw(request.activeVersion());
    List<String> validationMessages = notificationSettingsValidation(activeFieldLibrary, notificationFields,
        activeVersion);
    NotificationSettingsView view = new NotificationSettingsView(tenantKey,
        "BLOCKED", false, activeVersion,
        activeFieldLibrary, notificationFields, appendPersistenceBlocked(validationMessages),
        "PRICING_NOTIFICATION_PERSISTENCE_REQUIRED",
        traceId, List.of("PricingNotificationPersistenceBlocked"),
        DURABLE_UI_STORE_REQUIRED);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(view);
  }

  ResponseEntity<NotificationSettingsView> publishNotificationSettings(String tenantId, String uiTraceId) {
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    NotificationSettingsView existing = NotificationSettingsView.unconfigured(tenantKey, traceId);
    List<String> validationMessages = notificationSettingsValidation(existing.activeFieldLibrary(),
        existing.notificationFields(), existing.activeVersion());
    if (!validationMessages.isEmpty()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(existing.withStatus("BLOCKED", "PRICING_NOTIFICATION_PUBLISH_BLOCKED",
          traceId, validationMessages, List.of("PricingNotificationPublishBlocked")));
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(existing.withStatus("BLOCKED",
        "PRICING_NOTIFICATION_PERSISTENCE_REQUIRED", traceId, List.of(DURABLE_UI_STORE_REQUIRED),
        List.of("PricingNotificationPersistenceBlocked")));
  }

  PricingAccessSettingsView pricingAccessSettings(String tenantId, String userRoleId, String uiTraceId) {
    String tenantKey = normalizedTenantKey(tenantId);
    String roleId = normalizedRaw(userRoleId);
    PricingAccessSettingsView existing = PricingAccessSettingsView.unconfigured(tenantKey, normalizeTrace(uiTraceId));
    return existing.withResolvedRole(roleId.isBlank() ? existing.activeRoleId() : roleId, normalizeTrace(uiTraceId));
  }

  ResponseEntity<PricingAccessSettingsView> savePricingAccessSettings(String tenantId, String uiTraceId,
      String actorUserId, PricingAccessSettingsRequest request) {
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    if (request == null) {
      return ResponseEntity.badRequest().body(PricingAccessSettingsView.unconfigured(tenantKey, traceId));
    }

    List<RolePricingAccessConfig> roles = safeRolePricingAccess(request.roles());
    List<PricingProfileConfig> pricingProfiles = safePricingProfiles(request.pricingProfiles());
    List<FeatureFlagConfig> featureFlags = safeFeatureFlags(request.featureFlags());
    String activeRoleId = normalizedRaw(request.activeRoleId());
    String activePricingProfileId = normalizedRaw(request.activePricingProfileId());
    String activeVersion = normalizedRaw(request.activeVersion());
    List<String> validationMessages = pricingAccessValidation(roles, pricingProfiles, featureFlags, activeRoleId,
        activePricingProfileId, activeVersion);
    List<PricingAccessAuditRecord> auditRecords = pricingAccessAuditRecords(tenantKey, normalizedRaw(actorUserId), roles,
        pricingProfiles, featureFlags, activeRoleId, activePricingProfileId, activeVersion);
    PricingAccessSettingsView view = new PricingAccessSettingsView(tenantKey,
        "BLOCKED", false, activeVersion, activeRoleId,
        activePricingProfileId, roles, pricingProfiles, featureFlags, List.of(), List.of(), null,
        disabledFeatureIds(featureFlags), auditRecords, appendPersistenceBlocked(validationMessages),
        "PRICING_ACCESS_PERSISTENCE_REQUIRED",
        traceId, List.of("PricingAccessPersistenceBlocked"),
        DURABLE_UI_STORE_REQUIRED)
        .withResolvedRole(activeRoleId, traceId);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(view);
  }

  ResponseEntity<?> createDraftScenario(String tenantId, String tenantContext, String uiTraceId,
      DraftScenarioRequest request) {
    ResponseEntity<Map<String, String>> denied = denyCrossTenantDraftAccess(tenantId, tenantContext, uiTraceId);
    if (denied != null) return denied;
    String tenantKey = normalizedTenantKey(tenantId);
    String traceId = normalizeTrace(uiTraceId);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "BLOCKED",
        "message", DURABLE_UI_STORE_REQUIRED,
        "code", "DRAFT_SCENARIO_PERSISTENCE_REQUIRED",
        "tenantContext", tenantKey,
        "uiTraceId", traceId));
  }

  ResponseEntity<?> updateDraftScenario(String tenantId, String scenarioId, String section, String tenantContext,
      String uiTraceId, DraftScenarioRequest request) {
    ResponseEntity<Map<String, String>> denied = denyCrossTenantDraftAccess(tenantId, tenantContext, uiTraceId);
    if (denied != null) return denied;
    String traceId = normalizeTrace(uiTraceId);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "BLOCKED",
        "message", DURABLE_UI_STORE_REQUIRED,
        "code", "DRAFT_SCENARIO_PERSISTENCE_REQUIRED",
        "scenarioId", normalizedRaw(scenarioId),
        "section", normalizedRaw(section),
        "uiTraceId", traceId));
  }

  ResponseEntity<?> getDraftScenario(String tenantId, String scenarioId, String tenantContext, String uiTraceId) {
    ResponseEntity<Map<String, String>> denied = denyCrossTenantDraftAccess(tenantId, tenantContext, uiTraceId);
    if (denied != null) return denied;
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "BLOCKED",
        "message", DURABLE_UI_STORE_REQUIRED,
        "code", "DRAFT_SCENARIO_PERSISTENCE_REQUIRED",
        "scenarioId", normalizedRaw(scenarioId),
        "uiTraceId", normalizeTrace(uiTraceId)));
  }

  ResponseEntity<?> findDraftScenarios(String tenantId, String tenantContext, String uiTraceId,
      String borrowerLastName, String loanNumber, String status) {
    ResponseEntity<Map<String, String>> denied = denyCrossTenantDraftAccess(tenantId, tenantContext, uiTraceId);
    if (denied != null) return denied;
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "BLOCKED",
        "message", DURABLE_UI_STORE_REQUIRED,
        "code", "DRAFT_SCENARIO_PERSISTENCE_REQUIRED",
        "tenantContext", normalizedTenantKey(tenantId),
        "uiTraceId", normalizeTrace(uiTraceId)));
  }

  private ResponseEntity<Map<String, String>> denyCrossTenantDraftAccess(String tenantId, String tenantContext, String uiTraceId) {
    String requested = normalizedTenantKey(tenantId);
    String presented = normalizedTenantKey(tenantContext);
    if (presented.isBlank() || requested.equals(presented)) return null;
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "DENIED", "message", "Draft scenario access denied.", "uiTraceId", normalizeTrace(uiTraceId)));
  }

  private List<String> appendPersistenceBlocked(List<String> validationMessages) {
    List<String> messages = new ArrayList<>(validationMessages == null ? List.of() : validationMessages);
    messages.add(DURABLE_UI_STORE_REQUIRED);
    return List.copyOf(messages);
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

  Map<String, Object> tenantProducts(String tenantId, Integer page, Integer pageSize, String uiTraceId) {
    String tenant = normalizedTenantKey(tenantId);
    List<Map<String, Object>> products = catalogProductRefs();
    return Map.of(
        "tenantContext", tenant,
        "page", page == null ? 1 : page,
        "pageSize", pageSize == null ? products.size() : pageSize,
        "totalElements", products.size(),
        "products", products,
        "availableFilters", catalogAvailableFilters(products),
        "dependencyStatus", "CATALOG_SERVICE_DROPDOWN_CONTRACT_NOT_CONFIGURED",
        "uiTraceId", normalizeTrace(uiTraceId),
        "fallbackReason", "Configured catalog-service product dropdowns are unavailable; pricing-bff returns safe non-pricing option refs so the browser does not probe missing endpoints.");
  }

  Map<String, Object> productCatalogProducts(String tenantId, String uiTraceId) {
    return Map.of(
        "tenantContext", normalizedTenantKey(tenantId),
        "products", catalogProductRefs(),
        "dependencyStatus", "CATALOG_SERVICE_PRODUCTS_CONTRACT_NOT_CONFIGURED",
        "uiTraceId", normalizeTrace(uiTraceId));
  }

  Map<String, Object> productCatalogInvestors(String tenantId, String uiTraceId) {
    return Map.of(
        "tenantContext", normalizedTenantKey(tenantId),
        "investors", catalogInvestorRefs(),
        "dependencyStatus", "CATALOG_SERVICE_INVESTORS_CONTRACT_NOT_CONFIGURED",
        "uiTraceId", normalizeTrace(uiTraceId));
  }

  Map<String, Object> productCatalogChannels(String tenantId, String uiTraceId) {
    return Map.of(
        "tenantContext", normalizedTenantKey(tenantId),
        "channels", catalogChannelRefs(),
        "dependencyStatus", "CATALOG_SERVICE_CHANNELS_CONTRACT_NOT_CONFIGURED",
        "uiTraceId", normalizeTrace(uiTraceId));
  }

  private List<Map<String, Object>> catalogProductRefs() {
    return List.of(
        Map.of("productCode", "catalog-ref-conventional", "productName", "Conventional catalog reference", "productType", "Conventional", "investorCode", "FNMA", "channelCode", "RETAIL", "status", "CONFIG_REQUIRED"),
        Map.of("productCode", "catalog-ref-fha", "productName", "FHA catalog reference", "productType", "FHA", "investorCode", "GNMA", "channelCode", "RETAIL", "status", "CONFIG_REQUIRED"),
        Map.of("productCode", "catalog-ref-jumbo", "productName", "Jumbo catalog reference", "productType", "Jumbo", "investorCode", "OB", "channelCode", "WHOLESALE", "status", "CONFIG_REQUIRED"));
  }

  private List<Map<String, Object>> catalogInvestorRefs() {
    return List.of(
        Map.of("investorCode", "FNMA", "name", "FNMA catalog reference", "status", "CONFIG_REQUIRED"),
        Map.of("investorCode", "GNMA", "name", "GNMA catalog reference", "status", "CONFIG_REQUIRED"),
        Map.of("investorCode", "OB", "name", "OB catalog reference", "status", "CONFIG_REQUIRED"));
  }

  private List<Map<String, Object>> catalogChannelRefs() {
    return List.of(
        Map.of("channelCode", "RETAIL", "name", "Retail catalog reference", "status", "CONFIG_REQUIRED"),
        Map.of("channelCode", "WHOLESALE", "name", "Wholesale catalog reference", "status", "CONFIG_REQUIRED"),
        Map.of("channelCode", "CORRESPONDENT", "name", "Correspondent catalog reference", "status", "CONFIG_REQUIRED"));
  }

  private Map<String, List<String>> catalogAvailableFilters(List<Map<String, Object>> products) {
    return Map.of(
        "productTypes", uniqueStringValues(products, "productType"),
        "investors", uniqueStringValues(products, "investorCode"),
        "channels", uniqueStringValues(products, "channelCode"));
  }

  private List<String> uniqueStringValues(List<Map<String, Object>> records, String key) {
    return records.stream().map(record -> normalizedRaw(record.get(key))).filter(value -> !value.isBlank()).distinct().toList();
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
    quoteRunContexts.put(new QuoteRunContextKey(normalizedTenantKey(tenantId), runId),
        new QuoteRunContext(tenantId, runId, quoteRunCreditApplicationFields(intake)));
    ScenarioIntakeMetadata metadata = scenarioIntakeMetadata(tenantId, traceId);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new QuoteRunLaunch(runId, "CREATED", "/quote/" + runId + "/offers", validation, traceId,
            List.of("UIFlowOpened", "ProgressiveQuickQuoteReviewed", "ScenarioMetadataReviewed", "BorrowerIntakeSubmitted"), false,
            "SCENARIO_QUOTE_CATALOG_CONTRACTS_NOT_CONFIGURED", "audit-package-required-after-scenario-service-create",
            "replay-hash-required-after-scenario-service-create", metadata.validationIssues(), backendFactRefs(intake),
            quoteServiceMissingFacts(intake), metadata.quickQuoteState()));
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/intake-metadata")
  ScenarioIntakeMetadata scenarioIntakeMetadata(@PathVariable String tenantId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = normalizeTrace(uiTraceId);
    List<ScenarioIntakeFieldGroup> fieldGroups = List.of(
        new ScenarioIntakeFieldGroup("scenario-identity", "Scenario identity",
            "Step 1: capture the minimum scenario and channel facts needed before progressive intake expands.",
            List.of(
                metadataField("borrowerLastName", "Borrower last name", "scenario-identity", "text", false,
                    "Optional for pricing launch; required only by save/retrieve scenario workflows when supplied.", "LoanPASS:quoteBorrowerInfo.borrowerLastName", "MAPPED",
                    List.of()),
                metadataField("loanNumber", "Loan number", "scenario-identity", "text", false,
                    "Optional for pricing launch; required only by save/retrieve scenario workflows when supplied.", "LoanPASS:quoteBorrowerInfo.loanNumber", "MAPPED",
                    List.of()),
                metadataField("channel", "Channel", "scenario-identity", "select", true,
                    "Select the LoanPASS channelType supplied by configured metadata.", "LoanPASS:channelType", "UNKNOWN",
                    List.of("Catalog-service channel catalog is required before this can be marked verified.")),
                metadataField("clientId", "Client id", "scenario-identity", "text", false,
                    "Optional LoanPASS client identity when supplied by configured tenant mapping.", "LoanPASS:clientId", "UNKNOWN", List.of()))),
        new ScenarioIntakeFieldGroup("borrower-credit", "Borrower and credit",
            "Step 2: capture borrower and credit fact refs for downstream validation without local pricing decisions.",
            List.of(
                metadataField("numberOfBorrowers", "Number of borrowers", "borrower-credit", "number", false,
                    "Map to quoteBorrowerInfo.numberOfBorrowers when supplied.", "LoanPASS:quoteBorrowerInfo.numberOfBorrowers", "MAPPED", List.of()),
                metadataField("decisionCreditScore", "Decision credit score", "borrower-credit", "number", false,
                    "Map to creditScore and field@decision-credit-score.", "LoanPASS:creditScore", "MAPPED", List.of()),
                metadataField("documentationType", "Documentation type", "borrower-credit", "select", false,
                    "Map to incomeDocumentationType and field@documentation-type.", "LoanPASS:incomeDocumentationType", "UNKNOWN", List.of()))),
        new ScenarioIntakeFieldGroup("loan-structure", "Loan structure",
            "Step 3: capture loan facts that quote-service needs for launch.",
            List.of(
                metadataField("loanPurpose", "Loan purpose", "loan-structure", "select", false,
                    "Purpose fact captured for scenario completeness and quote launch.", "catalog-service:loan-purpose-catalog", "UNKNOWN", List.of()),
                metadataField("baseLoanAmount", "Base loan amount", "loan-structure", "number", false,
                    "Map to requestedLoanAmount and field@base-loan-amount.", "LoanPASS:requestedLoanAmount", "MAPPED", List.of()),
                metadataField("purchasePrice", "Purchase price", "loan-structure", "number", false,
                    "Map to purchasePrice and field@purchase-price.", "LoanPASS:purchasePrice", "MAPPED", List.of()),
                metadataField("appraisedValue", "Appraised value", "loan-structure", "number", false,
                    "Map to propertyValue and field@appraised-value.", "LoanPASS:propertyValue", "MAPPED", List.of()),
                metadataField("downPaymentOrEquity", "Down payment or equity", "loan-structure", "number", false,
                    "Capture down payment or equity as supplied by the user; no ratio is inferred.", "loan-structure metadata", "UNKNOWN", List.of()))),
        new ScenarioIntakeFieldGroup("property", "Property",
            "Step 4: capture property refs for configured downstream validation only.",
            List.of(
                metadataField("quoteAddressDTO.state", "Property state", "property", "select", false,
                    "Map to quoteAddressDTO.state and field@state.", "LoanPASS:quoteAddressDTO.state", "MAPPED", List.of()),
                metadataField("quoteAddressDTO.zip", "Property zip", "property", "text", false,
                    "Map to quoteAddressDTO.zip.", "LoanPASS:quoteAddressDTO.zip", "MAPPED", List.of()),
                metadataField("quoteAddressDTO.countyFips", "County FIPS", "property", "text", false,
                    "Map to quoteAddressDTO.countyFips and field@county when configured.", "LoanPASS:quoteAddressDTO.countyFips", "UNKNOWN", List.of()),
                metadataField("propertyType", "Property type", "property", "select", false,
                    "Property type captured for downstream scenario validation only.", "catalog-service:property-type-catalog", "UNKNOWN", List.of()),
                metadataField("occupancyType", "Occupancy type", "property", "select", false,
                    "Occupancy fact captured for downstream scenario validation only.", "catalog-service:occupancy-catalog", "UNKNOWN", List.of()),
                metadataField("numberOfUnits", "Number of units", "property", "number", false,
                    "Map to numberOfUnits and field@number-of-units.", "LoanPASS:numberOfUnits", "MAPPED", List.of()))),
        new ScenarioIntakeFieldGroup("income-assets", "Income and assets",
            "Step 5: capture optional income and asset facts without deriving capacity or pricing.",
            List.of(
                metadataField("totalMonthlyIncome", "Total monthly income", "income-assets", "number", false,
                    "Map to totalMonthlyIncome and field@total-monthly-income.", "LoanPASS:totalMonthlyIncome", "MAPPED", List.of()),
                metadataField("monthlyDebt", "Monthly debt", "income-assets", "number", false,
                    "Optional monthly debt fact for connected workflow review.", "income-asset metadata", "UNKNOWN", List.of()),
                metadataField("estimatedDti", "Estimated DTI", "income-assets", "number", false,
                    "Map to debtToIncomeRatio and field@estimated-dti.", "LoanPASS:debtToIncomeRatio", "MAPPED", List.of()),
                metadataField("monthsOfReserves", "Months of reserves", "income-assets", "number", false,
                    "Map to monthsOfReserves and field@months-of-reserves.", "LoanPASS:monthsOfReserves", "MAPPED", List.of()))),
        new ScenarioIntakeFieldGroup("loan-product-controls", "Loan Product Controls",
            "Step 6: capture LoanPASS product/pricing controls only.",
            List.of(
                metadataField("mortgageType", "Mortgage type", "loan-product-controls", "select", false,
                    "Map to mortgageType and field@desired-mortgage-type.", "LoanPASS:mortgageType", "UNKNOWN", List.of()),
                metadataField("desiredLoanTerm", "Desired loan term", "loan-product-controls", "select", false,
                    "Map to loanTermType and field@desired-loan-term.", "LoanPASS:loanTermType", "UNKNOWN", List.of()),
                metadataField("desiredAmortizationType", "Desired amortization type", "loan-product-controls", "select", false,
                    "Map to amortizationType and field@desired-amortization-type.", "LoanPASS:amortizationType", "UNKNOWN", List.of()),
                metadataField("desiredRateLockPeriod", "Desired rate lock period", "loan-product-controls", "number", false,
                    "Map to desiredRateLockPeriod; do not send effectiveDate or quote filters.", "LoanPASS:desiredRateLockPeriod", "UNKNOWN", List.of()),
                metadataField("lienPosition", "Lien position", "loan-product-controls", "select", false,
                    "Map to lienPositionType and field@lien-position.", "LoanPASS:lienPositionType", "UNKNOWN", List.of()))));
    List<String> minimalFirstStepFields = fieldGroups.get(0).fields().stream()
        .filter(ScenarioIntakeField::required)
        .map(ScenarioIntakeField::fieldId)
        .toList();
    return new ScenarioIntakeMetadata(tenantId, "PARTIAL", fieldGroups,
        List.of("Disable quote progression when required backend facts are missing.",
            "Surface review references before connected quote decisions.",
            "Keep pricing calculations outside the workbench intake surface.",
            "Keep the first step minimal and reveal backend-mapped sections progressively."),
        List.of(new ScenarioIntakeValidationIssue("SCENARIO_SERVICE_CONTRACT_REQUIRED", "scenarioService", "BLOCKING",
            "Scenario setup, validation guidance, review package, and review reference must be configured before connected quote decisions can change."),
            new ScenarioIntakeValidationIssue("QUOTE_SERVICE_CONTRACT_REQUIRED", "quoteService", "BLOCKING",
                "Quote setup needs configured channel, quoteAddressDTO, product controls, and creditApplicationFields before live quote creation.")),
        "review-package-required-after-scenario-create", "review-reference-required-after-scenario-create",
        "Connected scenario, catalog, eligibility, pricing, and lock contracts are not fully configured; this local response carries non-secret progressive sections and actionable setup blockers only.", traceId,
        new ProgressiveQuickQuoteState(
            minimalFirstStepFields,
            fieldGroups.stream().skip(1).map(ScenarioIntakeFieldGroup::groupId).toList(),
            List.of("channel", "loanPurpose", "baseLoanAmount", "quoteAddressDTO.state",
                "quoteAddressDTO.zip", "decisionCreditScore", "documentationType", "mortgageType", "desiredLoanTerm",
                "desiredAmortizationType", "numberOfUnits"),
            List.of("creditApplicationFields", "quoteAddressDTO", "catalog-enum-variants"),
            List.of("loanpass-enum-mapping", "catalog-service-contract", "lock-period-catalog"),
            "LoanPASS field mapping, catalog enum variants, and lock-period configuration are required for full quote launch.",
            List.of(
                new LosPrefillMapping("borrowerLastName", "LoanPASS", "Borrower last name", "quoteBorrowerInfo.borrowerLastName", "borrowerLastName", "MAPPED", "pending configured LOS adapter", "required_to_save_retrieve", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:borrowerLastName", "eligibility:borrower-context")),
                new LosPrefillMapping("loanNumber", "LoanPASS", "Loan number", "quoteBorrowerInfo.loanNumber", "loanNumber", "MAPPED", "pending configured LOS adapter", "required_to_save_retrieve", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:loanNumber", "pricing:quote-run-identity")),
                new LosPrefillMapping("decisionCreditScore", "LoanPASS", "Credit score", "creditScore", "decisionCreditScore", "MAPPED", "pending configured LOS adapter", "improves_pricing", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:decisionCreditScore", "eligibility:credit")),
                new LosPrefillMapping("baseLoanAmount", "LoanPASS", "Requested loan amount", "requestedLoanAmount", "baseLoanAmount", "MAPPED", "pending configured LOS adapter", "required_to_price", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:baseLoanAmount", "pricing:loan-structure")),
                new LosPrefillMapping("state", "LoanPASS", "Property state", "quoteAddressDTO.state", "state", "MAPPED", "pending configured LOS adapter", "required_to_price", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:state", "eligibility:property")),
                new LosPrefillMapping("zip", "LoanPASS", "Property ZIP", "quoteAddressDTO.zip", "zip", "MAPPED", "pending configured LOS adapter", "required_to_price", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:zip", "eligibility:property")),
                new LosPrefillMapping("estimatedDti", "LoanPASS", "Debt-to-income ratio", "debtToIncomeRatio", "estimatedDti", "MAPPED", "pending configured LOS adapter", "improves_pricing", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:estimatedDti", "pricing:capacity")),
                new LosPrefillMapping("estimatedDSCR", "LoanPASS", "DSCR", "debtServiceCoverageRatio", "estimatedDSCR", "UNKNOWN", "pending configured LOS adapter", "improves_pricing", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:estimatedDSCR", "pricing:capacity")),
                new LosPrefillMapping("mortgageType", "LoanPASS", "Mortgage type", "mortgageType", "mortgageType", "UNKNOWN", "pending configured LOS adapter", "required_before_lock", "tenant:" + tenantId + ";channel:configured-metadata", "tenant quote-runs metadata endpoint", List.of("quote-fact:mortgageType", "lock:product-context")))));
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
                "Pricing-service waterfall review must provide base selection, final price steps, rounding review, and processing records before values can be shown.",
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

  QuoteJourneyMapView quoteJourneyMap(String tenantId, String runId, String uiTraceId) {
    String tenant = tenantId == null || tenantId.isBlank() ? "ui-preview-tenant" : tenantId;
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "journey-s19-local-trace" : uiTraceId;
    JourneyDrilldownRefs refs = new JourneyDrilldownRefs(runId, "scenario-ref-required",
        "quote-option-contract-required", "lock-ref-required", traceId);
    return new QuoteJourneyMapView(tenant, runId, "BLOCKED_WITH_FALLBACK_FACTS",
        "CROSS_SERVICE_CONTRACTS_PARTIAL_OR_UNAVAILABLE",
        List.of(
            new QuoteJourneyNode("scenario-facts", "Scenario facts", "scenario-service", "BLOCKED",
                new JourneyFreshness("STALE_OR_UNKNOWN", "scenario-service.snapshot-required",
                    "Scenario facts are visible only as configured refs until scenario-service snapshot freshness is supplied."),
                List.of("scenario-version-ref-required", "audit-package-required-after-scenario-service-create"),
                List.of("SCENARIO_SERVICE_CONTRACT_REQUIRED"), "replay-hash-required-after-scenario-service-create",
                List.of("catalog-candidates", "eligibility"), "/quote/" + runId + "/status", refs),
            new QuoteJourneyNode("catalog-candidates", "Catalog candidates", "catalog-service", "UNAVAILABLE",
                new JourneyFreshness("UNKNOWN", "catalog-service.catalog-version-required",
                    "Catalog participation is not configured for this run in the local BFF fallback."),
                List.of("product-catalog-version-ref-required", "catalog-snapshot-ref-required"),
                List.of("CATALOG_CONTRACT_UNAVAILABLE"), "catalog-replay-hash-required",
                List.of("rate-grids", "eligibility"), "/admin/products/catalog", refs),
            new QuoteJourneyNode("rate-grids", "Rate grids", "rate-feed-service", "UNAVAILABLE",
                new JourneyFreshness("UNKNOWN", "rate-feed-service.grid-version-required",
                    "Rate grid freshness must come from rate-feed-service; the BFF does not infer market data."),
                List.of("grid-version-ref-required", "rate-feed-audit-ref-required"),
                List.of("RATE_FEED_CONTRACT_UNAVAILABLE"), "rate-feed-replay-hash-required",
                List.of("pricing-waterfall"), "/ops/rate-feeds", refs),
            new QuoteJourneyNode("eligibility", "Eligibility", "eligibility-service", "VISIBLE_WITH_BLOCKERS",
                new JourneyFreshness("FRESHNESS_REQUIRED", "eligibility-service.decision-cache-required",
                    "Eligibility freshness is backend-owned and shown as a cache ref."),
                List.of("eligibility-service:decision-ref-required", "eligibility-evidence-id-required"),
                List.of("UNKNOWN_REQUIRED_FACT", "CONFLICTING_FACT"), "eligibility-replay-hash-required",
                List.of("pricing-waterfall", "quote-ranking"), "/quote/" + runId + "/eligibility", refs),
            new QuoteJourneyNode("pricing-waterfall", "Pricing waterfall", "pricing-service", "BLOCKED",
                new JourneyFreshness("FRESHNESS_REQUIRED", "pricing-service.waterfall-version-required",
                    "Pricing values remain redacted until a configured pricing-service waterfall contract responds."),
                List.of("pricing-service:waterfall-ref-required", "waterfall-evidence-hash-required"),
                List.of("PRICING_SERVICE_CONTRACT_REQUIRED"), "replay-hash-required",
                List.of("quote-ranking", "adjustments", "margin"), "/quote/" + runId + "/pricing-waterfall", refs),
            new QuoteJourneyNode("quote-ranking", "Quote ranking", "quote-service", "VISIBLE_WITH_REFS",
                new JourneyFreshness("FRESHNESS_REQUIRED", "quote-service.ranking-snapshot-required",
                    "Ranking evidence is shown as backend-owned refs without UI-side score calculation."),
                List.of("quote-service.ranking", "snapshot:quote-service:run:" + runId, "audit:quote-ready-required"),
                List.of("QUOTE_SERVICE_SELECTION_POLICY_REQUIRED"), "quote-ranking-replay-hash-required",
                List.of("selection", "lock"), "/quote/" + runId + "/offers", refs),
            new QuoteJourneyNode("selection", "Selection handoff", "pricing-bff", "VISIBLE_WITH_REFS",
                new JourneyFreshness("LOCAL_TRACE", "pricing-bff.selection-handoff", "Selection refs are preserved for downstream lock workflow."),
                List.of("selected-offer:required", "scenario-version:required", "audit:quote-selection-required"),
                List.of("SELECTED_OFFER_REQUIRED"), "selection-replay-hash-required",
                List.of("lock"), "/quote/" + runId + "/offers", refs),
            new QuoteJourneyNode("lock", "Lock", "lock-service", "BLOCKED",
                new JourneyFreshness("FRESHNESS_REQUIRED", "lock-service.freshness-check", "Lock freshness and eligibility remain lock-service owned."),
                List.of("lock-eligibility:pending:quote-option-contract-required", "lock-service.lifecycle-ref-required"),
                List.of("LOCK_SERVICE_CONTRACT_REQUIRED"), "lock-replay-hash-required",
                List.of("exception-compliance", "audit-integration"), "/quote/" + runId + "/lock", refs),
            new QuoteJourneyNode("exception-compliance", "Exception and compliance", "exception-service/compliance-service", "BLOCKED",
                new JourneyFreshness("UNKNOWN", "exception-compliance.review-required", "Governed review contracts are unavailable in local fallback mode."),
                List.of("exception-approval-route-ref-required", "compliance-review-ref-required"),
                List.of("EXCEPTION_SERVICE_CONTRACT_REQUIRED", "COMPLIANCE_SERVICE_CONTRACT_REQUIRED"),
                "exception-compliance-replay-hash-required", List.of("audit-integration"), "/exceptions/concessions", refs),
            new QuoteJourneyNode("audit-integration", "Audit and integration events", "audit-replay-service/integration-service", "BLOCKED",
                new JourneyFreshness("UNKNOWN", "audit-integration.event-envelope-required", "Audit and partner event delivery are represented by refs only."),
                List.of("audit-record-id-required", "event-envelope-ref-required", "partner-delivery-ref-required"),
                List.of("AUDIT_REPLAY_SERVICE_CONTRACT_REQUIRED", "INTEGRATION_SERVICE_CONTRACT_REQUIRED"),
                "audit-integration-replay-hash-required", List.of(), "/audit/replay", refs)),
        List.of("Configured cross-service contracts are partial or unavailable; the journey map shows fallback refs and blocked states instead of hiding gaps."),
        List.of("ScenarioService", "CatalogService", "RateFeedService", "EligibilityService", "PricingService", "QuoteService", "LockService", "ExceptionService", "ComplianceService", "AuditReplayService", "IntegrationService"),
        traceId, List.of("QuoteJourneyMapOpened"),
        "Configured service journey facts are unavailable or partial; pricing-bff exposes non-secret refs, blockers, freshness labels, replay hashes, and safe drilldown routes only.");
  }

  MarginProfitabilityView marginProfitability(String tenantContext, String uiTraceId) {
    String tenant = tenantContext == null || tenantContext.isBlank() ? "ui-preview-tenant" : tenantContext;
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "margin-s16-local-trace" : uiTraceId;
    return new MarginProfitabilityView(tenant, "MARGIN_SERVICE_PROFITABILITY_CONTRACT_NOT_CONFIGURED", false,
        List.of(
            new MarginEvidenceSection("company-margin", "Company", "margin-service company margin policy", "VISIBLE",
                List.of("company-policy-version-ref-required", "company-margin-audit-ref-required"), List.of()),
            new MarginEvidenceSection("channel-margin", "Channel", "margin-service channel margin policy", "VISIBLE",
                List.of("channel-policy-version-ref-required", "channel-margin-audit-ref-required"), List.of()),
            new MarginEvidenceSection("branch-margin", "Branch", "margin-service branch overlay policy", "VISIBLE",
                List.of("branch-overlay-version-ref-required", "branch-scope-audit-ref-required"), List.of()),
            new MarginEvidenceSection("lo-compensation", "LO compensation", "margin-service LO compensation plan", "REDACTED",
                List.of("lo-comp-plan-version-ref-required"),
                List.of(new MarginRedactionEvidence("LO compensation amount", "REDACTED",
                    "pricing.margin.compensation.view_sensitive is required", "audit:lo-compensation-redaction-required"))),
            new MarginEvidenceSection("broker-compensation", "Broker compensation", "margin-service broker compensation plan", "REDACTED",
                List.of("broker-comp-plan-version-ref-required"),
                List.of(new MarginRedactionEvidence("Broker compensation amount", "REDACTED",
                    "pricing.margin.compensation.view_sensitive is required", "audit:broker-compensation-redaction-required"))),
            new MarginEvidenceSection("profitability-floor", "Profitability floor", "margin-service profitability floor policy", "VISIBLE",
                List.of("profitability-floor-version-ref-required", "profitability-threshold-ref-required"), List.of()),
            new MarginEvidenceSection("approval-governance", "Approval", "margin-service approval governance", "VISIBLE",
                List.of("approval-audit-ref-required", "separation-of-duty-ref-required"), List.of()),
            new MarginEvidenceSection("replay", "Replay", "margin-service replay hash boundary", "VISIBLE",
                List.of("margin-replay-hash-required", "margin-version-graph-hash-required"), List.of())),
        new MarginFloorEvidence("quote-option-contract-required", "BLOCKED", "PROFITABILITY_FLOOR_BREACH",
            "profitability-floor-version-ref-required", "profitability-threshold-ref-required",
            "profitability-exception-route-ref-required", List.of("profitability-floor-audit-ref-required"),
            "Display backend floor evidence and exception path only; profitability floors remain backend-owned."),
        List.of("company-policy-version-ref-required", "channel-policy-version-ref-required",
            "branch-overlay-version-ref-required", "lo-comp-plan-version-ref-required", "broker-comp-plan-version-ref-required",
            "profitability-floor-version-ref-required"),
        List.of("audit:margin-profitability-required", "audit:compensation-redaction-required"),
        "margin-profitability-replay-hash-required", traceId, List.of("MarginProfitabilityModuleOpened"),
        "Configured margin-service evidence contracts are unavailable; pricing-bff exposes non-secret section refs, floor blockers, redaction reasons, audit refs, and replay hashes only.");
  }

  AdjustmentEvidenceView adjustmentEvidence(String tenantContext, String uiTraceId) {
    String tenant = tenantContext == null || tenantContext.isBlank() ? "ui-preview-tenant" : tenantContext;
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "adjustment-s17-local-trace" : uiTraceId;
    return new AdjustmentEvidenceView(tenant, "ADJUSTMENT_SERVICE_CONFIG_PARTIAL", "BLOCKED", List.of(
        new AdjustmentEvidenceRow("adjustment-llpa-contract-required", "LLPA evaluator evidence", "LLPA", "VISIBLE",
            List.of("fact:representative-credit", "fact:loan-to-value"), "adjustment-service.llpa-evaluator",
            "llpa-rulebook-version-ref-required", "Backend-owned LLPA evaluator refs are visible; values remain unavailable until configured adjustment-service contracts respond.",
            List.of(), List.of("conflict-manual-review-required")),
        new AdjustmentEvidenceRow("adjustment-fee-catalog-contract-required", "Fee calculation evidence", "FEE", "BLOCKED",
            List.of("fact:quote-option", "fact:fee-catalog-version"), "adjustment-service.fee-calculation",
            "fee-catalog-version-ref-required", "Fee amounts are blocked because configured fee catalog values are not available at this BFF boundary.",
            List.of(), List.of()),
        new AdjustmentEvidenceRow("adjustment-compensation-hook-contract-required", "Compensation hook evidence", "COMPENSATION", "VISIBLE",
            List.of("fact:channel", "fact:compensation-plan-ref"), "adjustment-service.compensation-hooks",
            "compensation-hook-version-ref-required", "Compensation hook source and audit references are visible without exposing compensation amounts.",
            List.of("lo-compensation-hook-ref-required", "broker-compensation-hook-ref-required"), List.of())) ,
        List.of(new AdjustmentConflictView("conflict-manual-review-required", "BLOCKING", "REQUIRE_MANUAL_REVIEW",
            "Configured conflict policy reports overlapping adjustment evidence and requires manual review before values can be used.",
            "Pricing Operations", List.of("adjustment-llpa-contract-required", "adjustment-fee-catalog-contract-required"))),
        List.of(new AdjustmentBlockedState("ADJUSTMENT_CONFIG_MISSING", "Adjustment-service configuration is incomplete; UI must not infer LLPA, fee, or compensation values.",
            "adjustment-service.configuration", "Pricing Operations")),
        List.of(
            new AdjustmentSummaryCard("LLPA", "LLPA evaluator evidence is represented by backend-owned adjustment ids, fact refs, rulebook refs, and conflict refs.",
                List.of("llpa-rulebook-version-ref-required", "audit:adjustment-llpa-required")),
            new AdjustmentSummaryCard("FEE", "Fee calculation evidence is blocked until a configured fee catalog response supplies authoritative values.",
                List.of("fee-catalog-version-ref-required", "audit:fee-calculation-required")),
            new AdjustmentSummaryCard("COMPENSATION", "Compensation hook refs are shown as source evidence only; sensitive amounts remain backend-owned.",
                List.of("compensation-hook-version-ref-required", "audit:compensation-hook-required"))),
        List.of("llpa-rulebook-version-ref-required", "fee-catalog-version-ref-required", "compensation-hook-version-ref-required"),
        List.of("audit:adjustment-evidence-required", "audit:adjustment-conflict-required"),
        "adjustment-evidence-replay-hash-required", traceId, List.of("AdjustmentEvidenceModuleOpened"),
        "Configured adjustment-service contracts are partial; pricing-bff exposes ids, fact refs, sources, conflicts, compensation hooks, summaries, blockers, audit refs, and replay refs only.");
  }

  ExceptionConcessionWorkbenchView exceptionConcessionWorkbench(String tenantContext, String uiTraceId) {
    String tenant = tenantContext == null || tenantContext.isBlank() ? "ui-preview-tenant" : tenantContext;
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "exception-s18-local-trace" : uiTraceId;
    return new ExceptionConcessionWorkbenchView(tenant, "EXCEPTION_SERVICE_CONCESSION_WORKBENCH_CONTRACT_NOT_CONFIGURED",
        "GOVERNED_REVIEW", List.of(
            new ExceptionConcessionSection("concession-request", "Concession request", "VISIBLE",
                List.of("exception-service.concession-request", "pricing-service.quote-ref"),
                List.of("audit:concession-request-required"),
                "Concession request state, reason refs, route hash, request hash, and policy refs are visible without pricing calculations."),
            new ExceptionConcessionSection("eligibility-exception", "Eligibility exception", "VISIBLE",
                List.of("exception-service.eligibility-exception", "eligibility-service.finding-ref"),
                List.of("audit:eligibility-exception-required"),
                "Eligibility exception finding refs, original result hash, exceptionable state, and related concession refs are shown as backend-owned evidence."),
            new ExceptionConcessionSection("authority-matrix", "Authority matrix", "VISIBLE",
                List.of("exception-service.authority-matrix", "governance-service.role-scope"),
                List.of("audit:authority-matrix-required"),
                "Approval route, matrix version, validation hash, conflict attestation, and separation-of-duties evidence are displayed from configured refs."),
            new ExceptionConcessionSection("manual-price-mutation-guard", "Manual price mutation guard", "BLOCKED",
                List.of("exception-service.manual-price-edit-guard", "pricing-service.ledger-hash"),
                List.of("audit:manual-price-edit-blocked"),
                "Manual price mutation commit is disabled while backend guard returns denial reason codes and escalation path."),
            new ExceptionConcessionSection("risk-events", "Monitoring and risk events", "VISIBLE",
                List.of("exception-service.risk-monitoring-events", "observability-service.alert-ref"),
                List.of("audit:risk-monitoring-required"),
                "Risk events, alert severity, redaction state, and replay flags are visible as non-PII refs."),
            new ExceptionConcessionSection("history-replay-export", "History, replay, and export", "VISIBLE",
                List.of("exception-service.exception-history", "audit-replay-service.replay-package"),
                List.of("audit:exception-history-required", "audit:exception-export-required"),
                "History timeline, replay hash, export manifest, retention, and redaction refs are grouped for governed review.")),
        new ManualPriceMutationGuardView(true, "BLOCKED",
            List.of("MANUAL_PRICE_EDIT_FORBIDDEN", "LEDGER_HASH_REQUIRED"),
            "exception-approval-escalation-path-required", "audit:manual-price-edit-blocked",
            "exception-concession-replay-hash-required"),
        List.of("quote-service.quote-ref", "pricing-service.ledger-ref", "margin-service.margin-ref",
            "adjustment-service.adjustment-ref", "lock-service.lock-ref", "compliance-service.review-ref"),
        List.of("authority-matrix-version-ref-required", "concession-policy-version-ref-required", "price-guard-policy-version-ref-required"),
        List.of("audit:exception-workbench-opened", "audit:manual-price-edit-blocked", "audit:exception-export-required"),
        List.of("Configured exception-service live integration is unavailable in local mode; actions stay disabled until backend guard and export contracts are wired."),
        "exception-concession-replay-hash-required", "exception-history-export-manifest-required", traceId,
        List.of("ExceptionConcessionWorkbenchOpened", "ManualPriceMutationGuardRendered"),
        "Configured exception-service contracts are unavailable; pricing-bff exposes backend-owned refs, blocker states, audit refs, replay hashes, and export refs only.");
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers")
  OfferComparisonView offerComparison(@PathVariable String tenantId, @PathVariable String runId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = normalizeTrace(uiTraceId);
    try {
      LoanPassExecutionSummaryResponse response = quoteServiceClient.executeSummary(tenantId, runId, traceId,
          quoteRunContextFields(tenantId, runId));
      return OfferComparisonView.fromLoanPassSummary(runId, traceId, response);
    } catch (PricingBffQuoteServiceLoanPassClient.QuoteServiceUnavailableException ex) {
      return OfferComparisonView.upstreamMissing(runId, traceId, ex.getMessage());
    }
  }

  @GetMapping("/api/v1/tenants/{tenantId}/quote-runs/{runId}/offers/{offerId}/explain")
  OfferExplanationView offerExplanation(@PathVariable String runId, @PathVariable String offerId,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return OfferExplanationView.available(runId, offerId, normalizeTrace(uiTraceId));
  }

  QuoteDetailView quoteDetail(String tenantId, String runId, String offerId, String uiTraceId) {
    String optionId = offerId == null || offerId.isBlank() ? "quote-option-contract-required" : offerId;
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "qd-s23-local-trace" : uiTraceId;
    try {
      LoanPassProductExecutionResult result = quoteServiceClient.executeProduct(tenantId, runId, optionId, traceId,
          quoteRunContextFields(tenantId, runId));
      return QuoteDetailView.fromLoanPassProduct(tenantId, runId, optionId, traceId, result);
    } catch (PricingBffQuoteServiceLoanPassClient.QuoteServiceUnavailableException ex) {
      return QuoteDetailView.blocked(tenantId, runId, optionId, traceId, ex.getMessage());
    }
  }

  private QuoteDetailView quoteDetailFallback(String tenantId, String runId, String optionId, String traceId) {
    OfferSummary summary = OfferComparisonView.contractVisible(runId, traceId).offers().stream()
        .filter(offer -> offer.offerId().equals(optionId))
        .findFirst()
        .orElse(new OfferSummary(optionId, 1, "Backend-ranked offer", "Backend-ranked offer", null,
            null, null, "payment-ref-required", "apr-ref-required", "score:backend-owned", "rank-score-ref-required",
            null, "Backend-owned refs", List.of("quote-service.option:" + optionId),
            List.of("Rank and score must come from quote-service ranking response"),
            List.of("DETAIL_EVIDENCE_REQUIRED"), "AVAILABLE", "scenario-ref-required", 7,
            List.of("quote-service.option:" + optionId, "pricing-service:waterfall-ref-required"),
            List.of("lock-eligibility:pending:" + optionId), List.of("snapshot:quote-service:run:" + runId),
            List.of("audit:quote-ready-required", "replay-hash-required"),
            List.of("summary", "ranking", "waterfall", "compliance", "audit-replay"),
            List.of(), List.of(), List.of(), List.of()));
    PricingWaterfallView waterfall = pricingWaterfall(tenantId, runId, traceId);
    return new QuoteDetailView(tenantId, runId, optionId, "DETAIL_VISIBLE_WITH_BACKEND_REFS", summary,
        OfferExplanationView.available(runId, optionId, traceId), waterfall,
        List.of(
            new QuoteDetailPanel("summary", "Card summary", "VISIBLE", List.of("product id", "investor id", "channel", "lock period"),
                List.of("quote-service.option:" + optionId, "catalog-service.product-ref-required"), List.of()),
            new QuoteDetailPanel("ranking", "Ranking and tie breakers", "VISIBLE", List.of("rank", "criterion scores", "tie breakers", "warnings"),
                List.of("quote-service.ranking", "quote-service.explanation"), List.of("tie-breaker-evidence-required")),
            new QuoteDetailPanel("waterfall", "Pricing waterfall", "REDACTED", List.of("note rate", "final price bps", "adjustments", "margins", "rounding review references"),
                List.of("pricing-service.waterfall", "adjustment-service.evidence", "margin-service.evidence"), List.of("restricted pricing values require backend permission")),
            new QuoteDetailPanel("compliance", "Compliance flags", "VISIBLE_WITH_BLOCKERS", List.of("compliance flags", "hidden fields", "unavailable reasons"),
                List.of("compliance-service.review-ref-required"), List.of("configured compliance evidence required")),
            new QuoteDetailPanel("audit-replay", "Review and processing records", "VISIBLE", List.of("review references", "processing records", "version references"),
                List.of("audit-replay-service.package-ref-required", "quote-service.snapshot"), List.of())),
        List.of(
            new QuoteDetailRedaction("selectedNoteRate", "REDACTED", "pricing.waterfall.restricted.read permission is required for selected note rate", "audit:note-rate-redaction-required"),
            new QuoteDetailRedaction("finalPriceBps", "REDACTED", "pricing.waterfall.restricted.read permission is required for final price", "audit:final-price-redaction-required"),
            new QuoteDetailRedaction("hiddenFields", "UNAVAILABLE", "Configured compliance-service hidden-field evidence is required before display", "audit:hidden-field-reason-required")),
        List.of("compliance-review-ref-required", "fair-lending-flag-ref-required", "privacy-redaction-ref-required"),
        List.of("audit:quote-detail-opened", "audit:waterfall-redactions-required"),
        "quote-detail-replay-hash-required", "quote-detail-evidence-hash-required", traceId,
        List.of("QuoteDetailOpened", "QuoteDetailBackendRefsBound"),
        "Configured quote, pricing, compliance, and review record setup is unavailable; pricing-bff exposes non-secret refs, redaction reasons, blockers, and processing records only.");
  }

  private static PricingWaterfallView blockedWaterfall(String tenantId, String runId, String traceId, String reason) {
    RedactedWaterfallValue unavailable = new RedactedWaterfallValue(null, true, reason);
    return new PricingWaterfallView(tenantId, runId, "BLOCKED", false, "QUOTE_SERVICE_CLIENT_REQUIRED",
        new WaterfallBaseSelection("quote-service-product-detail-unavailable", "quote-service.execute-product", unavailable,
            unavailable, List.of("quote-service execute-product required")),
        new WaterfallFinalPrice("quote-service-product-detail-unavailable", unavailable, List.of(), List.of(), List.of()),
        List.of(new WaterfallBlocker("QUOTE_SERVICE_CLIENT_REQUIRED", reason, "quote-service.execute-product")),
        List.of(), List.of("audit:quote-service-product-detail-blocked"), "replay-unavailable", "version-graph-unavailable",
        "result-unavailable", "evidence-unavailable", traceId, List.of("QuoteServiceWaterfallFailClosed"), reason);
  }

  private static PricingWaterfallView waterfallFromLoanPassProduct(String tenantId, String runId, String traceId,
      LoanPassProductExecutionResult product) {
    List<WaterfallLedgerRow> rows = new ArrayList<>();
    int ordinal = 1;
    for (CreditApplicationField field : product.calculatedFields()) {
      String fieldId = safeText(field.fieldId(), "calculated-field-" + ordinal);
      String value = fieldValueText(field);
      rows.add(new WaterfallLedgerRow(ordinal++, fieldId, new RedactedWaterfallValue(value, false, null),
          "QUOTE_SERVICE_CALCULATED_FIELD", new RedactedWaterfallValue(value, false, null),
          "quote-service.calculatedFields." + fieldId, fieldId, null));
    }
    RedactedWaterfallValue noteRate = new RedactedWaterfallValue(fieldValue(product.calculatedFields(), List.of("rate", "noteRate")), false, null);
    RedactedWaterfallValue price = new RedactedWaterfallValue(fieldValue(product.calculatedFields(), List.of("price")), false, null);
    return new PricingWaterfallView(tenantId, runId, "QUOTE_SERVICE_LOANPASS_PRODUCT_VISIBLE", true,
        statusText(product.metadata(), "source", "quote-service.execute-product"),
        new WaterfallBaseSelection("quote-service-product:" + product.productId(),
            statusText(product.metadata(), "versionNumber", safeText(product.versionNumber(), "version-unavailable")), noteRate, price,
            rows.stream().map(WaterfallLedgerRow::step).toList()),
        new WaterfallFinalPrice("quote-service-product:" + product.productId(), price, rows,
            fieldRefs(product.calculatedFields(), List.of("adjustment")), fieldRefs(product.calculatedFields(), List.of("round"))),
        List.of(), metadataStrings(product.metadata(), "metadataRefs"), List.of("audit:quote-service-product:" + product.productId()),
        "replay:quote-service-product:" + product.productId(), "version-graph:quote-service-product:" + product.productId(),
        "result:quote-service-product:" + product.productId(), "evidence:quote-service-product:" + product.productId(),
        traceId, List.of("QuoteServiceProductWaterfallBound"), "");
  }

  private static List<String> fieldRefs(List<CreditApplicationField> fields, List<String> keywords) {
    return fields.stream()
        .map(field -> safeText(field.fieldId(), ""))
        .filter(fieldId -> keywords.stream().anyMatch(keyword -> fieldId.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))))
        .toList();
  }

  private static int loanHouseOfferSortBucket(LoanPassExecutionProductSummary product) {
    if (product == null) return 3;
    String status = statusText(product.status(), "type", "").toLowerCase(Locale.ROOT);
    boolean hasVisibleTerms = fieldValue(product.calculatedFields(), List.of("note-rate", "quote-service-price", "quote-service-payment", "lock-days")) != null;
    if (hasVisibleTerms || "approved".equals(status) || "available".equals(status)) return 0;
    if (sourceEvidenceRefs(product.productFields(), product.versionNumber()).stream().anyMatch(ref -> ref.toLowerCase(Locale.ROOT).contains("loanhouse"))) return 1;
    return 2;
  }

  private static List<String> sourceEvidenceRefs(List<CreditApplicationField> fields, String versionNumber) {
    List<String> refs = new ArrayList<>();
    if (versionNumber != null && !versionNumber.isBlank()) refs.add("schemaVersion:" + versionNumber);
    for (CreditApplicationField field : fields == null ? List.<CreditApplicationField>of() : fields) {
      if (!safeText(field.fieldId(), "").toLowerCase(Locale.ROOT).contains("source-refs")) continue;
      Object value = field.value() == null ? null : field.value().value();
      if (value instanceof Map<?, ?> map) {
        addMapRef(refs, map, "sourceSystem");
        addMapRef(refs, map, "source_url");
        addMapRef(refs, map, "source_index");
        addMapRef(refs, map, "product_id");
        addMapRef(refs, map, "product_name");
        addMapRef(refs, map, "investor_name");
        addMapRef(refs, map, "lock_term_days");
      } else if (value != null && !String.valueOf(value).isBlank()) {
        refs.add("sourceRefs:" + value);
      }
    }
    return refs.stream().distinct().toList();
  }

  private static void addMapRef(List<String> refs, Map<?, ?> map, String key) {
    Object value = map.get(key);
    if (value != null && !String.valueOf(value).isBlank()) refs.add(key + ":" + value);
  }

  private static String sourceEvidenceLabel(List<String> refs, String versionNumber) {
    String joined = String.join(" ", refs == null ? List.of() : refs).toLowerCase(Locale.ROOT);
    if (joined.contains("loanhouse") || safeText(versionNumber, "").toLowerCase(Locale.ROOT).contains("loanhouse")) {
      return "LoanHouse capture";
    }
    return refs == null || refs.isEmpty() ? "Quote-service LoanPass" : "Quote-service capture";
  }

  private static List<String> mergeRefs(List<String> first, List<String> second) {
    List<String> merged = new ArrayList<>();
    if (first != null) merged.addAll(first);
    if (second != null) merged.addAll(second);
    return merged.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
  }

  private static String fieldValue(List<CreditApplicationField> fields, List<String> keywords) {
    return fields.stream()
        .filter(field -> keywords.stream().anyMatch(keyword -> safeText(field.fieldId(), "").toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))))
        .map(PricingBffUiFallbackAdapter::fieldValueText)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse(null);
  }

  private static String fieldValueText(CreditApplicationField field) {
    if (field == null || field.value() == null || field.value().value() == null) return "";
    return String.valueOf(field.value().value());
  }

  private static List<String> metadataStrings(Map<String, Object> metadata, String key) {
    Object value = metadata == null ? null : metadata.get(key);
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList();
    }
    if (value instanceof Iterable<?> iterable) {
      List<String> values = new ArrayList<>();
      for (Object item : iterable) values.add(String.valueOf(item));
      return values;
    }
    return value == null ? List.of() : List.of(String.valueOf(value));
  }

  private static List<String> statusMessages(Map<String, Object> status) {
    List<String> errors = metadataStrings(status, "errors");
    return errors.isEmpty() ? List.of("Quote-service supplied LoanPass product status.") : errors;
  }

  private static List<String> statusFlags(Map<String, Object> status) {
    String type = statusText(status, "type", "UNKNOWN");
    return type.isBlank() ? List.of() : List.of(type.toUpperCase(Locale.ROOT));
  }

  private static String statusText(Map<String, Object> metadata, String key, String fallback) {
    Object value = metadata == null ? null : metadata.get(key);
    return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
  }

  private static String safeText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
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
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(LockConfirmationResult.failClosed(runId, request.selectedOfferId(), traceId));
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
    return new ComplianceEvidenceRegistryView(normalizeTenant(tenantContext), "Configuration details need setup",
        complianceEvidenceFallbacks(), complianceDecisionFallbacks(), complianceAdvisoryReviewFallbacks(),
        fairLendingMonitoringFallbacks(), privacyRequestFallbacks(), securityEventFallbacks(),
        complianceAlertFallbacks(), retentionControlFallbacks(), complianceConfigurationGaps(), normalizeComplianceTrace(uiTraceId),
        List.of("ComplianceEvidenceRegistryOpened"),
        "Configured compliance, audit-replay, fair-lending, security, privacy, and retention service contracts are unavailable; this response carries non-secret UI fallback records only.");
  }

  @GetMapping("/api/v1/partners/{partnerId}/integrations/webhooks")
  PartnerWebhookHealthView partnerWebhookHealth(@PathVariable String partnerId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return new PartnerWebhookHealthView(partnerId, normalizeTenant(tenantContext), "RETRY_HEALTH_VISIBLE",
        "latest 30 events", "Exception queue size requires configured integration-service metrics", "Configured retry window required",
        partnerWebhookAttempts(), partnerSafetyToggles(),
        new PartnerWebhookAction(true,
            "Replay requires request correlation and explicit idempotency confirmation before it can be recorded.",
            "Confirm correlation id and idempotency before replay.", "/partners/support/webhooks"),
        new PartnerWebhookAction(false,
            "Endpoint test requires the configured partner webhook transport contract.",
            "Confirm endpoint ownership before testing.", "/partners/support/webhooks"),
        normalizePartnerTrace(uiTraceId), List.of("WebhookHealthChecked"));
  }

  @GetMapping("/api/v1/partners/{partnerId}/integrations/workbench")
  PartnerChannelWorkbenchView partnerChannelWorkbench(@PathVariable String partnerId,
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    return new PartnerChannelWorkbenchView(partnerId, normalizeTenant(tenantContext),
        "INTEGRATION_SERVICE_CHANNEL_CONTRACT_NOT_CONFIGURED", partnerChannelWorkbenchTabs(),
        new PartnerServiceAccountBlockedState(true, "integration-service.partner-channel.workbench.read",
            "integration-platform-owner", "credentials-not-rendered"),
        "Configured integration-service partner channel state is unavailable; pricing-bff exposes non-secret fallback modules, retry state, exception queue reasons, redaction state, and audit references only.",
        normalizePartnerTrace(uiTraceId), List.of("PartnerIntegrationWorkbenchOpened"));
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
    return new QualityDashboardView(normalizeTenant(tenantContext), "Configuration details need setup",
        new QualityValidationRun("validation-run-config-required", "BLOCKED", "RED",
            "Keep package closure paused and route unresolved items for rework until configured review records are linked.",
            List.of(
                new QualityValidationStage("V1", "Preflight", "PASS", "timestamp supplied by configured validator"),
                new QualityValidationStage("V2", "Setup validation", "FAIL", "connected service setup review required"),
                new QualityValidationStage("V3", "Execution Validation", "PENDING", "execution validator unavailable"),
                new QualityValidationStage("V4", "End-to-End Consistency", "PENDING", "pipeline evidence required"),
                new QualityValidationStage("V5", "Closure Validation", "BLOCKED", "attention item review required")),
            List.of(
                new QualityBlocker("setup-review-item", "P1", "workflow", "Release engineering", "OPEN",
                    "Configured setup review record is missing."),
                new QualityBlocker("review-package-completeness", "P2", "data", "Quality operations", "OPEN",
                    "Review package completeness must be supplied by configured quality services.")),
            List.of("validation summary", "review trail", "module review index", "attention item register")),
        new QualityReadinessStatus("fail", true,
            List.of("P1 setup review item is open", "Review package completeness needs attention"),
            List.of("Quality owner review required", "Release owner review required"),
            List.of("Readiness check needs configured result", "Schema review needs attention", "Configuration review pending",
                "Policy owner review required"),
            "missing required review records"),
        new QualityDriftSummary("pricing-quality", "configured analysis window required", "configured baseline required",
            List.of("product set supplied by quality API"), "stale",
            "Comparison controls are locked until baseline and sample-window evidence are supplied.",
            List.of(new QualityDriftMetric("setup review failure rate", "P2", "deviation value supplied by configured metrics"),
                new QualityDriftMetric("validation_rework_queue", "P3", "trend supplied by configured metrics"))),
        new QualityFairnessSummary(qualityDimensions(privileged), !privileged, "sample counts supplied by fairness API",
            "P1", "Risk and compliance owner", List.of("fairness-evidence-package-required")),
        List.of(
            new QualityIncident("quality-incident-setup", "P1", "Release engineering", "setup", "playbook-required",
                "mitigating", "review-package-required", List.of("pricing-workbench", "governance-service")),
            new QualityIncident("quality-incident-change", "P2", "Quality operations", "change", "playbook-required",
                "acknowledged", "review-package-required", List.of("observability-service"))),
        new QualityReplaySummary("policySnapshotId-required", "inputBundleRef-required", "deterministicSeed-required",
            false, "Replay is blocked until configured snapshot, seed, and event payload evidence are supplied.",
            List.of("regression replay", "deterministic quote replay", "webhook/event replay")),
        List.of(
            new QualityContractConformance("pricing-workbench quality", "FAIL", "schema review record required",
                List.of("quality dashboard setup review is pending configured service confirmation")),
            new QualityContractConformance("partner event review", "PENDING", "event envelope review required",
                List.of("webhook review requires integration service setup"))),
        new QualityEvidenceExport("quality-evidence-package-required", "INCOMPLETE", true,
            List.of("validation summary", "review trail", "module review index", "attention item register"),
            List.of("Review package remains incomplete until configured review storage is available")),
        traceId, List.of("QualityDashboardOpened"),
        "Configured quality analytics, change, fairness, review, and setup services are unavailable; this response carries non-secret UI fallback records only.");
  }

  @GetMapping("/api/v1/quality/evidence/export")
  QualityEvidenceExport qualityEvidenceExport() {
    return new QualityEvidenceExport("quality-evidence-package-required", "INCOMPLETE", true,
        List.of("validation summary", "review trail", "module review index", "attention item register"),
        List.of("Export is redacted and incomplete until configured quality evidence storage is available."));
  }

  @GetMapping("/api/v1/custom-rules/evidence")
  CustomRuleEvidenceView customRuleEvidence(
      @RequestHeader(value = "X-Tenant-Context", required = false) String tenantContext,
      @RequestHeader(value = "X-Ui-Trace-Id", required = false) String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "cr-s01-local-trace" : uiTraceId;
    return new CustomRuleEvidenceView(normalizeTenant(tenantContext), "Configuration details need setup", traceId,
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

  CustomRuleFieldsUiDto customRuleFieldsForUi(String scenarioId, String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "cr-s05-local-trace" : uiTraceId;
    String scenarioRef = scenarioId == null || scenarioId.isBlank() ? "scenario-ref-required" : scenarioId;
    List<String> validationMessages = List.of(
        "BACKEND_CONTRACT_UNAVAILABLE: configured field metadata contract is not wired to pricing-bff.",
        "METADATA_VERSION_UNAVAILABLE: field descriptors are fallback refs only until governance-service returns versions.");
    return new CustomRuleFieldsUiDto(scenarioRef, normalizeTenant(tenantContext),
        "BACKEND_CONTRACT_UNAVAILABLE", "BLOCKED_FALLBACK", traceId,
        List.of(
            new CustomRuleFieldUiDto("custom-field-evidence-source", "Evidence source", "text", "governance-service",
                "metadata-version-ref-required", "UNKNOWN", true,
                List.of("backend metadata required"), List.of("configured-governance-metadata")),
            new CustomRuleFieldUiDto("custom-field-decision-quality", "Decision quality", "enumeration",
                "typed-fact-contract", "typed-fact-version-ref-required", "CONFLICTING", true,
                List.of("VERIFIED", "UNKNOWN", "CONFLICTING"), List.of("typed-fact-contract")),
            new CustomRuleFieldUiDto("custom-field-review-note", "Review note", "text", "ui-metadata-contract",
                "ui-metadata-version-ref-required", "VERIFIED", false,
                List.of("free text from configured metadata"), List.of("ui-metadata-contract"))),
        List.of("VERIFIED", "UNKNOWN", "CONFLICTING"), validationMessages,
        List.of("metadata-version-ref-required", "typed-fact-version-ref-required", "ui-metadata-version-ref-required"),
        List.of(new CustomRuleUiError("BACKEND_CONTRACT_UNAVAILABLE", "governance-service",
            "Configured custom-rule field metadata contract is unavailable; UI must show blocked fallback state."),
            new CustomRuleUiError("METADATA_VERSION_UNAVAILABLE", "governance-service",
                "Metadata version refs are placeholders until backend returns approved refs.")),
        List.of("CustomRuleFieldsFallbackVisible"));
  }

  CustomRuleEvidenceUiDto customRuleEvidenceForUi(String quoteId, String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "cr-s05-local-trace" : uiTraceId;
    String quoteRef = quoteId == null || quoteId.isBlank() ? "quote-ref-required" : quoteId;
    List<String> reasonCodes = List.of("RULE_EVIDENCE_VISIBLE", "REQUIRED_FACT_UNKNOWN",
        "REQUIRED_FACT_CONFLICTING", "RULE_EVIDENCE_BLOCKED");
    return new CustomRuleEvidenceUiDto(quoteRef, normalizeTenant(tenantContext),
        "BACKEND_CONTRACT_UNAVAILABLE", "BLOCKED_FALLBACK", traceId,
        List.of(new CustomRuleEvidenceUiRule("rule-evidence-contract-required", "version-ref-required", "MATCHED",
            "RULE_EVIDENCE_VISIBLE", "governance-service", List.of("custom-field-evidence-source"),
            List.of("audit:custom-rule-evidence-required"), List.of("replay-hash-ref-required"))),
        List.of(new CustomRuleEvidenceUiRule("rule-skipped-conflicting-fact", "version-ref-required", "SKIPPED",
            "REQUIRED_FACT_CONFLICTING", "governance-service", List.of("custom-field-decision-quality"),
            List.of("audit:custom-rule-skip-required"), List.of("replay-hash-ref-required"))),
        List.of(new CustomRuleEvidenceUiRule("rule-blocked-contract-unavailable", "version-ref-required", "BLOCKED",
            "RULE_EVIDENCE_BLOCKED", "quote-service", List.of("quote-evidence-contract"),
            List.of("audit:quote-rule-blocked-required"), List.of("replay-hash-ref-required"))),
        List.of(new CustomRuleCalculationStepUiDto("calculation-step-contract-required", "quote-service",
            "BLOCKED", "calculation-evidence-ref-required", "No pricing math is computed by pricing-bff fallback.")),
        reasonCodes,
        List.of("audit:custom-rule-evidence-required", "audit:custom-rule-skip-required",
            "audit:quote-rule-blocked-required"),
        List.of("replay-hash-ref-required"),
        List.of(new CustomRuleUiError("DEPENDENCY_UNAVAILABLE", "quote-service",
            "Configured quote-service calculation evidence contract is unavailable."),
            new CustomRuleUiError("RULE_EVIDENCE_BLOCKED", "governance-service",
                "Rule evidence remains blocked until backend contracts return typed facts and approved refs.")),
        List.of("CustomRuleEvidenceFallbackVisible"));
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

  ScenarioAnalysisWorkspaceView scenarioAnalysisWorkspace(String tenantContext, String runId, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "sa-s15-local-trace" : uiTraceId;
    List<ScenarioAnalysisBlocker> guardrailBlockers = List.of(
        new ScenarioAnalysisBlocker("REQUIRED_FACTS_MISSING", "BLOCKER",
            "Scenario-analysis-service requires backend fact refs before this variant can be priced or promoted.",
            List.of("fact:fico-score-ref", "fact:ltv-ref", "fact:lock-period-ref", "fact:product-ref"),
            "scenario-analysis-service.guardrail-policy"),
        new ScenarioAnalysisBlocker("POLICY_DECISION_BACKEND_REQUIRED", "WARNING",
            "Guardrail policy result is shown as backend-owned text; pricing-bff does not infer eligibility or rate impact.",
            List.of("policy-version-ref", "audit-ref-required"), "scenario-analysis-service.guardrail-evaluate"));
    return new ScenarioAnalysisWorkspaceView(normalizeTenant(tenantContext), runId,
        "SCENARIO_ANALYSIS_SERVICE_CONTRACT_NOT_CONFIGURED",
        List.of(
            new ScenarioAnalysisDimension("fico", "FICO sensitivity", "backend-current-fico-ref", "scenario-analysis-service.fico-sensitivity", List.of("fact:fico-score-ref", "sourceQuoteVersion"), true),
            new ScenarioAnalysisDimension("ltv", "LTV sensitivity", "backend-current-ltv-ref", "scenario-analysis-service.ltv-sensitivity", List.of("fact:property-value-ref", "fact:loan-amount-ref"), true),
            new ScenarioAnalysisDimension("lockPeriod", "Lock period sensitivity", "backend-current-lock-period-ref", "scenario-analysis-service.lock-period-comparison", List.of("fact:lock-period-ref", "fact:lock-start-date-ref"), true),
            new ScenarioAnalysisDimension("product", "Product sensitivity", "backend-product-comparison-ref", "scenario-analysis-service.product-comparison", List.of("fact:product-ref", "fact:investor-ref"), true)),
        List.of(
            new ScenarioAnalysisVariant("variant-base", "Backend baseline variant", "VISIBLE", List.of("fico", "ltv", "lockPeriod"), List.of("fact:source-quote-version", "fact:fico-score-ref", "fact:ltv-ref"), List.of(), List.of("analysis-result-ref:base")),
            new ScenarioAnalysisVariant("variant-guardrail-blocked", "Guardrail blocked variant", "BLOCKED", List.of("fico", "ltv", "product"), List.of("fact:fico-score-ref", "fact:ltv-ref", "fact:product-ref"), guardrailBlockers, List.of("analysis-result-ref:blocker-details"))),
        List.of(
            new ScenarioAnalysisBatchRow("row-001", "variant-base", "fico + ltv + lockPeriod", "VISIBLE", "batch-result-ref:row-001", "no open guardrail blockers"),
            new ScenarioAnalysisBatchRow("row-002", "variant-guardrail-blocked", "fico + ltv + product", "BLOCKED", "batch-result-ref:row-002", "REQUIRED_FACTS_MISSING")),
        List.of(new ScenarioAnalysisSavedAnalysis("analysis-saved-required", "Saved FICO/LTV sensitivity", "analysis-version-ref-required", "timestamp-supplied-by-scenario-analysis-service", "export-ref-required", "replay-hash-required")),
        List.of("export-ref-required", "what-if-export-manifest-required"),
        List.of("replay-hash-required", "input-bundle-ref-required", "policy-snapshot-ref-required"),
        guardrailBlockers,
        "Configured scenario-analysis-service workspace contract is unavailable in this local BFF fallback; response carries backend-owned refs, blockers, export refs, replay refs, and required facts only.",
        traceId,
        List.of("ScenarioAnalysisWorkspaceOpened", "ScenarioAnalysisBackendRefsVisible"));
  }

  ResponseEntity<ScenarioRecalculationResult> scenarioAnalysisRecalculate(String tenantContext, String runId,
      String uiTraceId, Map<String, Object> request) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "sa-s15-local-trace" : uiTraceId;
    String dimensionId = normalizedRaw(request == null ? null : request.get("changedDimensionId"));
    String requestedValue = normalizedRaw(request == null ? null : request.get("requestedValue"));
    List<String> variantFacts = stringList(request == null ? null : request.get("variantFacts"));
    if (dimensionId.isBlank() || variantFacts.isEmpty()) {
      return ResponseEntity.badRequest().body(new ScenarioRecalculationResult(runId, normalizeTenant(tenantContext), "BLOCKED",
          "Recalculation requires changedDimensionId and backend variant fact refs before scenario-analysis-service can be called.",
          List.of(), List.of(new ScenarioAnalysisBlocker("VARIANT_FACTS_REQUIRED", "BLOCKER",
              "Send backend fact refs with the what-if request; pricing-bff will not derive them locally.",
              List.of("changedDimensionId", "variantFacts"), "pricing-bff.scenario-analysis.recalculate")),
          List.of("ScenarioAnalysisRecalculationBlocked"), traceId));
    }
    return ResponseEntity.accepted().body(new ScenarioRecalculationResult(runId, normalizeTenant(tenantContext),
        "BACKEND_RESULT_VISIBLE",
        "Scenario recalculation request recorded for " + dimensionId + " using backend facts; requested value is passed through as a fact update only.",
        List.of("scenario-analysis-service.result:" + dimensionId, "scenario-analysis-service.requested-value-ref:" + Integer.toUnsignedString(requestedValue.hashCode(), 36)),
        List.of(new ScenarioAnalysisBlocker("POLICY_DECISION_BACKEND_REQUIRED", "WARNING",
            "Guardrail policy reasons remain backend-owned and must be returned by scenario-analysis-service.",
            variantFacts, "scenario-analysis-service.guardrail-evaluate")),
        List.of("ScenarioAnalysisRecalculationRequested", "VariantFactsForwarded"), traceId));
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
        "Configuration details need setup", traceId,
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

  MlAdvisoryInsightsView mlAdvisoryInsights(String tenantContext, String uiTraceId) {
    String traceId = uiTraceId == null || uiTraceId.isBlank() ? "ml-s14-local-trace" : uiTraceId;
    return new MlAdvisoryInsightsView(normalizeTenant(tenantContext), "ML_ADVISORY_SERVICE_EVIDENCE_INCOMPLETE", traceId,
        List.of(new AdvisoryRecommendationInsight("advisory-config-required", "model-version-ref-required",
            "confidence-score-from-model-output", "Backend explanation is visible for analyst review only.",
            List.of("VIEW_EXPLANATION", "REQUEST_HUMAN_REVIEW", "EXPORT_AUDIT_EVIDENCE"),
            List.of("audit-ref-required", "replay-hash-required"), false)),
        List.of(
            new ModelVersionGovernanceInsight("model-version-ref-required", "DRIFT_BASELINE_REQUIRED",
                "ALERT_REVIEW_REQUIRED", List.of("feedback-loop-ref-required"),
                List.of("evidence-export-ref-required", "manifest-hash-required")),
            new ModelVersionGovernanceInsight("model-version-unavailable", "ADVISORY_UNAVAILABLE",
                "NO_ACTIVE_ALERT", List.of(), List.of())),
        true,
        "Configured ml-advisory-service evidence is incomplete; the BFF returns non-secret model refs, alert states, and explicit advisory-unavailable status only.",
        List.of("MlAdvisoryInsightsOpened", "MlAdvisoryGovernanceGroupedByModelVersion"));
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

  private List<ComplianceAdvisoryReview> complianceAdvisoryReviewFallbacks() {
    return List.of(
        new ComplianceAdvisoryReview("quote-review-config-required", "quote", "quote-id-required",
            "ADVISORY_BLOCKED_UNTIL_CONFIGURED", List.of("HIGH_COST_THRESHOLD_UNAVAILABLE", "APR_ADVISORY_LEDGER_REQUIRED"),
            List.of("audit-snapshot-ref-required", "replay-hash-ref-required"), "REGULATORY_APPROVAL_PENDING_CONFIG",
            List.of("evidence-export-ref-required"), true,
            List.of("Configured regulatory thresholds are unavailable; no local threshold or APR value is inferred.")),
        new ComplianceAdvisoryReview("portfolio-review-config-required", "portfolio", "portfolio-id-required",
            "MONITORING_BLOCKED_UNTIL_CONFIGURED", List.of("FAIR_LENDING_MONITORING_REQUIRED"),
            List.of("portfolio-audit-snapshot-ref-required"), "REGULATORY_APPROVAL_REQUIRES_UPSTREAM_STATE",
            List.of("portfolio-evidence-export-ref-required"), true,
            List.of("Configured compliance-service advisory findings are required before portfolio approval state can be marked ready.")));
  }

  private List<FairLendingMonitoringDrilldown> fairLendingMonitoringFallbacks() {
    return List.of(new FairLendingMonitoringDrilldown("fair-lending-monitoring-config-required",
        List.of("masked-class-label", "geography-bucket-ref", "product-family-ref"), true,
        "redaction-profile-required", List.of("fair-lending-snapshot-ref-required", "monitoring-export-ref-required"),
        List.of("Configured fair-lending monitoring dimensions must be supplied by compliance-service.")));
  }

  private List<String> complianceConfigurationGaps() {
    return List.of(
        "Configured regulatory threshold values are unavailable; the UI records this blocked gap instead of embedding plausible constants.",
        "Configured compliance-service fair-lending dimensions are required before drilldown can infer any grouping.",
        "Configured regulatory approval state and export refs are required before advisory findings can be closed.");
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
            "EXCEPTION_QUEUE_PENDING", "EXCEPTION_QUEUE_METRICS_REQUIRED", "2026-06-08T07:10:00Z",
            "Exception queue size and retry aging require configured integration-service metrics.",
            "CONFIRMED_REQUIRED_FOR_REPLAY", "MASKING_INDICATOR_PRESENT", "CONSENT_INDICATOR_PRESENT"));
  }

  private List<PartnerSafetyToggle> partnerSafetyToggles() {
    return List.of(
        new PartnerSafetyToggle("webhook-pricing-updates", "/partners/quotes", false,
            "Auto-emit is enabled in the visible BFF fallback state."),
        new PartnerSafetyToggle("webhook-lock-alerts", "/partners/alerts", true,
            "Auto-emit is paused for this route in the visible BFF fallback state."));
  }

  private List<PartnerChannelWorkbenchTab> partnerChannelWorkbenchTabs() {
    return List.of(
        partnerChannelTab("quote-requests", "Quote requests", "/partners/integrations/quote-requests",
            "QUOTE_REQUESTS_VISIBLE_WITH_CONTRACT_BLOCKERS", "partner-operations-owner",
            partnerChannelItem("quote-request-fallback", "Quote request intake setup", "READY_FALLBACK",
                "manual-review-required", "none", "payload-redacted", List.of("audit:partner-quote-request-required"))),
        partnerChannelTab("webhook-delivery", "Webhook delivery", "/partners/integrations/webhook-delivery",
            "WEBHOOK_DELIVERY_VISIBLE_WITH_RETRY_STATE", "integration-platform-owner",
            partnerChannelItem("webhook-pricing-updates", "Pricing update webhook", "FAILED",
                "retry-confirmation-required", "none", "payload-redacted", List.of("audit:webhook-delivery-required"))),
        partnerChannelTab("retries", "Retries", "/partners/integrations/retries", "RETRY_QUEUE_VISIBLE",
            "integration-platform-owner",
            partnerChannelItem("retry-webhook-pricing-updates", "Webhook retry queue", "PENDING_CONFIRMATION",
                "idempotency-confirmation-required", "none", "payload-redacted", List.of("audit:webhook-retry-required"))),
        partnerChannelTab("dlq", "Exception queue", "/partners/integrations/dlq", "EXCEPTION_QUEUE_VISIBLE_WITH_REASON",
            "integration-platform-owner",
            partnerChannelItem("dlq-lock-alerts", "Lock alert exception queue entry", "EXCEPTION_QUEUE_PENDING",
                "retry-window-contract-required", "EXCEPTION_QUEUE_METRICS_REQUIRED", "payload-redacted",
                List.of("audit:partner-dlq-required"))),
        partnerChannelTab("feed-adapters", "Investor delivery connections", "/partners/integrations/feed-adapters",
            "INVESTOR_DELIVERY_CONNECTIONS_BLOCKED_UNTIL_CONFIGURED", "feed-operations-owner",
            partnerChannelItem("investor-feed-adapter", "Investor delivery connection", "BLOCKED",
                "configured-feed-contract-required", "none", "payload-redacted", List.of("audit:feed-adapter-required"))),
        partnerChannelTab("sftp-adapters", "Partner file delivery", "/partners/integrations/sftp-adapters",
            "PARTNER_FILE_DELIVERY_BLOCKED_UNTIL_CONFIGURED", "feed-operations-owner",
            partnerChannelItem("partner-sftp-adapter", "Partner file delivery setup", "BLOCKED",
                "configured-sftp-contract-required", "none", "payload-redacted", List.of("audit:sftp-adapter-required"))),
        partnerChannelTab("health", "Health", "/partners/integrations/health", "HEALTH_VISIBLE_WITH_BLOCKED_ACCESS",
            "integration-platform-owner",
            partnerChannelItem("service-account-access", "Service account access", "BLOCKED",
                "capability-grant-required", "none", "credentials-not-rendered", List.of("audit:service-account-access-required"))));
  }

  private PartnerChannelWorkbenchTab partnerChannelTab(String tabId, String label, String route, String status,
      String recoveryOwner, PartnerChannelWorkbenchItem item) {
    return new PartnerChannelWorkbenchTab(tabId, label, route, status, recoveryOwner, List.of(item));
  }

  private PartnerChannelWorkbenchItem partnerChannelItem(String itemId, String label, String state, String retryState,
      String dlqReason, String payloadRedactionState, List<String> auditRefs) {
    return new PartnerChannelWorkbenchItem(itemId, label, state, retryState, dlqReason, payloadRedactionState, auditRefs);
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
    if (isBlankText(intake, "channel")) {
      blockers.put("channel", "LoanPASS channelType is required before a quote run can start.");
    }

    if (!blockers.isEmpty()) {
      return new IntakeValidation(false, "BLOCKED", "Complete the highlighted required fields.", blockers);
    }

    return new IntakeValidation(true, "PASSED", "Required quote-run launch fields are present.", Map.of());
  }

  private List<String> backendFactRefs(Map<String, Object> intake) {
    List<String> refs = new java.util.ArrayList<>();
    List.of("borrowerLastName", "loanNumber", "externalLoanId", "channel", "loanPurpose", "baseLoanAmount", "purchasePrice",
        "appraisedValue", "quoteAddressDTO.state", "quoteAddressDTO.zip", "propertyType", "occupancyType", "numberOfUnits",
        "decisionCreditScore", "documentationType", "totalMonthlyIncome", "estimatedDti", "mortgageType", "desiredLoanTerm",
        "desiredAmortizationType", "desiredRateLockPeriod", "lienPosition", "actorId", "clientContext").forEach(field -> {
          if (!isBlankText(intake, field)) {
            refs.add("fact:" + field);
          }
        });
    return refs;
  }

  private List<String> quoteServiceMissingFacts(Map<String, Object> intake) {
    List<String> missing = new java.util.ArrayList<>();
    Map<String, String> required = new LinkedHashMap<>();
    required.put("channel", "LoanPASS channelType is missing; catalog or tenant mapping evidence is required.");
    required.put("loanPurpose", "LoanPASS transactionType is missing; quote request requires an explicit purpose fact.");
    required.put("baseLoanAmount", "LoanPASS requestedLoanAmount/baseLoanAmount is missing.");
    required.put("quoteAddressDTO.state", "LoanPASS quoteAddressDTO.state is missing.");
    required.put("quoteAddressDTO.zip", "LoanPASS quoteAddressDTO.zip is missing.");
    required.put("decisionCreditScore", "LoanPASS creditScore/field@decision-credit-score is missing.");
    required.put("documentationType", "LoanPASS incomeDocumentationType/field@documentation-type is missing.");
    required.put("mortgageType", "LoanPASS mortgageType/field@desired-mortgage-type is missing.");
    required.put("desiredLoanTerm", "LoanPASS loanTermType/field@desired-loan-term is missing.");
    required.put("desiredAmortizationType", "LoanPASS amortizationType/field@desired-amortization-type is missing.");
    required.put("numberOfUnits", "LoanPASS numberOfUnits/field@number-of-units is missing.");
    required.forEach((field, message) -> {
      if (isBlankText(intake, field)) {
        missing.add(message);
      }
    });
    return missing;
  }

  private List<CreditApplicationField> quoteRunCreditApplicationFields(Map<String, Object> intake) {
    if (intake == null || intake.isEmpty()) return List.of();
    Map<String, Object> facts = new LinkedHashMap<>();
    flattenIntakeFacts("", intake, facts);
    putAliasFact(facts, intake, "state", "quoteAddressDTO.state");
    putAliasFact(facts, intake, "zip", "quoteAddressDTO.zip");
    return facts.entrySet().stream()
        .filter(entry -> !blankValue(entry.getValue()))
        .map(entry -> new CreditApplicationField(loanPassFieldId(entry.getKey()), creditApplicationValue(entry.getValue())))
        .toList();
  }

  private void flattenIntakeFacts(String prefix, Map<?, ?> source, Map<String, Object> facts) {
    source.forEach((rawKey, value) -> {
      if (rawKey == null) return;
      String key = rawKey.toString().trim();
      if (key.isBlank()) return;
      String path = prefix.isBlank() ? canonicalFactPath(key) : prefix + "." + key;
      if (value instanceof Map<?, ?> nested) {
        flattenIntakeFacts(path, nested, facts);
      } else if (!blankValue(value)) {
        facts.putIfAbsent(path, value);
      }
    });
  }

  private void putAliasFact(Map<String, Object> facts, Map<String, Object> intake, String alias, String canonicalPath) {
    if (intake != null && !blankValue(intake.get(alias)) && !facts.containsKey(canonicalPath)) {
      facts.put(canonicalPath, intake.get(alias));
    }
  }

  private String canonicalFactPath(String key) {
    return switch (key) {
      case "state" -> "quoteAddressDTO.state";
      case "zip" -> "quoteAddressDTO.zip";
      default -> key;
    };
  }

  private String loanPassFieldId(String factPath) {
    return switch (factPath) {
      case "baseLoanAmount" -> "field@base-loan-amount";
      case "purchasePrice" -> "field@purchase-price";
      case "appraisedValue" -> "field@appraised-value";
      case "quoteAddressDTO.state" -> "field@state";
      case "quoteAddressDTO.zip" -> "field@zip";
      case "quoteAddressDTO.countyFips" -> "field@county";
      case "decisionCreditScore" -> "field@decision-credit-score";
      case "documentationType" -> "field@documentation-type";
      case "mortgageType" -> "field@desired-mortgage-type";
      case "desiredLoanTerm" -> "field@desired-loan-term";
      case "desiredAmortizationType" -> "field@desired-amortization-type";
      case "numberOfUnits" -> "field@number-of-units";
      case "totalMonthlyIncome" -> "field@total-monthly-income";
      case "estimatedDti" -> "field@estimated-dti";
      case "monthsOfReserves" -> "field@months-of-reserves";
      case "lienPosition" -> "field@lien-position";
      default -> "field@" + factPath.replaceAll("([a-z])([A-Z])", "$1-$2")
          .replace('.', '-')
          .replace('_', '-')
          .toLowerCase(Locale.ROOT);
    };
  }

  private CreditApplicationValue creditApplicationValue(Object value) {
    String type = value instanceof Number ? "number" : value instanceof Boolean ? "boolean" : "string";
    return new CreditApplicationValue(type, value, null, null);
  }

  private List<CreditApplicationField> quoteRunContextFields(String tenantId, String runId) {
    QuoteRunContext context = quoteRunContexts.get(new QuoteRunContextKey(normalizedTenantKey(tenantId), runId));
    return context == null ? List.of() : context.creditApplicationFields();
  }

  private ScenarioIntakeField metadataField(String fieldId, String label, String groupId, String dataType, boolean required,
      String helpText, String sourceRef, String decisionQuality, List<String> validationMessages) {
    return new ScenarioIntakeField(fieldId, label, groupId, dataType, required, helpText, sourceRef, decisionQuality,
        validationMessages);
  }

  private boolean isBlankText(Map<String, Object> intake, String field) {
    Object value = fieldValue(intake, field);
    return blankValue(value);
  }

  private Object fieldValue(Map<String, Object> intake, String field) {
    Object value = null;
    if (intake != null) {
      value = intake.get(field);
      if (value == null && field.contains(".")) {
        String[] parts = field.split("\\.");
        Object current = intake;
        for (String part : parts) {
          if (current instanceof Map<?, ?> map) {
            current = map.get(part);
          } else {
            current = null;
            break;
          }
        }
        value = current;
      }
      if (blankValue(value) && "quoteAddressDTO.state".equals(field)) {
        value = intake.get("state");
      }
      if (blankValue(value) && "quoteAddressDTO.zip".equals(field)) {
        value = intake.get("zip");
      }
    }
    return value;
  }

  private boolean blankValue(Object value) {
    if (value == null) return true;
    if (value instanceof String text) return text.isBlank();
    if (value instanceof List<?> values) return values.isEmpty();
    if (value instanceof Map<?, ?> values) return values.isEmpty();
    return false;
  }

  private String deterministicRunId(String tenantId, Map<String, Object> intake) {
    String loanNumber = isBlankText(intake, "loanNumber") ? normalized(intake.get("externalLoanId")) : normalized(intake.get("loanNumber"));
    String seed = normalized(tenantId) + "|" + normalized(intake.get("borrowerLastName")) + "|"
        + normalized(intake.get("channel")) + "|" + loanNumber;
    return "run-" + Integer.toUnsignedString(seed.hashCode(), 36);
  }

  private String normalizeTrace(String uiTraceId) {
    return uiTraceId == null || uiTraceId.isBlank() ? "brw-s01-local-trace" : uiTraceId;
  }

  private String normalized(Object value) {
    return value == null ? "" : value.toString().trim().toLowerCase(Locale.ROOT);
  }

  private String normalizedRaw(Object value) {
    return value == null ? "" : value.toString().trim();
  }

  private Map<String, Object> safeObjectMap(Map<String, Object> values) {
    if (values == null || values.isEmpty()) return Map.of();
    Map<String, Object> safe = new LinkedHashMap<>();
    values.forEach((key, value) -> {
      String normalizedKey = normalizedRaw(key);
      if (!normalizedKey.isBlank()) safe.put(normalizedKey, value);
    });
    return safe;
  }

  private List<String> stringList(Object value) {
    if (value instanceof List<?> values) {
      return values.stream().map(this::normalizedRaw).filter(item -> !item.isBlank()).toList();
    }
    return List.of();
  }

  private String normalizedTenantKey(String tenantId) {
    String normalized = normalized(tenantId);
    return normalized.isBlank() ? "tenant-context-required" : normalized;
  }

  private List<RolePricingAccessConfig> safeRolePricingAccess(List<RolePricingAccessConfig> roles) {
    if (roles == null) {
      return List.of();
    }
    return roles.stream()
        .filter(role -> role != null && role.roleId() != null && !role.roleId().isBlank())
        .map(role -> new RolePricingAccessConfig(normalizedRaw(role.roleId()), normalizedRaw(role.label()),
            safeStringList(role.pricingVisibleFieldIds()), role.pricingActionsEnabled()))
        .toList();
  }

  private List<PricingProfileConfig> safePricingProfiles(List<PricingProfileConfig> profiles) {
    if (profiles == null) {
      return List.of();
    }
    return profiles.stream()
        .filter(profile -> profile != null && profile.profileId() != null && !profile.profileId().isBlank())
        .map(profile -> new PricingProfileConfig(normalizedRaw(profile.profileId()), normalizedRaw(profile.label()),
            safeStringList(profile.fieldIds()), safeStringList(profile.sourceRefs()), profile.active()))
        .toList();
  }

  private List<FeatureFlagConfig> safeFeatureFlags(List<FeatureFlagConfig> flags) {
    if (flags == null) {
      return List.of();
    }
    return flags.stream()
        .filter(flag -> flag != null && flag.flagId() != null && !flag.flagId().isBlank())
        .map(flag -> new FeatureFlagConfig(normalizedRaw(flag.flagId()), flag.enabled(),
            safeStringList(flag.affectedFeatures()), normalizedRaw(flag.sourceRef())))
        .toList();
  }

  private List<String> pricingAccessValidation(List<RolePricingAccessConfig> roles,
      List<PricingProfileConfig> pricingProfiles, List<FeatureFlagConfig> featureFlags, String activeRoleId,
      String activePricingProfileId, String activeVersion) {
    List<String> validationMessages = new ArrayList<>();
    if (roles.isEmpty()) {
      validationMessages.add("At least one role with pricing-visible field IDs is required before pipeline pricing access can be published.");
    }
    if (activeRoleId == null || activeRoleId.isBlank()) {
      validationMessages.add("An active role ID is required before pricing-visible fields can be resolved.");
    } else if (roles.stream().noneMatch(role -> role.roleId().equals(activeRoleId))) {
      validationMessages.add("Active role ID references missing role pricing access config: " + activeRoleId);
    }
    List<String> rolesWithoutVisibleFields = roles.stream()
        .filter(role -> role.pricingVisibleFieldIds().isEmpty())
        .map(RolePricingAccessConfig::roleId)
        .toList();
    if (!rolesWithoutVisibleFields.isEmpty()) {
      validationMessages.add("Roles require at least one pricing-visible field ID: " + String.join(", ", rolesWithoutVisibleFields));
    }
    if (pricingProfiles.isEmpty()) {
      validationMessages.add("At least one tenant pricing profile is required before pricing behavior can resolve profile context.");
    }
    if (activePricingProfileId == null || activePricingProfileId.isBlank()) {
      validationMessages.add("An active pricing profile ID is required before pricing behavior can resolve tenant profile context.");
    } else if (pricingProfiles.stream().noneMatch(profile -> profile.profileId().equals(activePricingProfileId))) {
      validationMessages.add("Active pricing profile ID references missing tenant pricing profile: " + activePricingProfileId);
    }
    if (featureFlags.isEmpty()) {
      validationMessages.add("Tenant feature flags are required before UI/API feature availability can be resolved.");
    }
    if (activeVersion == null || activeVersion.isBlank()) {
      validationMessages.add("Tenant pricing access settings version is required before audit and publish.");
    }
    return validationMessages;
  }

  private List<PricingAccessAuditRecord> pricingAccessAuditRecords(String tenantKey, String actorUserId,
      List<RolePricingAccessConfig> roles, List<PricingProfileConfig> pricingProfiles, List<FeatureFlagConfig> featureFlags,
      String activeRoleId, String activePricingProfileId, String activeVersion) {
    String actor = actorUserId == null || actorUserId.isBlank() ? "actor-context-required" : actorUserId;
    return List.of(
        new PricingAccessAuditRecord(tenantKey, actor, "roles-pricing-visible-fields", activeVersion,
            roles.stream().map(role -> role.roleId() + ":" + String.join("|", role.pricingVisibleFieldIds())).toList(),
            "audit:pricing-access:roles:" + tenantKey),
        new PricingAccessAuditRecord(tenantKey, actor, "pricing-profile-assignment", activeVersion,
            List.of("activeRoleId=" + activeRoleId, "activePricingProfileId=" + activePricingProfileId,
                "profileIds=" + String.join("|", pricingProfiles.stream().map(PricingProfileConfig::profileId).toList())),
            "audit:pricing-access:profiles:" + tenantKey),
        new PricingAccessAuditRecord(tenantKey, actor, "feature-flag-availability", activeVersion,
            featureFlags.stream().map(flag -> flag.flagId() + "=" + flag.enabled()).toList(),
            "audit:pricing-access:feature-flags:" + tenantKey));
  }

  private List<String> disabledFeatureIds(List<FeatureFlagConfig> featureFlags) {
    return featureFlags.stream().filter(flag -> !flag.enabled()).map(FeatureFlagConfig::flagId).toList();
  }

  private List<String> unavailableFeatures(List<FeatureFlagConfig> featureFlags) {
    return featureFlags.stream()
        .filter(flag -> !flag.enabled())
        .flatMap(flag -> flag.affectedFeatures().stream())
        .distinct()
        .toList();
  }

  private List<PipelineFieldSetting> safePipelineFields(List<PipelineFieldSetting> fields) {
    if (fields == null) {
      return List.of();
    }
    return fields.stream()
        .filter(field -> field != null && field.fieldId() != null && !field.fieldId().isBlank())
        .map(field -> new PipelineFieldSetting(normalizedRaw(field.fieldId()), normalizedRaw(field.label()),
            normalizedRaw(field.dataType()), field.visible(), normalizedRaw(field.sourceRef())))
        .toList();
  }

  private List<ClientSettingsFieldRef> safeClientSettingsFields(List<ClientSettingsFieldRef> fields) {
    if (fields == null) {
      return List.of();
    }
    return fields.stream()
        .filter(field -> field != null && field.systemFieldId() != null && !field.systemFieldId().isBlank())
        .map(field -> new ClientSettingsFieldRef(normalizedRaw(field.systemFieldId()), normalizedRaw(field.label()),
            normalizedRaw(field.dataType()), normalizedRaw(field.sourceRef())))
        .toList();
  }

  private Map<String, String> safeClientFieldValues(Map<String, String> values) {
    if (values == null) {
      return Map.of();
    }
    Map<String, String> normalizedValues = new LinkedHashMap<>();
    values.forEach((fieldId, value) -> {
      String normalizedFieldId = normalizedRaw(fieldId);
      String normalizedValue = normalizedRaw(value);
      if (!normalizedFieldId.isBlank() && !normalizedValue.isBlank()) {
        normalizedValues.put(normalizedFieldId, normalizedValue);
      }
    });
    return Map.copyOf(normalizedValues);
  }

  private List<String> clientSettingsValidation(List<ClientSettingsFieldRef> systemFields,
      Map<String, String> fieldValues, String activeVersion) {
    List<String> validationMessages = new ArrayList<>();
    if (systemFields.isEmpty()) {
      validationMessages.add("Client settings fields must reference imported system field IDs before publishing.");
    }
    if (activeVersion == null || activeVersion.isBlank()) {
      validationMessages.add("Tenant active client settings version is required before pipeline behavior can consume values.");
    }
    List<String> systemFieldIds = systemFields.stream().map(ClientSettingsFieldRef::systemFieldId).toList();
    List<String> missingFieldRefs = fieldValues.keySet().stream()
        .filter(fieldId -> !systemFieldIds.contains(fieldId))
        .toList();
    if (!missingFieldRefs.isEmpty()) {
      validationMessages.add("Client setting values reference missing system field IDs: " + String.join(", ", missingFieldRefs));
    }
    return validationMessages;
  }

  private List<ClientSettingsFieldRef> effectiveActiveFieldLibrary(String tenantKey,
      List<ClientSettingsFieldRef> activeFieldLibrary) {
    List<ClientSettingsFieldRef> requestLibrary = safeClientSettingsFields(activeFieldLibrary);
    if (!requestLibrary.isEmpty()) {
      return requestLibrary;
    }
    return List.of();
  }

  private List<PricingNotificationFieldView> safeNotificationFields(List<PricingNotificationFieldConfig> fields,
      List<ClientSettingsFieldRef> activeFieldLibrary) {
    if (fields == null) {
      return List.of();
    }
    List<String> activeFieldIds = activeFieldLibrary.stream().map(ClientSettingsFieldRef::systemFieldId).toList();
    return fields.stream()
        .filter(field -> field != null && field.fieldId() != null && !field.fieldId().isBlank())
        .map(field -> {
          String fieldId = normalizedRaw(field.fieldId());
          String sendNotificationFieldId = normalizedRaw(field.sendNotificationFieldId());
          List<String> includeConditions = safeStringList(field.includeConditions());
          List<String> additionalConditions = safeStringList(field.additionalConditions());
          List<String> visibleReferences = visibleNotificationReferences(field.showReferences(), field.references(),
              sendNotificationFieldId, activeFieldIds, includeConditions, additionalConditions);
          return new PricingNotificationFieldView(fieldId, normalizedRaw(field.nameAlias()),
              normalizedRaw(field.descriptionAlias()), sendNotificationFieldId, includeConditions, additionalConditions,
              field.showReferences(), visibleReferences);
        })
        .toList();
  }

  private List<String> visibleNotificationReferences(boolean showReferences, List<String> requestReferences,
      String sendNotificationFieldId, List<String> activeFieldIds, List<String> includeConditions,
      List<String> additionalConditions) {
    if (!showReferences) {
      return List.of();
    }
    List<String> references = new ArrayList<>(safeStringList(requestReferences));
    if (!sendNotificationFieldId.isBlank() && activeFieldIds.contains(sendNotificationFieldId)) {
      references.add("field:" + sendNotificationFieldId);
    }
    includeConditions.stream().map(condition -> "include-condition:" + condition).forEach(references::add);
    additionalConditions.stream().map(condition -> "additional-condition:" + condition).forEach(references::add);
    return List.copyOf(references.stream().distinct().toList());
  }

  private List<String> notificationSettingsValidation(List<ClientSettingsFieldRef> activeFieldLibrary,
      List<PricingNotificationFieldView> notificationFields, String activeVersion) {
    List<String> validationMessages = new ArrayList<>();
    if (activeFieldLibrary.isEmpty()) {
      validationMessages.add("Pricing notification settings must reference the tenant active field library before publishing.");
    }
    if (activeVersion == null || activeVersion.isBlank()) {
      validationMessages.add("Tenant active pricing notification settings version is required before publishing.");
    }
    if (notificationFields.isEmpty()) {
      validationMessages.add("At least one pricing notification field must be configured before publishing.");
    }
    List<String> activeFieldIds = activeFieldLibrary.stream().map(ClientSettingsFieldRef::systemFieldId).toList();
    for (PricingNotificationFieldView field : notificationFields) {
      if (field.nameAlias().isBlank()) {
        validationMessages.add("Pricing notification " + field.fieldId() + " requires a name alias.");
      }
      if (field.descriptionAlias().isBlank()) {
        validationMessages.add("Pricing notification " + field.fieldId() + " requires a description alias.");
      }
      if (field.sendNotificationFieldId().isBlank() || !activeFieldIds.contains(field.sendNotificationFieldId())) {
        validationMessages.add("Pricing notification " + field.fieldId()
            + " references missing send-notification field ID: " + field.sendNotificationFieldId());
      }
      List<String> invalidConditions = new ArrayList<>();
      field.includeConditions().stream().filter(condition -> !validNotificationCondition(condition))
          .forEach(invalidConditions::add);
      field.additionalConditions().stream().filter(condition -> !validNotificationCondition(condition))
          .forEach(invalidConditions::add);
      if (!invalidConditions.isEmpty()) {
        validationMessages.add("Pricing notification " + field.fieldId() + " has invalid conditions: "
            + String.join(", ", invalidConditions));
      }
    }
    return validationMessages;
  }

  private boolean validNotificationCondition(String condition) {
    String normalizedCondition = normalizedRaw(condition);
    return normalizedCondition.startsWith("field:") || normalizedCondition.startsWith("condition:")
        || normalizedCondition.startsWith("ref:");
  }

  private PriceScenarioTableSettings safePriceScenarioTable(PriceScenarioTableSettings settings) {
    if (settings == null) {
      return new PriceScenarioTableSettings("", "", "", List.of());
    }
    return new PriceScenarioTableSettings(normalizedRaw(settings.adjustedRateFieldId()),
        normalizedRaw(settings.lockPeriodFieldId()), normalizedRaw(settings.adjustedPriceFieldId()),
        safeStringList(settings.extraColumnFieldIds()));
  }

  private DefaultPricingFilters safeDefaultPricingFilters(DefaultPricingFilters filters) {
    return new DefaultPricingFilters(filters == null ? List.of() : safeStringList(filters.fieldIds()));
  }

  private LockingFieldSettings safeLockingFields(LockingFieldSettings settings) {
    if (settings == null) {
      return new LockingFieldSettings("", "", "");
    }
    return new LockingFieldSettings(normalizedRaw(settings.requestDateFieldId()),
        normalizedRaw(settings.expirationDateFieldId()), normalizedRaw(settings.extensionDaysFieldId()));
  }

  private List<String> safeStringList(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().map(this::normalizedRaw).filter(value -> !value.isBlank()).toList();
  }

  private List<String> priceScenarioCalculationFieldIds(PriceScenarioTableSettings settings) {
    List<String> fieldIds = new ArrayList<>();
    if (!settings.adjustedRateFieldId().isBlank()) {
      fieldIds.add(settings.adjustedRateFieldId());
    }
    if (!settings.lockPeriodFieldId().isBlank()) {
      fieldIds.add(settings.lockPeriodFieldId());
    }
    if (!settings.adjustedPriceFieldId().isBlank()) {
      fieldIds.add(settings.adjustedPriceFieldId());
    }
    fieldIds.addAll(settings.extraColumnFieldIds());
    return List.copyOf(fieldIds);
  }

  private List<String> lockingFieldIds(LockingFieldSettings settings) {
    List<String> fieldIds = new ArrayList<>();
    if (!settings.requestDateFieldId().isBlank()) {
      fieldIds.add(settings.requestDateFieldId());
    }
    if (!settings.expirationDateFieldId().isBlank()) {
      fieldIds.add(settings.expirationDateFieldId());
    }
    if (!settings.extensionDaysFieldId().isBlank()) {
      fieldIds.add(settings.extensionDaysFieldId());
    }
    return List.copyOf(fieldIds);
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
      List<String> placeholders, LoanPassProduct product, List<String> creditApplicationFieldIds) {}

  record LoanPassProduct(String productId, String selectedProgramId, String priceGroupId, String mortgageType,
      String loanQualificationType, String desiredLoanTerm, String desiredAmortizationType, String channelType) {}

  record PipelineSettingsRequest(List<PipelineFieldSetting> pipelineFields,
      PriceScenarioTableSettings priceScenarioTable, DefaultPricingFilters defaultPricingFilters,
      LockingFieldSettings lockingFields) {}

  record PipelineSettingsView(String tenantContext, String status, boolean configured,
      List<PipelineFieldSetting> pipelineFields, PriceScenarioTableSettings priceScenarioTable,
      DefaultPricingFilters defaultPricingFilters, LockingFieldSettings lockingFields,
      PipelineSettingsBindingSummary bindingSummary, String dependencyStatus, List<String> validationMessages,
      String uiTraceId, List<String> events, String fallbackReason) {
    static PipelineSettingsView unconfigured(String tenantContext, String traceId) {
      return new PipelineSettingsView(tenantContext, "UNCONFIGURED", false, List.of(),
          new PriceScenarioTableSettings("", "", "", List.of()), new DefaultPricingFilters(List.of()),
          new LockingFieldSettings("", "", ""),
          new PipelineSettingsBindingSummary(List.of(), List.of(), List.of(), List.of()),
          "PIPELINE_SETTINGS_NOT_CONFIGURED",
          List.of("Tenant pipeline settings have not been stored for this local BFF tenant."), traceId,
          List.of("PipelineSettingsRequested"),
          "Pricing-bff returns only tenant-supplied field IDs; it does not infer pricing, eligibility, or lock policy rules.");
    }
  }

  record PipelineFieldSetting(String fieldId, String label, String dataType, boolean visible, String sourceRef) {}

  record PriceScenarioTableSettings(String adjustedRateFieldId, String lockPeriodFieldId,
      String adjustedPriceFieldId, List<String> extraColumnFieldIds) {}

  record DefaultPricingFilters(List<String> fieldIds) {}

  record LockingFieldSettings(String requestDateFieldId, String expirationDateFieldId, String extensionDaysFieldId) {}

  record PipelineSettingsBindingSummary(List<String> pipelineColumnFieldIds,
      List<String> priceScenarioCalculationFieldIds, List<String> defaultFilterFieldIds, List<String> lockingFieldIds) {}

  record ClientSettingsRequest(List<ClientSettingsFieldRef> systemFields, Map<String, String> clientFieldValues,
      String activeVersion) {}

  record ClientSettingsFieldRef(String systemFieldId, String label, String dataType, String sourceRef) {}

  record ClientSettingsView(String tenantContext, String status, boolean configured, String activeVersion,
      List<ClientSettingsFieldRef> systemFields, Map<String, String> clientFieldValues,
      List<String> validationMessages, String dependencyStatus, String uiTraceId, List<String> events,
      String fallbackReason) {
    static ClientSettingsView unconfigured(String tenantContext, String traceId) {
      return new ClientSettingsView(tenantContext, "UNCONFIGURED", false, "", List.of(), Map.of(),
          List.of("Tenant client settings have not been stored for this local BFF tenant."),
          "CLIENT_SETTINGS_NOT_CONFIGURED", traceId, List.of("ClientSettingsRequested"),
          "Pricing-bff exposes configured tenant client settings only; no client-level pricing defaults are inferred.");
    }

    ClientSettingsView withStatus(String status, String dependencyStatus, String traceId,
        List<String> validationMessages, List<String> events) {
      return new ClientSettingsView(tenantContext, status, configured, activeVersion, systemFields, clientFieldValues,
          validationMessages, dependencyStatus, traceId, events, fallbackReason);
    }
  }

  record NotificationSettingsRequest(List<ClientSettingsFieldRef> activeFieldLibrary,
      List<PricingNotificationFieldConfig> notificationFields, String activeVersion) {}

  record PricingNotificationFieldConfig(String fieldId, String nameAlias, String descriptionAlias,
      String sendNotificationFieldId, List<String> includeConditions, List<String> additionalConditions,
      boolean showReferences, List<String> references) {}

  record PricingNotificationFieldView(String fieldId, String nameAlias, String descriptionAlias,
      String sendNotificationFieldId, List<String> includeConditions, List<String> additionalConditions,
      boolean showReferences, List<String> visibleReferences) {}

  record NotificationSettingsView(String tenantContext, String status, boolean configured, String activeVersion,
      List<ClientSettingsFieldRef> activeFieldLibrary, List<PricingNotificationFieldView> notificationFields,
      List<String> validationMessages, String dependencyStatus, String uiTraceId, List<String> events,
      String fallbackReason) {
    static NotificationSettingsView unconfigured(String tenantContext, String traceId) {
      return new NotificationSettingsView(tenantContext, "UNCONFIGURED", false, "", List.of(), List.of(),
          List.of("Tenant pricing notification settings have not been stored for this local BFF tenant."),
          "PRICING_NOTIFICATION_SETTINGS_NOT_CONFIGURED", traceId, List.of("PricingNotificationSettingsRequested"),
          "Pricing-bff exposes configured notification field aliases, references, and condition expressions only; notification delivery remains outside this fallback slice.");
    }

    NotificationSettingsView withStatus(String status, String dependencyStatus, String traceId,
        List<String> validationMessages, List<String> events) {
      return new NotificationSettingsView(tenantContext, status, configured, activeVersion, activeFieldLibrary,
          notificationFields, validationMessages, dependencyStatus, traceId, events, fallbackReason);
    }
  }

  record PricingAccessSettingsRequest(List<RolePricingAccessConfig> roles,
      List<PricingProfileConfig> pricingProfiles, List<FeatureFlagConfig> featureFlags, String activeRoleId,
      String activePricingProfileId, String activeVersion) {}

  record RolePricingAccessConfig(String roleId, String label, List<String> pricingVisibleFieldIds,
      boolean pricingActionsEnabled) {}

  record PricingProfileConfig(String profileId, String label, List<String> fieldIds, List<String> sourceRefs,
      boolean active) {}

  record FeatureFlagConfig(String flagId, boolean enabled, List<String> affectedFeatures, String sourceRef) {}

  record PricingAccessAuditRecord(String tenantContext, String actorUserId, String changeType, String activeVersion,
      List<String> changedValues, String auditRef) {}

  record PricingAccessSettingsView(String tenantContext, String status, boolean configured, String activeVersion,
      String activeRoleId, String activePricingProfileId, List<RolePricingAccessConfig> roles,
      List<PricingProfileConfig> pricingProfiles, List<FeatureFlagConfig> featureFlags,
      List<String> authorizedPricingFieldIds, List<String> unavailableFeatures, PricingProfileConfig activePricingProfile,
      List<String> disabledFeatureFlagIds, List<PricingAccessAuditRecord> auditRecords, List<String> validationMessages,
      String dependencyStatus, String uiTraceId, List<String> events, String fallbackReason) {
    static PricingAccessSettingsView unconfigured(String tenantContext, String traceId) {
      return new PricingAccessSettingsView(tenantContext, "UNCONFIGURED", false, "", "", "", List.of(), List.of(),
          List.of(), List.of(), List.of(), null, List.of(), List.of(),
          List.of("Tenant role, pricing profile, and feature flag settings have not been stored for this local BFF tenant."),
          "PRICING_ACCESS_SETTINGS_NOT_CONFIGURED", traceId, List.of("PricingAccessSettingsRequested"),
          "Pricing-bff exposes tenant-supplied role, pricing profile, and feature flag settings only; identity management and pricing rules remain downstream-owned.");
    }

    PricingAccessSettingsView withResolvedRole(String roleId, String traceId) {
      String resolvedRoleId = roleId == null || roleId.isBlank() ? activeRoleId : roleId;
      List<String> authorizedFields = roles.stream()
          .filter(role -> role.roleId().equals(resolvedRoleId))
          .findFirst()
          .map(RolePricingAccessConfig::pricingVisibleFieldIds)
          .orElse(List.of());
      PricingProfileConfig resolvedProfile = pricingProfiles.stream()
          .filter(profile -> profile.profileId().equals(activePricingProfileId))
          .findFirst()
          .orElse(null);
      return new PricingAccessSettingsView(tenantContext, status, configured, activeVersion, resolvedRoleId,
          activePricingProfileId, roles, pricingProfiles, featureFlags, authorizedFields,
          featureFlags.stream().filter(flag -> !flag.enabled()).flatMap(flag -> flag.affectedFeatures().stream()).distinct().toList(), resolvedProfile,
          featureFlags.stream().filter(flag -> !flag.enabled()).map(FeatureFlagConfig::flagId).toList(), auditRecords, validationMessages,
          dependencyStatus, traceId, events, fallbackReason);
    }
  }

  record ProductCatalogManagerView(String tenantContext, String dependencyStatus, List<ProductCatalogArea> areas,
      ProductCatalogLifecycle lifecycle, List<String> events, String fallbackReason, String uiTraceId) {}

  record ProductCatalogArea(String areaId, String label, String sourceRef, String status, String guidance,
      List<String> fields, List<String> validationMessages) {}

  record ProductCatalogLifecycle(String state, boolean actionsDisabled, List<String> actions,
      List<String> snapshotRefs, List<String> auditRefs, String blocker) {}

  record DraftScenarioRequest(String status, Map<String, Object> data, Map<String, Object> initialFacts,
      String externalLoanId, Integer scenarioVersion, String section) {}

  record DraftScenarioView(String scenarioId, int scenarioVersion, String status, Map<String, Object> intake,
      Map<String, Object> initialFacts, Map<String, Object> data, String externalLoanId, String uiTraceId,
      List<String> events) {
    DraftScenarioView withTrace(String traceId) {
      return new DraftScenarioView(scenarioId, scenarioVersion, status, intake, initialFacts, data, externalLoanId,
          traceId, events);
    }
  }

  record IntakeValidation(boolean passed, String status, String message, Map<String, String> blockers) {}

  record QuoteRunLaunch(String runId, String status, String nextRoute, IntakeValidation validationSummary, String uiTraceId,
      List<String> events, boolean fallbackMode, String dependencyStatus, String auditPackageId, String replayHashRef,
      List<ScenarioIntakeValidationIssue> validationIssues, List<String> backendFactRefs,
      List<String> missingContractBlockers, ProgressiveQuickQuoteState quickQuoteState) {
    static QuoteRunLaunch blocked(String traceId, IntakeValidation validation) {
      return new QuoteRunLaunch(null, "BLOCKED", null, validation, traceId, List.of("UIFlowOpened"), true,
          "UPSTREAM_NOT_CALLED", null, null, List.of(), List.of(), List.of(), null);
    }
  }

  record QuoteRunContextKey(String tenantKey, String runId) {}

  record QuoteRunContext(String tenantId, String runId, List<CreditApplicationField> creditApplicationFields) {
    QuoteRunContext {
      creditApplicationFields = List.copyOf(creditApplicationFields == null ? List.of() : creditApplicationFields);
    }
  }

  record ScenarioIntakeMetadata(String tenantContext, String dependencyStatus, List<ScenarioIntakeFieldGroup> fieldGroups,
      List<String> decisionControls, List<ScenarioIntakeValidationIssue> validationIssues, String auditPackageId,
      String replayHashRef, String fallbackReason, String uiTraceId, ProgressiveQuickQuoteState quickQuoteState) {}

  record ProgressiveQuickQuoteState(List<String> minimalFirstStepFields, List<String> progressiveSectionOrder,
      List<String> quoteServiceRequiredFacts, List<String> backendOwnedFactSources, List<String> blockedByContracts,
      String fallbackReason, List<LosPrefillMapping> losPrefillMappings) {}

  record LosPrefillMapping(String fieldId, String sourceSystem, String losFieldLabel, String losFieldKey,
      String wcpeField, String confidence, String lastSync, String missingCategory, String scope,
      String authorizationState, List<String> affectedOutputTraces) {}

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

  record QuoteJourneyMapView(String tenantContext, String runId, String status, String dependencyStatus,
      List<QuoteJourneyNode> nodes, List<String> blockers, List<String> serviceContracts, String uiTraceId,
      List<String> events, String fallbackReason) {}

  record QuoteJourneyNode(String nodeId, String label, String serviceName, String status, JourneyFreshness freshness,
      List<String> evidenceRefs, List<String> blockers, String replayHash, List<String> downstreamDependencies,
      String drilldownRoute, JourneyDrilldownRefs drilldownRefs) {}

  record JourneyFreshness(String status, String evidenceRef, String message) {}

  record JourneyDrilldownRefs(String runId, String scenarioRef, String quoteRef, String lockRef,
      String correlationRef) {}

  record MarginProfitabilityView(String tenantContext, String dependencyStatus, boolean compensationDetailsVisible,
      List<MarginEvidenceSection> sections, MarginFloorEvidence floorEvidence, List<String> versionRefs,
      List<String> auditRefs, String replayHash, String uiTraceId, List<String> events, String fallbackReason) {}

  record MarginEvidenceSection(String sectionId, String label, String sourceRef, String permissionState,
      List<String> evidenceRefs, List<MarginRedactionEvidence> redactions) {}

  record MarginRedactionEvidence(String fieldLabel, String state, String reason, String auditRef) {}

  record MarginFloorEvidence(String quoteOptionId, String decision, String decisionCode, String floorPolicyVersionRef,
      String thresholdRef, String exceptionRouteRef, List<String> auditRefs, String displayGuidance) {}

  record AdjustmentEvidenceView(String tenantContext, String dependencyStatus, String status,
      List<AdjustmentEvidenceRow> adjustments, List<AdjustmentConflictView> conflicts,
      List<AdjustmentBlockedState> blockers, List<AdjustmentSummaryCard> summaries, List<String> versionRefs,
      List<String> auditRefs, String replayHash, String uiTraceId, List<String> events, String fallbackReason) {}

  record AdjustmentEvidenceRow(String adjustmentId, String label, String category, String status,
      List<String> factRefs, String sourceRef, String sourceVersionRef, String summary,
      List<String> compensationHooks, List<String> conflictIds) {}

  record AdjustmentConflictView(String conflictId, String severity, String reasonCode, String reason,
      String resolutionOwner, List<String> affectedAdjustmentIds) {}

  record AdjustmentBlockedState(String reasonCode, String message, String sourceRef, String resolutionOwner) {}

  record AdjustmentSummaryCard(String category, String summary, List<String> evidenceRefs) {}

  record ExceptionConcessionWorkbenchView(String tenantContext, String dependencyStatus, String status,
      List<ExceptionConcessionSection> sections, ManualPriceMutationGuardView manualPriceMutationGuard,
      List<String> crossServiceRefs, List<String> versionRefs, List<String> auditRefs, List<String> blockers,
      String replayHash, String exportManifestRef, String uiTraceId, List<String> events, String fallbackReason) {}

  record ExceptionConcessionSection(String sectionId, String label, String status, List<String> backendRefs,
      List<String> auditRefs, String summary) {}

  record ManualPriceMutationGuardView(boolean commitDisabled, String decision, List<String> reasonCodes,
      String escalationPath, String auditRef, String replayHash) {}

  record OfferComparisonView(String runId, String status, List<OfferSummary> offers, List<String> sortOptions,
      String selectedOfferId, boolean commitBlocked, String fallbackReason, List<String> requiredFacts,
      List<String> backendRefs, String uiTraceId, List<String> events) {
    static OfferComparisonView upstreamMissing(String runId, String traceId) {
      return upstreamMissing(runId, traceId,
          "Offer comparison requires a configured quote-service LoanPass execute-summary client before commit.");
    }

    static OfferComparisonView upstreamMissing(String runId, String traceId, String reason) {
      return new OfferComparisonView(runId, "UPSTREAM_EXPLAINABILITY_REQUIRED", List.of(),
          List.of("payment", "apr", "confidence"), null, true,
          reason,
          List.of("quote-service LoanPass execute-summary", "publishedVersionRequest metadata"),
          List.of("quote-service.execute-summary"), traceId,
          List.of("OfferListRendered"));
    }

    static OfferComparisonView fromLoanPassSummary(String runId, String traceId, LoanPassExecutionSummaryResponse response) {
      List<LoanPassExecutionProductSummary> products = response == null ? List.of() : response.products().stream()
          .sorted(Comparator.comparingInt(PricingBffUiFallbackAdapter::loanHouseOfferSortBucket))
          .toList();
      List<OfferSummary> offers = new ArrayList<>();
      for (int i = 0; i < products.size(); i++) {
        offers.add(OfferSummary.fromLoanPassSummary(runId, products.get(i), i + 1));
      }
      Map<String, Object> metadata = response == null ? Map.of() : response.metadata();
      List<String> warnings = metadataStrings(metadata, "warnings");
      if (offers.isEmpty()) {
        return new OfferComparisonView(runId, "QUOTE_SERVICE_FAIL_CLOSED", List.of(),
            List.of("rank", "rate", "apr", "lockPeriodDays"), null, true,
            "Quote-service LoanPass execute-summary returned no products; BFF kept browser flow fail-closed.",
            warnings.isEmpty() ? List.of("quote-service execute-summary products") : warnings,
            List.of("quote-service.execute-summary"), traceId, List.of("QuoteServiceSummaryFailClosed"));
      }
      return new OfferComparisonView(runId, "QUOTE_SERVICE_LOANPASS_SUMMARY_VISIBLE", offers,
          List.of("rank", "rate", "apr", "lockPeriodDays", "confidence"), null, false,
          "Quote-service LoanPass execute-summary response was normalized by pricing-bff; browser did not call quote-service directly.",
          warnings, List.of("quote-service.execute-summary"), traceId, List.of("QuoteServiceSummaryBound"));
    }

    static OfferComparisonView contractVisible(String runId, String traceId) {
      return new OfferComparisonView(runId, "QUOTE_SERVICE_EVIDENCE_VISIBLE", List.of(
          new OfferSummary("quote-option-contract-required", 1, "Backend-ranked offer", "Backend-ranked offer", null,
              null, null, "payment-ref-required", "apr-ref-required", "score:backend-owned", "rank-score-ref-required",
              null, "Backend-owned refs", List.of("quote-service.ranking"),
              List.of("Rank 1 from quote-service ranking response", "Policy and version refs are displayed without UI-side pricing math"),
              List.of("LOCK_PERIOD_REQUIRED", "FILTER_FACTS_PENDING"), "AVAILABLE", "scenario-ref-required", 7,
              List.of("eligibility-service:decision-ref-required", "pricing-service:waterfall-ref-required"),
              List.of("lock-eligibility:pending:quote-option-contract-required"),
              List.of("snapshot:quote-service:run:" + runId),
              List.of("audit:quote-ready-required", "replay-hash-required"),
              List.of("ranking", "comparison", "detail"), List.of(), List.of(), List.of(), List.of()),
          new OfferSummary("quote-option-backup-contract", 2, "Alternate backend-ranked offer", "Backend-ranked offer", null,
              null, null, "payment-ref-required", "apr-ref-required", "score:backend-owned", "rank-score-ref-required-secondary",
              null, "Backend-owned refs", List.of("quote-service.ranking"),
              List.of("Rank 2 remains selectable only when configured selection policy permits it"),
              List.of("NON_TOP_RANK_REASON_REQUIRED"), "AVAILABLE", "scenario-ref-required", 7,
              List.of("eligibility-service:alternate-decision-ref-required"),
              List.of("lock-eligibility:pending:quote-option-backup-contract"),
              List.of("snapshot:quote-service:run:" + runId),
              List.of("audit:quote-ready-required", "audit:alternate-option-required"),
              List.of("ranking", "comparison", "detail"), List.of(), List.of(), List.of(), List.of())),
          List.of("rank", "score", "confidence"), null, false,
          "Quote-service offer evidence is represented with backend-owned refs; UI actions stay blocked only when required facts are missing.",
          List.of("requestedLockPeriods", "scenarioVersion", "filterFacts"),
          List.of("quote-service.ranking", "quote-service.explanation", "quote-service.selection"), traceId,
          List.of("OfferListRendered", "QuoteServiceEvidenceBound"));
    }
  }

  record OfferSummary(String offerId, int rank, String productLabel, String productFamily, String investor,
      String rate, String price, String payment, String apr, String confidence, String rankScore, String lockPeriodDays,
      String sourceLabel, List<String> sourceRefs, List<String> rationaleChips, List<String> scenarioFlags,
      String explanationStatus, String sourceScenarioId, int scenarioVersion, List<String> upstreamRefs,
      List<String> lockEligibilityRefs, List<String> snapshotRefs, List<String> auditIds, List<String> explanationSections,
      List<String> productRuleRefs, List<String> stipulationRefs, List<String> rateRefs, List<String> lockPeriodOptions) {
    static OfferSummary fromLoanPassSummary(String runId, LoanPassExecutionProductSummary product, int rank) {
      String productId = safeText(product.productId(), "loanpass-product-" + rank);
      Map<String, Object> status = product.status();
      List<String> productRules = fieldRefs(product.productFields(), List.of("rule", "requirement", "eligibility"));
      List<String> stipulations = fieldRefs(product.calculatedFields(), List.of("stip", "condition"));
      List<String> rates = fieldRefs(product.calculatedFields(), List.of("rate", "apr", "price", "payment"));
      List<String> locks = fieldRefs(product.calculatedFields(), List.of("lock"));
      List<String> sourceRefs = sourceEvidenceRefs(product.productFields(), product.versionNumber());
      String sourceLabel = sourceEvidenceLabel(sourceRefs, product.versionNumber());
      return new OfferSummary(productId, rank, firstNonBlank(product.productName(), product.productCode(), productId),
          product.productName(), product.investorName(), fieldValue(product.calculatedFields(), List.of("note-rate", "noteRate")),
          fieldValue(product.calculatedFields(), List.of("quote-service-price")),
          fieldValue(product.calculatedFields(), List.of("quote-service-payment", "payment")),
          fieldValue(product.calculatedFields(), List.of("apr")),
          statusText(status, "type", product.isPricingEnabled() == null || product.isPricingEnabled() ? "AVAILABLE" : "PRICING_DISABLED"),
          "rank:" + rank, fieldValue(product.calculatedFields(), List.of("lock-days", "lock")), sourceLabel, sourceRefs,
          statusMessages(status), statusFlags(status), statusText(status, "type", "AVAILABLE"),
          runId, 1, mergeRefs(List.of("quote-service.execute-summary:product:" + productId), sourceRefs),
          locks.isEmpty() ? List.of("lock-period:quote-service-ref-required") : locks,
          List.of("snapshot:quote-service:run:" + runId), List.of("audit:quote-service-summary:" + productId),
          List.of("summary", "rates", "rules", "stipulations", "locks"), productRules, stipulations, rates, locks);
    }

    static OfferSummary fromLoanPassProduct(String runId, LoanPassProductExecutionResult product, String fallbackOfferId) {
      String productId = safeText(product.productId(), fallbackOfferId);
      Map<String, Object> status = product.status();
      List<String> productRules = fieldRefs(product.productFields(), List.of("rule", "requirement", "eligibility"));
      List<String> stipulations = fieldRefs(product.calculatedFields(), List.of("stip", "condition"));
      List<String> rates = fieldRefs(product.calculatedFields(), List.of("rate", "apr", "price", "payment"));
      List<String> locks = fieldRefs(product.calculatedFields(), List.of("lock"));
      List<String> sourceRefs = mergeRefs(sourceEvidenceRefs(product.productFields(), product.versionNumber()), metadataStrings(product.metadata(), "source"));
      String sourceLabel = sourceEvidenceLabel(sourceRefs, product.versionNumber());
      return new OfferSummary(productId, 1, firstNonBlank(product.productName(), product.productCode(), productId),
          product.productName(), product.investorName(), fieldValue(product.calculatedFields(), List.of("note-rate", "noteRate")),
          fieldValue(product.calculatedFields(), List.of("quote-service-price")),
          fieldValue(product.calculatedFields(), List.of("quote-service-payment", "payment")),
          fieldValue(product.calculatedFields(), List.of("apr")),
          statusText(status, "type", product.isPricingEnabled() == null || product.isPricingEnabled() ? "AVAILABLE" : "PRICING_DISABLED"),
          "quote-service-product-detail", fieldValue(product.calculatedFields(), List.of("lock-days", "lock")), sourceLabel, sourceRefs,
          statusMessages(status), statusFlags(status), statusText(status, "type", "AVAILABLE"),
          runId, 1, mergeRefs(List.of("quote-service.execute-product:product:" + productId), sourceRefs),
          locks, List.of("snapshot:quote-service:run:" + runId + ":product:" + productId),
          List.of("audit:quote-service-product:" + productId),
          List.of("summary", "rates", "rules", "stipulations", "locks"), productRules, stipulations, rates, locks);
    }
  }

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

  record QuoteDetailView(String tenantContext, String runId, String offerId, String status, OfferSummary summary,
      OfferExplanationView explanation, PricingWaterfallView waterfall, List<QuoteDetailPanel> panels,
      List<QuoteDetailRedaction> redactions, List<String> complianceFlags, List<String> auditRefs, String replayHash,
      String evidenceHash, String uiTraceId, List<String> events, String fallbackReason) {
    static QuoteDetailView blocked(String tenantId, String runId, String offerId, String traceId, String reason) {
      OfferSummary summary = new OfferSummary(offerId, 0, "Quote service product detail unavailable", null, null,
          null, null, null, null, "BLOCKED", "N/A", null, "Quote-service unavailable", List.of("quote-service.execute-product"),
          List.of(reason), List.of("QUOTE_SERVICE_CLIENT_REQUIRED"), "BLOCKED",
          runId, 0, List.of("quote-service.execute-product"), List.of(), List.of(), List.of(),
          List.of("product-detail"), List.of(), List.of(), List.of(), List.of());
      PricingWaterfallView waterfall = blockedWaterfall(tenantId, runId, traceId, reason);
      return new QuoteDetailView(tenantId, runId, offerId, "BLOCKED", summary,
          OfferExplanationView.missing(runId, offerId, traceId), waterfall,
          List.of(new QuoteDetailPanel("summary", "LoanPass product detail", "BLOCKED",
              List.of("rules", "stipulations", "rates", "lock periods"), List.of("quote-service.execute-product"), List.of(reason))),
          List.of(), List.of(), List.of("audit:quote-service-product-detail-blocked"),
          "quote-detail-replay-hash-unavailable", "quote-detail-evidence-hash-unavailable", traceId,
          List.of("QuoteServiceProductDetailFailClosed"), reason);
    }

    static QuoteDetailView fromLoanPassProduct(String tenantId, String runId, String offerId, String traceId,
        LoanPassProductExecutionResult product) {
      OfferSummary summary = OfferSummary.fromLoanPassProduct(runId, product, offerId);
      PricingWaterfallView waterfall = waterfallFromLoanPassProduct(tenantId, runId, traceId, product);
      List<String> blockers = metadataStrings(product.metadata(), "warnings");
      return new QuoteDetailView(tenantId, runId, summary.offerId(),
          blockers.isEmpty() ? "QUOTE_SERVICE_LOANPASS_PRODUCT_VISIBLE" : "QUOTE_SERVICE_PRODUCT_FAIL_CLOSED",
          summary, OfferExplanationView.available(runId, summary.offerId(), traceId), waterfall,
          List.of(
              new QuoteDetailPanel("summary", "LoanPass product summary", "VISIBLE",
                  List.of("productId", "productName", "productCode", "investorName"), List.of("quote-service.execute-product"), List.of()),
              new QuoteDetailPanel("rules", "Rules", summary.productRuleRefs().isEmpty() ? "EMPTY" : "VISIBLE",
                  summary.productRuleRefs(), List.of("quote-service.productFields"), List.of()),
              new QuoteDetailPanel("stipulations", "Stipulations", summary.stipulationRefs().isEmpty() ? "EMPTY" : "VISIBLE",
                  summary.stipulationRefs(), List.of("quote-service.calculatedFields"), List.of()),
              new QuoteDetailPanel("rates", "Rates", summary.rateRefs().isEmpty() ? "EMPTY" : "VISIBLE",
                  summary.rateRefs(), List.of("quote-service.calculatedFields"), List.of()),
              new QuoteDetailPanel("locks", "Lock periods", summary.lockPeriodOptions().isEmpty() ? "EMPTY" : "VISIBLE",
                  summary.lockPeriodOptions(), List.of("quote-service.calculatedFields"), List.of())),
          List.of(), statusFlags(product.status()), List.of("audit:quote-service-product-detail:" + summary.offerId()),
          "replay:quote-service-product:" + summary.offerId(), "evidence:quote-service-product:" + summary.offerId(), traceId,
          List.of("QuoteServiceProductDetailBound"),
          "Quote-service LoanPass execute-product response was normalized by pricing-bff; browser did not call quote-service directly.");
    }
  }

  record QuoteDetailPanel(String panelId, String label, String status, List<String> fields, List<String> backendRefs,
      List<String> blockers) {}

  record QuoteDetailRedaction(String fieldPath, String state, String reason, String auditRef) {}

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

    static LockConfirmationResult failClosed(String runId, String selectedOfferId, String traceId) {
      return new LockConfirmationResult(runId, selectedOfferId, "BLOCKED", null, "LOCK_SERVICE_EVIDENCE_REQUIRED",
          null, null,
          "Lock confirmation requires durable lock-service evidence; pricing-bff does not synthesize confirmed locks.",
          traceId, List.of("LockBlocked"),
          List.of("Durable lock-service confirmation evidence is required before returning CONFIRMED."),
          lifecycleAuditGroups(runId, "evidence-required-" + selectedOfferId));
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
      List<ComplianceAdvisoryReview> advisoryReviews,
      List<FairLendingMonitoringDrilldown> fairLendingMonitoring,
      List<PrivacyRequestSummary> privacyRequests, List<SecurityEventSummary> securityEvents,
      List<ComplianceAlertSummary> alerts, List<RetentionControlSummary> retentionControls,
      List<String> configurationGaps, String uiTraceId, List<String> events, String fallbackReason) {}

  record ComplianceEvidenceArtifact(String artifactId, String path, String artifactType, String owner,
      String retentionClass, String relatedModule, String version, String hash, String traceId, String policyVersion,
      String policyDigest, String jurisdictionCode, String continuityStatus, List<String> moduleLinks,
      boolean progressionBlocked, List<String> blockers) {}

  record ComplianceDecisionRationale(String decisionId, String reasonCode, String humanText, String jurisdictionCode,
      List<String> reasonTiers, boolean exportBlocked, String disclosureArtifactRef) {}

  record ComplianceAdvisoryReview(String reviewId, String reviewType, String subjectRef, String status,
      List<String> reasonCodes, List<String> auditSnapshotRefs, String regulatoryApprovalState, List<String> exportRefs,
      boolean blockedByConfiguration, List<String> configurationGaps) {}

  record FairLendingMonitoringDrilldown(String drilldownId, List<String> dimensions, boolean redacted,
      String redactionState, List<String> evidenceRefs, List<String> blockers) {}

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

  record CustomRuleFieldsUiDto(String scenarioId, String tenantContext, String dependencyStatus,
      String resultStatus, String uiTraceId, List<CustomRuleFieldUiDto> fields,
      List<String> factQualityOptions, List<String> validationMessages, List<String> metadataVersionRefs,
      List<CustomRuleUiError> errors, List<String> events) {}

  record CustomRuleFieldUiDto(String fieldRef, String label, String dataType, String sourceService,
      String versionRef, String factQuality, boolean requiredForRules, List<String> allowedValues,
      List<String> evidenceRefs) {}

  record CustomRuleEvidenceUiDto(String quoteId, String tenantContext, String dependencyStatus,
      String resultStatus, String uiTraceId, List<CustomRuleEvidenceUiRule> matchedRules,
      List<CustomRuleEvidenceUiRule> skippedRules, List<CustomRuleEvidenceUiRule> blockedRules,
      List<CustomRuleCalculationStepUiDto> calculationSteps, List<String> reasonCodes,
      List<String> auditRefs, List<String> replayHashRefs, List<CustomRuleUiError> errors,
      List<String> events) {}

  record CustomRuleEvidenceUiRule(String ruleRef, String versionRef, String outcome, String reasonCode,
      String sourceService, List<String> factRefs, List<String> auditRefs, List<String> replayHashRefs) {}

  record CustomRuleCalculationStepUiDto(String stepRef, String sourceService, String status,
      String evidenceRef, String summary) {}

  record CustomRuleUiError(String code, String sourceService, String message) {}

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

  record ScenarioAnalysisWorkspaceView(String tenantContext, String runId, String dependencyStatus,
      List<ScenarioAnalysisDimension> dimensions, List<ScenarioAnalysisVariant> variants,
      List<ScenarioAnalysisBatchRow> batchGrid, List<ScenarioAnalysisSavedAnalysis> savedAnalyses,
      List<String> exportRefs, List<String> replayRefs, List<ScenarioAnalysisBlocker> blockers,
      String fallbackReason, String uiTraceId, List<String> events) {}

  record ScenarioAnalysisDimension(String dimensionId, String label, String value, String sourceRef,
      List<String> requiredFacts, boolean backendOnly) {}

  record ScenarioAnalysisVariant(String variantId, String label, String status, List<String> dimensionRefs,
      List<String> factRefs, List<ScenarioAnalysisBlocker> guardrailBlockers, List<String> resultRefs) {}

  record ScenarioAnalysisBatchRow(String rowId, String variantId, String dimensionSummary, String status,
      String backendResultRef, String guardrailSummary) {}

  record ScenarioAnalysisSavedAnalysis(String analysisId, String name, String versionRef, String savedAt,
      String exportRef, String replayHash) {}

  record ScenarioAnalysisBlocker(String blockerCode, String severity, String reason, List<String> requiredFacts,
      String sourceRef) {}

  record ScenarioRecalculationResult(String runId, String tenantContext, String status, String message,
      List<String> backendResultRefs, List<ScenarioAnalysisBlocker> blockers, List<String> events, String uiTraceId) {}

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

  record MlAdvisoryInsightsView(String tenantContext, String dependencyStatus, String uiTraceId,
      List<AdvisoryRecommendationInsight> recommendations,
      List<ModelVersionGovernanceInsight> modelVersions, boolean advisoryUnavailable,
      String fallbackReason, List<String> events) {}

  record AdvisoryRecommendationInsight(String recommendationId, String modelVersion, String confidence,
      String explanation, List<String> allowedActions, List<String> auditRefs, boolean automaticDecisionApplied) {}

  record ModelVersionGovernanceInsight(String modelVersion, String driftStatus, String alertState,
      List<String> feedbackLoops, List<String> exportEvidenceRefs) {}

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

  record PartnerChannelWorkbenchView(String partnerId, String tenantContext, String dependencyStatus,
      List<PartnerChannelWorkbenchTab> tabs, PartnerServiceAccountBlockedState serviceAccount, String fallbackReason,
      String uiTraceId, List<String> events) {}

  record PartnerChannelWorkbenchTab(String tabId, String label, String route, String status, String recoveryOwner,
      List<PartnerChannelWorkbenchItem> items) {}

  record PartnerChannelWorkbenchItem(String itemId, String label, String state, String retryState, String dlqReason,
      String payloadRedactionState, List<String> auditRefs) {}

  record PartnerServiceAccountBlockedState(boolean blocked, String missingCapability, String recoveryOwner,
      String credentialExposure) {}

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

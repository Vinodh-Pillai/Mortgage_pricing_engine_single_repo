package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-catalog")
class CatalogController {
  private final CatalogService service;
  private final DomainRepository domainRepository;
  private final AuthorizationService authorizationService;

  CatalogController(CatalogService service, DomainRepository domainRepository, AuthorizationService authorizationService) {
    this.service = service;
    this.domainRepository = domainRepository;
    this.authorizationService = authorizationService;
  }

  @PostMapping("/conventional-products/drafts")
  ConventionalProductDraftResponse addConventionalProduct(@PathVariable UUID tenantId, @RequestBody ConventionalProductDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addConventionalProductDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/products/drafts")
  CatalogResponse addProduct(@PathVariable UUID tenantId, @RequestBody ProductRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addProduct(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/investors/drafts")
  InvestorCatalogDraftResponse addInvestor(@PathVariable UUID tenantId, @RequestBody InvestorCatalogDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addInvestorCatalogDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/investors/resolve")
  InvestorResolveResponse resolveInvestors(@PathVariable UUID tenantId, @RequestBody InvestorResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveInvestors(tenantId, request, h.actorId, h.correlationId, canViewInvestorSecret(h.roles));
  }

  @PostMapping("/taxonomy/drafts")
  ProductTaxonomyDraftResponse addTaxonomy(@PathVariable UUID tenantId, @RequestBody ProductTaxonomyDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addProductTaxonomyDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/taxonomy/resolve")
  ProductTaxonomyResolveResponse resolveTaxonomy(@PathVariable UUID tenantId, @RequestBody ProductTaxonomyResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveProductTaxonomy(tenantId, request, h.actorId, h.correlationId);
  }

  @PostMapping("/channels/drafts")
  ChannelTaxonomyDraftResponse addChannel(@PathVariable UUID tenantId, @RequestBody ChannelTaxonomyDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addChannelTaxonomyDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/channels/resolve")
  ChannelResolveResponse resolveChannel(@PathVariable UUID tenantId, @RequestBody ChannelResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveChannel(tenantId, request, h.actorId, h.correlationId);
  }

  @PostMapping("/term-amortization/drafts")
  TermAmortizationDraftResponse addTermAmortization(@PathVariable UUID tenantId, @RequestBody TermAmortizationDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addTermAmortizationDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/term-amortization/resolve")
  TermAmortizationResolveResponse resolveTermAmortization(@PathVariable UUID tenantId, @RequestBody TermAmortizationResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveTermAmortization(tenantId, request, h.actorId, h.correlationId);
  }

  @PostMapping("/property-types/drafts")
  PropertyTypeDraftResponse addPropertyType(@PathVariable UUID tenantId, @RequestBody PropertyTypeDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addPropertyTypeDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/occupancy-types/drafts")
  OccupancyTypeDraftResponse addOccupancyType(@PathVariable UUID tenantId, @RequestBody OccupancyTypeDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addOccupancyTypeDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/property-occupancy/resolve")
  PropertyOccupancyResolveResponse resolvePropertyOccupancy(@PathVariable UUID tenantId, @RequestBody PropertyOccupancyResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolvePropertyOccupancy(tenantId, request, h.actorId, h.correlationId);
  }

  @GetMapping("/property-occupancy")
  PropertyOccupancyListResponse listPropertyOccupancy(@PathVariable UUID tenantId, @RequestParam(required = false) java.time.Instant asOf, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.listPublishedPropertyOccupancy(tenantId, asOf);
  }

  @PostMapping("/loan-purposes/drafts")
  @ResponseStatus(HttpStatus.CREATED)
  LoanPurposeDraftResponse addLoanPurpose(@PathVariable UUID tenantId, @RequestBody LoanPurposeDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addLoanPurposeDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/loan-purposes/resolve")
  LoanPurposeResolveResponse resolveLoanPurpose(@PathVariable UUID tenantId, @RequestBody LoanPurposeResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveLoanPurpose(tenantId, request, h.actorId, h.correlationId);
  }

  @PostMapping("/markets/imports")
  @ResponseStatus(HttpStatus.ACCEPTED)
  MarketImportResponse importMarkets(@PathVariable UUID tenantId, @RequestBody MarketImportRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.importMarkets(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/markets/resolve")
  MarketResolveResponse resolveMarket(@PathVariable UUID tenantId, @RequestBody MarketResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveMarket(tenantId, request, h.actorId, h.correlationId);
  }

  @PostMapping("/versions/current/actions/draft")
  CatalogResponse draft(@PathVariable UUID tenantId, @RequestBody LifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.draft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/validate")
  CatalogResponse validate(@PathVariable UUID tenantId, @RequestBody LifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.validate(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/submit-approval")
  CatalogResponse submitApproval(@PathVariable UUID tenantId, @RequestBody LifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.submitApproval(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/approve")
  CatalogResponse approve(@PathVariable UUID tenantId, @RequestBody LifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("APPROVE_CATALOG", h.roles); return withRoles(h.roles, () -> service.approve(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/reject")
  CatalogResponse reject(@PathVariable UUID tenantId, @RequestBody RejectCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("APPROVE_CATALOG", h.roles); return withRoles(h.roles, () -> service.reject(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/publish")
  CatalogResponse publish(@PathVariable UUID tenantId, @RequestBody PublishCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("PUBLISH_CATALOG", h.roles); return withRoles(h.roles, () -> service.publish(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/suspend")
  CatalogResponse suspend(@PathVariable UUID tenantId, @RequestBody LifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("PUBLISH_CATALOG", h.roles); return withRoles(h.roles, () -> service.suspend(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/retire")
  CatalogResponse retire(@PathVariable UUID tenantId, @RequestBody LifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("PUBLISH_CATALOG", h.roles); return withRoles(h.roles, () -> service.retire(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/current/actions/rollback")
  CatalogResponse rollback(@PathVariable UUID tenantId, @RequestBody VersionedLifecycleActionRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("ROLLBACK_CATALOG", h.roles); return withRoles(h.roles, () -> service.rollback(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/versions/{artifactType}/{artifactId}/actions")
  CatalogVersionActionResponse applyVersionAction(@PathVariable UUID tenantId, @PathVariable String artifactType, @PathVariable UUID artifactId, @RequestBody CatalogVersionActionRequest request, HttpServletRequest http) {
    Headers h = headers(http);
    String action = request == null || request.action() == null ? "" : request.action().toUpperCase(Locale.ROOT);
    authorizationService.authorize(permissionForVersionAction(action), h.roles);
    return withRoles(h.roles, () -> service.applyVersionAction(tenantId, artifactType, artifactId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @GetMapping("/versions/{artifactType}/{artifactCode}/as-of")
  CatalogVersionAsOfResponse resolveVersionAsOf(@PathVariable UUID tenantId, @PathVariable String artifactType, @PathVariable String artifactCode, @RequestParam(required = false) java.time.Instant asOf, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.resolveVersionAsOf(tenantId, artifactType, artifactCode, asOf == null ? java.time.Instant.now() : asOf);
  }

  @GetMapping("/active")
  CatalogResponse active(@PathVariable UUID tenantId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.active(tenantId);
  }

  @GetMapping("/current")
  CatalogResponse current(@PathVariable UUID tenantId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.current(tenantId);
  }

  @PostMapping("/config-snapshots/resolve")
  ProductConfigSnapshot resolve(@PathVariable UUID tenantId, @RequestBody ResolveCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolve(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId);
  }

  @PostMapping("/conventional-products/resolve")
  ConventionalProductResolveResponse resolveConventionalProducts(@PathVariable UUID tenantId, @RequestBody ConventionalProductResolveRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveConventionalProducts(tenantId, request, h.actorId, h.correlationId);
  }

  @GetMapping("/config-snapshots/{snapshotId}")
  ProductConfigSnapshot snapshot(@PathVariable UUID tenantId, @PathVariable UUID snapshotId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.snapshot(tenantId, snapshotId);
  }

  @GetMapping("/events")
  List<CatalogEvent> events(@PathVariable UUID tenantId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.events(tenantId);
  }

  @GetMapping("/audit")
  List<CatalogAuditRecord> audit(@PathVariable UUID tenantId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.audit(tenantId);
  }

  @GetMapping("/versions")
  List<CatalogVersionControlRecord> versions(@PathVariable UUID tenantId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.versions(tenantId);
  }

  @GetMapping("/investors")
  InvestorListResponse listInvestors(@PathVariable UUID tenantId, @RequestParam(required = false) String status, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles);
    return new InvestorListResponse(domainRepository.listInvestors(tenantId, status), domainRepository.listInvestors(tenantId, status).size());
  }

  @PostMapping("/investors")
  InvestorResponse upsertInvestor(@PathVariable UUID tenantId, @RequestBody InvestorUpsertRequest request, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("WRITE_CATALOG", roles);
    return domainRepository.upsertInvestor(tenantId, request);
  }

  @GetMapping("/investors/{investorId}/eligibility")
  InvestorEligibilityMatrixResponse investorEligibility(@PathVariable UUID tenantId, @PathVariable UUID investorId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles);
    return new InvestorEligibilityMatrixResponse(investorId, domainRepository.investorEligibility(tenantId, investorId));
  }

  @PostMapping("/investors/{investorId}/eligibility")
  InvestorEligibilityMatrixResponse upsertInvestorEligibility(@PathVariable UUID tenantId, @PathVariable UUID investorId, @RequestBody InvestorEligibilityMatrixRequest request, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("WRITE_CATALOG", roles);
    return domainRepository.upsertInvestorEligibility(tenantId, investorId, request);
  }

  @GetMapping("/products")
  ProductListResponse listProductsDomain(@PathVariable UUID tenantId, @RequestParam(required = false) String status, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles);
    return new ProductListResponse(domainRepository.listProducts(tenantId, status), domainRepository.listProducts(tenantId, status).size());
  }

  @GetMapping("/channels")
  ChannelListResponse listChannelsDomain(@PathVariable UUID tenantId, @RequestParam(required = false) String status, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles);
    return new ChannelListResponse(domainRepository.listChannels(tenantId, status), domainRepository.listChannels(tenantId, status).size());
  }

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, Object>> error(CatalogException ex, HttpServletRequest request) {
    String errorCode = ex.getMessage();
    HttpStatus status = catalogErrorStatus(errorCode);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("errorCode", errorCode);
    body.put("code", errorCode);
    body.put("message", errorCode);
    body.put("fieldErrors", fieldErrors(errorCode));
    body.put("correlationId", request.getHeader("X-Correlation-Id"));
    return ResponseEntity.status(status).body(body);
  }

  private static List<Map<String, String>> fieldErrors(String errorCode) {
    if ("VERSION_CONFLICT".equals(errorCode) || "CATALOG_VERSION_CONFLICT".equals(errorCode)) {
      return List.of(Map.of(
          "field", "rowVersion",
          "code", "STALE",
          "message", "Reload and retry with the latest row version."));
    }
    if ("NO_ELIGIBLE_CONVENTIONAL_PRODUCT".equals(errorCode)) {
      return List.of(Map.of(
          "field", "scenarioFacts",
          "code", errorCode,
          "message", "No eligible conventional product matched the supplied scenario facts."));
    }
    if ("MISSING_SCENARIO_FACTS".equals(errorCode)) {
      return List.of(Map.of(
          "field", "scenarioFacts",
          "code", errorCode,
          "message", "Required conventional product scenario facts were not supplied."));
    }
    if ("CHANNEL_MAPPING_NOT_FOUND".equals(errorCode)) {
      return List.of(Map.of(
          "field", "externalValue",
          "code", "UNKNOWN_MAPPING",
          "message", "Select or map a valid channel."));
    }
    if ("INVESTOR_NOT_ACTIVE_FOR_CHANNEL".equals(errorCode)) {
      return List.of(Map.of(
          "field", "channelCode",
          "code", "NOT_ENABLED",
          "message", "Choose a channel enabled for this investor."));
    }
    if ("TERM_AMORTIZATION_NOT_SUPPORTED".equals(errorCode)) {
      return List.of(Map.of(
          "field", "termMonths",
          "code", "UNSUPPORTED_TERM",
          "message", "Use one of 120, 180, 240, 360 months for published fixed profiles."));
    }
    if ("PROPERTY_OCCUPANCY_NOT_PUBLISHED".equals(errorCode)) {
      return List.of(Map.of(
          "field", "propertyType",
          "code", "NOT_PUBLISHED",
          "message", "Select a published property type."));
    }
    if ("LOAN_PURPOSE_NOT_SUPPORTED".equals(errorCode) || "CONSTRUCTION_TO_PERMANENT_DISABLED".equals(errorCode)) {
      return List.of(Map.of(
          "field", "loanPurpose",
          "code", "DISABLED",
          "message", "Select Purchase, Rate/Term Refinance, or Cash-Out Refinance."));
    }
    if ("MARKET_RESTRICTED".equals(errorCode)) {
      return List.of(Map.of(
          "field", "countyFips",
          "code", "RESTRICTED",
          "message", "Select an enabled property market or escalate to product operations."));
    }
    if ("PRODUCT_CONFIG_SNAPSHOT_UNAVAILABLE".equals(errorCode)) {
      return List.of(Map.of(
          "field", "asOf",
          "code", "NO_PUBLISHED_CONFIG",
          "message", "Choose a timestamp with published catalog configuration."));
    }
    if ("MARKET_NOT_ENABLED".equals(errorCode)) {
      return List.of(Map.of(
          "field", "countyFips",
          "code", "MARKET_NOT_ENABLED",
          "message", "Choose an enabled property market for the requested channel and product family."));
    }
    if ("INCLUDE_INACTIVE_REQUIRES_DEBUG_PERMISSION".equals(errorCode)) {
      return List.of(Map.of(
          "field", "includeInactive",
          "code", "PERMISSION_DENIED",
          "message", "includeInactive requires product catalog debug permission."));
    }
    if ("INVALID_COUNTY_FIPS".equals(errorCode)) {
      return List.of(Map.of(
          "field", "countyFips",
          "code", "INVALID_COUNTY_FIPS",
          "message", "County FIPS must be five digits and match the submitted state."));
    }
    if ("INVALID_STATE_CODE".equals(errorCode)) {
      return List.of(Map.of(
          "field", "stateCode",
          "code", "INVALID_STATE_CODE",
          "message", "Use a USPS state code or DC."));
    }
    return List.of();
  }

  private static String permissionForVersionAction(String action) {
    return switch (action) {
      case "VALIDATE", "SUBMIT_APPROVAL" -> "WRITE_CATALOG";
      case "APPROVE", "REJECT" -> "APPROVE_CATALOG";
      case "PUBLISH", "SUSPEND", "RETIRE" -> "PUBLISH_CATALOG";
      case "ROLLBACK" -> "ROLLBACK_CATALOG";
      default -> "WRITE_CATALOG";
    };
  }

  private static boolean canViewInvestorSecret(String roles) {
    if (roles == null) return false;
    return Arrays.stream(roles.split(",")).map(String::trim).anyMatch(role -> role.equals("INVESTOR_SECRET_VIEW") || role.equals("investor:secret-view"));
  }

  private Headers headers(HttpServletRequest request) {
    return new Headers(request.getHeader("Idempotency-Key"), request.getHeader("X-Actor-Id"), request.getHeader("X-Correlation-Id"), request.getHeader("X-Roles"));
  }

  private <T> T withRoles(String roles, java.util.function.Supplier<T> action) {
    try {
      RequestContext.roles(roles);
      return action.get();
    } finally {
      RequestContext.clear();
    }
  }

  static HttpStatus catalogErrorStatus(String errorCode) {
    if ("SEPARATION_OF_DUTIES_VIOLATION".equals(errorCode) || "INCLUDE_INACTIVE_REQUIRES_DEBUG_PERMISSION".equals(errorCode)) return HttpStatus.FORBIDDEN;
    if ("IDEMPOTENCY_CONFLICT".equals(errorCode) || "VERSION_CONFLICT".equals(errorCode) || "CATALOG_VERSION_CONFLICT".equals(errorCode) || "IMPORT_ALREADY_PROCESSED".equals(errorCode)) return HttpStatus.CONFLICT;
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }

  record Headers(String idempotencyKey, String actorId, String correlationId, String roles) {}
}

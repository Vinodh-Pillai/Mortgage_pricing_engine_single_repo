package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import com.wcpe.catalog.auth.TenantProductAuthorization;
import com.wcpe.catalog.auth.TenantProductAuthorizationService;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/product-catalog")
class CatalogController {
  private final CatalogService service;
  private final DomainRepository domainRepository;
  private final AuthorizationService authorizationService;
  private final TenantProductAuthorizationService tenantAuthorizationService;

  CatalogController(CatalogService service, DomainRepository domainRepository, AuthorizationService authorizationService) {
    this(service, domainRepository, authorizationService, null);
  }

  @Autowired
  CatalogController(CatalogService service, DomainRepository domainRepository, AuthorizationService authorizationService, TenantProductAuthorizationService tenantAuthorizationService) {
    this.service = service;
    this.domainRepository = domainRepository;
    this.authorizationService = authorizationService;
    this.tenantAuthorizationService = tenantAuthorizationService;
  }

  @PostMapping("/conventional-products/drafts")
  ConventionalProductDraftResponse addConventionalProduct(@PathVariable UUID tenantId, @RequestBody ConventionalProductDraftRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addConventionalProductDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/products/drafts")
  CatalogResponse addProduct(@PathVariable UUID tenantId, @RequestBody ProductRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addProduct(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/enumerations/imports")
  @ResponseStatus(HttpStatus.ACCEPTED)
  EnumerationCatalogImportResponse importEnumerations(@PathVariable UUID tenantId, @RequestBody EnumerationCatalogImportRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.importEnumerations(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PutMapping("/enumerations/{enumTypeId}")
  EnumerationCatalogUpdateResponse updateEnumeration(@PathVariable UUID tenantId, @PathVariable String enumTypeId,
                                                     @RequestBody EnumerationCatalogUpdateRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.updateEnumeration(tenantId, enumTypeId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/field-metadata/imports")
  @ResponseStatus(HttpStatus.ACCEPTED)
  FieldMetadataImportResponse importFieldMetadata(@PathVariable UUID tenantId, @RequestBody FieldMetadataImportRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.importFieldMetadata(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/product-specification/fields/imports/system")
  @ResponseStatus(HttpStatus.ACCEPTED)
  ProductSpecificationSystemFieldImportResponse importProductSpecificationFieldsFromSystem(@PathVariable UUID tenantId,
                                                                                           @RequestBody ProductSpecificationSystemFieldImportRequest request,
                                                                                           HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.importProductSpecificationFieldsFromSystem(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @GetMapping("/field-metadata")
  List<FieldMetadataResponse> listFieldMetadata(@PathVariable UUID tenantId, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.listFieldMetadata(tenantId, h.actorId, h.correlationId);
  }

  @GetMapping("/field-library")
  FieldLibraryQueryResponse queryFieldLibrary(@PathVariable UUID tenantId, @RequestParam String category,
                                               @RequestParam(defaultValue = "false") boolean includeEnums,
                                               HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.queryFieldLibrary(tenantId, category, includeEnums, h.actorId, h.correlationId);
  }

  @GetMapping("/product-specification/fields")
  ProductSpecificationFieldListResponse productSpecificationFields(@PathVariable UUID tenantId, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.productSpecificationFields(tenantId, h.actorId, h.correlationId);
  }

  @PutMapping("/field-consumer-mappings/{consumer}")
  FieldConsumerMappingResponse saveFieldConsumerMapping(@PathVariable UUID tenantId, @PathVariable String consumer,
                                                        @RequestBody FieldConsumerMappingRequest request,
                                                        HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.saveFieldConsumerMapping(tenantId, consumer, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @GetMapping("/field-consumer-mappings/{consumer}")
  FieldConsumerMappingResponse resolveFieldConsumerMapping(@PathVariable UUID tenantId, @PathVariable String consumer,
                                                           @RequestParam(defaultValue = "tenant-active") String mappingScope,
                                                           HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveFieldConsumerMapping(tenantId, consumer, mappingScope, h.actorId, h.correlationId);
  }

  @GetMapping("/field-metadata/{fieldId}/consumer-references")
  FieldConsumerReferenceResponse fieldConsumerReferences(@PathVariable UUID tenantId, @PathVariable String fieldId, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.fieldConsumerReferences(tenantId, fieldId, h.actorId, h.correlationId);
  }

  @PutMapping("/product-specification/fields/order-draft")
  ProductSpecificationFieldOrderDraftResponse saveProductSpecificationFieldOrderDraft(@PathVariable UUID tenantId,
                                                                                       @RequestBody ProductSpecificationFieldOrderDraftRequest request,
                                                                                       HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.saveProductSpecificationFieldOrderDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PutMapping("/product-specification/fields/tenant-draft")
  ProductSpecificationTenantFieldDraftResponse saveProductSpecificationTenantFieldDraft(@PathVariable UUID tenantId,
                                                                                         @RequestBody ProductSpecificationTenantFieldDraftRequest request,
                                                                                         HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.saveProductSpecificationTenantFieldDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PutMapping("/product-specification/fields/condition-draft")
  ProductSpecificationConditionDraftResponse saveProductSpecificationConditionDraft(@PathVariable UUID tenantId,
                                                                                   @RequestBody ProductSpecificationConditionDraftRequest request,
                                                                                   HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.saveProductSpecificationConditionDraft(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/product-specification/fields/{fieldId}/conditions/evaluate")
  ProductSpecificationFieldConditionEvaluationResponse evaluateProductSpecificationFieldConditions(@PathVariable UUID tenantId,
                                                                                                  @PathVariable String fieldId,
                                                                                                  @RequestBody(required = false) ProductSpecificationFieldConditionEvaluationRequest request,
                                                                                                  HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.evaluateProductSpecificationFieldConditions(tenantId, fieldId, request, h.actorId, h.correlationId);
  }

  @GetMapping("/field-metadata/{fieldId}")
  FieldMetadataResponse getFieldMetadata(@PathVariable UUID tenantId, @PathVariable String fieldId, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveFieldMetadata(tenantId, fieldId, h.actorId, h.correlationId);
  }

  @GetMapping("/enumerations/{enumTypeId}")
  EnumerationTypeResponse getEnumeration(@PathVariable UUID tenantId, @PathVariable String enumTypeId,
                                         @RequestParam(required = false) java.time.LocalDate asOf, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveEnumeration(tenantId, enumTypeId, asOf, h.actorId, h.correlationId);
  }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  ProductCreationResponse createProduct(@PathVariable UUID tenantId, @RequestBody ProductCreationRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.createProduct(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PutMapping("/products/{productCode}/pricing-configuration")
  ProductPricingConfigurationResponse attachProductPricingConfiguration(@PathVariable UUID tenantId, @PathVariable String productCode, @RequestBody ProductPricingConfigurationRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles);
    ProductPricingConfigurationRequest scoped = new ProductPricingConfigurationRequest(productCode, request == null ? null : request.effectiveStart(), request == null ? null : request.effectiveEnd(), request == null ? null : request.refs());
    return withRoles(h.roles, () -> service.attachProductPricingConfiguration(tenantId, scoped, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @GetMapping("/products/{productCode}/pricing-configuration")
  ProductPricingConfigurationResponse resolveProductPricingConfiguration(@PathVariable UUID tenantId, @PathVariable String productCode, @RequestParam(required = false) java.time.Instant asOf, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.resolveProductPricingConfiguration(tenantId, productCode, asOf == null ? java.time.Instant.now() : asOf);
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

  @PostMapping("/loanpass-mapped/config-snapshots/resolve")
  ProductConfigSnapshot resolveLoanPassMappedCatalog(@PathVariable UUID tenantId, @RequestBody LoanPassMappedCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("READ_CATALOG", h.roles); return service.resolveLoanPassMappedCatalog(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId);
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
    List<ProductResponse> products = domainRepository.listProducts(tenantId, status);
    if (tenantAuthorizationService != null) {
      List<TenantProductAuthorization> rules = tenantAuthorizationService.getAuthorizedRulesAsOf(tenantId, java.time.Instant.now());
      if (rules.isEmpty()) throw new CatalogException("TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED");
      products = products.stream()
          .filter(product -> rules.stream().anyMatch(rule -> rule.matches(product.code(), null, null)))
          .toList();
    }
    return new ProductListResponse(products, products.size());
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
    if ("PRODUCT_CONFIG_SNAPSHOT_UNAVAILABLE".equals(errorCode) || "TENANT_PRODUCT_AUTHORIZATION_CONFIG_REQUIRED".equals(errorCode) || "PRODUCT_NOT_AUTHORIZED".equals(errorCode)) {
      return List.of(Map.of(
          "field", "tenantProductAuthorization",
          "code", errorCode,
          "message", tenantProductAuthorizationMessage(errorCode)));
    }
    if (errorCode.startsWith("TENANT_MAPPING_")) {
      return List.of(Map.of(
          "field", tenantMappingField(errorCode),
          "code", errorCode,
          "message", tenantMappingMessage(errorCode)));
    }
    if (errorCode.startsWith("PRICING_CONFIG_") || "PRODUCT_PRICING_CONFIGURATION_NOT_FOUND".equals(errorCode)) {
      return List.of(Map.of(
          "field", "pricingConfigurationRefs",
          "code", errorCode,
          "message", "Provide published, tenant-scoped pricing configuration reference codes and version IDs effective for the requested date."));
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
    if ("PRODUCT_CODE_REQUIRED".equals(errorCode) || "PRODUCT_NAME_REQUIRED".equals(errorCode) || "PRODUCT_FAMILY_REQUIRED".equals(errorCode)
        || "PRODUCT_TYPE_REQUIRED".equals(errorCode) || "SUPPORTED_TERMS_REQUIRED".equals(errorCode) || "AMORTIZATION_TYPES_REQUIRED".equals(errorCode)
        || "LOAN_PURPOSES_REQUIRED".equals(errorCode) || "SUPPORTED_CHANNELS_REQUIRED".equals(errorCode) || "ALLOWED_STATES_REQUIRED".equals(errorCode)
        || "EFFECTIVE_START_REQUIRED".equals(errorCode) || "EFFECTIVE_WINDOW_INVALID".equals(errorCode) || "INVALID_PRODUCT_STATUS".equals(errorCode)
        || "MAPPING_METADATA_KEY_REQUIRED".equals(errorCode) || "MAPPING_METADATA_VALUE_REQUIRED".equals(errorCode) || "PRODUCT_CODE_DUPLICATE".equals(errorCode)) {
      return List.of(Map.of(
          "field", productCreationField(errorCode),
          "code", errorCode,
          "message", productCreationMessage(errorCode)));
    }
    if (errorCode.startsWith("ENUM_")) {
      return List.of(Map.of(
          "field", errorCode.equals("ENUM_TYPE_NOT_FOUND") ? "enumTypeId" : "enumerations",
          "code", errorCode,
          "message", enumerationMessage(errorCode)));
    }
    if (errorCode.startsWith("FIELD_") || "FIELD_METADATA_NOT_FOUND".equals(errorCode)) {
      return List.of(Map.of(
          "field", fieldMetadataField(errorCode),
          "code", errorCode,
          "message", fieldMetadataMessage(errorCode)));
    }
    if (errorCode.startsWith("PRODUCT_SPEC_")) {
      return List.of(Map.of(
          "field", errorCode.startsWith("PRODUCT_SPEC_CONDITION_") ? "productSpecification.conditions" : "productSpecification.fields",
          "code", errorCode,
          "message", "PRODUCT_SPEC_TYPE_BREAKING_EDIT_REQUIRES_MIGRATION".equals(errorCode)
              ? "Create a migration-controlled field version before changing the value type of a mapped field."
              : "Fix product specification field builder values before publishing."));
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

  private static String productCreationField(String errorCode) {
    return switch (errorCode) {
      case "PRODUCT_CODE_REQUIRED", "PRODUCT_CODE_DUPLICATE" -> "productCode";
      case "PRODUCT_NAME_REQUIRED" -> "displayName";
      case "PRODUCT_FAMILY_REQUIRED" -> "productFamily";
      case "PRODUCT_TYPE_REQUIRED" -> "productType";
      case "SUPPORTED_TERMS_REQUIRED" -> "supportedTerms";
      case "AMORTIZATION_TYPES_REQUIRED" -> "amortizationTypes";
      case "LOAN_PURPOSES_REQUIRED" -> "loanPurposes";
      case "SUPPORTED_CHANNELS_REQUIRED" -> "supportedChannels";
      case "ALLOWED_STATES_REQUIRED" -> "allowedStates";
      case "EFFECTIVE_START_REQUIRED", "EFFECTIVE_WINDOW_INVALID" -> "effectiveWindow";
      case "INVALID_PRODUCT_STATUS" -> "status";
      case "MAPPING_METADATA_KEY_REQUIRED", "MAPPING_METADATA_VALUE_REQUIRED" -> "metadataRefs";
      default -> "product";
    };
  }

  private static String productCreationMessage(String errorCode) {
    return switch (errorCode) {
      case "PRODUCT_CODE_DUPLICATE" -> "Choose a unique product code or create a new version through the versioning workflow.";
      case "EFFECTIVE_WINDOW_INVALID" -> "Use an effective end after the effective start.";
      case "MAPPING_METADATA_KEY_REQUIRED", "MAPPING_METADATA_VALUE_REQUIRED" -> "Provide complete LoanPass mapping metadata keys and values.";
      default -> "Provide a valid product creation value for " + productCreationField(errorCode) + ".";
    };
  }

  private static String enumerationMessage(String errorCode) {
    return switch (errorCode) {
      case "ENUM_TYPE_NOT_FOUND" -> "Enumeration type was not found in the system/default catalog.";
      case "ENUM_TYPE_DUPLICATE" -> "Enumeration type already exists; use a versioned update workflow instead of inventing replacement variants.";
      case "ENUM_VARIANT_DELETE_BLOCKED" -> "Enumeration variants referenced by fields or conditions cannot be deleted; create an additive tenant override or versioned replacement.";
      default -> "Provide LoanPass enumeration types and variants from the approved field library source.";
    };
  }

  private static String fieldMetadataField(String errorCode) {
    return switch (errorCode) {
      case "FIELD_ID_REQUIRED", "FIELD_ID_DUPLICATE", "FIELD_METADATA_NOT_FOUND" -> "id";
      case "FIELD_NAME_REQUIRED" -> "name";
      case "FIELD_CATEGORY_REQUIRED" -> "category";
      case "FIELD_VALUE_TYPE_REQUIRED", "FIELD_VALUE_TYPE_UNSUPPORTED" -> "valueType";
      default -> "fieldMetadata";
    };
  }

  private static String fieldMetadataMessage(String errorCode) {
    return switch (errorCode) {
      case "FIELD_ID_DUPLICATE" -> "Field metadata import contains a duplicate field id for this tenant.";
      case "FIELD_VALUE_TYPE_UNSUPPORTED" -> "Use a supported field value type such as header, enum, number, string, text, date, time, duration, boolean, US state, or US county.";
      case "FIELD_METADATA_NOT_FOUND" -> "Field metadata id was not found in the tenant catalog.";
      default -> "Provide complete field metadata from the approved ReferenceFormfields source.";
    };
  }

  private static String tenantProductAuthorizationMessage(String errorCode) {
    if ("PRODUCT_CONFIG_SNAPSHOT_UNAVAILABLE".equals(errorCode)) return "Choose a timestamp with published catalog configuration.";
    if ("PRODUCT_NOT_AUTHORIZED".equals(errorCode)) return "Configure an active tenant product authorization for the requested product, investor, channel, and as-of timestamp.";
    return "Configure active tenant product authorization before catalog, quote, or eligibility APIs can consider products.";
  }

  private static String tenantMappingField(String errorCode) {
    return switch (errorCode) {
      case "TENANT_MAPPING_TENANT_REQUIRED", "TENANT_MAPPING_TENANT_INVALID", "TENANT_MAPPING_TENANT_MISMATCH" -> "mappedTenantId";
      case "TENANT_MAPPING_CHANNEL_REQUIRED" -> "mappedChannelCode";
      case "TENANT_MAPPING_INVESTOR_REQUIRED" -> "mappedInvestorCode";
      case "TENANT_MAPPING_AUDIT_REF_REQUIRED" -> "tenantMappingAuditRef";
      default -> "tenantMapping";
    };
  }

  private static String tenantMappingMessage(String errorCode) {
    return switch (errorCode) {
      case "TENANT_MAPPING_TENANT_MISMATCH" -> "Resolved tenant mapping must match the requested tenant path.";
      case "TENANT_MAPPING_AUDIT_REF_REQUIRED" -> "Resolved tenant mapping audit reference is required before catalog authorization.";
      default -> "Resolve a complete tenant/channel/investor mapping before catalog authorization.";
    };
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
    if ("ENUM_TYPE_NOT_FOUND".equals(errorCode)) return HttpStatus.NOT_FOUND;
    if ("FIELD_CONSUMER_MAPPING_NOT_FOUND".equals(errorCode)) return HttpStatus.NOT_FOUND;
    if ("SEPARATION_OF_DUTIES_VIOLATION".equals(errorCode) || "INCLUDE_INACTIVE_REQUIRES_DEBUG_PERMISSION".equals(errorCode)) return HttpStatus.FORBIDDEN;
    if ("IDEMPOTENCY_CONFLICT".equals(errorCode) || "VERSION_CONFLICT".equals(errorCode) || "CATALOG_VERSION_CONFLICT".equals(errorCode) || "IMPORT_ALREADY_PROCESSED".equals(errorCode)) return HttpStatus.CONFLICT;
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }

  record Headers(String idempotencyKey, String actorId, String correlationId, String roles) {}
}

@RestController
@RequestMapping("/api/v1/field-library")
class SystemFieldLibraryController {
  private final CatalogService service;
  private final AuthorizationService authorizationService;

  SystemFieldLibraryController(CatalogService service, AuthorizationService authorizationService) {
    this.service = service;
    this.authorizationService = authorizationService;
  }

  @GetMapping
  FieldLibraryQueryResponse querySystemFieldLibrary(@RequestParam String category,
                                                    @RequestParam(defaultValue = "false") boolean includeEnums,
                                                    HttpServletRequest http) {
    authorizationService.authorize("READ_CATALOG", http.getHeader("X-Roles"));
    return service.querySystemFieldLibrary(category, includeEnums, http.getHeader("X-Actor-Id"), http.getHeader("X-Correlation-Id"));
  }
}

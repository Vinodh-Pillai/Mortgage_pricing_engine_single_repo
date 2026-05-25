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
  CatalogResponse addProduct(@PathVariable UUID tenantId, @RequestBody ProductRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addProduct(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/investors/drafts")
  CatalogResponse addInvestor(@PathVariable UUID tenantId, @RequestBody InvestorRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addInvestor(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/taxonomy/drafts")
  CatalogResponse addTaxonomy(@PathVariable UUID tenantId, @RequestBody ReferenceCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addReference(tenantId, "PRODUCT_TAXONOMY", request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/channels/drafts")
  CatalogResponse addChannel(@PathVariable UUID tenantId, @RequestBody ReferenceCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addReference(tenantId, "CHANNEL", request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/term-amortization/drafts")
  CatalogResponse addTermAmortization(@PathVariable UUID tenantId, @RequestBody ReferenceCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addReference(tenantId, "TERM_AMORTIZATION", request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/property-types/drafts")
  CatalogResponse addPropertyType(@PathVariable UUID tenantId, @RequestBody ReferenceCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addReference(tenantId, "PROPERTY_TYPE", request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/occupancy-types/drafts")
  CatalogResponse addOccupancyType(@PathVariable UUID tenantId, @RequestBody ReferenceCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addReference(tenantId, "OCCUPANCY_TYPE", request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/loan-purposes/drafts")
  CatalogResponse addLoanPurpose(@PathVariable UUID tenantId, @RequestBody ReferenceCatalogRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addReference(tenantId, "LOAN_PURPOSE", request, h.idempotencyKey, h.actorId, h.correlationId));
  }

  @PostMapping("/markets/imports")
  CatalogResponse addMarket(@PathVariable UUID tenantId, @RequestBody MarketRequest request, HttpServletRequest http) {
    Headers h = headers(http); authorizationService.authorize("WRITE_CATALOG", h.roles); return withRoles(h.roles, () -> service.addMarket(tenantId, request, h.idempotencyKey, h.actorId, h.correlationId));
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

  @GetMapping("/config-snapshots/{snapshotId}")
  ProductConfigSnapshot snapshot(@PathVariable UUID tenantId, @PathVariable UUID snapshotId) { return service.snapshot(tenantId, snapshotId); }

  @GetMapping("/events")
  List<CatalogEvent> events(@PathVariable UUID tenantId) { return service.events(tenantId); }

  @GetMapping("/audit")
  List<CatalogAuditRecord> audit(@PathVariable UUID tenantId) { return service.audit(tenantId); }

  @GetMapping("/versions")
  List<CatalogVersionControlRecord> versions(@PathVariable UUID tenantId, HttpServletRequest http) {
    String roles = http.getHeader("X-Roles"); authorizationService.authorize("READ_CATALOG", roles); return service.versions(tenantId);
  }

  @GetMapping("/investors")
  InvestorListResponse listInvestors(@PathVariable UUID tenantId, @RequestParam(required = false) String status) {
    return new InvestorListResponse(domainRepository.listInvestors(tenantId, status), domainRepository.listInvestors(tenantId, status).size());
  }

  @GetMapping("/products")
  ProductListResponse listProductsDomain(@PathVariable UUID tenantId, @RequestParam(required = false) String status) {
    return new ProductListResponse(domainRepository.listProducts(tenantId, status), domainRepository.listProducts(tenantId, status).size());
  }

  @GetMapping("/channels")
  ChannelListResponse listChannelsDomain(@PathVariable UUID tenantId, @RequestParam(required = false) String status) {
    return new ChannelListResponse(domainRepository.listChannels(tenantId, status), domainRepository.listChannels(tenantId, status).size());
  }

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, Object>> error(CatalogException ex) {
    HttpStatus status = "IDEMPOTENCY_CONFLICT".equals(ex.getMessage()) ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
    return ResponseEntity.status(status).body(Map.of("code", ex.getMessage(), "message", ex.getMessage()));
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

  record Headers(String idempotencyKey, String actorId, String correlationId, String roles) {}
}

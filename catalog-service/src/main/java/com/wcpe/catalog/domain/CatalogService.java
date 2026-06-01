package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogService {
  private static final Set<String> WRITER_ROLES = Set.of("CATALOG_WRITER", "CATALOG_MANAGER", "CATALOG_ADMIN");
  private static final Set<String> APPROVER_ROLES = Set.of("CATALOG_APPROVER", "CATALOG_MANAGER", "CATALOG_ADMIN");
  private static final Set<String> PUBLISHER_ROLES = Set.of("CATALOG_PUBLISHER", "CATALOG_MANAGER", "CATALOG_ADMIN");
  private final CatalogRepository repository;
  private final AuthorizationService authorizationService;

  CatalogService(CatalogRepository repository, AuthorizationService authorizationService) {
    this.repository = repository;
    this.authorizationService = authorizationService;
  }

  @Transactional
  CatalogResponse addProduct(UUID tenantId, ProductRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ProductDefinition product = repository.addProduct(tenantId, catalogId, request);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "ProductDefinitionAdded.v1", Map.of("productCode", product.productCode()));
      audit(tenantId, catalogId, "PRODUCT_DEFINITION_ADDED", before, after, Map.of("productCode", product.productCode()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse addInvestor(UUID tenantId, InvestorRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      InvestorProgram investor = repository.addInvestor(tenantId, catalogId, request);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "InvestorProgramAdded.v1", Map.of("investorCode", investor.investorCode()));
      audit(tenantId, catalogId, "INVESTOR_PROGRAM_ADDED", before, after, Map.of("investorCode", investor.investorCode()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse addReference(UUID tenantId, String catalogType, ReferenceCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, Map.of("type", catalogType, "request", request), CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      ReferenceEntry entry = repository.addReference(tenantId, catalogId, catalogType, request);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, catalogType + "Changed.v1", Map.of("code", entry.code()));
      audit(tenantId, catalogId, catalogType + "_CHANGED", before, after, Map.of("code", entry.code()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse addMarket(UUID tenantId, MarketRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      MarketArea market = repository.addMarket(tenantId, catalogId, request);
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "MarketCatalogChanged.v1", Map.of("stateCode", market.stateCode(), "countyFips", Objects.toString(market.countyFips(), "")));
      audit(tenantId, catalogId, "MARKET_CATALOG_CHANGED", before, after, Map.of("stateCode", market.stateCode()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse draft(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      String current = before.status().name();
      if (!"REJECTED".equals(current) && !"ROLLED_BACK".equals(current)) {
        repository.transition(tenantId, catalogId, CatalogStatus.valueOf(current), CatalogStatus.DRAFT);
      } else {
        repository.resetToDraft(tenantId, catalogId);
      }
      CatalogResponse after = repository.current(tenantId);
      emit(tenantId, catalogId, "CatalogSetToDraft.v1", Map.of("from", current, "to", "DRAFT"));
      audit(tenantId, catalogId, "CATALOG_SET_TO_DRAFT", before, after, Map.of("from", current, "to", "DRAFT"), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse validate(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return transition(tenantId, CatalogStatus.DRAFT, CatalogStatus.VALIDATED, "CATALOG_VALIDATED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse submitApproval(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_WRITER", WRITER_ROLES);
    return transition(tenantId, CatalogStatus.VALIDATED, CatalogStatus.PENDING_APPROVAL, "CATALOG_SUBMITTED_FOR_APPROVAL", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse approve(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_APPROVER", APPROVER_ROLES);
    String submitterId = repository.findSubmitterId(tenantId);
    authorizationService.enforceSoD(actorId, submitterId, "SUBMITTED:" + submitterId);
    return transition(tenantId, CatalogStatus.PENDING_APPROVAL, CatalogStatus.APPROVED, "CATALOG_APPROVED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse reject(UUID tenantId, RejectCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_APPROVER", APPROVER_ROLES);
    return transition(tenantId, CatalogStatus.PENDING_APPROVAL, CatalogStatus.REJECTED, "CATALOG_REJECTED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse publish(UUID tenantId, PublishCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      repository.publish(tenantId, catalogId);
      CatalogResponse after = repository.active(tenantId);
      String reason = request.reason() == null ? "publish" : request.reason();
      emit(tenantId, catalogId, "CatalogPublished.v1", Map.of("reason", reason));
      audit(tenantId, catalogId, "CATALOG_PUBLISHED", before, after, Map.of("reason", reason), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  CatalogResponse suspend(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
    return transition(tenantId, CatalogStatus.PUBLISHED, CatalogStatus.SUSPENDED, "CATALOG_SUSPENDED", request, idempotencyKey, actorId, correlationId);
  }

  @Transactional
  CatalogResponse retire(UUID tenantId, LifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_PUBLISHER", PUBLISHER_ROLES);
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      CatalogStatus current = before.status();
      if (current == CatalogStatus.PUBLISHED) repository.transition(tenantId, catalogId, CatalogStatus.PUBLISHED, CatalogStatus.RETIRED);
      else if (current == CatalogStatus.SUSPENDED) repository.transition(tenantId, catalogId, CatalogStatus.SUSPENDED, CatalogStatus.RETIRED);
      else throw new CatalogException("INVALID_CATALOG_STATUS_TRANSITION");
      CatalogResponse after = repository.current(tenantId);
      String reason = request.reason() == null ? "retire" : request.reason();
      emit(tenantId, catalogId, "CatalogRetired.v1", Map.of("reason", reason));
      audit(tenantId, catalogId, "CATALOG_RETIRED", before, after, Map.of("reason", reason), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  CatalogResponse current(UUID tenantId) { return repository.current(tenantId); }
  CatalogResponse active(UUID tenantId) { return repository.active(tenantId); }

  @Transactional
  CatalogResponse rollback(UUID tenantId, VersionedLifecycleActionRequest request, String idempotencyKey, String actorId, String correlationId) {
    requireRole("CATALOG_ADMIN", Set.of("CATALOG_ADMIN"));
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      if (request.expectedVersion() != null) repository.requireVersion(tenantId, catalogId, request.expectedVersion());
      repository.forceStatus(tenantId, catalogId, CatalogStatus.ROLLED_BACK);
      CatalogResponse after = repository.current(tenantId);
      String reason = request.reason() == null ? "rollback" : request.reason();
      emit(tenantId, catalogId, "CatalogRolledBack.v1", Map.of("reason", reason));
      audit(tenantId, catalogId, "CATALOG_ROLLED_BACK", before, after, Map.of("reason", reason), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  @Transactional
  ProductConfigSnapshot resolve(UUID tenantId, ResolveCatalogRequest request, String idempotencyKey, String actorId, String correlationId) {
    return repository.idempotent(tenantId, idempotencyKey, request, ProductConfigSnapshot.class, () -> {
      ProductConfigSnapshot snapshot = repository.resolve(tenantId, request);
      UUID catalogId = repository.activeCatalogId(tenantId);
      emit(tenantId, catalogId, "ProductConfigSnapshotMaterialized.v1", Map.of("snapshotHash", snapshot.snapshotHash()));
      audit(tenantId, catalogId, "PRODUCT_CONFIG_SNAPSHOT_MATERIALIZED", null, snapshot, Map.of("snapshotHash", snapshot.snapshotHash()), actorId, correlationId, idempotencyKey);
      return snapshot;
    });
  }

  ProductConfigSnapshot snapshot(UUID tenantId, UUID snapshotId) { return repository.snapshot(tenantId, snapshotId); }
  List<CatalogEvent> events(UUID tenantId) { return repository.events(tenantId); }
  List<CatalogAuditRecord> audit(UUID tenantId) { return repository.audit(tenantId); }
  List<CatalogVersionControlRecord> versions(UUID tenantId) { return repository.versionControls(tenantId, repository.currentCatalogId(tenantId)); }
  CatalogRepository getRepository() { return repository; }

  private CatalogResponse transition(UUID tenantId, CatalogStatus expected, CatalogStatus next, String action, Object request, String idempotencyKey, String actorId, String correlationId) {
    return repository.idempotent(tenantId, idempotencyKey, request, CatalogResponse.class, () -> {
      UUID catalogId = repository.currentCatalogId(tenantId);
      CatalogResponse before = repository.current(tenantId);
      repository.transition(tenantId, catalogId, expected, next);
      CatalogResponse after = next == CatalogStatus.PUBLISHED ? repository.active(tenantId) : repository.current(tenantId);
      emit(tenantId, catalogId, action + ".v1", Map.of("from", expected.name(), "to", next.name()));
      audit(tenantId, catalogId, action, before, after, Map.of("from", expected.name(), "to", next.name()), actorId, correlationId, idempotencyKey);
      return after;
    });
  }

  private static void requireRole(String required, Set<String> allowed) {
    String roles = RequestContext.roles();
    if (roles == null || roles.isBlank()) throw new CatalogException("ROLE_REQUIRED_" + required);
    boolean ok = Arrays.stream(roles.split(",")).map(String::trim).anyMatch(allowed::contains);
    if (!ok) throw new CatalogException("ROLE_REQUIRED_" + required);
  }

  private void enforceSoD(String submitterId, String approverId) {
    authorizationService.enforceSoD(approverId, submitterId, "SUBMITTED:" + submitterId);
  }

  private void emit(UUID tenantId, UUID catalogId, String eventType, Map<String, Object> payload) {
    repository.event(new CatalogEvent(UUID.randomUUID(), tenantId, catalogId, eventType, Instant.now(), payload));
  }

  private void audit(UUID tenantId, UUID catalogId, String action, Object before, Object after, Map<String, Object> payload, String actorId, String correlationId, String idempotencyKey) {
    repository.audit(tenantId, catalogId, action, repository.replayHash(tenantId, catalogId), before, after, payload, actorId, correlationId, idempotencyKey);
  }
}

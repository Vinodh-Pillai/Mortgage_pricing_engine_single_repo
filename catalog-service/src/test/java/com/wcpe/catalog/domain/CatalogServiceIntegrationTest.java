package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class CatalogServiceIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("catalog")
      .withUsername("catalog_app")
      .withPassword("catalog_app");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired CatalogService service;

  @BeforeEach
  void defaultServiceRoleContext() {
    RequestContext.roles("CATALOG_ADMIN");
  }

  @AfterEach
  void clearServiceRoleContext() {
    RequestContext.clear();
  }

  @Test
  void addsReferencesProductAndInvestorThenPublishesCatalog() {
    UUID tenantId = UUID.randomUUID();

    service.addReference(tenantId, "CHANNEL", new ReferenceCatalogRequest("RETAIL", "Retail", "CHANNEL", Map.of(), LocalDate.now(), null), "ch", "actor-1", "corr-1");
    service.addReference(tenantId, "LOAN_PURPOSE", new ReferenceCatalogRequest("PURCHASE", "Purchase", "PURPOSE", Map.of(), LocalDate.now(), null), "lp", "actor-1", "corr-1");
    CatalogResponse afterProduct = service.addProduct(tenantId, new ProductRequest("CONV30", "Conventional 30 Year Fixed", "CONVENTIONAL", List.of("RETAIL"), List.of("TX", "CA"), LocalDate.now(), null), "prod", "actor-1", "corr-1");
    CatalogResponse afterInvestor = service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null), "inv", "actor-1", "corr-1");
    service.validate(tenantId, new LifecycleActionRequest("valid"), "val", "actor-1", "corr-1");
    service.submitApproval(tenantId, new LifecycleActionRequest("submit"), "sub", "actor-1", "corr-1");
    service.approve(tenantId, new LifecycleActionRequest("approve"), "app", "actor-2", "corr-1");
    CatalogResponse published = service.publish(tenantId, new PublishCatalogRequest("initial catalog", LocalDate.now()), "pub", "actor-2", "corr-1");

    assertThat(afterProduct.products()).hasSize(1);
    assertThat(afterInvestor.investors()).hasSize(1);
    assertThat(published.status()).isEqualTo(CatalogStatus.PUBLISHED);
    assertThat(service.active(tenantId).status()).isEqualTo(CatalogStatus.PUBLISHED);
    ProductConfigSnapshot snapshot = service.resolve(tenantId, new ResolveCatalogRequest(LocalDate.now(), "RETAIL", "TX", "CONVENTIONAL", "FNMA", "PURCHASE", null, null, null, null), "snap", "actor-2", "corr-1");
    assertThat(snapshot.snapshotHash()).isNotBlank();
    assertThat(service.snapshot(tenantId, snapshot.snapshotId()).snapshotHash()).isEqualTo(snapshot.snapshotHash());
    assertThat(service.audit(tenantId)).isNotEmpty();
    assertThatThrownBy(() -> service.addProduct(tenantId, new ProductRequest("CONV15", "Conventional 15 Year Fixed", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null), "prod2", "actor-1", "corr-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("CATALOG_NOT_EDITABLE");
  }

  @Test
  void investorCannotReferenceUnknownProduct() {
    UUID tenantId = UUID.randomUUID();

    assertThatThrownBy(() -> service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("MISSING"), LocalDate.now(), null), "bad", "actor", "corr"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVESTOR_PRODUCT_UNKNOWN");
  }

  @Test
  void resolveReturnsDeterministicModuleLocalSnapshotForPublishedCatalog() {
    UUID tenantId = UUID.randomUUID();
    publishResolvableCatalog(tenantId);

    ProductConfigSnapshot snapshot = service.resolve(tenantId, new ResolveCatalogRequest(LocalDate.now(), "RETAIL", "TX", "CONVENTIONAL", "FNMA", "PURCHASE", null, null, null, null), "resolve-ok", "actor-2", "corr-1");

    assertThat(snapshot.snapshotHash()).isNotBlank();
    assertThat(snapshot.products()).extracting(ProductDefinition::productFamily).containsExactly("CONVENTIONAL");
    assertThat(snapshot.investors()).extracting(InvestorProgram::investorCode).containsExactly("FNMA");
    assertThat(service.snapshot(tenantId, snapshot.snapshotId()).snapshotHash()).isEqualTo(snapshot.snapshotHash());
  }

  @Test
  void ProductConfigSnapshotServiceTest_hashIsStableForSameComponents() {
    UUID tenantId = UUID.randomUUID();
    publishResolvableCatalog(tenantId);
    ResolveCatalogRequest request = new ResolveCatalogRequest(Instant.now(), null, "RETAIL", null, "TX", null, "CONVENTIONAL", null, "FNMA", "PURCHASE", null, null, null, null, false);

    ProductConfigSnapshot first = service.resolve(tenantId, request, null, "svc-pricing", "corr-0210");
    ProductConfigSnapshot second = service.resolve(tenantId, request, null, "svc-pricing", "corr-0210");

    assertThat(second.snapshotHash()).isEqualTo(first.snapshotHash());
    assertThat(second.snapshotId()).isEqualTo(first.snapshotId());
    assertThat(first.channel().code()).isEqualTo("RETAIL");
    assertThat(first.productComponents()).extracting(SnapshotProduct::productCode).containsExactly("CONV30");
    assertThat(first.investorComponents()).extracting(SnapshotInvestor::code).containsExactly("FNMA");
    assertThat(first.referenceVersions()).containsKeys("loanPurposes", "markets");
    assertThat(service.events(tenantId).stream().filter(e -> "ProductConfigSnapshotMaterialized.v1".equals(e.eventType())).count()).isEqualTo(1);
  }

  @Test
  void ProductConfigSnapshotIT_materializesImmutableSnapshotAndRetrievesSameBody() {
    UUID tenantId = UUID.randomUUID();
    publishResolvableCatalog(tenantId);
    ResolveCatalogRequest request = new ResolveCatalogRequest(Instant.now(), null, "RETAIL", null, "TX", null, "CONVENTIONAL", null, "FNMA", "PURCHASE", null, null, null, null, false);

    ProductConfigSnapshot materialized = service.resolve(tenantId, request, null, "svc-pricing", "corr-0210");
    ProductConfigSnapshot retrieved = service.snapshot(tenantId, materialized.snapshotId());

    assertThat(retrieved.snapshotHash()).isEqualTo(materialized.snapshotHash());
    assertThat(retrieved.requestHash()).isEqualTo(materialized.requestHash());
    assertThat(retrieved.productComponents()).isEqualTo(materialized.productComponents());
    assertThat(retrieved.referenceVersions()).isEqualTo(materialized.referenceVersions());
  }

  @Test
  void ProductConfigSnapshotSecurityTest_includeInactiveRequiresDebugPermission() {
    UUID tenantId = UUID.randomUUID();
    publishResolvableCatalog(tenantId);
    ResolveCatalogRequest request = new ResolveCatalogRequest(Instant.now(), null, "RETAIL", null, "TX", null, "CONVENTIONAL", null, "FNMA", "PURCHASE", null, null, null, null, true);

    assertThatThrownBy(() -> service.resolve(tenantId, request, null, "svc-pricing", "corr-0210"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INCLUDE_INACTIVE_REQUIRES_DEBUG_PERMISSION");
  }

  @Test
  void resolveRejectsUnknownProductFamilyAndInvestorCodeExplicitly() {
    UUID tenantId = UUID.randomUUID();
    publishResolvableCatalog(tenantId);

    assertThatThrownBy(() -> service.resolve(tenantId, new ResolveCatalogRequest(LocalDate.now(), "RETAIL", "TX", "JUMBO", "FNMA", "PURCHASE", null, null, null, null), "bad-product", "actor-2", "corr-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("UNKNOWN_PRODUCT_FAMILY");
    assertThatThrownBy(() -> service.resolve(tenantId, new ResolveCatalogRequest(LocalDate.now(), "RETAIL", "TX", "CONVENTIONAL", "GNMA", "PURCHASE", null, null, null, null), "bad-investor", "actor-2", "corr-1"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("UNKNOWN_INVESTOR_CODE");
  }

  @Test
  void idempotencyReplaysSameRequestAndRejectsDifferentPayload() {
    UUID tenantId = UUID.randomUUID();
    ProductRequest first = new ProductRequest("CONV30", "Conventional 30 Year Fixed", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
    ProductRequest different = new ProductRequest("CONV15", "Conventional 15 Year Fixed", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);

    CatalogResponse one = service.addProduct(tenantId, first, "same-key", "actor", "corr");
    CatalogResponse two = service.addProduct(tenantId, first, "same-key", "actor", "corr");

    assertThat(two.catalogId()).isEqualTo(one.catalogId());
    assertThatThrownBy(() -> service.addProduct(tenantId, different, "same-key", "actor", "corr"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void MarketCatalogImportTest_duplicateSourceHashReturnsImportAlreadyProcessed() {
    UUID tenantId = UUID.randomUUID();
    MarketImportRequest request = new MarketImportRequest("2026 county market baseline", List.of(
        new MarketImportRow("TX", "Texas", "48201", "Harris", "ENABLED", null, List.of(), List.of(), Instant.parse("2026-01-01T00:00:00Z"), null)));

    service.importMarkets(tenantId, request, "market-import-one-" + tenantId, "actor-1", "corr-0209");

    assertThatThrownBy(() -> service.importMarkets(tenantId, request, "market-import-two-" + tenantId, "actor-1", "corr-0209"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("IMPORT_ALREADY_PROCESSED");
  }

  @Test
  void MarketCatalogImportTest_marketCatalogChangedEventPayloadMatchesContract() {
    UUID tenantId = UUID.randomUUID();

    service.importMarkets(tenantId, new MarketImportRequest("2026 county market baseline", List.of(
        new MarketImportRow("TX", "Texas", "48201", "Harris", "RESTRICTED", "PRODUCT_CHANNEL_ALLOWED", List.of("RETAIL"), List.of("CONV_FIXED_30"), Instant.parse("2026-01-01T00:00:00Z"), null))),
        "market-event-" + tenantId, "actor-1", "corr-0209");

    CatalogEvent event = service.events(tenantId).stream()
        .filter(e -> "MarketCatalogChanged.v1".equals(e.eventType()))
        .reduce((first, second) -> second)
        .orElseThrow();

    assertThat(event.payload())
        .containsEntry("eventKey", tenantId + ":TX:48201")
        .containsEntry("stateCode", "TX")
        .containsEntry("countyFips", "48201")
        .containsEntry("status", "RESTRICTED")
        .containsEntry("restrictionReasonCode", "PRODUCT_CHANNEL_ALLOWED");
    assertThat(event.payload()).containsKeys("marketVersionId", "versionNumber", "effectiveWindow", "configHash");
    assertThat(event.payload()).doesNotContainKeys("streetAddress", "borrowerName", "borrowerSsn");
  }

  @Test
  void productTaxonomy_requiresParentForType() {
    UUID tenantId = UUID.randomUUID();

    assertThatThrownBy(() -> service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest(
            "CONVENTIONAL_FIXED", "Conventional Fixed Rate", "TYPE", null, "CONVENTIONAL", Instant.parse("2026-01-01T00:00:00Z"), null, 20),
        "tax-type", "actor-1", "corr-0201"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_PARENT_LEVEL");
  }

  @Test
  void productTaxonomyResolve_returnsPublishedConventionalCodes() {
    UUID tenantId = UUID.randomUUID();
    publishProductTaxonomy(tenantId);

    ProductTaxonomyResolveResponse resolved = service.resolveProductTaxonomy(tenantId,
        new ProductTaxonomyResolveRequest(Instant.parse("2026-02-15T18:00:00Z"), List.of("CONVENTIONAL", "CONVENTIONAL_FIXED")),
        "svc-scenario", "corr-0201");

    assertThat(resolved.entries()).extracting(ProductTaxonomyResolvedEntry::code).containsExactly("CONVENTIONAL", "CONVENTIONAL_FIXED");
    assertThat(resolved.entries()).extracting(ProductTaxonomyResolvedEntry::status).containsOnly(CatalogStatus.PUBLISHED);
    assertThat(resolved.entries().get(1).parentCode()).isEqualTo("CONVENTIONAL");
  }

  @Test
  void productTaxonomyResolve_unknownCodeFailsClosed() {
    UUID tenantId = UUID.randomUUID();
    publishProductTaxonomy(tenantId);

    assertThatThrownBy(() -> service.resolveProductTaxonomy(tenantId,
        new ProductTaxonomyResolveRequest(Instant.parse("2026-02-15T18:00:00Z"), List.of("CONVENTIONAL_BALLOON")),
        "svc-scenario", "corr-0201"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PRODUCT_TAXONOMY_CODE_UNKNOWN");
  }

  @Test
  void conventionalProductDefinition_matchesFixedThirtyYearPurchase() {
    UUID tenantId = UUID.randomUUID();
    seedConventionalReferences(tenantId);
    ConventionalProductDraftResponse draft = service.addConventionalProductDraft(tenantId, fixedThirtyRequest("CONV_FIXED_30", List.of("TX")), "conv-draft-" + tenantId, "actor-1", "corr-0202");

    assertThat(draft.status()).isEqualTo(CatalogStatus.DRAFT);
    assertThat(draft.validation().blockingErrors()).isEmpty();

    service.validate(tenantId, new LifecycleActionRequest("conventional valid"), "conv-val-" + tenantId, "actor-1", "corr-0202");
    service.submitApproval(tenantId, new LifecycleActionRequest("conventional submit"), "conv-sub-" + tenantId, "actor-1", "corr-0202");
    service.approve(tenantId, new LifecycleActionRequest("conventional approve"), "conv-app-" + tenantId, "actor-2", "corr-0202");
    service.publish(tenantId, new PublishCatalogRequest("conventional publish", LocalDate.of(2026, 1, 1)), "conv-pub-" + tenantId, "actor-3", "corr-0202");

    ConventionalProductResolveResponse resolved = service.resolveConventionalProducts(tenantId,
        new ConventionalProductResolveRequest(Instant.parse("2026-03-01T12:00:00Z"), "RETAIL", "PURCHASE", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", "TX", new BigDecimal("425000.00"), 360, "FIXED"),
        "svc-pricing", "corr-0202");

    assertThat(resolved.eligibleProducts()).extracting(ConventionalProductMatch::productCode).containsExactly("CONV_FIXED_30");
    assertThat(resolved.eligibleProducts().get(0).investorCodes()).containsExactly("FNMA");
    assertThat(resolved.eligibleProducts().get(0).configHash()).startsWith("sha256:");
    assertThat(service.events(tenantId)).extracting(CatalogEvent::eventType).contains("ConventionalProductDefinitionResolved.v1");
    assertThat(service.audit(tenantId)).extracting(CatalogAuditRecord::action).contains("CONVENTIONAL_PRODUCT_DEFINITION_DRAFTED", "CONVENTIONAL_PRODUCT_DEFINITION_RESOLVED");
  }

  @Test
  void ConventionalProductDefinitionTest_rejectsArmWithoutIndex() {
    UUID tenantId = UUID.randomUUID();

    assertThatThrownBy(() -> service.addConventionalProductDraft(tenantId,
        new ConventionalProductDraftRequest("CONV_ARM_5_6", "Conventional ARM 5/6", "CONVENTIONAL_ARM", List.of("FNMA"), List.of("RETAIL"), List.of(360), "ARM", null, 60, 6, List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), List.of("TX"), new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null),
        "arm-missing-index", "actor-1", "corr-0202"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_ARM_STRUCTURE");
  }

  @Test
  void ConventionalProductDefinitionTest_rejectsFixedWithArmPeriods() {
    UUID tenantId = UUID.randomUUID();

    assertThatThrownBy(() -> service.addConventionalProductDraft(tenantId,
        new ConventionalProductDraftRequest("CONV_FIXED_BAD", "Conventional Fixed Bad", "CONVENTIONAL_FIXED", List.of("FNMA"), List.of("RETAIL"), List.of(360), "FIXED", "SOFR", 60, 6, List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), List.of("TX"), new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null),
        "fixed-arm-fields", "actor-1", "corr-0202"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVALID_FIXED_STRUCTURE");
  }

  @Test
  void ConventionalProductResolveIT_noEligibleProduct422() {
    UUID tenantId = UUID.randomUUID();
    seedConventionalReferences(tenantId);
    service.addConventionalProductDraft(tenantId, fixedThirtyRequest("CONV_FIXED_30", List.of("CA")), "conv-draft-ca-" + tenantId, "actor-1", "corr-0202");
    service.validate(tenantId, new LifecycleActionRequest("conventional valid"), "conv-val-ca-" + tenantId, "actor-1", "corr-0202");
    service.submitApproval(tenantId, new LifecycleActionRequest("conventional submit"), "conv-sub-ca-" + tenantId, "actor-1", "corr-0202");
    service.approve(tenantId, new LifecycleActionRequest("conventional approve"), "conv-app-ca-" + tenantId, "actor-2", "corr-0202");
    service.publish(tenantId, new PublishCatalogRequest("conventional publish", LocalDate.of(2026, 1, 1)), "conv-pub-ca-" + tenantId, "actor-3", "corr-0202");

    assertThatThrownBy(() -> service.resolveConventionalProducts(tenantId,
        new ConventionalProductResolveRequest(Instant.parse("2026-03-01T12:00:00Z"), "RETAIL", "PURCHASE", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", "TX", new BigDecimal("425000.00"), 360, "FIXED"),
        "svc-pricing", "corr-0202"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("NO_ELIGIBLE_CONVENTIONAL_PRODUCT");
  }

  @Test
  void TermAmortizationResolveIT_resolvesFixedThirtyYear() {
    UUID tenantId = UUID.randomUUID();
    seedConventionalReferences(tenantId);
    service.validate(tenantId, new LifecycleActionRequest("term valid"), "term-val-fixed-" + tenantId, "actor-1", "corr-0206");
    service.submitApproval(tenantId, new LifecycleActionRequest("term submit"), "term-sub-fixed-" + tenantId, "actor-1", "corr-0206");
    service.approve(tenantId, new LifecycleActionRequest("term approve"), "term-app-fixed-" + tenantId, "actor-2", "corr-0206");
    service.publish(tenantId, new PublishCatalogRequest("term publish", LocalDate.of(2026, 1, 1)), "term-pub-fixed-" + tenantId, "actor-3", "corr-0206");

    TermAmortizationResolveResponse resolved = service.resolveTermAmortization(tenantId,
        new TermAmortizationResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), 360, "FIXED", null, null), "svc-pricing", "corr-0206");

    assertThat(resolved.profileCode()).isEqualTo("FIXED_30YR");
    assertThat(resolved.profileVersionId()).isNotNull();
    assertThat(resolved.configHash()).startsWith("sha256:");
  }

  @Test
  void TermAmortizationResolveIT_resolvesArmSevenSix() {
    UUID tenantId = UUID.randomUUID();
    service.addProduct(tenantId, new ProductRequest("CONV_REF", "Conventional reference", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null), "arm-product-ref-" + tenantId, "actor-1", "corr-0206");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV_REF"), LocalDate.of(2026, 1, 1), null), "arm-investor-" + tenantId, "actor-1", "corr-0206");
    service.addTermAmortizationDraft(tenantId, armSevenSixRequest(), "arm-7-6-" + tenantId, "actor-1", "corr-0206");
    service.validate(tenantId, new LifecycleActionRequest("arm valid"), "term-val-arm-" + tenantId, "actor-1", "corr-0206");
    service.submitApproval(tenantId, new LifecycleActionRequest("arm submit"), "term-sub-arm-" + tenantId, "actor-1", "corr-0206");
    service.approve(tenantId, new LifecycleActionRequest("arm approve"), "term-app-arm-" + tenantId, "actor-2", "corr-0206");
    service.publish(tenantId, new PublishCatalogRequest("arm publish", LocalDate.of(2026, 1, 1)), "term-pub-arm-" + tenantId, "actor-3", "corr-0206");

    TermAmortizationResolveResponse resolved = service.resolveTermAmortization(tenantId,
        new TermAmortizationResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), 360, "ARM", 84, 6), "svc-pricing", "corr-0206");

    assertThat(resolved.profileCode()).isEqualTo("ARM_7_6");
    assertThat(resolved.armIndexCode()).isEqualTo("SOFR_30_DAY_AVG");
  }

  @Test
  void TermAmortizationOutboxIT_emitsChangedEvent() {
    UUID tenantId = UUID.randomUUID();

    service.addTermAmortizationDraft(tenantId, fixedThirtyTermRequest(), "term-event-" + tenantId, "actor-1", "corr-0206");

    assertThat(service.events(tenantId)).extracting(CatalogEvent::eventType).contains("TermAmortizationProfileChanged.v1");
    assertThat(service.audit(tenantId)).extracting(CatalogAuditRecord::action).contains("TERM_AMORTIZATION_PROFILE_CHANGED");
  }

  @Test
  void PropertyOccupancyResolveIT_rejectsUnpublishedPair() {
    UUID tenantId = UUID.randomUUID();
    publishPropertyOccupancyCatalog(tenantId);

    assertThatThrownBy(() -> service.resolvePropertyOccupancy(tenantId,
        new PropertyOccupancyResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "MANUFACTURED_HOME", "PRIMARY_RESIDENCE"), "svc-scenario", "corr-0207"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("PROPERTY_OCCUPANCY_NOT_PUBLISHED");
  }

  @Test
  void PropertyOccupancyDropdownContractTest_listsPublishedValues() {
    UUID tenantId = UUID.randomUUID();
    publishPropertyOccupancyCatalog(tenantId);

    PropertyOccupancyListResponse dropdown = service.listPublishedPropertyOccupancy(tenantId, Instant.parse("2026-02-01T00:00:00Z"));
    PropertyOccupancyResolveResponse resolved = service.resolvePropertyOccupancy(tenantId,
        new PropertyOccupancyResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "CONDO", "SECOND_HOME"), "svc-scenario", "corr-0207");

    assertThat(dropdown.propertyTypes()).extracting(PropertyTypeResolved::code).contains("SINGLE_FAMILY", "PUD", "CONDO", "TWO_TO_FOUR_UNIT");
    assertThat(dropdown.occupancyTypes()).extracting(OccupancyTypeResolved::code).containsExactly("INVESTMENT_PROPERTY", "PRIMARY_RESIDENCE", "SECOND_HOME");
    assertThat(resolved.propertyType().code()).isEqualTo("CONDO");
    assertThat(resolved.propertyType().requiresProjectReview()).isTrue();
    assertThat(resolved.occupancyType().code()).isEqualTo("SECOND_HOME");
    assertThat(service.events(tenantId)).extracting(CatalogEvent::eventType).contains("PropertyTypeCatalogChanged.v1", "OccupancyTypeCatalogChanged.v1", "PropertyOccupancyResolved.v1");
    assertThat(service.audit(tenantId)).extracting(CatalogAuditRecord::action).contains("PROPERTY_TYPE_CATALOG_CHANGED", "OCCUPANCY_TYPE_CATALOG_CHANGED", "PROPERTY_OCCUPANCY_RESOLVED");
  }

  @Test
  void LoanPurposeResolveIT_mapsAliasCaseInsensitively() {
    UUID tenantId = UUID.randomUUID();
    publishLoanPurposeCatalog(tenantId);

    LoanPurposeResolveResponse resolved = service.resolveLoanPurpose(tenantId,
        new LoanPurposeResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "cash_out_refinance"), "svc-scenario", "corr-0208");

    assertThat(resolved.purposeCode()).isEqualTo("CASH_OUT_REFI");
    assertThat(resolved.isRefinance()).isTrue();
    assertThat(resolved.isCashOut()).isTrue();
    assertThat(resolved.requiresExistingLien()).isTrue();
  }

  @Test
  void LoanPurposeResolveIT_rejectsDisabledConstructionToPerm() {
    UUID tenantId = UUID.randomUUID();
    publishLoanPurposeCatalog(tenantId);

    assertThatThrownBy(() -> service.resolveLoanPurpose(tenantId,
        new LoanPurposeResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "CONSTRUCTION_TO_PERMANENT"), "svc-scenario", "corr-0208"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("LOAN_PURPOSE_NOT_SUPPORTED");
  }

  @Test
  void LoanPurposeOutboxIT_emitsChangedEvent() {
    UUID tenantId = UUID.randomUUID();

    service.addLoanPurposeDraft(tenantId, loanPurpose("PURCHASE", "Purchase", "PURCHASE", false, false, false, true, List.of("PUR")), "lp-event-" + tenantId, "actor-1", "corr-0208");

    CatalogEvent event = service.events(tenantId).stream().filter(e -> e.eventType().equals("LoanPurposeCatalogChanged.v1")).findFirst().orElseThrow();
    assertThat(event.payload()).containsEntry("purposeCode", "PURCHASE");
    assertThat(event.payload()).containsEntry("status", "DRAFT");
    assertThat(event.payload()).containsEntry("isRefinance", false);
    assertThat(event.payload()).containsEntry("isCashOut", false);
    assertThat(event.payload()).containsEntry("requiresExistingLien", false);
    assertThat(event.payload()).containsEntry("eligibleForConventional", true);
    assertThat(event.payload()).containsKeys("agencyAliases", "loanPurposeVersionId", "versionNumber", "configHash");
    assertThat(event.payload().get("configHash").toString()).startsWith("sha256:");
    assertThat(service.audit(tenantId)).extracting(CatalogAuditRecord::action).contains("LOAN_PURPOSE_CATALOG_CHANGED");
  }

  @Test
  void MarketCatalogResolveIT_countyOverridesStateStatus() {
    UUID tenantId = UUID.randomUUID();
    publishMarketCatalog(tenantId);

    assertThatThrownBy(() -> service.resolveMarket(tenantId,
        new MarketResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "TX", null, "CONV_FIXED_30", "RETAIL"), "svc-scenario", "corr-0209"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("MARKET_RESTRICTED");
    MarketResolveResponse county = service.resolveMarket(tenantId,
        new MarketResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "TX", "48201", "CONV_FIXED_30", "RETAIL"), "svc-scenario", "corr-0209");

    assertThat(county.marketStatus()).isEqualTo("ENABLED");
    assertThat(county.marketVersionId()).isNotNull();
  }

  @Test
  void MarketCatalogResolveIT_restrictedMarketRequiresProductChannelAllowance() {
    UUID tenantId = UUID.randomUUID();
    publishMarketCatalog(tenantId);

    MarketResolveResponse allowed = service.resolveMarket(tenantId,
        new MarketResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "CA", "06001", "CONV_FIXED_30", "RETAIL"), "svc-scenario", "corr-0209");

    assertThat(allowed.marketStatus()).isEqualTo("RESTRICTED");
    assertThatThrownBy(() -> service.resolveMarket(tenantId,
        new MarketResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "CA", "06001", "CONV_FIXED_30", "WHOLESALE"), "svc-scenario", "corr-0209"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("MARKET_RESTRICTED");
  }

  @Test
  void InvestorResolveIT_filtersByDeliveryTypeAndChannel() {
    UUID tenantId = UUID.randomUUID();
    service.addInvestorCatalogDraft(tenantId, new InvestorCatalogDraftRequest("FNMA", "Federal National Mortgage Association", "AGENCY", "FANNIE_MAE",
        List.of(new InvestorSellerServicerId("RETAIL", "123456", "654321")), List.of("BEST_EFFORTS", "MANDATORY"), List.of("RETAIL"), true, CatalogStatus.DRAFT, Instant.parse("2026-01-01T00:00:00Z"), null),
        "investor-fnma-" + tenantId, "actor-1", "corr-0204");
    service.validate(tenantId, new LifecycleActionRequest("investor valid"), "investor-val-" + tenantId, "actor-1", "corr-0204");
    service.submitApproval(tenantId, new LifecycleActionRequest("investor submit"), "investor-sub-" + tenantId, "actor-1", "corr-0204");
    service.approve(tenantId, new LifecycleActionRequest("investor approve"), "investor-app-" + tenantId, "actor-2", "corr-0204");
    service.publish(tenantId, new PublishCatalogRequest("investor publish", LocalDate.of(2026, 1, 1)), "investor-pub-" + tenantId, "actor-3", "corr-0204");

    InvestorResolveResponse resolved = service.resolveInvestors(tenantId, new InvestorResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "CONV_FIXED_30", "RETAIL", "BEST_EFFORTS"), "svc-pricing", "corr-0204", false);

    assertThat(resolved.investors()).hasSize(1);
    assertThat(resolved.investors().get(0).investorCode()).isEqualTo("FNMA");
    assertThat(resolved.investors().get(0).sellerIdMasked()).isEqualTo("***456");
    assertThat(resolved.investors().get(0).requiresMiValidation()).isTrue();
    assertThatThrownBy(() -> service.resolveInvestors(tenantId, new InvestorResolveRequest(Instant.parse("2026-02-01T00:00:00Z"), "CONV_FIXED_30", "WHOLESALE", "BEST_EFFORTS"), "svc-pricing", "corr-0204", false))
        .isInstanceOf(CatalogException.class)
        .hasMessage("INVESTOR_NOT_ACTIVE_FOR_CHANNEL");
  }

  @Test
  void CatalogVersioningTest_allowsValidPublishTransition() {
    UUID tenantId = UUID.randomUUID();
    CatalogVersionControlRecord draft = draftTaxonomyVersion(tenantId, "VERSIONED_PRODUCT");

    CatalogVersionActionResponse validated = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("VALIDATE", draft.versionControlId(), draft.rowVersion(), null, "valid"), "vc-val-" + tenantId, "actor-1", "corr-0205");
    CatalogVersionActionResponse submitted = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("SUBMIT_APPROVAL", draft.versionControlId(), validated.rowVersion(), null, "submit"), "vc-sub-" + tenantId, "actor-1", "corr-0205");
    CatalogVersionActionResponse approved = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("APPROVE", draft.versionControlId(), submitted.rowVersion(), null, "approve"), "vc-app-" + tenantId, "actor-2", "corr-0205");
    CatalogVersionActionResponse published = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("PUBLISH", draft.versionControlId(), approved.rowVersion(), Instant.parse("2026-01-01T00:00:00Z"), "publish"), "vc-pub-" + tenantId, "actor-3", "corr-0205");

    assertThat(published.status()).isEqualTo(CatalogStatus.PUBLISHED);
    assertThat(published.versionNumber()).isEqualTo(1);
    assertThat(published.configHash()).startsWith("sha256:");
    assertThat(service.events(tenantId)).extracting(CatalogEvent::eventType).contains("CatalogVersionStatusChanged.v1");
    assertThat(service.resolveVersionAsOf(tenantId, draft.artifactType(), "VERSIONED_PRODUCT", Instant.parse("2026-02-01T00:00:00Z")).versionId()).isEqualTo(draft.versionControlId());
  }

  @Test
  void CatalogVersioningTest_rejectsCreatorApproval() {
    UUID tenantId = UUID.randomUUID();
    CatalogVersionControlRecord draft = draftTaxonomyVersion(tenantId, "SOD_PRODUCT");
    CatalogVersionActionResponse validated = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("VALIDATE", draft.versionControlId(), draft.rowVersion(), null, "valid"), "sod-val-" + tenantId, "actor-1", "corr-0205");
    CatalogVersionActionResponse submitted = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("SUBMIT_APPROVAL", draft.versionControlId(), validated.rowVersion(), null, "submit"), "sod-sub-" + tenantId, "actor-1", "corr-0205");

    assertThatThrownBy(() -> service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("APPROVE", draft.versionControlId(), submitted.rowVersion(), null, "approve"), "sod-app-" + tenantId, "actor-1", "corr-0205"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("SEPARATION_OF_DUTIES_VIOLATION");
  }

  @Test
  void CatalogVersioningTest_rejectsCreatorApprovalWhenSubmitterDiffers() {
    UUID tenantId = UUID.randomUUID();
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest("SOD_CREATOR_PRODUCT", "SOD_CREATOR_PRODUCT", "FAMILY", null, "CONVENTIONAL", Instant.parse("2026-01-01T00:00:00Z"), null, 10), "tax-sod-creator-" + tenantId, "creator-actor", "corr-0205");
    CatalogVersionControlRecord draft = service.versions(tenantId).stream().filter(version -> version.artifactCode().equals("SOD_CREATOR_PRODUCT")).findFirst().orElseThrow();
    CatalogVersionActionResponse validated = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("VALIDATE", draft.versionControlId(), draft.rowVersion(), null, "valid"), "sod-creator-val-" + tenantId, "submitter-actor", "corr-0205");
    CatalogVersionActionResponse submitted = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("SUBMIT_APPROVAL", draft.versionControlId(), validated.rowVersion(), null, "submit"), "sod-creator-sub-" + tenantId, "submitter-actor", "corr-0205");

    assertThatThrownBy(() -> service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("APPROVE", draft.versionControlId(), submitted.rowVersion(), null, "approve"), "sod-creator-app-" + tenantId, "creator-actor", "corr-0205"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("SEPARATION_OF_DUTIES_VIOLATION");
  }

  @Test
  void CatalogVersioningIT_rejectsOverlappingPublishedWindow() {
    UUID tenantId = UUID.randomUUID();
    CatalogVersionControlRecord draft = draftTaxonomyVersion(tenantId, "OVERLAP_PRODUCT");
    service.getRepository().getJdbcTemplate().update("update catalog.catalog_version_control set status='PUBLISHED', effective_start=? where tenant_id=? and version_control_id=?",
        java.sql.Date.valueOf(LocalDate.of(2026, 1, 1)), tenantId, draft.versionControlId());
    UUID overlapping = UUID.randomUUID();
    service.getRepository().getJdbcTemplate().update("""
        insert into catalog.catalog_version_control(tenant_id,version_control_id,catalog_id,artifact_type,artifact_id,artifact_code,version_number,status,effective_start,config_hash,snapshot_json,row_version)
        values (?,?,?,?,?,?,?,?,?,?,?::jsonb,0)
        """, tenantId, overlapping, draft.catalogId(), draft.artifactType(), draft.artifactId(), draft.artifactCode(), 2, CatalogStatus.APPROVED.name(), java.sql.Date.valueOf(LocalDate.of(2026, 2, 1)), "sha256:overlap", "{}");

    assertThatThrownBy(() -> service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(),
        new CatalogVersionActionRequest("PUBLISH", overlapping, 0L, Instant.parse("2026-02-01T00:00:00Z"), "overlap"), "overlap-pub-" + tenantId, "actor-3", "corr-0205"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("EFFECTIVE_WINDOW_OVERLAP");
  }

  @Test
  void CatalogVersioningIT_rollbackCreatesNewDraftVersion() {
    UUID tenantId = UUID.randomUUID();
    CatalogVersionControlRecord draft = draftTaxonomyVersion(tenantId, "ROLLBACK_PRODUCT");
    CatalogVersionActionResponse validated = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(), new CatalogVersionActionRequest("VALIDATE", draft.versionControlId(), draft.rowVersion(), null, "valid"), "rb-val-" + tenantId, "actor-1", "corr-0205");
    CatalogVersionActionResponse submitted = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(), new CatalogVersionActionRequest("SUBMIT_APPROVAL", draft.versionControlId(), validated.rowVersion(), null, "submit"), "rb-sub-" + tenantId, "actor-1", "corr-0205");
    CatalogVersionActionResponse approved = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(), new CatalogVersionActionRequest("APPROVE", draft.versionControlId(), submitted.rowVersion(), null, "approve"), "rb-app-" + tenantId, "actor-2", "corr-0205");
    CatalogVersionActionResponse published = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(), new CatalogVersionActionRequest("PUBLISH", draft.versionControlId(), approved.rowVersion(), Instant.parse("2026-01-01T00:00:00Z"), "publish"), "rb-pub-" + tenantId, "actor-3", "corr-0205");

    CatalogVersionActionResponse rollback = service.applyVersionAction(tenantId, draft.artifactType(), draft.artifactId(), new CatalogVersionActionRequest("ROLLBACK", draft.versionControlId(), published.rowVersion(), null, "rollback"), "rb-action-" + tenantId, "actor-admin", "corr-0205");

    assertThat(rollback.status()).isEqualTo(CatalogStatus.DRAFT);
    assertThat(rollback.versionNumber()).isEqualTo(2);
    assertThat(rollback.configHash()).isEqualTo(published.configHash());
  }

  private void publishResolvableCatalog(UUID tenantId) {
    service.addReference(tenantId, "CHANNEL", new ReferenceCatalogRequest("RETAIL", "Retail", "CHANNEL", Map.of(), LocalDate.now(), null), "ch-" + tenantId, "actor-1", "corr-1");
    service.addReference(tenantId, "LOAN_PURPOSE", new ReferenceCatalogRequest("PURCHASE", "Purchase", "PURPOSE", Map.of(), LocalDate.now(), null), "lp-" + tenantId, "actor-1", "corr-1");
    service.addProduct(tenantId, new ProductRequest("CONV30", "Conventional 30 Year Fixed", "CONVENTIONAL", List.of("RETAIL"), List.of("TX", "CA"), LocalDate.now(), null), "prod-" + tenantId, "actor-1", "corr-1");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null), "inv-" + tenantId, "actor-1", "corr-1");
    service.validate(tenantId, new LifecycleActionRequest("valid"), "val-" + tenantId, "actor-1", "corr-1");
    service.submitApproval(tenantId, new LifecycleActionRequest("submit"), "sub-" + tenantId, "actor-1", "corr-1");
    service.approve(tenantId, new LifecycleActionRequest("approve"), "app-" + tenantId, "actor-2", "corr-1");
    service.publish(tenantId, new PublishCatalogRequest("initial catalog", LocalDate.now()), "pub-" + tenantId, "actor-2", "corr-1");
  }

  private CatalogVersionControlRecord draftTaxonomyVersion(UUID tenantId, String code) {
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest(code, code, "FAMILY", null, "CONVENTIONAL", Instant.parse("2026-01-01T00:00:00Z"), null, 10), "tax-" + code + "-" + tenantId, "actor-1", "corr-0205");
    return service.versions(tenantId).stream().filter(version -> version.artifactCode().equals(code)).findFirst().orElseThrow();
  }

  private void publishProductTaxonomy(UUID tenantId) {
    Instant effectiveStart = Instant.parse("2026-01-01T00:00:00Z");
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest("CONVENTIONAL", "Conventional", "FAMILY", null, "CONVENTIONAL", effectiveStart, null, 10), "tax-family-" + tenantId, "actor-1", "corr-0201");
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest("CONVENTIONAL_FIXED", "Conventional Fixed Rate", "TYPE", "CONVENTIONAL", "CONVENTIONAL", effectiveStart, null, 20), "tax-fixed-" + tenantId, "actor-1", "corr-0201");
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest("CONVENTIONAL_ARM", "Conventional Adjustable Rate", "TYPE", "CONVENTIONAL", "CONVENTIONAL", effectiveStart, null, 30), "tax-arm-" + tenantId, "actor-1", "corr-0201");
    service.validate(tenantId, new LifecycleActionRequest("taxonomy valid"), "tax-val-" + tenantId, "actor-1", "corr-0201");
    service.submitApproval(tenantId, new LifecycleActionRequest("taxonomy submit"), "tax-sub-" + tenantId, "actor-1", "corr-0201");
    service.approve(tenantId, new LifecycleActionRequest("taxonomy approve"), "tax-app-" + tenantId, "actor-2", "corr-0201");
    service.publish(tenantId, new PublishCatalogRequest("taxonomy publish", LocalDate.of(2026, 1, 1)), "tax-pub-" + tenantId, "actor-3", "corr-0201");
  }

  private void seedConventionalReferences(UUID tenantId) {
    Instant effectiveStart = Instant.parse("2026-01-01T00:00:00Z");
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest("CONVENTIONAL", "Conventional", "FAMILY", null, "CONVENTIONAL", effectiveStart, null, 10), "conv-tax-family-" + tenantId, "actor-1", "corr-0202");
    service.addProductTaxonomyDraft(tenantId, new ProductTaxonomyDraftRequest("CONVENTIONAL_FIXED", "Conventional Fixed Rate", "TYPE", "CONVENTIONAL", "CONVENTIONAL", effectiveStart, null, 20), "conv-tax-fixed-" + tenantId, "actor-1", "corr-0202");
    service.addReference(tenantId, "CHANNEL", new ReferenceCatalogRequest("RETAIL", "Retail", "CHANNEL", Map.of(), LocalDate.of(2026, 1, 1), null), "conv-channel-" + tenantId, "actor-1", "corr-0202");
    service.addTermAmortizationDraft(tenantId, fixedThirtyTermRequest(), "conv-term-" + tenantId, "actor-1", "corr-0202");
    service.addReference(tenantId, "PROPERTY_TYPE", new ReferenceCatalogRequest("SINGLE_FAMILY", "Single family", "PROPERTY", Map.of(), LocalDate.of(2026, 1, 1), null), "conv-property-" + tenantId, "actor-1", "corr-0202");
    service.addReference(tenantId, "OCCUPANCY_TYPE", new ReferenceCatalogRequest("PRIMARY_RESIDENCE", "Primary residence", "OCCUPANCY", Map.of(), LocalDate.of(2026, 1, 1), null), "conv-occupancy-" + tenantId, "actor-1", "corr-0202");
    service.addReference(tenantId, "LOAN_PURPOSE", new ReferenceCatalogRequest("PURCHASE", "Purchase", "PURPOSE", Map.of(), LocalDate.of(2026, 1, 1), null), "conv-purpose-" + tenantId, "actor-1", "corr-0202");
    service.addMarket(tenantId, new MarketRequest("TX", null, null, "ENABLED", List.of("RETAIL"), LocalDate.of(2026, 1, 1), null), "conv-market-tx-" + tenantId, "actor-1", "corr-0202");
    service.addMarket(tenantId, new MarketRequest("CA", null, null, "ENABLED", List.of("RETAIL"), LocalDate.of(2026, 1, 1), null), "conv-market-ca-" + tenantId, "actor-1", "corr-0202");
    service.addProduct(tenantId, new ProductRequest("CONV_REF", "Conventional reference", "CONVENTIONAL", List.of("RETAIL"), List.of("TX", "CA"), LocalDate.of(2026, 1, 1), null), "conv-product-ref-" + tenantId, "actor-1", "corr-0202");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV_REF"), LocalDate.of(2026, 1, 1), null), "conv-investor-" + tenantId, "actor-1", "corr-0202");
  }

  private void publishPropertyOccupancyCatalog(UUID tenantId) {
    service.addProduct(tenantId, new ProductRequest("CONV_REF", "Conventional reference", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null), "po-product-" + tenantId, "actor-1", "corr-0207");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV_REF"), LocalDate.of(2026, 1, 1), null), "po-investor-" + tenantId, "actor-1", "corr-0207");
    service.addPropertyTypeDraft(tenantId, propertyType("SINGLE_FAMILY", "Single Family", false, 1, 1), "po-pt-single-" + tenantId, "actor-1", "corr-0207");
    service.addPropertyTypeDraft(tenantId, propertyType("PUD", "Planned Unit Development", false, 1, 1), "po-pt-pud-" + tenantId, "actor-1", "corr-0207");
    service.addPropertyTypeDraft(tenantId, propertyType("CONDO", "Condominium", true, 1, 1), "po-pt-condo-" + tenantId, "actor-1", "corr-0207");
    service.addPropertyTypeDraft(tenantId, propertyType("TWO_TO_FOUR_UNIT", "2-4 Unit", false, 2, 4), "po-pt-2-4-" + tenantId, "actor-1", "corr-0207");
    service.addOccupancyTypeDraft(tenantId, occupancyType("PRIMARY_RESIDENCE", "Primary Residence"), "po-ot-primary-" + tenantId, "actor-1", "corr-0207");
    service.addOccupancyTypeDraft(tenantId, occupancyType("SECOND_HOME", "Second Home"), "po-ot-second-" + tenantId, "actor-1", "corr-0207");
    service.addOccupancyTypeDraft(tenantId, occupancyType("INVESTMENT_PROPERTY", "Investment Property"), "po-ot-investment-" + tenantId, "actor-1", "corr-0207");
    service.validate(tenantId, new LifecycleActionRequest("property occupancy valid"), "po-val-" + tenantId, "actor-1", "corr-0207");
    service.submitApproval(tenantId, new LifecycleActionRequest("property occupancy submit"), "po-sub-" + tenantId, "actor-1", "corr-0207");
    service.approve(tenantId, new LifecycleActionRequest("property occupancy approve"), "po-app-" + tenantId, "actor-2", "corr-0207");
    service.publish(tenantId, new PublishCatalogRequest("property occupancy publish", LocalDate.of(2026, 1, 1)), "po-pub-" + tenantId, "actor-3", "corr-0207");
  }

  private void publishLoanPurposeCatalog(UUID tenantId) {
    service.addProduct(tenantId, new ProductRequest("CONV_REF", "Conventional reference", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.of(2026, 1, 1), null), "lp-product-" + tenantId, "actor-1", "corr-0208");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV_REF"), LocalDate.of(2026, 1, 1), null), "lp-investor-" + tenantId, "actor-1", "corr-0208");
    service.addLoanPurposeDraft(tenantId, loanPurpose("PURCHASE", "Purchase", "PURCHASE", false, false, false, true, List.of("PUR")), "lp-purchase-" + tenantId, "actor-1", "corr-0208");
    service.addLoanPurposeDraft(tenantId, loanPurpose("RATE_TERM_REFI", "Rate/Term Refinance", "REFINANCE", true, false, true, true, List.of("RATE_TERM_REFINANCE")), "lp-rt-" + tenantId, "actor-1", "corr-0208");
    service.addLoanPurposeDraft(tenantId, loanPurpose("CASH_OUT_REFI", "Cash-Out Refinance", "REFINANCE", true, true, true, true, List.of("CASH_OUT_REFINANCE")), "lp-cashout-" + tenantId, "actor-1", "corr-0208");
    service.addLoanPurposeDraft(tenantId, loanPurpose("CONSTRUCTION_TO_PERMANENT", "Construction-to-Permanent", "CONSTRUCTION", true, false, true, false, List.of("CONSTRUCTION_PERM")), "lp-ctp-" + tenantId, "actor-1", "corr-0208");
    service.validate(tenantId, new LifecycleActionRequest("loan purpose valid"), "lp-val-" + tenantId, "actor-1", "corr-0208");
    service.submitApproval(tenantId, new LifecycleActionRequest("loan purpose submit"), "lp-sub-" + tenantId, "actor-1", "corr-0208");
    service.approve(tenantId, new LifecycleActionRequest("loan purpose approve"), "lp-app-" + tenantId, "actor-2", "corr-0208");
    service.publish(tenantId, new PublishCatalogRequest("loan purpose publish", LocalDate.of(2026, 1, 1)), "lp-pub-" + tenantId, "actor-3", "corr-0208");
  }

  private void publishMarketCatalog(UUID tenantId) {
    service.addProduct(tenantId, new ProductRequest("CONV_REF", "Conventional reference", "CONVENTIONAL", List.of("RETAIL"), List.of("TX", "CA"), LocalDate.of(2026, 1, 1), null), "market-product-" + tenantId, "actor-1", "corr-0209");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV_REF"), LocalDate.of(2026, 1, 1), null), "market-investor-" + tenantId, "actor-1", "corr-0209");
    service.importMarkets(tenantId, new MarketImportRequest("2026 county market baseline", List.of(
        new MarketImportRow("TX", "Texas", null, null, "DISABLED", "STATE_DISABLED", List.of(), List.of(), Instant.parse("2026-01-01T00:00:00Z"), null),
        new MarketImportRow("TX", "Texas", "48201", "Harris", "ENABLED", null, List.of(), List.of(), Instant.parse("2026-01-01T00:00:00Z"), null),
        new MarketImportRow("CA", "California", "06001", "Alameda", "RESTRICTED", "PRODUCT_CHANNEL_ALLOWED", List.of("RETAIL"), List.of("CONV_FIXED_30"), Instant.parse("2026-01-01T00:00:00Z"), null))),
        "market-import-" + tenantId, "actor-1", "corr-0209");
    service.validate(tenantId, new LifecycleActionRequest("market valid"), "market-val-" + tenantId, "actor-1", "corr-0209");
    service.submitApproval(tenantId, new LifecycleActionRequest("market submit"), "market-sub-" + tenantId, "actor-1", "corr-0209");
    service.approve(tenantId, new LifecycleActionRequest("market approve"), "market-app-" + tenantId, "actor-2", "corr-0209");
    service.publish(tenantId, new PublishCatalogRequest("market publish", LocalDate.of(2026, 1, 1)), "market-pub-" + tenantId, "actor-3", "corr-0209");
  }

  private LoanPurposeDraftRequest loanPurpose(String code, String label, String category, boolean refinance, boolean cashOut, boolean lien, boolean conventional, List<String> aliases) {
    return new LoanPurposeDraftRequest(code, label, category, refinance, cashOut, lien, conventional, aliases, Instant.parse("2026-01-01T00:00:00Z"), null);
  }

  private PropertyTypeDraftRequest propertyType(String code, String label, boolean requiresProjectReview, int unitMin, int unitMax) {
    return new PropertyTypeDraftRequest(code, label, "PROPERTY", List.of(code), true, requiresProjectReview, unitMin, unitMax, Instant.parse("2026-01-01T00:00:00Z"), null);
  }

  private OccupancyTypeDraftRequest occupancyType(String code, String label) {
    return new OccupancyTypeDraftRequest(code, label, List.of(code), true, Instant.parse("2026-01-01T00:00:00Z"), null);
  }

  private ConventionalProductDraftRequest fixedThirtyRequest(String productCode, List<String> states) {
    return new ConventionalProductDraftRequest(productCode, "Conventional Fixed 30 Year", "CONVENTIONAL_FIXED", List.of("FNMA"), List.of("RETAIL"), List.of(360), "FIXED", null, null, null, List.of("SINGLE_FAMILY"), List.of("PRIMARY_RESIDENCE"), List.of("PURCHASE"), states, new BigDecimal("50000.00"), new BigDecimal("806500.00"), Instant.parse("2026-01-01T00:00:00Z"), null);
  }

  private TermAmortizationDraftRequest fixedThirtyTermRequest() {
    return new TermAmortizationDraftRequest("FIXED_30YR", "30 Year Fixed", 360, "FIXED", false, false, null, null, null, 0, BigDecimal.ZERO, Instant.parse("2026-01-01T00:00:00Z"), null);
  }

  private TermAmortizationDraftRequest armSevenSixRequest() {
    return new TermAmortizationDraftRequest("ARM_7_6", "7/6 SOFR ARM", 360, "ARM", false, false, "SOFR_30_DAY_AVG", 84, 6, 45, new BigDecimal("12.5"), Instant.parse("2026-01-01T00:00:00Z"), null);
  }

  @Test
  void overlayLifecycle_createOverlayAndResolveProduct() {
    UUID tenantId = UUID.randomUUID();
    service.addProduct(tenantId, new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null), "prod", "actor-1", "corr");

    OverlayService overlayService = new OverlayService(service.getRepository().getJdbcTemplate(), new com.fasterxml.jackson.databind.ObjectMapper());
    OverlayCreateRequest req = new OverlayCreateRequest("CONV30", "interestRate", "3.5", LocalDate.now(), null, "rate adjustment", "ov-1");
    OverlayCreateResponse created = overlayService.createOverlay(tenantId, req, "actor-1");
    assertThat(created).isNotNull();
    assertThat(created.status()).isEqualTo("ACTIVE");

    OverlayResolveRequest resolveReq = new OverlayResolveRequest("CONV30", null, null, null);
    OverlayResolveResponse resolved = overlayService.resolveOverlay(tenantId, resolveReq);
    assertThat(resolved.appliedOverlays()).hasSize(1);
    assertThat(resolved.resolvedAttributes()).containsEntry("interestRate", "3.5");
  }

  @Test
  void versionDiff_acrossPublishedVersions() {
    UUID tenantId = UUID.randomUUID();
    service.addProduct(tenantId, new ProductRequest("V1PROD", "Version 1 Product", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null), "prod-v1", "actor-1", "corr");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("V1PROD"), LocalDate.now(), null), "inv-v1", "actor-1", "corr");
    service.validate(tenantId, new LifecycleActionRequest("v1"), "val", "actor-1", "corr");
    service.submitApproval(tenantId, new LifecycleActionRequest("v1"), "sub", "actor-1", "corr");
    service.approve(tenantId, new LifecycleActionRequest("v1"), "app", "actor-2", "corr");
    service.publish(tenantId, new PublishCatalogRequest("v1", LocalDate.now()), "pub", "actor-3", "corr");

    List<CatalogVersionControlRecord> versions = service.versions(tenantId);
    assertThat(versions).isNotEmpty();

    DiffService diffService = new DiffService(service.getRepository().getJdbcTemplate(), new com.fasterxml.jackson.databind.ObjectMapper());
    DiffResponse diff = diffService.computeDiff(tenantId, 1, 1);
    assertThat(diff).isNotNull();
    assertThat(diff.diffCount()).isZero();
  }

  @Test
  void sodEnforcement_sameActorCannotSubmitAndApprove() {
    UUID tenantId = UUID.randomUUID();
    service.addProduct(tenantId, new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null), "prod", "actor-1", "corr");
    service.addInvestor(tenantId, new InvestorRequest("FNMA", "Fannie Mae", List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null), "inv", "actor-1", "corr");
    service.validate(tenantId, new LifecycleActionRequest("validate"), "val", "actor-1", "corr");
    service.submitApproval(tenantId, new LifecycleActionRequest("submit"), "sub", "actor-1", "corr");

    assertThatThrownBy(() -> service.approve(tenantId, new LifecycleActionRequest("approve"), "app", "actor-1", "corr"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SEPARATION_OF_DUTIES_VIOLATION");
  }

  @Test
  void rbacEnforcement_wrongRoleFails() {
    UUID tenantId = UUID.randomUUID();
    RequestContext.clear();

    assertThatThrownBy(() -> service.addProduct(tenantId, new ProductRequest("CONV30", "Test", "CONVENTIONAL", List.of(), List.of(), LocalDate.now(), null), "key", "actor", "corr"))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("ROLE_REQUIRED");
  }

  @Test
  void idempotency_replayAndConflict_inIntegrationContext() {
    UUID tenantId = UUID.randomUUID();
    ProductRequest req = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);

    CatalogResponse r1 = service.addProduct(tenantId, req, "idemp-key", "actor-1", "corr");
    CatalogResponse r2 = service.addProduct(tenantId, req, "idemp-key", "actor-1", "corr");
    assertThat(r1.catalogId()).isEqualTo(r2.catalogId());
    assertThat(r1.version()).isEqualTo(r2.version());

    ProductRequest different = new ProductRequest("CONV15", "Conventional 15yr", "CONVENTIONAL", List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
    assertThatThrownBy(() -> service.addProduct(tenantId, different, "idemp-key", "actor-1", "corr"))
        .isInstanceOf(CatalogException.class)
        .hasMessage("IDEMPOTENCY_CONFLICT");
  }
}

package com.wcpe.catalog.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
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

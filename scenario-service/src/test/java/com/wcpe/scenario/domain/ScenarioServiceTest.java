package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
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
class ScenarioServiceTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("scenario")
      .withUsername("scenario_app")
      .withPassword("scenario_app");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired ScenarioService service;
  private final UUID tenantId = UUID.fromString("018fa4f0-1a4f-7e99-a02d-1b0100010001");

  @Test
  void createDraftScenarioCreatesVersionOneAndAuditHash() {
    ScenarioResponse response = createDraft("k1");

    assertThat(response.scenarioVersion()).isEqualTo(1);
    assertThat(response.status()).isEqualTo(ScenarioStatus.DRAFT_INCOMPLETE);
    assertThat(response.replayHash()).startsWith("sha256:");
    assertThat(service.events(tenantId, response.scenarioId())).extracting(EventRecord::eventType).contains("ScenarioDraftCreated.v1");
  }

  @Test
  void createDraftScenarioReplaysSameResponseForSameIdempotencyKey() {
    ScenarioResponse first = createDraft("same-key");
    ScenarioResponse second = createDraft("same-key");

    assertThat(second.scenarioId()).isEqualTo(first.scenarioId());
  }

  @Test
  void borrowerCreditDerivesLowestRepresentativeScore() {
    ScenarioResponse draft = createDraft("borrower-create");

    ScenarioResponse updated = service.updateBorrowers(tenantId, draft.scenarioId(), "borrower-update", "c-1",
        new BorrowerCreditRequest(1, List.of(
            new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
            new BorrowerCredit("B2", "CO_BORROWER", true, "AVAILABLE", 718, "TRI_MERGE", LocalDate.now()))));

    assertThat(updated.completedSections()).contains("BORROWER_CREDIT");
    assertThat(updated.derivedFields()).containsEntry("representativeCreditScore", 718);
  }

  @Test
  void loanStructureCalculatesLtvCltvHcltvWithBigDecimal() {
    ScenarioResponse draft = createDraft("loan-create");

    ScenarioResponse updated = service.updateLoan(tenantId, draft.scenarioId(), "loan-update", "c-2",
        new LoanStructureRequest(1, "PURCHASE", new BigDecimal("400000.00"), "FIRST", 360, "FIXED",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 30, new BigDecimal("500000.00")));

    assertThat(updated.completedSections()).contains("LOAN_STRUCTURE");
    assertThat(updated.derivedFields()).containsEntry("ltv", new BigDecimal("0.80000"));
    assertThat(updated.derivedFields()).containsEntry("ltvBps", new BigDecimal("8000.0000"));
  }

  @Test
  void propertyCaptureRejectsInactiveMarket() {
    ScenarioResponse draft = createDraft("property-create");

    ScenarioResponse updated = service.updateProperty(tenantId, draft.scenarioId(), "property-update", "c-3",
        new PropertyRequest(1, "ZZ", "Travis", "78701", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", 1,
            new BigDecimal("500000.00"), null, Map.of("manufacturedHome", false)));

    assertThat(updated.validationIssues()).extracting(ValidationIssue::code).contains("MARKET_NOT_ACTIVE");
  }

  @Test
  void completeScenarioNormalizesAndSubmits() {
    ScenarioResponse draft = createDraft("complete-create");
    ScenarioResponse b = service.updateBorrowers(tenantId, draft.scenarioId(), "b", "c", new BorrowerCreditRequest(1, List.of(new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 740, "TRI_MERGE", LocalDate.now()))));
    ScenarioResponse l = service.updateLoan(tenantId, draft.scenarioId(), "l", "c", new LoanStructureRequest(b.scenarioVersion(), "PURCHASE", new BigDecimal("400000.00"), "FIRST", 360, "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 30, new BigDecimal("500000.00")));
    ScenarioResponse p = service.updateProperty(tenantId, draft.scenarioId(), "p", "c", new PropertyRequest(l.scenarioVersion(), "TX", "Travis", "78701", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", 1, new BigDecimal("500000.00"), null, Map.of()));
    ScenarioResponse i = service.updateIncomeAssets(tenantId, draft.scenarioId(), "i", "c", new IncomeAssetRequest(p.scenarioVersion(), new BigDecimal("12000.00"), new BigDecimal("3600.00"), new BigDecimal("80000.00"), "FULL_DOC", false, false));

    ScenarioResponse normalized = service.normalize(tenantId, draft.scenarioId(), "n", "c");
    ScenarioResponse submitted = service.submit(tenantId, draft.scenarioId(), "s", "c");

    assertThat(i.blockingIssueCount()).isZero();
    assertThat(normalized.status()).isEqualTo(ScenarioStatus.NORMALIZED);
    assertThat(submitted.status()).isEqualTo(ScenarioStatus.READY_FOR_ELIGIBILITY);
  }

  @Test
  void replayPackageReturnsManifestAndRedactsByDefault() {
    ScenarioResponse draft = createDraft("replay-create");

    ReplayPackage replay = service.replay(tenantId, draft.scenarioId(), "latest", "role-default");

    assertThat(replay.versionManifest()).isNotEmpty();
    assertThat(replay.redactionApplied()).isTrue();
    assertThat(replay.eventReferences()).extracting(EventRecord::eventType).contains("ScenarioReplayPackageViewed.v1");
  }

  @Test
  void batchImportCreatesAcceptedDrafts() {
    BatchImportResponse response = service.importBatch(tenantId, "batch-key", "c", new BatchImportRequest(List.of(
        new CreateScenarioRequest("PURCHASE", "RETAIL", "One", null, "BATCH_IMPORT", Map.of()),
        new CreateScenarioRequest("CASH_OUT_REFI", "WHOLESALE", "Two", null, "BATCH_IMPORT", Map.of()))));

    assertThat(response.acceptedCount()).isEqualTo(2);
    assertThat(response.rejectedCount()).isZero();
  }

  private ScenarioResponse createDraft(String key) {
    return service.createDraft(tenantId, key, "corr-1", new CreateScenarioRequest("PURCHASE", "RETAIL", "Smith purchase", "LOS-1", "PRICING_WORKBENCH", Map.of("propertyState", "TX")));
  }
}

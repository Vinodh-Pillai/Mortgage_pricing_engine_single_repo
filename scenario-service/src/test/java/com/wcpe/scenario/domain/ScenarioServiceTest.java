package com.wcpe.scenario.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ScenarioServiceTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("scenario")
      .withUsername("scenario_app")
      .withPassword("scenario_app");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    DockerClientFactory.instance().client();
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired ScenarioService service;
  @Autowired SubmissionProfileService profileService;
  private final UUID tenantId = UUID.fromString("018fa4f0-1a4f-7e99-a02d-1b0100010001");

  @BeforeEach
  void roles() {
    ensureProfile("RETAIL", "PURCHASE");
    ensureProfile("WHOLESALE", "PURCHASE");
    ensureProfile("WHOLESALE", "CASH_OUT_REFI");
    RequestContext.roles("SCENARIO_WRITER,SCENARIO_REPLAY");
  }

  @AfterEach
  void clearRoles() {
    RequestContext.clear();
  }

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
  void rejectsSameKeyDifferentPatchBody() {
    ScenarioResponse draft = createDraft("patch-conflict-create");
    service.updateProperty(tenantId, draft.scenarioId(), "same-patch-key", "c-3",
        new PropertyRequest(1, "TX", "Travis", "78701", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", 1,
            new BigDecimal("500000.00"), null, Map.of()));

    assertThatThrownBy(() -> service.updateProperty(tenantId, draft.scenarioId(), "same-patch-key", "c-4",
        new PropertyRequest(1, "CA", "Orange", "92618", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", 1,
            new BigDecimal("500000.00"), null, Map.of())))
        .isInstanceOf(ScenarioException.class)
        .extracting("code")
        .isEqualTo("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void replaysSamePatchResponseWithoutDuplicateEvent() {
    ScenarioResponse draft = createDraft("patch-replay-create");

    ScenarioResponse first = service.updateProperty(tenantId, draft.scenarioId(), "property-replay", "c-3",
        new PropertyRequest(1, "TX", "Travis", "78701", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", 1,
            new BigDecimal("500000.00"), null, Map.of()));
    ScenarioResponse second = service.updateProperty(tenantId, draft.scenarioId(), "property-replay", "c-4",
        new PropertyRequest(1, "TX", "Travis", "78701", "SINGLE_FAMILY", "PRIMARY_RESIDENCE", 1,
            new BigDecimal("500000.00"), null, Map.of()));

    assertThat(second.scenarioVersion()).isEqualTo(first.scenarioVersion());
    assertThat(service.events(tenantId, draft.scenarioId()).stream()
        .filter(e -> "ScenarioPropertyUpdated.v1".equals(e.eventType())))
        .hasSize(1);
  }

  @Test
  void allowsSameKeyAcrossDifferentTenants() {
    UUID otherTenant = UUID.fromString("018fa4f0-1a4f-7e99-a02d-1b0100010002");

    ScenarioResponse first = createDraft("tenant-shared-key");
    ScenarioResponse second = service.createDraft(otherTenant, "tenant-shared-key", "corr-1",
        new CreateScenarioRequest("PURCHASE", "RETAIL", "Jones purchase", "LOS-2", "PRICING_WORKBENCH", Map.of("propertyState", "TX")));

    assertThat(second.scenarioId()).isNotEqualTo(first.scenarioId());
  }

  @Test
  void mutatingCommandsRequireIdempotencyKey() {
    assertThatThrownBy(() -> service.createDraft(tenantId, " ", "corr-1",
        new CreateScenarioRequest("PURCHASE", "RETAIL", "Smith purchase", "LOS-1", "PRICING_WORKBENCH", Map.of())))
        .isInstanceOf(ScenarioException.class)
        .extracting("code")
        .isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
  }

  @Test
  void missingSubmissionProfileFailsClosedOnCreate() {
    UUID tenantWithoutProfile = UUID.fromString("018fa4f0-1a4f-7e99-a02d-1b0100010099");

    assertThatThrownBy(() -> service.createDraft(tenantWithoutProfile, "missing-profile", "corr-1",
        new CreateScenarioRequest("PURCHASE", "RETAIL", "No profile", "LOS-X", "PRICING_WORKBENCH", Map.of())))
        .isInstanceOf(ScenarioException.class)
        .extracting("code")
        .isEqualTo("SUBMISSION_PROFILE_NOT_FOUND");
  }

  @Test
  void createDraftAppliesPublishedProfileValidationIssues() {
    ScenarioResponse response = service.createDraft(tenantId, "profile-warning", "corr-1",
        new CreateScenarioRequest("CASH_OUT_REFI", "WHOLESALE", "Wholesale missing property", "LOS-3", "PRICING_WORKBENCH", Map.of()));

    assertThat(response.validationIssues()).extracting(ValidationIssue::code).contains("FIELD_REQUIRED_BY_PROFILE");
    assertThat(response.warningIssueCount()).isGreaterThan(0);
  }

  @Test
  void borrowerCreditDerivesLowestRepresentativeScore() {
    ScenarioResponse draft = createDraft("borrower-create");

    BorrowerCreditResponse updated = service.updateBorrowers(tenantId, draft.scenarioId(), "borrower-update", "c-1",
        new BorrowerCreditRequest(1, List.of(
            new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
            new BorrowerCredit("B2", "CO_BORROWER", true, "AVAILABLE", 718, "TRI_MERGE", LocalDate.now()))));

    assertThat(updated.updatedSections()).contains("BORROWER_CREDIT");
    assertThat(updated.representativeCreditScore()).isEqualTo(718);
    assertThat(updated.representativeCreditScoreRule()).isEqualTo("LOWEST_REPRESENTATIVE_SCORE");
    assertThat(service.events(tenantId, draft.scenarioId())).extracting(EventRecord::eventType).contains("ScenarioBorrowerCreditUpdated.v1");
  }

  @Test
  void borrowerCreditExcludesFrozenNoScoreAndNonOccupyingBorrowers() {
    ScenarioResponse draft = createDraft("borrower-exclusions");

    BorrowerCreditResponse updated = service.updateBorrowers(tenantId, draft.scenarioId(), "borrower-exclusion-update", "c-1",
        new BorrowerCreditRequest(1, List.of(
            new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
            new BorrowerCredit("B2", "CO_BORROWER", true, "FROZEN", 620, "TRI_MERGE", LocalDate.now()),
            new BorrowerCredit("B3", "NON_OCCUPANT_CO_BORROWER", false, "AVAILABLE", 610, "TRI_MERGE", LocalDate.now()))));

    assertThat(updated.creditReadinessStatus()).isEqualTo("COMPLETE");
    assertThat(updated.representativeCreditScore()).isEqualTo(742);
  }

  @Test
  void borrowerCreditRejectsScoreOutsideMortgageRange() {
    ScenarioResponse draft = createDraft("borrower-invalid-score");

    assertThatThrownBy(() -> service.updateBorrowers(tenantId, draft.scenarioId(), "borrower-invalid-score-update", "c-1",
        new BorrowerCreditRequest(1, List.of(
            new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 299, "TRI_MERGE", LocalDate.now())))))
        .isInstanceOf(ScenarioException.class)
        .extracting("code")
        .isEqualTo("CREDIT_SCORE_OUT_OF_RANGE");
  }

  @Test
  void borrowerCreditRejectsDuplicatePrimaryBorrowers() {
    ScenarioResponse draft = createDraft("borrower-duplicate-primary");

    assertThatThrownBy(() -> service.updateBorrowers(tenantId, draft.scenarioId(), "borrower-duplicate-primary-update", "c-1",
        new BorrowerCreditRequest(1, List.of(
            new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 742, "TRI_MERGE", LocalDate.now()),
            new BorrowerCredit("B2", "PRIMARY", true, "AVAILABLE", 718, "TRI_MERGE", LocalDate.now())))))
        .isInstanceOf(ScenarioException.class)
        .extracting("code")
        .isEqualTo("DUPLICATE_PRIMARY_BORROWER");
  }

  @Test
  void loanStructureCalculatesLtvCltvHcltvWithBigDecimal() {
    ScenarioResponse draft = createDraft("loan-create");

    LoanStructureResponse updated = service.updateLoan(tenantId, draft.scenarioId(), "loan-update", "c-2",
        new LoanStructureRequest(1, "PURCHASE", new BigDecimal("400000.00"), "FIRST", 360, "FIXED",
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 30, new BigDecimal("500000.00")));

    assertThat(updated.loanStructureStatus()).isEqualTo("COMPLETE");
    assertThat(updated.metrics()).containsEntry("ltv", new BigDecimal("0.80000"));
    assertThat(updated.metrics()).containsEntry("ltvBps", new BigDecimal("8000.0000"));
    assertThat(service.events(tenantId, draft.scenarioId())).extracting(EventRecord::eventType).contains("ScenarioLoanStructureUpdated.v1");
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
    BorrowerCreditResponse b = service.updateBorrowers(tenantId, draft.scenarioId(), "b", "c", new BorrowerCreditRequest(1, List.of(new BorrowerCredit("B1", "PRIMARY", true, "AVAILABLE", 740, "TRI_MERGE", LocalDate.now()))));
    LoanStructureResponse l = service.updateLoan(tenantId, draft.scenarioId(), "l", "c", new LoanStructureRequest(b.scenarioVersion(), "PURCHASE", new BigDecimal("400000.00"), "FIRST", 360, "FIXED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 30, new BigDecimal("500000.00")));
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
    assertThat((Map<String, Object>) replay.normalizedSnapshot().get("hashVerification"))
        .containsEntry("verified", true)
        .containsKey("expectedHash")
        .containsKey("actualHash");
    assertThat(replay.auditPackageId()).isNotNull();
    assertThat(replay.eventReferences()).extracting(EventRecord::eventType).contains("ScenarioReplayPackageViewed.v1");
  }

  @Test
  void replayPackageRequiresFullExportPermission() {
    ScenarioResponse draft = createDraft("replay-full-denied");

    assertThatThrownBy(() -> service.replay(tenantId, draft.scenarioId(),
        new ScenarioReplayAccessRequest("latest", "full", true, "EXPORT_FIXTURE", "corr-full")))
        .isInstanceOf(ScenarioException.class)
        .extracting("code")
        .isEqualTo("ACCESS_DENIED");
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

  private void ensureProfile(String channel, String quoteIntent) {
    RequestContext.roles("SCENARIO_ADMIN");
    try {
      if (profileService.getActiveChannelProfile(tenantId, channel, quoteIntent) != null) return;
      Instant from = Instant.parse("2025-01-01T00:00:00Z");
      SubmissionProfileResponse draft = profileService.createDraft(tenantId, "seed-" + channel + "-" + quoteIntent, "seed-corr", "test-admin",
          new CreateSubmissionProfileRequest(channel, quoteIntent, channel + " " + quoteIntent + " Required Fields", from, null,
              List.of(new SubmissionProfileFieldRule("PROPERTY", "propertyState", "always()", FieldSeverity.WARNING,
                  "Property state is required by the active submission profile.", "Provide property state before final submission."))));
      profileService.publish(tenantId, "publish-" + channel + "-" + quoteIntent, "seed-corr", "test-admin",
          new PublishSubmissionProfileRequest(draft.profileId(), from, null, "approved-by-test", "seed-change-set", Instant.now()));
    } finally {
      RequestContext.clear();
    }
  }
}

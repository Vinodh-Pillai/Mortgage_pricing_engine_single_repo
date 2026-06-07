package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateSheetReviewServiceTest {
  private static final String TENANT_ONE = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_TWO = "22222222-2222-2222-2222-222222222222";
  private static final Instant NOW = Instant.parse("2026-06-06T12:06:00Z");

  private final RateSheetReviewService service = new RateSheetReviewService(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void filtersRateSheetReviewQueueByTenantStatusInvestorAndEffectiveDate() {
    GovernanceValidationResult<RateSheetQueueSnapshot> result = service.queue(queueRequest("idem-queue-1"));

    assertTrue(result.valid());
    RateSheetQueueSnapshot snapshot = result.value().orElseThrow();
    assertEquals(1, snapshot.rows().size());
    assertEquals("version-current", snapshot.rows().get(0).versionId());
    assertEquals(1, snapshot.statusCounts().get("DRAFT"));
  }

  @Test
  void loadsReviewPageWithRowEvidenceMappingsChecklistAuditAndNoRawValuesInEvents() {
    service.addNote(noteCommand("idem-note-1", "HIGH_VARIANCE"));

    GovernanceValidationResult<RateSheetReviewSnapshot> result = service.review(reviewRequest("idem-review-1", policy(true)));

    assertTrue(result.valid());
    RateSheetReviewSnapshot snapshot = result.value().orElseThrow();
    assertEquals("RateSheetReviewStarted.v1", service.outboxEvents().get(1).eventType());
    assertEquals(RateSheetReviewService.AUDIT_ACTION, service.auditRecords().get(0).action());
    assertEquals(2, snapshot.rowsPage().size());
    assertEquals(2, snapshot.varianceSummary().findingCount());
    assertEquals(1, snapshot.varianceSummary().missingMappingRowCount());
    assertFalse(snapshot.varianceSummary().requiredNoteMissing());
    assertTrue(snapshot.checklist().publishBlocked());
    assertEquals("true", snapshot.permissions().get("annotate"));
    assertEquals("row-2", snapshot.mappingExceptionRowIds().get(0));
    assertFalse(service.outboxEvents().get(1).payload().toString().contains("101.125"));
    assertTrue(service.outboxEvents().get(1).payload().containsKey("rowHashSet"));
  }

  @Test
  void failsClosedWhenConfigurableTolerancePolicyOrChecklistMetadataIsMissing() {
    GovernanceValidationResult<RateSheetReviewSnapshot> missingPolicy = service.review(reviewRequest("idem-review-2", policy(false)));
    GovernanceValidationResult<RateSheetReviewSnapshot> missingPermission =
        service.review(
            new RateSheetReviewRequest(
                "idem-review-3",
                "analyst-1",
                List.of("admin.config.write"),
                currentHeader(),
                currentRows(),
                findings(),
                checklist(),
                policy(true),
                new RateSheetPage(0, 25),
                "corr-PII-12-S06"));

    assertFalse(missingPolicy.valid());
    assertEquals("POLICY_NOT_SATISFIED: rate sheet review policy and tolerance configuration are required", missingPolicy.error().orElseThrow());
    assertFalse(missingPermission.valid());
    assertEquals("TENANT_ACCESS_DENIED", missingPermission.error().orElseThrow());
  }

  @Test
  void storesReviewNotesAndRequiresNotesForConfiguredVarianceSeverity() {
    GovernanceValidationResult<RateSheetReviewSnapshot> beforeNote = service.review(reviewRequest("idem-review-4", policy(true)));
    GovernanceValidationResult<ReviewNote> note = service.addNote(noteCommand("idem-note-2", "HIGH_VARIANCE"));
    GovernanceValidationResult<RateSheetReviewSnapshot> afterNote = service.review(reviewRequest("idem-review-5", policy(true)));

    assertTrue(beforeNote.value().orElseThrow().varianceSummary().requiredNoteMissing());
    assertTrue(note.valid());
    assertEquals("RateSheetVarianceAnnotated.v1", service.outboxEvents().get(1).eventType());
    assertEquals(1, service.notes(TENANT_ONE, "version-current").size());
    assertFalse(afterNote.value().orElseThrow().varianceSummary().requiredNoteMissing());
  }

  @Test
  void comparesCurrentAndPriorRowsUsingStableRowHashes() {
    GovernanceValidationResult<RateSheetComparison> result = service.compare(compareRequest());

    assertTrue(result.valid());
    RateSheetComparison comparison = result.value().orElseThrow();
    assertEquals(2, comparison.changedRowCount());
    assertEquals("CHANGED", comparison.rows().stream().filter(row -> row.rowId().equals("row-1")).findFirst().orElseThrow().status());
    assertEquals("ADDED", comparison.rows().stream().filter(row -> row.rowId().equals("row-2")).findFirst().orElseThrow().status());
    assertEquals(64, comparison.replayRef().length());
  }

  @Test
  void replaysIdempotentReviewRequestAndRejectsChangedReplay() {
    RateSheetReviewSnapshot first = service.review(reviewRequest("idem-review-6", policy(true))).value().orElseThrow();
    RateSheetReviewSnapshot replay = service.review(reviewRequest("idem-review-6", policy(true))).value().orElseThrow();
    GovernanceValidationResult<RateSheetReviewSnapshot> conflict = service.review(reviewRequest("idem-review-6", new RateSheetReviewPolicy("policy-v2", true, List.of("HIGH"))));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
  }

  private RateSheetQueueRequest queueRequest(String idempotencyKey) {
    return new RateSheetQueueRequest(
        TENANT_ONE,
        idempotencyKey,
        "analyst-1",
        readPermissions(),
        List.of(
            currentHeader(),
            new RateSheetHeader(TENANT_ONE, "artifact-other", "version-other", "investor-b", "product-a", "retail", LocalDate.parse("2026-06-15"), "DRAFT", 1, "rowhash-other", NOW.minusSeconds(60)),
            new RateSheetHeader(TENANT_TWO, "artifact-tenant-two", "version-tenant-two", "investor-a", "product-a", "retail", LocalDate.parse("2026-06-15"), "DRAFT", 1, "rowhash-other-tenant", NOW.minusSeconds(30))),
        new RateSheetQueueFilter("DRAFT", "investor-a", "product-a", "retail", LocalDate.parse("2026-06-15")),
        "corr-PII-12-S06");
  }

  private RateSheetReviewRequest reviewRequest(String idempotencyKey, RateSheetReviewPolicy policy) {
    return new RateSheetReviewRequest(idempotencyKey, "analyst-1", readWritePermissions(), currentHeader(), currentRows(), findings(), checklist(), policy, new RateSheetPage(0, 25), "corr-PII-12-S06");
  }

  private ReviewNoteCommand noteCommand(String idempotencyKey, String findingCode) {
    return new ReviewNoteCommand(TENANT_ONE, "artifact-rate-sheet", "version-current", idempotencyKey, "analyst-1", readWritePermissions(), "row-1", findingCode, "tenant-configured-reason", "Variance reviewed with configured evidence.", "corr-PII-12-S06");
  }

  private RateSheetCompareRequest compareRequest() {
    return new RateSheetCompareRequest(TENANT_ONE, "version-current", "version-prior", readPermissions(), currentRows(), priorRows(), "corr-PII-12-S06");
  }

  private RateSheetHeader currentHeader() {
    return new RateSheetHeader(TENANT_ONE, "artifact-rate-sheet", "version-current", "investor-a", "product-a", "retail", LocalDate.parse("2026-06-15"), "DRAFT", 7, "rowhash-set-current", NOW);
  }

  private List<RateSheetRow> currentRows() {
    return List.of(
        new RateSheetRow(TENANT_ONE, "version-current", "row-1", "product-a", "investor-a", "retail", "lock-30", "6.625", "101.125", "adjustment-hash-1", "rowhash-current-1", "MAPPED"),
        new RateSheetRow(TENANT_ONE, "version-current", "row-2", "product-a", "investor-a", "retail", "lock-45", "6.750", "100.875", "adjustment-hash-2", "rowhash-current-2", "MISSING_MAPPING"));
  }

  private List<RateSheetRow> priorRows() {
    return List.of(new RateSheetRow(TENANT_ONE, "version-prior", "row-1", "product-a", "investor-a", "retail", "lock-30", "6.625", "101.000", "adjustment-hash-1", "rowhash-prior-1", "MAPPED"));
  }

  private List<VarianceFinding> findings() {
    return List.of(
        new VarianceFinding(TENANT_ONE, "version-current", "row-1", "HIGH_VARIANCE", "HIGH", "rate-sheet.variance.high", false),
        new VarianceFinding(TENANT_ONE, "version-current", "row-2", "MAPPING_MISSING", "BLOCKER", "rate-sheet.mapping.missing", true));
  }

  private RateSheetReviewChecklist checklist() {
    return new RateSheetReviewChecklist("checklist-v1", List.of("mapping-reviewed", "variance-notes-complete", "validation-run-current"), false);
  }

  private RateSheetReviewPolicy policy(boolean tolerancesConfigured) {
    return new RateSheetReviewPolicy("policy-v1", tolerancesConfigured, List.of("HIGH"));
  }

  private List<String> readPermissions() {
    return List.of(RateSheetReviewService.READ_PERMISSION);
  }

  private List<String> readWritePermissions() {
    return List.of(RateSheetReviewService.READ_PERMISSION, RateSheetReviewService.WRITE_PERMISSION, "admin.config.submit", "admin.config.approve");
  }
}

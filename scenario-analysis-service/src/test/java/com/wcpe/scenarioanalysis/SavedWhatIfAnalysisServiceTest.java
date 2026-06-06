package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.AnalysisVersionConflictException;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.CreateAnalysisCommand;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.InMemorySelectionAvailabilityChecker;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.InMemorySavedAnalysisRepository;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.PatchAnalysisCommand;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.SelectionNotAvailableException;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.SharePermission;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.ShareTargetNotFoundException;
import com.wcpe.scenarioanalysis.SavedWhatIfAnalysisService.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavedWhatIfAnalysisServiceTest {
  private InMemorySavedAnalysisRepository repository;
  private SavedWhatIfAnalysisService service;

  @BeforeEach
  void setUp() {
    repository = new InMemorySavedAnalysisRepository();
    service = new SavedWhatIfAnalysisService(
        repository,
        Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void whatIfAnalysisTestSavesSelectedVariantsAndComputesHash() {
    var response = service.saveAnalysis(validCommand("idem-save"));

    assertThat(response.status()).isEqualTo("SAVED");
    assertThat(response.version()).isEqualTo(1);
    assertThat(response.selectedVariantIds()).containsExactly("variant-001", "variant-002");
    assertThat(response.selectedGridCellIds()).containsExactly("grid-cell-001");
    assertThat(response.analysisHash()).startsWith("sha256:");
    assertThat(response.notes()).isEqualTo("Safe borrower-visible summary without raw identifiers");
    assertThat(response.notesHash()).startsWith("sha256:");
    assertThat(response.noteHistory()).hasSize(1);
    assertThat(response.noteHistory().get(0).version()).isEqualTo(1);
    assertThat(response.noteHistory().get(0).notes()).isEqualTo("Safe borrower-visible summary without raw identifiers");
    assertThat(response.noteHistory().get(0).notesHash()).isEqualTo(response.notesHash());
    assertThat(response.auditRef()).contains("WHAT_IF_ANALYSIS_SAVED");
    assertThat(response.replayRef()).startsWith("replay:saved-what-if-analysis:");
    assertThat(response.linkedToQuoteDecision()).isTrue();
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void replaysSameSaveForDuplicateIdempotencyKey() {
    var first = service.saveAnalysis(validCommand("idem-replay"));
    var replay = service.saveAnalysis(validCommand("idem-replay"));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
  }

  @Test
  void duplicateIdempotencyKeyWithDifferentAnalysisConflicts() {
    service.saveAnalysis(validCommand("idem-conflict"));

    assertThatThrownBy(() -> service.saveAnalysis(new CreateAnalysisCommand(
        "tenant-001",
        "Different name",
        "borrower-retention",
        "quote-123",
        4,
        "pricing-config-v8",
        List.of("variant-001"),
        List.of("grid-cell-001"),
        "safe notes",
        List.of("fico-ltv"),
        "private",
        "standard-audit",
        true,
        "idem-conflict",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different saved analysis request");
  }

  @Test
  void analysisConflictTestRejectsStalePatch() {
    var created = service.saveAnalysis(validCommand("idem-stale"));

    assertThatThrownBy(() -> service.patchAnalysis(new PatchAnalysisCommand(
        "tenant-001",
        created.analysisId(),
        2,
        "Updated analysis",
        null,
        null,
        null,
        null,
        null,
        "actor-001",
        "corr-patch",
        "cause-patch")))
        .isInstanceOf(AnalysisVersionConflictException.class)
        .hasMessage("If-Match version does not match current saved analysis version");
  }

  @Test
  void patchUpdatesNotesHashAndSharePermissions() {
    var created = service.saveAnalysis(validCommand("idem-patch"));

    var patched = service.patchAnalysis(new PatchAnalysisCommand(
        "tenant-001",
        created.analysisId(),
        1,
        "Updated analysis",
        "Updated safe note",
        List.of("saved", "branch-a"),
        "team",
        null,
        List.of(new SharePermission("team", "branch-a", "read")),
        "actor-001",
        "corr-patch",
        "cause-patch"));

    assertThat(patched.version()).isEqualTo(2);
    assertThat(patched.name()).isEqualTo("Updated analysis");
    assertThat(patched.visibility()).isEqualTo("team");
    assertThat(patched.notes()).isEqualTo("Updated safe note");
    assertThat(patched.notesHash()).isNotEqualTo(created.notesHash());
    assertThat(patched.noteHistory()).hasSize(2);
    assertThat(patched.noteHistory().get(0).notes()).isEqualTo("Safe borrower-visible summary without raw identifiers");
    assertThat(patched.noteHistory().get(1).version()).isEqualTo(2);
    assertThat(patched.noteHistory().get(1).notes()).isEqualTo("Updated safe note");
    assertThat(patched.noteHistory().get(1).notesHash()).isEqualTo(patched.notesHash());
    assertThat(patched.sharePermissions()).containsExactly(new SharePermission("team", "branch-a", "read"));
  }

  @Test
  void analysisVisibilityPolicyTestRejectsUnauthorizedShare() {
    var created = service.saveAnalysis(validCommand("idem-share"));

    assertThatThrownBy(() -> service.patchAnalysis(new PatchAnalysisCommand(
        "tenant-001",
        created.analysisId(),
        1,
        null,
        null,
        null,
        "team",
        null,
        List.of(new SharePermission("external-group", "unknown", "read")),
        "actor-001",
        "corr-share",
        "cause-share")))
        .isInstanceOf(ShareTargetNotFoundException.class)
        .hasMessage("share target was not found");
  }

  @Test
  void saveAnalysisITFiltersByQuoteTagAndStatus() {
    var saved = service.saveAnalysis(validCommand("idem-search"));
    service.saveAnalysis(new CreateAnalysisCommand(
        "tenant-001",
        "Other quote",
        "borrower-retention",
        "quote-999",
        1,
        "pricing-config-v8",
        List.of("variant-999"),
        List.of(),
        "safe notes",
        List.of("other"),
        "private",
        "standard-audit",
        false,
        "idem-search-other",
        "actor-001",
        "corr-001",
        "cause-001"));

    var results = service.searchAnalyses("tenant-001", "quote-123", "fico-ltv", null);

    assertThat(results.statusFilter()).isEqualTo("SAVED");
    assertThat(results.analyses()).extracting("analysisId").containsExactly(saved.analysisId());
  }

  @Test
  void analysisArchiveITHidesArchivedByDefault() {
    var created = service.saveAnalysis(validCommand("idem-archive"));
    service.patchAnalysis(new PatchAnalysisCommand(
        "tenant-001",
        created.analysisId(),
        1,
        null,
        null,
        null,
        null,
        "ARCHIVED",
        null,
        "actor-001",
        "corr-archive",
        "cause-archive"));

    assertThat(service.searchAnalyses("tenant-001", null, null, null).analyses()).isEmpty();
    assertThat(service.searchAnalyses("tenant-001", null, null, "ARCHIVED").analyses()).hasSize(1);
  }

  @Test
  void getAnalysisShowsStaleBadgeWhenVersionsChange() {
    var created = service.saveAnalysis(validCommand("idem-stale-badge"));

    var opened = service.getAnalysis("tenant-001", created.analysisId(), 5, "pricing-config-v9");

    assertThat(opened.stale()).isTrue();
    assertThat(opened.validationMessages()).contains("saved analysis is stale relative to supplied quote or pricing config version");
  }

  @Test
  void rejectsSsnPatternInNotes() {
    assertThatThrownBy(() -> service.saveAnalysis(new CreateAnalysisCommand(
        "tenant-001",
        "Unsafe notes",
        "borrower-retention",
        "quote-123",
        4,
        "pricing-config-v8",
        List.of("variant-001"),
        List.of(),
        "contains 123-45-6789",
        List.of("fico-ltv"),
        "private",
        "standard-audit",
        false,
        "idem-ssn",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(ValidationException.class)
        .hasMessage("notes contain a prohibited SSN pattern");
  }

  @Test
  void missingSelectionsFailClosed() {
    assertThatThrownBy(() -> service.saveAnalysis(new CreateAnalysisCommand(
        "tenant-001",
        "No selection",
        "borrower-retention",
        "quote-123",
        4,
        "pricing-config-v8",
        List.of(),
        List.of(),
        "safe notes",
        List.of("fico-ltv"),
        "private",
        "standard-audit",
        false,
        "idem-no-selection",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(SelectionNotAvailableException.class)
        .hasMessage("at least one selected variant or grid cell is required");
  }

  @Test
  void missingSelectedVariantFailsClosed() {
    InMemorySelectionAvailabilityChecker availabilityChecker = new InMemorySelectionAvailabilityChecker();
    availabilityChecker.markVariantMissing("tenant-001", "quote-123", "variant-missing");
    service = new SavedWhatIfAnalysisService(repository, availabilityChecker, Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.saveAnalysis(new CreateAnalysisCommand(
        "tenant-001",
        "Missing variant",
        "borrower-retention",
        "quote-123",
        4,
        "pricing-config-v8",
        List.of("variant-missing"),
        List.of("grid-cell-001"),
        "safe notes",
        List.of("fico-ltv"),
        "private",
        "standard-audit",
        false,
        "idem-missing-variant",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(SelectionNotAvailableException.class)
        .hasMessageContaining("missingVariantIds=[variant-missing]");
  }

  @Test
  void expiredSelectedGridCellFailsClosed() {
    InMemorySelectionAvailabilityChecker availabilityChecker = new InMemorySelectionAvailabilityChecker();
    availabilityChecker.markGridCellExpired("tenant-001", "quote-123", "grid-cell-expired");
    service = new SavedWhatIfAnalysisService(repository, availabilityChecker, Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.saveAnalysis(new CreateAnalysisCommand(
        "tenant-001",
        "Expired grid cell",
        "borrower-retention",
        "quote-123",
        4,
        "pricing-config-v8",
        List.of("variant-001"),
        List.of("grid-cell-expired"),
        "safe notes",
        List.of("fico-ltv"),
        "private",
        "standard-audit",
        false,
        "idem-expired-grid-cell",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(SelectionNotAvailableException.class)
        .hasMessageContaining("expiredGridCellIds=[grid-cell-expired]");
  }

  private static CreateAnalysisCommand validCommand(String idempotencyKey) {
    return new CreateAnalysisCommand(
        "tenant-001",
        "FICO LTV comparison",
        "borrower-retention",
        "quote-123",
        4,
        "pricing-config-v8",
        List.of("variant-001", "variant-002"),
        List.of("grid-cell-001"),
        "Safe borrower-visible summary without raw identifiers",
        List.of("fico-ltv", "branch-a"),
        "private",
        "standard-audit",
        true,
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }
}

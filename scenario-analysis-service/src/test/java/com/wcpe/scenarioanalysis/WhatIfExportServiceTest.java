package com.wcpe.scenarioanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.scenarioanalysis.WhatIfExportService.ExportExpiredException;
import com.wcpe.scenarioanalysis.WhatIfExportService.ExportRevokedException;
import com.wcpe.scenarioanalysis.WhatIfExportService.IdempotencyConflictException;
import com.wcpe.scenarioanalysis.WhatIfExportService.PolicyNotSatisfiedException;
import com.wcpe.scenarioanalysis.WhatIfExportService.ValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatIfExportServiceTest {
  private TestOnlyInMemoryExportRepository repository;
  private TestOnlyInMemoryExportStorage storage;
  private WhatIfExportService service;

  @BeforeEach
  void setUp() {
    repository = new TestOnlyInMemoryExportRepository();
    storage = new TestOnlyInMemoryExportStorage();
    service = new WhatIfExportService(repository, storage, Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void whatIfExportContentTestUsesCanonicalCsvColumns() {
    var response = service.createExport(validCommand("idem-export"));

    assertThat(response.status()).isEqualTo("READY");
    assertThat(response.rowCount()).isEqualTo(2);
    assertThat(response.contentSha256()).startsWith("sha256:");
    assertThat(response.auditRef()).contains("WHAT_IF_EXPORT_GENERATED");
    assertThat(response.eventTypes()).containsExactly("whatif.export.requested.v1", "whatif.export.generated.v1");
    assertThat(response.validationMessages()).contains(WhatIfExportService.NON_BINDING_DISCLAIMER);
    assertThat(response.fileName()).isEqualTo("what-if-export.csv");

    var download = service.download("tenant-001", response.exportId());
    String csv = new String(download.content(), StandardCharsets.UTF_8);
    assertThat(download.contentType()).isEqualTo("text/csv; charset=utf-8");
    assertThat(download.fileName()).isEqualTo("what-if-export.csv");
    assertThat(csv).startsWith("export_id,source_type,source_id,row_id,format,recipient_type,include_ledger,include_ineligible,generated_at,disclaimer\r\n");
    assertThat(csv).contains("\"selected-rows\",\"analysis-123\",\"row-001\",\"CSV\",\"internal\"");
    assertThat(normalizeExportId(csv)).isEqualTo(goldenCsv());
  }

  @Test
  void csvInjectionGuardTestPrefixesFormulaCells() {
    assertThat(WhatIfExportService.csvCell("=cmd|' /C calc'!A0")).isEqualTo("\"'=cmd|' /C calc'!A0\"");
    assertThat(WhatIfExportService.csvCell("@unsafe")).isEqualTo("\"'@unsafe\"");
  }

  @Test
  void borrowerExportPolicyTestBlocksIneligibleRows() {
    assertThatThrownBy(() -> service.createExport(new WhatIfExportService.CreateExportCommand(
        "tenant-001",
        "selected-rows",
        "analysis-123",
        List.of("row-001"),
        "CSV",
        "borrower-facing draft",
        true,
        false,
        "borrower.csv",
        "idem-borrower",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("borrower-facing export requires active compliance template configuration");
  }

  @Test
  void exportReplaysSameIdempotencyKeyAndConflictsOnDifferentRequest() {
    var first = service.createExport(validCommand("idem-replay"));
    var replay = service.createExport(validCommand("idem-replay"));

    assertThat(replay).isEqualTo(first);
    assertThat(repository.size()).isEqualTo(1);
    assertThatThrownBy(() -> service.createExport(new WhatIfExportService.CreateExportCommand(
        "tenant-001",
        "selected-rows",
        "analysis-123",
        List.of("row-009"),
        "CSV",
        "internal",
        true,
        false,
        "what-if-export.csv",
        "idem-replay",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(IdempotencyConflictException.class)
        .hasMessage("idempotency key was already used with a different export request");
  }

  @Test
  void exportDownloadTestEnforcesTenantExpiryAndRevoke() {
    var response = service.createExport(validCommand("idem-revoke"));

    assertThatThrownBy(() -> service.download("tenant-999", response.exportId()))
        .isInstanceOf(WhatIfExportService.NotFoundException.class)
        .hasMessage("what-if export was not found");

    service.revoke("tenant-001", response.exportId(), "actor-001", "corr-revoke");

    assertThatThrownBy(() -> service.download("tenant-001", response.exportId()))
        .isInstanceOf(ExportRevokedException.class)
        .hasMessage("export link was revoked");

    WhatIfExportService expiringService = new WhatIfExportService(repository, storage, Clock.fixed(Instant.parse("2026-06-08T12:00:00Z"), ZoneOffset.UTC));
    var expiring = expiringService.createExport(validCommand("idem-expiring"));
    WhatIfExportService expiredService = new WhatIfExportService(repository, storage, Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC));
    assertThatThrownBy(() -> expiredService.download("tenant-001", expiring.exportId()))
        .isInstanceOf(ExportExpiredException.class)
        .hasMessage("export link has expired");
  }

  @Test
  void selectedRowsRequireRowsAndPdfRequiresTemplate() {
    assertThatThrownBy(() -> service.createExport(new WhatIfExportService.CreateExportCommand(
        "tenant-001",
        "selected-rows",
        "analysis-123",
        List.of(),
        "CSV",
        "internal",
        true,
        false,
        "empty.csv",
        "idem-empty",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(ValidationException.class)
        .hasMessage("rowIds are required for selected-rows export scope");

    assertThatThrownBy(() -> service.createExport(new WhatIfExportService.CreateExportCommand(
        "tenant-001",
        "saved-analysis",
        "analysis-123",
        List.of(),
        "PDF",
        "internal",
        true,
        false,
        "what-if.pdf",
        "idem-pdf",
        "actor-001",
        "corr-001",
        "cause-001")))
        .isInstanceOf(PolicyNotSatisfiedException.class)
        .hasMessage("PDF export requires an approved document template service; CSV and JSON are supported locally");
  }

  private static WhatIfExportService.CreateExportCommand validCommand(String idempotencyKey) {
    return new WhatIfExportService.CreateExportCommand(
        "tenant-001",
        "selected-rows",
        "analysis-123",
        List.of("row-001", "row-002"),
        "CSV",
        "internal",
        true,
        false,
        "what-if-export.csv",
        idempotencyKey,
        "actor-001",
        "corr-001",
        "cause-001");
  }

  private static String normalizeExportId(String csv) {
    return Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        .matcher(csv)
        .replaceAll("<export-id>");
  }

  private static String goldenCsv() {
    try (var input = WhatIfExportServiceTest.class.getResourceAsStream("/golden/what-if/export/fico-sensitivity-export-v1.csv")) {
      assertThat(input).isNotNull();
      return new String(input.readAllBytes(), StandardCharsets.UTF_8)
          .replace("\r\n", "\n")
          .replace("\n", "\r\n");
    } catch (IOException ex) {
      throw new AssertionError("Unable to read what-if export golden fixture", ex);
    }
  }
}

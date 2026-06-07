package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceExportService.ApprovalCommand;
import com.wcpe.compliance.ComplianceExportService.ComplianceExportJob;
import com.wcpe.compliance.ComplianceExportService.CreateComplianceExportRequest;
import com.wcpe.compliance.ComplianceExportService.DeliveryPolicyRef;
import com.wcpe.compliance.ComplianceExportService.DownloadCommand;
import com.wcpe.compliance.ComplianceExportService.ExportArtifactRef;
import com.wcpe.compliance.ComplianceExportService.ExportManifest;
import com.wcpe.compliance.ComplianceExportService.ExportSubjectFilter;
import com.wcpe.compliance.ComplianceExportService.ExportTemplateVersionRef;
import com.wcpe.compliance.ComplianceExportService.RedactionProfileRef;
import com.wcpe.compliance.ComplianceExportService.RunComplianceExport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ComplianceExportServiceTest {
  private static final Instant NOW = Instant.parse("2026-06-01T10:15:30Z");

  @Test
  void requiresApprovedTemplate() {
    ComplianceExportJob job = ComplianceExportService.createExportRequest(request(false, true, true));

    assertEquals("failed_blocked", job.status());
    assertEquals(List.of("ComplianceExportFailed.v1"), job.outboxEventTypes());
    assertTrue(job.reasonCodes().contains("EXPORT_TEMPLATE_NOT_APPROVED"));
  }

  @Test
  void createsPendingApprovalForSensitiveGovernedExport() {
    ComplianceExportJob job = ComplianceExportService.createExportRequest(request(true, true, true));

    assertEquals("pending_approval", job.status());
    assertEquals("tenant-a", job.tenantId());
    assertEquals(List.of("ComplianceExportRequested.v1"), job.outboxEventTypes());
    assertTrue(job.auditRef().startsWith("compliance-export:tenant-a:export-001:sha256:"));
  }

  @Test
  void enforcesApprovalSeparationOfDuties() {
    ComplianceExportJob job = ComplianceExportService.createExportRequest(request(true, true, true));

    ComplianceShellValidationError error =
        assertThrows(
            ComplianceShellValidationError.class,
            () -> ComplianceExportService.approveExport(job, new ApprovalCommand("analyst-1", NOW, "corr-approve")));

    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertTrue(error.getDetails().contains("SOD_VIOLATION"));
  }

  @Test
  void hashesEveryArtifactInDeterministicManifest() {
    ComplianceExportJob approved =
        ComplianceExportService.approveExport(
            ComplianceExportService.createExportRequest(request(true, true, true)),
            new ApprovalCommand("approver-1", NOW.plusSeconds(60), "corr-approve"));

    ComplianceExportJob completed = ComplianceExportService.runExport(approved, runCommand(artifact(2), artifact(1)));
    ComplianceExportJob replay = ComplianceExportService.runExport(approved, runCommand(artifact(1), artifact(2)));

    assertEquals("completed", completed.status());
    assertEquals(2, completed.artifactCount());
    assertEquals(completed.manifestHash(), replay.manifestHash());
    assertEquals("chain-of-custody:" + completed.manifestHash(), completed.manifest().chainOfCustodyHash());
    assertEquals(List.of("ComplianceExportCompleted.v1"), completed.outboxEventTypes());
  }

  @Test
  void blocksSensitiveUnredactedDownloadPackage() {
    ComplianceExportJob approved =
        ComplianceExportService.approveExport(
            ComplianceExportService.createExportRequest(request(true, true, true)),
            new ApprovalCommand("approver-1", NOW.plusSeconds(60), "corr-approve"));

    ComplianceExportJob failed = ComplianceExportService.runExport(approved, runCommand(unredactedSensitiveArtifact()));

    assertEquals("failed_blocked", failed.status());
    assertEquals(List.of("ComplianceExportFailed.v1"), failed.outboxEventTypes());
    assertTrue(failed.reasonCodes().contains("REDACTION_PROFILE_REQUIRED:1"));
  }

  @Test
  void manifestDownloadFailsAfterExpiry() {
    ComplianceExportJob approved =
        ComplianceExportService.approveExport(
            ComplianceExportService.createExportRequest(request(true, true, true)),
            new ApprovalCommand("approver-1", NOW.plusSeconds(60), "corr-approve"));
    ComplianceExportJob completed = ComplianceExportService.runExport(approved, runCommand(artifact(1)));

    ExportManifest manifest =
        ComplianceExportService.manifestForDownload(
            completed, new DownloadCommand("auditor-1", "audit review", "corr-download"), NOW.plusSeconds(120));
    ComplianceShellValidationError expired =
        assertThrows(
            ComplianceShellValidationError.class,
            () ->
                ComplianceExportService.manifestForDownload(
                    completed,
                    new DownloadCommand("auditor-1", "audit review", "corr-download"),
                    NOW.plusSeconds(90000)));

    assertEquals(completed.manifestHash(), manifest.manifestHash());
    assertTrue(expired.getDetails().contains("EXPORT_EXPIRED"));
  }

  @Test
  void manifestHashChangesWhenArtifactHashChanges() {
    ComplianceExportJob approved =
        ComplianceExportService.approveExport(
            ComplianceExportService.createExportRequest(request(true, true, true)),
            new ApprovalCommand("approver-1", NOW.plusSeconds(60), "corr-approve"));

    ComplianceExportJob first = ComplianceExportService.runExport(approved, runCommand(artifact(1)));
    ComplianceExportJob second =
        ComplianceExportService.runExport(
            approved,
            runCommand(
                new ExportArtifactRef(
                    1,
                    "audit_snapshot",
                    "snapshot:audit-001",
                    "object://local/export-001/audit.json",
                    "application/json",
                    ComplianceExportService.hashPayload("{\"audit\":\"changed\"}"),
                    true)));

    assertNotEquals(first.manifestHash(), second.manifestHash());
  }

  private static CreateComplianceExportRequest request(
      boolean templateApproved, boolean sensitiveExport, boolean redactionApproved) {
    return new CreateComplianceExportRequest(
        "tenant-a",
        "export-001",
        "analyst-1",
        new ExportSubjectFilter("portfolio", "portfolio-2026-05", LocalDate.parse("2026-05-01"), LocalDate.parse("2026-05-31")),
        new ExportTemplateVersionRef("fair-lending-audit-package", "v1", templateApproved, "approval:template:v1"),
        new RedactionProfileRef("borrower-pii-redaction", "v1", redactionApproved),
        new DeliveryPolicyRef("local-reference-only", "v1", true, true),
        List.of("audit_snapshot", "fair_lending_snapshot"),
        List.of("audit-snapshot:snapshot-001", "fair-lending:snapshot-2026-05"),
        sensitiveExport,
        true,
        "idem-001",
        "corr-create",
        NOW,
        NOW.plusSeconds(86400));
  }

  private static RunComplianceExport runCommand(ExportArtifactRef... artifacts) {
    return new RunComplianceExport("export-runner", List.of(artifacts), NOW.plusSeconds(120), "corr-run");
  }

  private static ExportArtifactRef artifact(int sequence) {
    String payload = "{\"sequence\":" + sequence + ",\"kind\":\"audit\"}";
    return new ExportArtifactRef(
        sequence,
        sequence == 1 ? "audit_snapshot" : "fair_lending_snapshot",
        sequence == 1 ? "audit-snapshot:snapshot-001" : "fair-lending:snapshot-2026-05",
        "object://local/export-001/artifact-" + sequence + ".json",
        "application/json",
        ComplianceExportService.hashPayload(payload),
        true);
  }

  private static ExportArtifactRef unredactedSensitiveArtifact() {
    return new ExportArtifactRef(
        1,
        "audit_snapshot",
        "audit-snapshot:snapshot-001",
        "object://local/export-001/audit.json",
        "application/json",
        ComplianceExportService.hashPayload("{\"borrower\":\"redaction required\"}"),
        false);
  }
}

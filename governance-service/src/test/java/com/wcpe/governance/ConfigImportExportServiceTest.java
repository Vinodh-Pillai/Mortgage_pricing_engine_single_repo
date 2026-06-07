package com.wcpe.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigImportExportServiceTest {
  private static final String TENANT = "11111111-1111-1111-1111-111111111111";
  private final ConfigImportExportService service =
      new ConfigImportExportService(Clock.fixed(Instant.parse("2026-06-07T01:00:00Z"), ZoneOffset.UTC));

  @Test
  void dryRunValidatesBundleWithoutCreatingDraftsOrPublishing() {
    ConfigImportJob job = service.importBundle(importCommand(ConfigImportMode.DRY_RUN, "idem-import-1", completePayload())).value().orElseThrow();

    assertEquals("DRY_RUN_PASSED", job.status());
    assertTrue(job.findings().isEmpty());
    assertTrue(job.draftRefs().isEmpty());
    assertEquals(64, job.fileHash().length());
    assertEquals(ConfigImportExportService.IMPORT_STARTED_EVENT, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigImportExportService.IMPORT_COMPLETED_EVENT, service.outboxEvents().get(1).eventType());
    assertEquals(2, service.outboxEvents().size(), "dry-run must not emit drafts-created or publish events");
    assertEquals(ConfigImportExportService.IMPORT_AUDIT_ACTION, service.auditRecords().get(0).action());
  }

  @Test
  void createDraftsUsesProfileValidationProvenanceAndIdempotency() {
    ConfigImportCommand command = importCommand(ConfigImportMode.CREATE_DRAFTS, "idem-import-2", completePayload());

    ConfigImportJob first = service.importBundle(command).value().orElseThrow();
    ConfigImportJob replay = service.importBundle(command).value().orElseThrow();
    GovernanceValidationResult<ConfigImportJob> conflict =
        service.importBundle(importCommand(ConfigImportMode.CREATE_DRAFTS, "idem-import-2", Map.of("artifactName", "changed", "schemaVersion", "2026.06")));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
    assertEquals("DRAFTS_CREATED", first.status());
    assertEquals(1, first.draftRefs().size());
    assertEquals(first.importJobId(), first.draftRefs().get(0).provenance().get("importJobId"));
    assertTrue(service.outboxEvents().stream().anyMatch(event -> event.eventType().equals(ConfigImportExportService.IMPORT_DRAFTS_CREATED_EVENT)));
    assertTrue(service.outboxEvents().stream().noneMatch(event -> event.eventType().contains("Published")));
  }

  @Test
  void importFailsClosedWhenProfileRequiredFieldsAreMissing() {
    ConfigImportJob job =
        service.importBundle(importCommand(ConfigImportMode.CREATE_DRAFTS, "idem-import-3", Map.of("artifactName", "tenant-configured")))
            .value()
            .orElseThrow();

    assertEquals("VALIDATION_FAILED", job.status());
    assertTrue(job.draftRefs().isEmpty());
    assertEquals("REQUIRED_FIELD_MISSING", job.findings().get(0).code());
    assertTrue(job.findings().get(0).blocking());
  }

  @Test
  void unsupportedArtifactTypesAreRejectedByProfileWithoutSideEffects() {
    GovernanceValidationResult<ConfigImportJob> result =
        service.importBundle(
            new ConfigImportCommand(
                TENANT,
                "idem-import-4",
                "admin-user-1",
                importProfile(),
                ConfigImportMode.DRY_RUN,
                "admin-shell",
                "bundle.json",
                "json",
                "{\"artifactName\":\"tenant-configured\"}",
                "unsupported-artifact",
                completePayload(),
                "corr-PII-12-S10"));

    assertFalse(result.valid());
    assertEquals("POLICY_NOT_SATISFIED: unsupported artifact type", result.error().orElseThrow());
    assertTrue(service.outboxEvents().isEmpty());
    assertTrue(service.auditRecords().isEmpty());
  }

  @Test
  void exportCreatesRedactedManifestPackageHashAndAuditedDownload() {
    ConfigExportJob job = service.exportPackage(exportCommand("idem-export-1")).value().orElseThrow();

    assertEquals("PACKAGE_CREATED", job.status());
    assertEquals(64, job.packageHash().length());
    assertEquals(job.packageHash(), job.manifest().packageHash());
    assertEquals(1, job.manifest().artifactRefs().size());
    PackageArtifactRef artifact = job.manifest().artifactRefs().get(0);
    assertEquals("REDACTED_BY_EXPORT_PROFILE", artifact.redactedPayload().get("confidentialStrategy"));
    assertEquals("tenant-configured-rule-set", artifact.redactedPayload().get("artifactName"));
    assertNotEquals(artifact.payloadHash(), artifact.redactedPayloadHash());
    assertEquals(ConfigImportExportService.EXPORT_CREATED_EVENT, service.outboxEvents().get(0).eventType());
    assertEquals(ConfigImportExportService.EXPORT_AUDIT_ACTION, service.auditRecords().get(0).action());

    ConfigExportJob downloaded = service.downloadPackage(TENANT, job.exportJobId(), "auditor-1", "corr-download").value().orElseThrow();

    assertEquals("DOWNLOADED", downloaded.status());
    assertEquals(Instant.parse("2026-06-07T01:00:00Z"), downloaded.downloadedAt());
    assertEquals(ConfigImportExportService.EXPORT_DOWNLOADED_EVENT, service.outboxEvents().get(1).eventType());
    assertEquals(ConfigImportExportService.DOWNLOAD_AUDIT_ACTION, service.auditRecords().get(1).action());
  }

  @Test
  void exportIdempotencyReplaysSameManifestAndRejectsChangedRequest() {
    ConfigExportCommand original = exportCommand("idem-export-2");

    ConfigExportJob first = service.exportPackage(original).value().orElseThrow();
    ConfigExportJob replay = service.exportPackage(original).value().orElseThrow();
    GovernanceValidationResult<ConfigExportJob> conflict =
        service.exportPackage(
            new ConfigExportCommand(
                TENANT,
                "idem-export-2",
                "auditor-1",
                exportProfile(),
                List.of(snapshot(Map.of("artifactName", "changed", "schemaVersion", "2026.06", "confidentialStrategy", "restricted"))),
                "ROLE_REDACTED",
                "evidence package",
                "corr-PII-12-S10"));

    assertEquals(first, replay);
    assertFalse(conflict.valid());
    assertEquals("IDEMPOTENCY_CONFLICT", conflict.error().orElseThrow());
  }

  private ConfigImportCommand importCommand(ConfigImportMode mode, String idempotencyKey, Map<String, String> payload) {
    return new ConfigImportCommand(
        TENANT,
        idempotencyKey,
        "admin-user-1",
        importProfile(),
        mode,
        "admin-shell",
        "valid-config-bundle.json",
        "json",
        "{\"artifactName\":\"tenant-configured-rule-set\",\"schemaVersion\":\"2026.06\"}",
        "pricing-rule-set",
        payload,
        "corr-PII-12-S10");
  }

  private ConfigImportProfile importProfile() {
    return new ConfigImportProfile(
        "tenant-import-profile",
        "profile-v1",
        List.of("pricing-rule-set", "margin-set"),
        List.of("json", "csv"),
        List.of("artifactName", "schemaVersion"),
        Map.of("pricing-rule-set", "schema://tenant/pricing-rule-set/2026.06"));
  }

  private Map<String, String> completePayload() {
    return Map.of("artifactName", "tenant-configured-rule-set", "schemaVersion", "2026.06", "sourceRef", "fixture-only");
  }

  private ConfigExportCommand exportCommand(String idempotencyKey) {
    return new ConfigExportCommand(
        TENANT,
        idempotencyKey,
        "auditor-1",
        exportProfile(),
        List.of(snapshot(Map.of("artifactName", "tenant-configured-rule-set", "schemaVersion", "2026.06", "confidentialStrategy", "restricted"))),
        "ROLE_REDACTED",
        "evidence package",
        "corr-PII-12-S10");
  }

  private ConfigExportProfile exportProfile() {
    return new ConfigExportProfile(
        "tenant-export-profile",
        "profile-v1",
        List.of("pricing-rule-set", "margin-set"),
        List.of("confidentialStrategy"),
        Duration.ofDays(30));
  }

  private ConfigExportArtifactSnapshot snapshot(Map<String, String> payload) {
    return new ConfigExportArtifactSnapshot(
        "pricing-rule-set:tenant-configured-rule-set",
        "pricing-rule-set",
        "version-2026-06",
        "schema://tenant/pricing-rule-set/2026.06",
        payload);
  }
}

package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.ComplianceEvidenceRegistryService.ArtifactExportView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.ComplianceEvidenceRegistryView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.LegalHoldCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.PrivacyProcessCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.PrivacyRequestView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.RetentionDeleteCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.RetentionDeletionGateView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.RetentionRuleView;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.SecurityAcknowledgeCommand;
import com.wcpe.compliance.ComplianceEvidenceRegistryService.SecurityEventView;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ComplianceEvidenceApiTest {
  private final ComplianceEvidenceRegistryService service = new ComplianceEvidenceRegistryService();

  @Test
  void returnsFullRegistry() {
    ComplianceEvidenceRegistryView registry = service.registry("PARTIAL");

    assertFalse(registry.artifacts().isEmpty());
    assertFalse(registry.decisions().isEmpty());
    assertFalse(registry.advisoryReviews().isEmpty());
    assertFalse(registry.fairLending().isEmpty());
    assertFalse(registry.privacyRequests().isEmpty());
    assertFalse(registry.securityEvents().isEmpty());
    assertFalse(registry.alerts().isEmpty());
    assertFalse(registry.retention().isEmpty());
    assertFalse(registry.configGaps().isEmpty());
    assertTrue(registry.auditRefs().stream().allMatch(ref -> ref.startsWith("audit-replay:")));
    assertTrue(registry.replayHash().startsWith("sha256:"));
    assertFalse(registry.versionRefs().isEmpty());
    assertEquals("available", registry.dependencyStatus().status());
  }

  @Test
  void artifactsWithRedaction() {
    ArtifactExportView metadataOnly = service.exportArtifact("artifact-audit-001", "METADATA_ONLY");
    ArtifactExportView full = service.exportArtifact("artifact-audit-001", "FULL");

    assertEquals("METADATA_ONLY", metadataOnly.redactionProfile());
    assertEquals("<redacted>", metadataOnly.artifact().owner());
    assertEquals("compliance-operations", full.artifact().owner());
    assertEquals(full.hashVerification(), metadataOnly.hashVerification());
    assertTrue(full.outboxEventTypes().contains("ComplianceArtifactExported.v1"));
  }

  @Test
  void fairLendingRedactionStates() {
    ComplianceEvidenceRegistryView full = service.registry("FULL");
    ComplianceEvidenceRegistryView redacted = service.registry("FULL_REDACTED");

    assertFalse(full.fairLending().get(0).redacted());
    assertTrue(redacted.fairLending().get(0).redacted());
    assertEquals("protected-class-drilldowns-redacted", redacted.fairLending().get(0).drilldowns().get(0));
  }

  @Test
  void privacyRequestsSLA() {
    PrivacyRequestView blocked = service.processPrivacyRequest("privacy-001", new PrivacyProcessCommand(false, true, "export:001", "consent:001", "corr"));
    PrivacyRequestView processed = service.processPrivacyRequest("privacy-001", new PrivacyProcessCommand(true, true, "export:001", "consent:001", "corr"));

    assertEquals("governance:privacy-sla-policy", processed.slaPolicyRef());
    assertEquals("blocked", blocked.status());
    assertEquals("processed", processed.status());
    assertTrue(blocked.blockers().contains("IDENTITY_VERIFICATION_REQUIRED"));
    assertTrue(processed.outboxEventTypes().contains("CompliancePrivacyRequestProcessed.v1"));
  }

  @Test
  void securityEventsActions() {
    SecurityEventView acknowledged = service.acknowledgeSecurityEvent("security-001", new SecurityAcknowledgeCommand("security-owner", "corr-ack"));

    assertTrue(acknowledged.acknowledged());
    assertEquals("security-owner", acknowledged.owner());
    assertTrue(acknowledged.outboxEventTypes().contains("ComplianceSecurityEventAcknowledged.v1"));
  }

  @Test
  void retentionLegalHoldBlocksExport() {
    RetentionRuleView held = service.applyLegalHold("retention-001", new LegalHoldCommand("legal-1", "litigation hold", Instant.parse("2026-06-01T00:00:00Z"), "corr-hold"));

    assertTrue(held.legalHold());
    assertEquals("blocked_by_legal_hold", held.deletionGate());
    assertTrue(held.outboxEventTypes().contains("ComplianceRetentionLegalHoldApplied.v1"));
  }

  @Test
  void retentionDeletionGateRequiresApproval() {
    RetentionDeletionGateView blocked = service.requestDeletion("retention-001", new RetentionDeleteCommand("records-1", null, "corr-delete"));
    RetentionDeletionGateView allowed = service.requestDeletion("retention-001", new RetentionDeleteCommand("records-1", "approval:delete:001", "corr-delete"));

    assertEquals("blocked", blocked.status());
    assertTrue(blocked.blockers().contains("DELETION_APPROVAL_REQUIRED"));
    assertEquals("allowed", allowed.status());
    assertTrue(allowed.outboxEventTypes().contains("ComplianceRetentionDeletionExecuted.v1"));
  }

  @Test
  void controllerExposesEvidenceEndpoints() {
    ComplianceEvidenceController controller = new ComplianceEvidenceController(service);

    assertNotNull(controller.registry("PARTIAL"));
    assertNotNull(controller.exportArtifact("artifact-audit-001", "PARTIAL"));
    assertEquals("processed", controller.processPrivacyRequest(
        "privacy-001", new PrivacyProcessCommand(true, true, "export:001", "consent:001", "corr")).status());
  }
}

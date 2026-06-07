package com.wcpe.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.compliance.RegulatoryConfigApprovalService.ApprovalDecision;
import com.wcpe.compliance.RegulatoryConfigApprovalService.ApprovalPackage;
import com.wcpe.compliance.RegulatoryConfigApprovalService.ConfigArtifactRef;
import com.wcpe.compliance.RegulatoryConfigApprovalService.CreateApprovalPackage;
import com.wcpe.compliance.RegulatoryConfigApprovalService.EvidenceRef;
import com.wcpe.compliance.RegulatoryConfigApprovalService.PublishPlan;
import com.wcpe.compliance.RegulatoryConfigApprovalService.RegulatoryConfigApproval;
import com.wcpe.compliance.RegulatoryConfigApprovalService.RollbackPlan;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulatoryApprovalPolicyTest {
  private static final Instant NOW = Instant.parse("2026-06-01T10:15:30Z");
  private static final String ARTIFACT_HASH = "sha256:artifact-v3";

  @Test
  void submitApprovePublish() {
    RegulatoryConfigApproval published =
        RegulatoryConfigApprovalService.publishApprovedConfig(
            approved(),
            new PublishPlan(
                "publisher-1",
                "publisher",
                List.of("compliance-policy-cache", "pricing-disclosure-cache"),
                ARTIFACT_HASH,
                NOW.plusSeconds(120),
                "corr-publish"));

    assertEquals(RegulatoryConfigApprovalService.PUBLISHED, published.status());
    assertEquals("publisher-1", published.publishedBy());
    assertEquals(List.of(RegulatoryConfigApprovalService.PUBLISHED_EVENT_TYPE), published.outboxEventTypes());
    assertTrue(published.auditRef().startsWith("regulatory-config-approval:tenant-a:approval-001:sha256:"));
    assertEquals(4, published.decisionLog().size());
  }

  @Test
  void enforcesSeparationOfDuties() {
    RegulatoryConfigApproval submitted = submitted();

    ComplianceShellValidationError error =
        assertThrows(
            ComplianceShellValidationError.class,
            () ->
                RegulatoryConfigApprovalService.approveRegulatoryConfig(
                    submitted,
                    new ApprovalDecision(
                        "author-1",
                        "legal-approver",
                        "Self approval must be rejected.",
                        ARTIFACT_HASH,
                        NOW.plusSeconds(90),
                        "corr-self")));

    assertEquals("COMPLIANCE_SHELL_VALIDATION_FAILED", error.getCode());
    assertTrue(error.getDetails().contains("SOD_VIOLATION"));
  }

  @Test
  void requiresValidationBeforeApproval() {
    RegulatoryConfigApproval draft = baseApproval();

    ComplianceShellValidationError error =
        assertThrows(
            ComplianceShellValidationError.class,
            () ->
                RegulatoryConfigApprovalService.submitApprovalPackage(
                    draft,
                    new ApprovalDecision(
                        "validator-1",
                        "validator",
                        "Submitting without validation should fail closed.",
                        ARTIFACT_HASH,
                        NOW.plusSeconds(30),
                        "corr-submit")));

    assertTrue(error.getDetails().contains("VALIDATION_REQUIRED"));
  }

  @Test
  void separatesDraftCreationFromSubmissionTimestamp() {
    RegulatoryConfigApproval draft = baseApproval();

    assertEquals(RegulatoryConfigApprovalService.DRAFT, draft.status());
    assertNull(draft.submittedAt());

    RegulatoryConfigApproval withValidation =
        RegulatoryConfigApprovalService.attachValidationReport(
            draft, evidence("schema-validation", "validation-report:state-high-cost:v3"));
    RegulatoryConfigApproval withSimulation =
        RegulatoryConfigApprovalService.attachSimulationEvidence(
            withValidation, evidence("golden-simulation", "simulation:golden-fixture:v3"));

    assertNull(withSimulation.submittedAt());

    RegulatoryConfigApproval submitted =
        RegulatoryConfigApprovalService.submitApprovalPackage(
            withSimulation,
            new ApprovalDecision(
                "validator-1",
                "validator",
                "Validation and simulation evidence attached for approval review.",
                ARTIFACT_HASH,
                NOW.plusSeconds(60),
                "corr-submit"));

    assertEquals(RegulatoryConfigApprovalService.SUBMITTED, submitted.status());
    assertEquals(NOW.plusSeconds(60), submitted.submittedAt());
  }

  @Test
  void rejectsStaleArtifactHashOnApproval() {
    RegulatoryConfigApproval submitted = submitted();

    ComplianceShellValidationError error =
        assertThrows(
            ComplianceShellValidationError.class,
            () ->
                RegulatoryConfigApprovalService.approveRegulatoryConfig(
                    submitted,
                    new ApprovalDecision(
                        "legal-1",
                        "legal-approver",
                        "Observed stale artifact hash.",
                        "sha256:stale",
                        NOW.plusSeconds(90),
                        "corr-stale")));

    assertTrue(error.getDetails().contains("STALE_ARTIFACT_HASH"));
  }

  @Test
  void requiresRollbackTargetApproved() {
    RegulatoryConfigApproval published =
        RegulatoryConfigApprovalService.publishApprovedConfig(
            approved(),
            new PublishPlan(
                "publisher-1",
                "publisher",
                List.of("compliance-policy-cache"),
                ARTIFACT_HASH,
                NOW.plusSeconds(120),
                "corr-publish"));

    ComplianceShellValidationError error =
        assertThrows(
            ComplianceShellValidationError.class,
            () ->
                RegulatoryConfigApprovalService.rollbackPublishedConfig(
                    published,
                    new RollbackPlan(
                        "publisher-2",
                        "publisher",
                        "Rollback target approval could not be proven.",
                        "artifact:state-high-cost:v2",
                        false,
                        NOW.plusSeconds(180),
                        "corr-rollback")));

    assertTrue(error.getDetails().contains("ROLLBACK_TARGET_INVALID"));
  }

  @Test
  void recordsRollbackWhenTargetIsApproved() {
    RegulatoryConfigApproval published =
        RegulatoryConfigApprovalService.publishApprovedConfig(
            approved(),
            new PublishPlan(
                "publisher-1",
                "publisher",
                List.of("compliance-policy-cache"),
                ARTIFACT_HASH,
                NOW.plusSeconds(120),
                "corr-publish"));

    RegulatoryConfigApproval rolledBack =
        RegulatoryConfigApprovalService.rollbackPublishedConfig(
            published,
            new RollbackPlan(
                "publisher-2",
                "publisher",
                "Restore prior approved version after deployment defect.",
                "artifact:state-high-cost:v2",
                true,
                NOW.plusSeconds(180),
                "corr-rollback"));

    assertEquals(RegulatoryConfigApprovalService.ROLLED_BACK, rolledBack.status());
    assertEquals("artifact:state-high-cost:v2", rolledBack.rollbackTargetRef());
    assertEquals(List.of(RegulatoryConfigApprovalService.ROLLED_BACK_EVENT_TYPE), rolledBack.outboxEventTypes());
    assertNotNull(rolledBack.approvalPackageHash());
  }

  private static RegulatoryConfigApproval approved() {
    return RegulatoryConfigApprovalService.approveRegulatoryConfig(
        submitted(),
        new ApprovalDecision(
            "legal-1",
            "legal-approver",
            "Legal/compliance approval after reviewing citations and simulation evidence.",
            ARTIFACT_HASH,
            NOW.plusSeconds(90),
            "corr-approve"));
  }

  private static RegulatoryConfigApproval submitted() {
    RegulatoryConfigApproval withValidation =
        RegulatoryConfigApprovalService.attachValidationReport(
            baseApproval(), evidence("schema-validation", "validation-report:state-high-cost:v3"));
    RegulatoryConfigApproval withSimulation =
        RegulatoryConfigApprovalService.attachSimulationEvidence(
            withValidation, evidence("golden-simulation", "simulation:golden-fixture:v3"));
    return RegulatoryConfigApprovalService.submitApprovalPackage(
        withSimulation,
        new ApprovalDecision(
            "validator-1",
            "validator",
            "Validation and simulation evidence attached for approval review.",
            ARTIFACT_HASH,
            NOW.plusSeconds(60),
            "corr-submit"));
  }

  private static RegulatoryConfigApproval baseApproval() {
    return RegulatoryConfigApprovalService.createApprovalPackage(
        new CreateApprovalPackage(
            "tenant-a",
            "approval-001",
            new ConfigArtifactRef(
                "state-high-cost-rule-pack",
                "state-high-cost",
                "v3",
                LocalDate.parse("2026-07-01"),
                null),
            ARTIFACT_HASH,
            new ApprovalPackage(
                List.of("source-doc:state-high-cost-2026"),
                List.of("citation:state-code-2026-15"),
                List.of("schema:regulatory-rule-pack:v1"),
                List.of("fixture:state-high-cost-golden:v3")),
            "author-1",
            "author",
            LocalDate.parse("2026-07-01"),
            null,
            NOW,
            "corr-create"));
  }

  private static EvidenceRef evidence(String type, String sourceRef) {
    return new EvidenceRef(
        type,
        sourceRef,
        RegulatoryConfigApprovalService.hashMaterial(type + ":" + sourceRef),
        NOW.plusSeconds(10));
  }
}

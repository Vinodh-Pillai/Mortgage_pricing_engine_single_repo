package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ComplianceEvidenceRegistryService {
  public static final String ARTIFACT_EXPORTED_EVENT_TYPE = "ComplianceArtifactExported.v1";
  public static final String PRIVACY_PROCESSED_EVENT_TYPE = "CompliancePrivacyRequestProcessed.v1";
  public static final String SECURITY_ACKNOWLEDGED_EVENT_TYPE = "ComplianceSecurityEventAcknowledged.v1";
  public static final String LEGAL_HOLD_APPLIED_EVENT_TYPE = "ComplianceRetentionLegalHoldApplied.v1";
  public static final String DELETION_EXECUTED_EVENT_TYPE = "ComplianceRetentionDeletionExecuted.v1";

  private final ComplianceEvidenceRegistryView registry;

  public ComplianceEvidenceRegistryService() {
    this.registry = seedRegistry();
  }

  public ComplianceEvidenceRegistryView registry(String redactionProfile) {
    RedactionProfile profile = RedactionProfile.from(redactionProfile);
    return applyRedaction(registry, profile);
  }

  public ArtifactExportView exportArtifact(String artifactId, String redactionProfile) {
    String id = requireNonBlank(artifactId, "artifactId");
    RedactionProfile profile = RedactionProfile.from(redactionProfile);
    ArtifactEvidence artifact = registry.artifacts().stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(() -> validation("Compliance artifact export validation failed.", List.of("ARTIFACT_NOT_FOUND")));
    if (artifact.blocked()) {
      return new ArtifactExportView(
          artifact.id(),
          profile.name(),
          null,
          artifact.hash(),
          artifact.auditRefs(),
          artifact.replayHash(),
          artifact.versionRefs(),
          artifact.dependencyStatus(),
          true,
          append(artifact.blockers(), "EXPORT_BLOCKED_BY_ARTIFACT_POLICY"),
          List.of());
    }
    ArtifactEvidence redacted = redactArtifact(artifact, profile);
    return new ArtifactExportView(
        redacted.id(),
        profile.name(),
        redacted,
        redacted.hash(),
        redacted.auditRefs(),
        redacted.replayHash(),
        redacted.versionRefs(),
        redacted.dependencyStatus(),
        false,
        List.of(),
        List.of(ARTIFACT_EXPORTED_EVENT_TYPE));
  }

  public PrivacyRequestView processPrivacyRequest(String requestId, PrivacyProcessCommand command) {
    String id = requireNonBlank(requestId, "requestId");
    PrivacyProcessCommand normalized = command == null ? PrivacyProcessCommand.empty() : command;
    PrivacyRequestView request = privacyRequest(id);
    List<String> blockers = new ArrayList<>();
    if (!normalized.identityVerified()) {
      blockers.add("IDENTITY_VERIFICATION_REQUIRED");
    }
    if (!normalized.scopeConfirmed()) {
      blockers.add("SCOPE_CONFIRMATION_REQUIRED");
    }
    if (isBlank(normalized.exportRef())) {
      blockers.add("EXPORT_REF_REQUIRED");
    }
    String status = blockers.isEmpty() ? "processed" : "blocked";
    return new PrivacyRequestView(
        request.id(),
        request.borrowerRef(),
        request.scope(),
        normalized.identityVerified() ? "verified" : "pending_verification",
        request.slaPolicyRef(),
        normalized.consentRef() == null ? request.consentRef() : normalized.consentRef().trim(),
        blockers,
        request.auditRefs(),
        replayHash("privacy", request.id(), status, blockers),
        request.versionRefs(),
        request.dependencyStatus(),
        status,
        blockers.isEmpty() ? List.of(PRIVACY_PROCESSED_EVENT_TYPE) : List.of());
  }

  public SecurityEventView acknowledgeSecurityEvent(String eventId, SecurityAcknowledgeCommand command) {
    String id = requireNonBlank(eventId, "eventId");
    SecurityAcknowledgeCommand normalized = command == null ? SecurityAcknowledgeCommand.empty() : command;
    SecurityEventView event = securityEvent(id);
    List<String> blockers = new ArrayList<>();
    if (isBlank(normalized.owner())) {
      blockers.add("ACKNOWLEDGMENT_OWNER_REQUIRED");
    }
    return new SecurityEventView(
        event.id(),
        event.category(),
        event.severity(),
        isBlank(normalized.owner()) ? event.owner() : normalized.owner().trim(),
        event.logId(),
        normalized.correlationId() == null ? event.correlationId() : normalized.correlationId().trim(),
        blockers.isEmpty(),
        blockers,
        event.auditRefs(),
        replayHash("security", event.id(), String.valueOf(blockers.isEmpty()), blockers),
        event.versionRefs(),
        event.dependencyStatus(),
        blockers.isEmpty() ? List.of(SECURITY_ACKNOWLEDGED_EVENT_TYPE) : List.of());
  }

  public RetentionRuleView applyLegalHold(String ruleId, LegalHoldCommand command) {
    String id = requireNonBlank(ruleId, "ruleId");
    LegalHoldCommand normalized = command == null ? LegalHoldCommand.empty() : command;
    RetentionRuleView rule = retentionRule(id);
    List<String> blockers = new ArrayList<>();
    if (isBlank(normalized.reason())) {
      blockers.add("LEGAL_HOLD_REASON_REQUIRED");
    }
    return new RetentionRuleView(
        rule.ruleId(),
        rule.retentionClass(),
        rule.retentionWindowRef(),
        true,
        "blocked_by_legal_hold",
        rule.backupEvidence(),
        blockers,
        rule.auditRefs(),
        replayHash("retention-hold", rule.ruleId(), normalized.reason(), blockers),
        rule.versionRefs(),
        rule.dependencyStatus(),
        isBlank(normalized.reason()) ? List.of() : List.of(LEGAL_HOLD_APPLIED_EVENT_TYPE));
  }

  public RetentionDeletionGateView requestDeletion(String ruleId, RetentionDeleteCommand command) {
    String id = requireNonBlank(ruleId, "ruleId");
    RetentionDeleteCommand normalized = command == null ? RetentionDeleteCommand.empty() : command;
    RetentionRuleView rule = retentionRule(id);
    List<String> blockers = new ArrayList<>();
    if (rule.legalHold()) {
      blockers.add("LEGAL_HOLD_ACTIVE");
    }
    if (isBlank(normalized.approvalRef())) {
      blockers.add("DELETION_APPROVAL_REQUIRED");
    }
    if (isBlank(rule.backupEvidence())) {
      blockers.add("BACKUP_EVIDENCE_REQUIRED");
    }
    boolean allowed = blockers.isEmpty();
    return new RetentionDeletionGateView(
        rule.ruleId(),
        allowed ? "allowed" : "blocked",
        normalized.approvalRef() == null ? "" : normalized.approvalRef().trim(),
        rule.backupEvidence(),
        blockers,
        rule.auditRefs(),
        replayHash("retention-delete", rule.ruleId(), normalized.approvalRef(), blockers),
        rule.versionRefs(),
        rule.dependencyStatus(),
        allowed ? List.of(DELETION_EXECUTED_EVENT_TYPE) : List.of());
  }

  private ComplianceEvidenceRegistryView applyRedaction(ComplianceEvidenceRegistryView view, RedactionProfile profile) {
    return new ComplianceEvidenceRegistryView(
        view.artifacts().stream().map(artifact -> redactArtifact(artifact, profile)).toList(),
        view.decisions().stream().map(decision -> redactDecision(decision, profile)).toList(),
        view.advisoryReviews(),
        view.fairLending().stream().map(item -> redactFairLending(item, profile)).toList(),
        view.privacyRequests().stream().map(item -> redactPrivacy(item, profile)).toList(),
        view.securityEvents(),
        view.alerts(),
        view.retention(),
        view.configGaps(),
        view.auditRefs(),
        view.replayHash(),
        view.versionRefs(),
        view.dependencyStatus());
  }

  private ArtifactEvidence redactArtifact(ArtifactEvidence artifact, RedactionProfile profile) {
    if (profile == RedactionProfile.FULL || profile == RedactionProfile.PARTIAL) {
      return artifact;
    }
    return new ArtifactEvidence(
        artifact.id(),
        profile == RedactionProfile.METADATA_ONLY ? artifact.path() : "<redacted>",
        artifact.type(),
        "<redacted>",
        artifact.retention(),
        artifact.module(),
        artifact.version(),
        artifact.hash(),
        artifact.trace(),
        artifact.policy(),
        artifact.jurisdiction(),
        artifact.continuity(),
        profile == RedactionProfile.METADATA_ONLY ? List.of() : artifact.links(),
        artifact.blocked(),
        artifact.blockers(),
        artifact.auditRefs(),
        artifact.replayHash(),
        artifact.versionRefs(),
        artifact.dependencyStatus());
  }

  private DecisionEvidence redactDecision(DecisionEvidence decision, RedactionProfile profile) {
    if (profile == RedactionProfile.FULL) {
      return decision;
    }
    return new DecisionEvidence(
        decision.id(),
        decision.reasonCode(),
        profile == RedactionProfile.METADATA_ONLY ? "<redacted>" : maskBorrowerText(decision.humanText()),
        decision.jurisdiction(),
        profile == RedactionProfile.METADATA_ONLY ? List.of() : decision.tiers(),
        decision.exportBlocked(),
        decision.disclosureRef(),
        decision.auditRefs(),
        decision.replayHash(),
        decision.versionRefs(),
        decision.dependencyStatus());
  }

  private FairLendingEvidence redactFairLending(FairLendingEvidence item, RedactionProfile profile) {
    return new FairLendingEvidence(
        item.id(),
        item.metricCode(),
        profile == RedactionProfile.FULL ? item.drilldowns() : List.of("protected-class-drilldowns-redacted"),
        profile != RedactionProfile.FULL,
        item.auditRefs(),
        item.replayHash(),
        item.versionRefs(),
        item.dependencyStatus());
  }

  private PrivacyRequestView redactPrivacy(PrivacyRequestView item, RedactionProfile profile) {
    return new PrivacyRequestView(
        item.id(),
        profile == RedactionProfile.FULL ? item.borrowerRef() : "borrower:<redacted>",
        item.scope(),
        item.identityStatus(),
        item.slaPolicyRef(),
        profile == RedactionProfile.FULL ? item.consentRef() : "consent:<redacted>",
        item.blockers(),
        item.auditRefs(),
        item.replayHash(),
        item.versionRefs(),
        item.dependencyStatus(),
        item.status(),
        item.outboxEventTypes());
  }

  private PrivacyRequestView privacyRequest(String requestId) {
    return registry.privacyRequests().stream()
        .filter(request -> request.id().equals(requestId))
        .findFirst()
        .orElseThrow(() -> validation("Compliance privacy request validation failed.", List.of("PRIVACY_REQUEST_NOT_FOUND")));
  }

  private SecurityEventView securityEvent(String eventId) {
    return registry.securityEvents().stream()
        .filter(event -> event.id().equals(eventId))
        .findFirst()
        .orElseThrow(() -> validation("Compliance security event validation failed.", List.of("SECURITY_EVENT_NOT_FOUND")));
  }

  private RetentionRuleView retentionRule(String ruleId) {
    return registry.retention().stream()
        .filter(rule -> rule.ruleId().equals(ruleId))
        .findFirst()
        .orElseThrow(() -> validation("Compliance retention validation failed.", List.of("RETENTION_RULE_NOT_FOUND")));
  }

  private static ComplianceEvidenceRegistryView seedRegistry() {
    List<String> auditRefs = List.of("audit-replay:snapshot:compliance-registry-001");
    List<String> versionRefs = List.of("compliance-evidence-registry:v1", "governance-config-gap:v1");
    DependencyStatus dependencyStatus = new DependencyStatus("available", List.of("audit-replay-service", "governance-service"), List.of());
    List<ConfigGapView> gaps = List.of(
        new ConfigGapView(
            "gap-privacy-sla-config",
            "privacy_sla_policy_ref",
            "policy_ref_required",
            "governance:privacy-sla-policy",
            "open",
            auditRefs,
            replayHash("gap", "privacy_sla_policy_ref", "open", List.of()),
            versionRefs,
            dependencyStatus));
    List<ArtifactEvidence> artifacts = List.of(
        artifact("artifact-audit-001", "audit_snapshot", false, List.of()),
        artifact("artifact-retention-hold-001", "retention_control", true, List.of("LEGAL_HOLD_ACTIVE")));
    List<DecisionEvidence> decisions = List.of(
        new DecisionEvidence(
            "decision-001",
            "DISCLOSURE_REVIEW_REQUIRED",
            "Disclosure package review requires approved evidence before export.",
            "multi-state",
            List.of("advisory", "legal"),
            true,
            "disclosure:package:001",
            auditRefs,
            replayHash("decision", "decision-001", "DISCLOSURE_REVIEW_REQUIRED", List.of()),
            versionRefs,
            dependencyStatus));
    List<AdvisoryReviewView> reviews = List.of(
        new AdvisoryReviewView(
            "review-001",
            "apr_advisory",
            "quote:sample-001",
            "blocked_missing_config",
            List.of("ADVISORY_CONFIG_REQUIRED"),
            auditRefs,
            "regulatory_state:pending_config",
            List.of("export:blocked:review-001"),
            List.of("advisory threshold config must be supplied by governance"),
            replayHash("review", "review-001", "blocked_missing_config", List.of("ADVISORY_CONFIG_REQUIRED")),
            versionRefs,
            dependencyStatus));
    List<FairLendingEvidence> fairLending = List.of(
        new FairLendingEvidence(
            "fair-lending-001",
            "disparity-monitor",
            List.of("peer-group:masked", "comparison-group:masked"),
            true,
            auditRefs,
            replayHash("fair-lending", "fair-lending-001", "redacted", List.of()),
            versionRefs,
            dependencyStatus));
    List<PrivacyRequestView> privacy = List.of(
        new PrivacyRequestView(
            "privacy-001",
            "borrower:sample-001",
            "data_export",
            "pending_verification",
            "governance:privacy-sla-policy",
            "consent:sample-001",
            List.of("IDENTITY_VERIFICATION_REQUIRED"),
            auditRefs,
            replayHash("privacy", "privacy-001", "pending", List.of("IDENTITY_VERIFICATION_REQUIRED")),
            versionRefs,
            dependencyStatus,
            "pending",
            List.of()));
    List<SecurityEventView> security = List.of(
        new SecurityEventView(
            "security-001",
            "evidence_export_access",
            "critical",
            "security-operations",
            "log:security:001",
            "corr-security-001",
            false,
            List.of("ACKNOWLEDGMENT_REQUIRED"),
            auditRefs,
            replayHash("security", "security-001", "open", List.of("ACKNOWLEDGMENT_REQUIRED")),
            versionRefs,
            dependencyStatus,
            List.of()));
    List<AlertView> alerts = List.of(
        new AlertView(
            "alert-001",
            "critical",
            "config_gap",
            "missing governance policy reference",
            "compliance-operations",
            false,
            List.of("GOVERNANCE_CONFIG_REQUIRED"),
            auditRefs,
            replayHash("alert", "alert-001", "open", List.of("GOVERNANCE_CONFIG_REQUIRED")),
            versionRefs,
            dependencyStatus));
    List<RetentionRuleView> retention = List.of(
        new RetentionRuleView(
            "retention-001",
            "compliance_evidence",
            "governance:retention-window-policy",
            false,
            "requires_approval",
            "backup:evidence:001",
            List.of("DELETION_APPROVAL_REQUIRED"),
            auditRefs,
            replayHash("retention", "retention-001", "requires_approval", List.of("DELETION_APPROVAL_REQUIRED")),
            versionRefs,
            dependencyStatus,
            List.of()));
    return new ComplianceEvidenceRegistryView(
        artifacts,
        decisions,
        reviews,
        fairLending,
        privacy,
        security,
        alerts,
        retention,
        gaps,
        auditRefs,
        replayHash("registry", "compliance", "v1", List.of()),
        versionRefs,
        dependencyStatus);
  }

  private static ArtifactEvidence artifact(String id, String type, boolean blocked, List<String> blockers) {
    List<String> auditRefs = List.of("audit-replay:artifact:" + id);
    List<String> versionRefs = List.of("compliance-artifact-schema:v1");
    DependencyStatus dependencyStatus = new DependencyStatus("available", List.of("audit-replay-service"), List.of());
    String hash = replayHash("artifact", id, type, blockers);
    return new ArtifactEvidence(
        id,
        "object://compliance-evidence/" + id + ".json",
        type,
        "compliance-operations",
        "governance:retention-window-policy",
        "compliance-service",
        "v1",
        hash,
        "trace:" + id,
        "governance:policy:evidence-registry",
        "multi-state",
        "continuity:verified",
        List.of("audit-replay:snapshot:" + id, "governance:policy:evidence-registry"),
        blocked,
        blockers,
        auditRefs,
        replayHash("artifact-replay", id, hash, blockers),
        versionRefs,
        dependencyStatus);
  }

  private static String replayHash(String area, String id, String status, List<String> details) {
    return "sha256:" + sha256(area + "|" + id + "|" + (status == null ? "" : status) + "|" + String.join(",", details));
  }

  private static String maskBorrowerText(String value) {
    return value == null ? "" : value.replaceAll("(?i)borrower", "subject");
  }

  private static <T> List<T> append(List<T> values, T value) {
    List<T> copy = new ArrayList<>(values == null ? List.of() : values);
    copy.add(value);
    return List.copyOf(copy);
  }

  private static String requireNonBlank(String value, String field) {
    if (isBlank(value)) {
      throw validation("Compliance evidence validation failed.", List.of(field + " must be provided"));
    }
    return value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static ComplianceShellValidationError validation(String message, List<String> details) {
    return new ComplianceShellValidationError(message, details);
  }

  private static String sha256(String material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest((material == null ? "" : material).getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder();
      for (byte value : hash) {
        encoded.append(String.format(Locale.ROOT, "%02x", value));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is required for compliance evidence", exception);
    }
  }

  public enum RedactionProfile {
    FULL,
    PARTIAL,
    FULL_REDACTED,
    METADATA_ONLY;

    static RedactionProfile from(String value) {
      if (isBlank(value)) {
        return PARTIAL;
      }
      return RedactionProfile.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
  }

  public record ComplianceEvidenceRegistryView(
      List<ArtifactEvidence> artifacts,
      List<DecisionEvidence> decisions,
      List<AdvisoryReviewView> advisoryReviews,
      List<FairLendingEvidence> fairLending,
      List<PrivacyRequestView> privacyRequests,
      List<SecurityEventView> securityEvents,
      List<AlertView> alerts,
      List<RetentionRuleView> retention,
      List<ConfigGapView> configGaps,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public ComplianceEvidenceRegistryView {
      artifacts = sorted(artifacts, ArtifactEvidence::id);
      decisions = sorted(decisions, DecisionEvidence::id);
      advisoryReviews = sorted(advisoryReviews, AdvisoryReviewView::id);
      fairLending = sorted(fairLending, FairLendingEvidence::id);
      privacyRequests = sorted(privacyRequests, PrivacyRequestView::id);
      securityEvents = sorted(securityEvents, SecurityEventView::id);
      alerts = sorted(alerts, AlertView::id);
      retention = sorted(retention, RetentionRuleView::ruleId);
      configGaps = sorted(configGaps, ConfigGapView::id);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  private static <T> List<T> sorted(List<T> values, java.util.function.Function<T, String> key) {
    return values == null
        ? List.of()
        : values.stream().filter(Objects::nonNull).sorted(Comparator.comparing(key)).toList();
  }

  public record ArtifactEvidence(
      String id,
      String path,
      String type,
      String owner,
      String retention,
      String module,
      String version,
      String hash,
      String trace,
      String policy,
      String jurisdiction,
      String continuity,
      List<String> links,
      boolean blocked,
      List<String> blockers,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public ArtifactEvidence {
      links = links == null ? List.of() : List.copyOf(links);
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  public record DecisionEvidence(
      String id,
      String reasonCode,
      String humanText,
      String jurisdiction,
      List<String> tiers,
      boolean exportBlocked,
      String disclosureRef,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public DecisionEvidence {
      tiers = tiers == null ? List.of() : List.copyOf(tiers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  public record AdvisoryReviewView(
      String id,
      String type,
      String subject,
      String status,
      List<String> reasonCodes,
      List<String> auditRefs,
      String regulatoryState,
      List<String> exportRefs,
      List<String> configGaps,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public AdvisoryReviewView {
      reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      exportRefs = exportRefs == null ? List.of() : List.copyOf(exportRefs);
      configGaps = configGaps == null ? List.of() : List.copyOf(configGaps);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  public record FairLendingEvidence(
      String id,
      String metricCode,
      List<String> drilldowns,
      boolean redacted,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public FairLendingEvidence {
      drilldowns = drilldowns == null ? List.of() : List.copyOf(drilldowns);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  public record PrivacyRequestView(
      String id,
      String borrowerRef,
      String scope,
      String identityStatus,
      String slaPolicyRef,
      String consentRef,
      List<String> blockers,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus,
      String status,
      List<String> outboxEventTypes) {
    public PrivacyRequestView {
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record SecurityEventView(
      String id,
      String category,
      String severity,
      String owner,
      String logId,
      String correlationId,
      boolean acknowledged,
      List<String> blockers,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus,
      List<String> outboxEventTypes) {
    public SecurityEventView {
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record AlertView(
      String id,
      String severity,
      String alertClass,
      String trigger,
      String route,
      boolean acknowledged,
      List<String> blockers,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public AlertView {
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  public record RetentionRuleView(
      String ruleId,
      String retentionClass,
      String retentionWindowRef,
      boolean legalHold,
      String deletionGate,
      String backupEvidence,
      List<String> blockers,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus,
      List<String> outboxEventTypes) {
    public RetentionRuleView {
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record ConfigGapView(
      String id,
      String configKey,
      String reasonCode,
      String governanceRef,
      String status,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus) {
    public ConfigGapView {
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
    }
  }

  public record DependencyStatus(String status, List<String> available, List<String> gaps) {
    public DependencyStatus {
      available = available == null ? List.of() : List.copyOf(available);
      gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }

    static DependencyStatus empty() {
      return new DependencyStatus("unknown", List.of(), List.of("dependency evidence unavailable"));
    }
  }

  public record ArtifactExportView(
      String artifactId,
      String redactionProfile,
      ArtifactEvidence artifact,
      String hashVerification,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus,
      boolean blocked,
      List<String> blockers,
      List<String> outboxEventTypes) {
    public ArtifactExportView {
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record PrivacyProcessCommand(
      boolean identityVerified, boolean scopeConfirmed, String exportRef, String consentRef, String correlationId) {
    static PrivacyProcessCommand empty() {
      return new PrivacyProcessCommand(false, false, null, null, null);
    }
  }

  public record SecurityAcknowledgeCommand(String owner, String correlationId) {
    static SecurityAcknowledgeCommand empty() {
      return new SecurityAcknowledgeCommand(null, null);
    }
  }

  public record LegalHoldCommand(String actorId, String reason, Instant occurredAt, String correlationId) {
    static LegalHoldCommand empty() {
      return new LegalHoldCommand(null, null, null, null);
    }
  }

  public record RetentionDeleteCommand(String actorId, String approvalRef, String correlationId) {
    static RetentionDeleteCommand empty() {
      return new RetentionDeleteCommand(null, null, null);
    }
  }

  public record RetentionDeletionGateView(
      String ruleId,
      String status,
      String approvalRef,
      String backupEvidence,
      List<String> blockers,
      List<String> auditRefs,
      String replayHash,
      List<String> versionRefs,
      DependencyStatus dependencyStatus,
      List<String> outboxEventTypes) {
    public RetentionDeletionGateView {
      blockers = blockers == null ? List.of() : List.copyOf(blockers);
      auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      dependencyStatus = dependencyStatus == null ? DependencyStatus.empty() : dependencyStatus;
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }
}

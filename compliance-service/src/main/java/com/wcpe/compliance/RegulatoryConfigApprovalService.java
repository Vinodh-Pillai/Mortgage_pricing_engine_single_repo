package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class RegulatoryConfigApprovalService {
  public static final String CREATED_EVENT_TYPE = "RegulatoryConfigSubmitted.v1";
  public static final String APPROVED_EVENT_TYPE = "RegulatoryConfigApproved.v1";
  public static final String REJECTED_EVENT_TYPE = "RegulatoryConfigRejected.v1";
  public static final String PUBLISHED_EVENT_TYPE = "RegulatoryConfigPublished.v1";
  public static final String ROLLED_BACK_EVENT_TYPE = "RegulatoryConfigRolledBack.v1";

  public static final String DRAFT = "draft";
  public static final String READY_FOR_REVIEW = "ready_for_review";
  public static final String SUBMITTED = "submitted";
  public static final String APPROVED = "approved";
  public static final String REJECTED = "rejected";
  public static final String PUBLISHED = "published";
  public static final String ROLLED_BACK = "rolled_back";

  private RegulatoryConfigApprovalService() {}

  public static RegulatoryConfigApproval createApprovalPackage(CreateApprovalPackage command) {
    validateCreate(command);
    String packageHash = approvalPackageHash(command.packageRefs());
    String artifactHash = requireTrimmed(command.artifactHash(), "artifactHash", new ArrayList<>());
    RegulatoryConfigDecision decision =
        decision(
            command.authorId(),
            command.authorRole(),
            null,
            DRAFT,
            "Approval package created.",
            command.correlationId(),
            command.createdAt());
    return new RegulatoryConfigApproval(
        command.approvalId().trim(),
        command.tenantId().trim(),
        command.artifactRef().normalized(),
        DRAFT,
        command.effectiveFrom(),
        command.effectiveTo(),
        command.authorId().trim(),
        null,
        null,
        null,
        null,
        null,
        artifactHash,
        packageHash,
        null,
        command.packageRefs().normalized(),
        null,
        null,
        List.of(decision),
        List.of(CREATED_EVENT_TYPE),
        auditRef(command.tenantId(), command.approvalId(), packageHash),
        command.correlationId().trim());
  }

  public static RegulatoryConfigApproval attachValidationReport(
      RegulatoryConfigApproval approval, EvidenceRef validationReport) {
    validateApproval(approval);
    EvidenceRef normalized = validateEvidence(validationReport, "validationReport");
    return withEvidence(approval, READY_FOR_REVIEW, normalized, approval.simulationEvidence(), null);
  }

  public static RegulatoryConfigApproval attachSimulationEvidence(
      RegulatoryConfigApproval approval, EvidenceRef simulationEvidence) {
    validateApproval(approval);
    EvidenceRef normalized = validateEvidence(simulationEvidence, "simulationEvidence");
    return withEvidence(approval, READY_FOR_REVIEW, approval.validationReport(), normalized, null);
  }

  public static RegulatoryConfigApproval submitApprovalPackage(
      RegulatoryConfigApproval approval, ApprovalDecision submitDecision) {
    validateApproval(approval);
    validateDecision(submitDecision, "submitDecision");
    if (approval.validationReport() == null) {
      throw validation("Regulatory approval submit validation failed.", List.of("VALIDATION_REQUIRED"));
    }
    if (approval.simulationEvidence() == null) {
      throw validation("Regulatory approval submit validation failed.", List.of("SIMULATION_REQUIRED"));
    }
    requireObservedHash(approval, submitDecision.observedArtifactHash());
    return withDecision(approval, SUBMITTED, submitDecision, null, null, null, null, null);
  }

  public static RegulatoryConfigApproval approveRegulatoryConfig(
      RegulatoryConfigApproval approval, ApprovalDecision approvalDecision) {
    validateApproval(approval);
    validateDecision(approvalDecision, "approvalDecision");
    if (!SUBMITTED.equals(approval.status())) {
      throw validation("Regulatory approval decision validation failed.", List.of("VALIDATION_REQUIRED"));
    }
    if (sameActor(approval.authorId(), approvalDecision.actorId())) {
      throw validation("Regulatory approval decision validation failed.", List.of("SOD_VIOLATION"));
    }
    requireObservedHash(approval, approvalDecision.observedArtifactHash());
    return withDecision(
        approval,
        APPROVED,
        approvalDecision,
        approvalDecision.actorId().trim(),
        approvalDecision.decidedAt(),
        null,
        null,
        List.of(APPROVED_EVENT_TYPE));
  }

  public static RegulatoryConfigApproval rejectRegulatoryConfig(
      RegulatoryConfigApproval approval, ApprovalDecision rejectDecision) {
    validateApproval(approval);
    validateDecision(rejectDecision, "rejectDecision");
    if (!SUBMITTED.equals(approval.status())) {
      throw validation("Regulatory approval decision validation failed.", List.of("VALIDATION_REQUIRED"));
    }
    return withDecision(
        approval, REJECTED, rejectDecision, null, null, null, null, List.of(REJECTED_EVENT_TYPE));
  }

  public static RegulatoryConfigApproval publishApprovedConfig(
      RegulatoryConfigApproval approval, PublishPlan publishPlan) {
    validateApproval(approval);
    validatePublishPlan(publishPlan);
    if (!APPROVED.equals(approval.status())) {
      throw validation("Regulatory approval publish validation failed.", List.of("UNAPPROVED_ARTIFACT"));
    }
    requireObservedHash(approval, publishPlan.observedArtifactHash());
    ApprovalDecision decision =
        new ApprovalDecision(
            publishPlan.publisherId(),
            publishPlan.publisherRole(),
            "Publish approved config; cache domains=" + String.join(",", publishPlan.cacheDomains()),
            publishPlan.observedArtifactHash(),
            publishPlan.publishedAt(),
            publishPlan.correlationId());
    return withDecision(
        approval,
        PUBLISHED,
        decision,
        approval.approvedBy(),
        approval.approvedAt(),
        publishPlan.publisherId().trim(),
        publishPlan.publishedAt(),
        List.of(PUBLISHED_EVENT_TYPE));
  }

  public static RegulatoryConfigApproval rollbackPublishedConfig(
      RegulatoryConfigApproval approval, RollbackPlan rollbackPlan) {
    validateApproval(approval);
    validateRollbackPlan(rollbackPlan);
    if (!PUBLISHED.equals(approval.status())) {
      throw validation("Regulatory approval rollback validation failed.", List.of("UNAPPROVED_ARTIFACT"));
    }
    if (!rollbackPlan.rollbackTargetApproved()) {
      throw validation("Regulatory approval rollback validation failed.", List.of("ROLLBACK_TARGET_INVALID"));
    }
    ApprovalDecision decision =
        new ApprovalDecision(
            rollbackPlan.actorId(),
            rollbackPlan.actorRole(),
            rollbackPlan.reason(),
            approval.artifactHash(),
            rollbackPlan.rolledBackAt(),
            rollbackPlan.correlationId());
    return withDecision(
        new RegulatoryConfigApproval(
            approval.approvalId(),
            approval.tenantId(),
            approval.artifactRef(),
            approval.status(),
            approval.effectiveFrom(),
            approval.effectiveTo(),
            approval.authorId(),
            approval.submittedAt(),
            approval.approvedBy(),
            approval.approvedAt(),
            approval.publishedBy(),
            approval.publishedAt(),
            approval.artifactHash(),
            approval.approvalPackageHash(),
            rollbackPlan.rollbackTargetRef().trim(),
            approval.approvalPackage(),
            approval.validationReport(),
            approval.simulationEvidence(),
            approval.decisionLog(),
            approval.outboxEventTypes(),
            approval.auditRef(),
            approval.correlationId()),
        ROLLED_BACK,
        decision,
        approval.approvedBy(),
        approval.approvedAt(),
        approval.publishedBy(),
        approval.publishedAt(),
        List.of(ROLLED_BACK_EVENT_TYPE));
  }

  public static String hashMaterial(String value) {
    String material = value == null ? "" : value;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder("sha256:");
      for (byte b : hash) {
        builder.append(String.format(Locale.ROOT, "%02x", b));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }

  private static RegulatoryConfigApproval withEvidence(
      RegulatoryConfigApproval approval,
      String status,
      EvidenceRef validationReport,
      EvidenceRef simulationEvidence,
      List<String> eventTypes) {
    return new RegulatoryConfigApproval(
        approval.approvalId(),
        approval.tenantId(),
        approval.artifactRef(),
        status,
        approval.effectiveFrom(),
        approval.effectiveTo(),
        approval.authorId(),
        approval.submittedAt(),
        approval.approvedBy(),
        approval.approvedAt(),
        approval.publishedBy(),
        approval.publishedAt(),
        approval.artifactHash(),
        approval.approvalPackageHash(),
        approval.rollbackTargetRef(),
        approval.approvalPackage(),
        validationReport,
        simulationEvidence,
        approval.decisionLog(),
        eventTypes == null ? List.of() : eventTypes,
        approval.auditRef(),
        approval.correlationId());
  }

  private static RegulatoryConfigApproval withDecision(
      RegulatoryConfigApproval approval,
      String newStatus,
      ApprovalDecision command,
      String approvedBy,
      Instant approvedAt,
      String publishedBy,
      Instant publishedAt,
      List<String> eventTypes) {
    List<RegulatoryConfigDecision> decisions = new ArrayList<>(approval.decisionLog());
    decisions.add(
        decision(
            command.actorId(),
            command.actorRole(),
            approval.status(),
            newStatus,
            command.comments(),
            command.correlationId(),
            command.decidedAt()));
    return new RegulatoryConfigApproval(
        approval.approvalId(),
        approval.tenantId(),
        approval.artifactRef(),
        newStatus,
        approval.effectiveFrom(),
        approval.effectiveTo(),
        approval.authorId(),
        SUBMITTED.equals(newStatus) ? command.decidedAt() : approval.submittedAt(),
        approvedBy,
        approvedAt,
        publishedBy,
        publishedAt,
        approval.artifactHash(),
        approval.approvalPackageHash(),
        approval.rollbackTargetRef(),
        approval.approvalPackage(),
        approval.validationReport(),
        approval.simulationEvidence(),
        decisions,
        eventTypes == null ? List.of() : eventTypes,
        approval.auditRef(),
        command.correlationId().trim());
  }

  private static RegulatoryConfigDecision decision(
      String actorId,
      String actorRole,
      String previousStatus,
      String newStatus,
      String comments,
      String correlationId,
      Instant decidedAt) {
    return new RegulatoryConfigDecision(
        actorId.trim(),
        actorRole.trim(),
        comments.trim(),
        previousStatus,
        newStatus,
        correlationId.trim(),
        decidedAt);
  }

  private static void validateCreate(CreateApprovalPackage command) {
    List<String> details = new ArrayList<>();
    if (command == null) {
      details.add("request");
    } else {
      requireTrimmed(command.tenantId(), "tenantId", details);
      requireTrimmed(command.approvalId(), "approvalId", details);
      requireTrimmed(command.authorId(), "authorId", details);
      requireTrimmed(command.authorRole(), "authorRole", details);
      requireTrimmed(command.artifactHash(), "artifactHash", details);
      requireTrimmed(command.correlationId(), "correlationId", details);
      if (command.createdAt() == null) {
        details.add("createdAt must be provided");
      }
      if (command.artifactRef() == null || command.artifactRef().missingRequiredFields()) {
        details.add("artifactRef must include artifactType, artifactId, and artifactVersion");
      }
      if (command.packageRefs() == null || command.packageRefs().missingRequiredFields()) {
        details.add("approvalPackage must include sourceRefs, citations, and schemaRefs");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Regulatory approval package validation failed.", details);
    }
  }

  private static void validateApproval(RegulatoryConfigApproval approval) {
    if (approval == null) {
      throw validation("Regulatory approval validation failed.", List.of("approval"));
    }
  }

  private static EvidenceRef validateEvidence(EvidenceRef evidence, String field) {
    List<String> details = new ArrayList<>();
    if (evidence == null) {
      details.add(field);
    } else {
      requireTrimmed(evidence.evidenceType(), field + ".evidenceType", details);
      requireTrimmed(evidence.sourceRef(), field + ".sourceRef", details);
      requireTrimmed(evidence.payloadHash(), field + ".payloadHash", details);
      if (evidence.createdAt() == null) {
        details.add(field + ".createdAt must be provided");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Regulatory approval evidence validation failed.", details);
    }
    return evidence.normalized();
  }

  private static void validateDecision(ApprovalDecision decision, String field) {
    List<String> details = new ArrayList<>();
    if (decision == null) {
      details.add(field);
    } else {
      requireTrimmed(decision.actorId(), field + ".actorId", details);
      requireTrimmed(decision.actorRole(), field + ".actorRole", details);
      requireTrimmed(decision.comments(), field + ".comments", details);
      requireTrimmed(decision.observedArtifactHash(), field + ".observedArtifactHash", details);
      requireTrimmed(decision.correlationId(), field + ".correlationId", details);
      if (decision.decidedAt() == null) {
        details.add(field + ".decidedAt must be provided");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Regulatory approval decision validation failed.", details);
    }
  }

  private static void validatePublishPlan(PublishPlan plan) {
    List<String> details = new ArrayList<>();
    if (plan == null) {
      details.add("publishPlan");
    } else {
      requireTrimmed(plan.publisherId(), "publisherId", details);
      requireTrimmed(plan.publisherRole(), "publisherRole", details);
      requireTrimmed(plan.observedArtifactHash(), "observedArtifactHash", details);
      requireTrimmed(plan.correlationId(), "correlationId", details);
      if (plan.publishedAt() == null) {
        details.add("publishedAt must be provided");
      }
      if (plan.cacheDomains() == null || plan.cacheDomains().stream().anyMatch(RegulatoryConfigApprovalService::isBlank)) {
        details.add("cacheDomains must include non-empty affected domains");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Regulatory approval publish validation failed.", details);
    }
  }

  private static void validateRollbackPlan(RollbackPlan plan) {
    List<String> details = new ArrayList<>();
    if (plan == null) {
      details.add("rollbackPlan");
    } else {
      requireTrimmed(plan.actorId(), "actorId", details);
      requireTrimmed(plan.actorRole(), "actorRole", details);
      requireTrimmed(plan.reason(), "reason", details);
      requireTrimmed(plan.rollbackTargetRef(), "rollbackTargetRef", details);
      requireTrimmed(plan.correlationId(), "correlationId", details);
      if (plan.rolledBackAt() == null) {
        details.add("rolledBackAt must be provided");
      }
    }
    if (!details.isEmpty()) {
      throw validation("Regulatory approval rollback validation failed.", details);
    }
  }

  private static void requireObservedHash(RegulatoryConfigApproval approval, String observedArtifactHash) {
    if (!Objects.equals(approval.artifactHash(), observedArtifactHash.trim())) {
      throw validation("Regulatory approval artifact validation failed.", List.of("STALE_ARTIFACT_HASH"));
    }
  }

  private static String approvalPackageHash(ApprovalPackage refs) {
    ApprovalPackage normalized = refs.normalized();
    return hashMaterial(
        String.join(
            "|",
            sorted(normalized.sourceRefs()),
            sorted(normalized.citations()),
            sorted(normalized.schemaRefs()),
            sorted(normalized.simulationFixtureRefs())));
  }

  private static String sorted(List<String> values) {
    return String.join(",", values.stream().sorted(Comparator.naturalOrder()).toList());
  }

  private static String auditRef(String tenantId, String approvalId, String packageHash) {
    return "regulatory-config-approval:" + tenantId.trim() + ":" + approvalId.trim() + ":" + packageHash;
  }

  private static String requireTrimmed(String value, String field, List<String> details) {
    if (isBlank(value)) {
      details.add(field + " must be a non-empty string");
      return "";
    }
    return value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static boolean sameActor(String left, String right) {
    return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
  }

  private static ComplianceShellValidationError validation(String message, List<String> details) {
    return new ComplianceShellValidationError(message, details);
  }

  public record ConfigArtifactRef(
      String artifactType,
      String artifactId,
      String artifactVersion,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    boolean missingRequiredFields() {
      return isBlank(artifactType) || isBlank(artifactId) || isBlank(artifactVersion);
    }

    ConfigArtifactRef normalized() {
      return new ConfigArtifactRef(
          artifactType.trim(), artifactId.trim(), artifactVersion.trim(), effectiveFrom, effectiveTo);
    }
  }

  public record ApprovalPackage(
      List<String> sourceRefs,
      List<String> citations,
      List<String> schemaRefs,
      List<String> simulationFixtureRefs) {
    public ApprovalPackage {
      sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
      citations = citations == null ? List.of() : List.copyOf(citations);
      schemaRefs = schemaRefs == null ? List.of() : List.copyOf(schemaRefs);
      simulationFixtureRefs = simulationFixtureRefs == null ? List.of() : List.copyOf(simulationFixtureRefs);
    }

    boolean missingRequiredFields() {
      return sourceRefs.isEmpty()
          || citations.isEmpty()
          || schemaRefs.isEmpty()
          || sourceRefs.stream().anyMatch(RegulatoryConfigApprovalService::isBlank)
          || citations.stream().anyMatch(RegulatoryConfigApprovalService::isBlank)
          || schemaRefs.stream().anyMatch(RegulatoryConfigApprovalService::isBlank)
          || simulationFixtureRefs.stream().anyMatch(RegulatoryConfigApprovalService::isBlank);
    }

    ApprovalPackage normalized() {
      return new ApprovalPackage(
          trimAll(sourceRefs), trimAll(citations), trimAll(schemaRefs), trimAll(simulationFixtureRefs));
    }
  }

  private static List<String> trimAll(List<String> values) {
    return values.stream().map(String::trim).toList();
  }

  public record EvidenceRef(
      String evidenceType, String sourceRef, String payloadHash, Instant createdAt) {
    EvidenceRef normalized() {
      return new EvidenceRef(
          evidenceType.trim(), sourceRef.trim(), payloadHash.trim(), createdAt);
    }
  }

  public record CreateApprovalPackage(
      String tenantId,
      String approvalId,
      ConfigArtifactRef artifactRef,
      String artifactHash,
      ApprovalPackage packageRefs,
      String authorId,
      String authorRole,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      Instant createdAt,
      String correlationId) {}

  public record ApprovalDecision(
      String actorId,
      String actorRole,
      String comments,
      String observedArtifactHash,
      Instant decidedAt,
      String correlationId) {}

  public record PublishPlan(
      String publisherId,
      String publisherRole,
      List<String> cacheDomains,
      String observedArtifactHash,
      Instant publishedAt,
      String correlationId) {
    public PublishPlan {
      cacheDomains = cacheDomains == null ? List.of() : List.copyOf(cacheDomains);
    }
  }

  public record RollbackPlan(
      String actorId,
      String actorRole,
      String reason,
      String rollbackTargetRef,
      boolean rollbackTargetApproved,
      Instant rolledBackAt,
      String correlationId) {}

  public record RegulatoryConfigDecision(
      String actorId,
      String actorRole,
      String comments,
      String previousStatus,
      String newStatus,
      String correlationId,
      Instant decidedAt) {}

  public record RegulatoryConfigApproval(
      String approvalId,
      String tenantId,
      ConfigArtifactRef artifactRef,
      String status,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String authorId,
      Instant submittedAt,
      String approvedBy,
      Instant approvedAt,
      String publishedBy,
      Instant publishedAt,
      String artifactHash,
      String approvalPackageHash,
      String rollbackTargetRef,
      ApprovalPackage approvalPackage,
      EvidenceRef validationReport,
      EvidenceRef simulationEvidence,
      List<RegulatoryConfigDecision> decisionLog,
      List<String> outboxEventTypes,
      String auditRef,
      String correlationId) {
    public RegulatoryConfigApproval {
      decisionLog = decisionLog == null ? List.of() : List.copyOf(decisionLog);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }
}

package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ComplianceReasonCodeCatalog {
  public static final String PUBLISHED_EVENT_TYPE = "ComplianceReasonCodePublished.v1";
  public static final String DEPRECATED_EVENT_TYPE = "ComplianceReasonCodeDeprecated.v1";
  public static final String DRAFT = "draft";
  public static final String PENDING_APPROVAL = "pending_approval";
  public static final String PUBLISHED = "published";
  public static final String DEPRECATED = "deprecated";
  public static final String SUSPENDED = "suspended";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";

  private static final Set<String> ALLOWED_SEVERITIES = Set.of("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");
  private static final Set<String> ALLOWED_AUDIENCES = Set.of("internal", "borrower_safe");

  private ComplianceReasonCodeCatalog() {}

  public static ComplianceReasonCodeVersion createDraft(
      DraftReasonCodeCommand command, List<ComplianceReasonCodeVersion> existingVersions) {
    validateDraftCommand(command);
    List<ComplianceReasonCodeVersion> existing = existingVersions == null ? List.of() : existingVersions;
    if (existing.stream()
        .anyMatch(
            version ->
                same(version.tenantId(), command.tenantId())
                    && same(version.code(), command.code())
                    && same(version.category(), command.category()))) {
      throw validation("Reason code draft validation failed.", List.of("DUPLICATE_CODE"));
    }

    return new ComplianceReasonCodeVersion(
        command.tenantId().trim(),
        normalizeCode(command.code()),
        normalizeCode(command.category()),
        1,
        DRAFT,
        normalizeCode(command.severity()),
        command.effectiveFrom(),
        command.effectiveTo(),
        command.internalLabel().trim(),
        command.borrowerSafeLabel(),
        command.borrowerSafeApproved(),
        command.description().trim(),
        copy(command.citations()),
        copy(command.ruleMappings()),
        copy(command.localeText()),
        blankToNull(command.successorCode()),
        command.actorId().trim(),
        null,
        null,
        null,
        command.correlationId().trim(),
        hashMaterial(command));
  }

  public static ComplianceReasonCodeVersion approve(
      ComplianceReasonCodeVersion draft, ApprovalCommand command) {
    validateVersion(draft, "draft");
    validateApprovalCommand(command);
    if (same(draft.createdBy(), command.approverId())) {
      throw validation("Reason code approval validation failed.", List.of("AUTHOR_CANNOT_APPROVE_OWN_WORDING"));
    }
    if (draft.borrowerSafeLabel() && !command.borrowerSafeApproved()) {
      throw validation("Reason code approval validation failed.", List.of("BORROWER_TEXT_NOT_APPROVED"));
    }
    return draft.withApproval(
        PENDING_APPROVAL,
        command.borrowerSafeApproved(),
        command.approverId().trim(),
        command.approvalComment().trim(),
        command.approvedAt(),
        command.correlationId().trim());
  }

  public static ReasonCodeLifecycleResult publish(ComplianceReasonCodeVersion approved) {
    validateVersion(approved, "approved");
    if (!PENDING_APPROVAL.equals(approved.status())) {
      throw validation("Reason code publish validation failed.", List.of("STALE_REASON_CODE_VERSION"));
    }
    if (approved.borrowerSafeLabel() && !approved.borrowerSafeApproved()) {
      throw validation("Reason code publish validation failed.", List.of("BORROWER_TEXT_NOT_APPROVED"));
    }
    ComplianceReasonCodeVersion published = approved.withStatus(PUBLISHED);
    return new ReasonCodeLifecycleResult(
        published,
        List.of(PUBLISHED_EVENT_TYPE),
        "COMPLIANCE_REASON_CODE_PUBLISHED",
        "reason-code-audit:" + published.hash(),
        published.correlationId());
  }

  public static ReasonCodeLifecycleResult deprecate(
      ComplianceReasonCodeVersion published, DeprecationCommand command) {
    validateVersion(published, "published");
    validateDeprecationCommand(command);
    if (!PUBLISHED.equals(published.status())) {
      throw validation("Reason code deprecation validation failed.", List.of("STALE_REASON_CODE_VERSION"));
    }
    ComplianceReasonCodeVersion deprecated =
        published.withDeprecation(command.successorCode().trim(), command.correlationId().trim());
    return new ReasonCodeLifecycleResult(
        deprecated,
        List.of(DEPRECATED_EVENT_TYPE),
        "COMPLIANCE_REASON_CODE_DEPRECATED",
        "reason-code-audit:" + deprecated.hash(),
        deprecated.correlationId());
  }

  public static ReasonCodeResolution resolve(
      ResolveReasonCodeRequest request, List<ComplianceReasonCodeVersion> versions) {
    validateResolveRequest(request);
    List<ComplianceReasonCodeVersion> candidates = versions == null ? List.of() : versions;
    ComplianceReasonCodeVersion selected =
        candidates.stream()
            .filter(version -> same(version.tenantId(), request.tenantId()))
            .filter(version -> same(version.code(), request.code()))
            .filter(version -> version.isEffectiveFor(request.asOfDate()))
            .sorted(Comparator.comparing(ComplianceReasonCodeVersion::version).reversed())
            .findFirst()
            .orElseThrow(
                () -> validation("Reason code resolution failed.", List.of("REASON_CODE_NOT_FOUND")));

    if (DEPRECATED.equals(selected.status()) && !request.replay()) {
      if (selected.successorCode() == null || selected.successorCode().isBlank()) {
        throw validation(
            "Reason code resolution failed.", List.of("DEPRECATED_CODE_WITHOUT_SUCCESSOR"));
      }
      return resolve(
          new ResolveReasonCodeRequest(
              request.tenantId(),
              selected.successorCode(),
              request.asOfDate(),
              request.audience(),
              request.locale(),
              false,
              request.correlationId()),
          candidates);
    }
    if (SUSPENDED.equals(selected.status())) {
      throw validation("Reason code resolution failed.", List.of("REASON_CODE_SUSPENDED"));
    }
    if ("borrower_safe".equals(request.audience())
        && (selected.borrowerSafeTextFor(request.locale()) == null || !selected.borrowerSafeApproved())) {
      throw validation("Reason code resolution failed.", List.of("BORROWER_TEXT_NOT_APPROVED"));
    }

    String displayText =
        "borrower_safe".equals(request.audience())
            ? selected.borrowerSafeTextFor(request.locale())
            : selected.internalLabel();
    return new ReasonCodeResolution(
        selected.tenantId(),
        selected.code(),
        selected.category(),
        selected.version(),
        selected.status(),
        selected.severity(),
        displayText,
        selected.citations(),
        selected.ruleMappings(),
        selected.successorCode(),
        selected.hash(),
        "reason-code-resolve-audit:" + selected.hash(),
        request.correlationId());
  }

  private static void validateDraftCommand(DraftReasonCodeCommand command) {
    if (command == null) {
      throw validation("Reason code draft must be an object.", List.of("request"));
    }
    List<String> details = new ArrayList<>();
    requireNonBlank(command.tenantId(), "tenantId", details);
    requireNonBlank(command.code(), "code", details);
    requireNonBlank(command.category(), "category", details);
    requireNonBlank(command.severity(), "severity", details);
    requireNonBlank(command.internalLabel(), "internalLabel", details);
    requireNonBlank(command.description(), "description", details);
    requireNonBlank(command.actorId(), "actorId", details);
    requireNonBlank(command.idempotencyKey(), "idempotencyKey", details);
    requireNonBlank(command.correlationId(), "correlationId", details);
    if (command.effectiveFrom() == null) {
      details.add("effectiveFrom must be provided");
    }
    if (command.effectiveFrom() != null
        && command.effectiveTo() != null
        && command.effectiveTo().isBefore(command.effectiveFrom())) {
      details.add("effectiveTo must be on or after effectiveFrom");
    }
    if (command.severity() != null && !ALLOWED_SEVERITIES.contains(normalizeCode(command.severity()))) {
      details.add("severity must be a configured enum value");
    }
    if (command.citations() == null || command.citations().isEmpty()) {
      details.add("citations must be provided");
    }
    if (command.ruleMappings() == null || command.ruleMappings().isEmpty()) {
      details.add("ruleMappings must be provided");
    }
    if (command.localeText() == null || command.localeText().isEmpty()) {
      details.add("localeText must be provided");
    }
    if (!details.isEmpty()) {
      throw validation("Reason code draft validation failed.", details);
    }
  }

  private static void validateApprovalCommand(ApprovalCommand command) {
    if (command == null) {
      throw validation("Reason code approval must be an object.", List.of("request"));
    }
    List<String> details = new ArrayList<>();
    requireNonBlank(command.approverId(), "approverId", details);
    requireNonBlank(command.approvalComment(), "approvalComment", details);
    requireNonBlank(command.correlationId(), "correlationId", details);
    if (command.approvedAt() == null) {
      details.add("approvedAt must be provided");
    }
    if (!details.isEmpty()) {
      throw validation("Reason code approval validation failed.", details);
    }
  }

  private static void validateDeprecationCommand(DeprecationCommand command) {
    if (command == null) {
      throw validation("Reason code deprecation must be an object.", List.of("request"));
    }
    List<String> details = new ArrayList<>();
    requireNonBlank(command.successorCode(), "successorCode", details);
    requireNonBlank(command.actorId(), "actorId", details);
    requireNonBlank(command.correlationId(), "correlationId", details);
    if (!details.isEmpty()) {
      throw validation("Reason code deprecation validation failed.", details);
    }
  }

  private static void validateResolveRequest(ResolveReasonCodeRequest request) {
    if (request == null) {
      throw validation("Reason code resolve request must be an object.", List.of("request"));
    }
    List<String> details = new ArrayList<>();
    requireNonBlank(request.tenantId(), "tenantId", details);
    requireNonBlank(request.code(), "code", details);
    requireNonBlank(request.audience(), "audience", details);
    requireNonBlank(request.correlationId(), "correlationId", details);
    if (request.asOfDate() == null) {
      details.add("asOfDate must be provided");
    }
    if (request.audience() != null && !ALLOWED_AUDIENCES.contains(request.audience())) {
      details.add("audience must be internal or borrower_safe");
    }
    if (!details.isEmpty()) {
      throw validation("Reason code resolution failed.", details);
    }
  }

  private static void validateVersion(ComplianceReasonCodeVersion version, String field) {
    if (version == null) {
      throw validation("Reason code version must be provided.", List.of(field + " must be provided"));
    }
  }

  private static ComplianceShellValidationError validation(String message, List<String> details) {
    return new ComplianceShellValidationError(message, details);
  }

  private static List<String> copy(List<String> values) {
    return values == null ? List.of() : List.copyOf(values.stream().map(String::trim).toList());
  }

  private static String borrowerSafeText(List<String> localeText, String locale) {
    String requested = locale == null || locale.isBlank() ? "en-US" : locale.trim();
    return localeText.stream()
        .filter(text -> text.startsWith(requested + "|borrower_safe|"))
        .map(text -> text.substring((requested + "|borrower_safe|").length()))
        .findFirst()
        .orElse(null);
  }

  private static String hashMaterial(DraftReasonCodeCommand command) {
    return sha256(
        String.join(
            "|",
            command.tenantId().trim(),
            normalizeCode(command.code()),
            normalizeCode(command.category()),
            normalizeCode(command.severity()),
            command.effectiveFrom().toString(),
            command.effectiveTo() == null ? "" : command.effectiveTo().toString(),
            command.internalLabel().trim(),
            String.valueOf(command.borrowerSafeLabel()),
            String.valueOf(command.borrowerSafeApproved()),
            command.description().trim(),
            String.join(",", copy(command.citations())),
            String.join(",", copy(command.ruleMappings())),
            String.join(",", copy(command.localeText()))));
  }

  private static String sha256(String material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder();
      for (byte value : hash) {
        encoded.append(String.format(Locale.ROOT, "%02x", value));
      }
      return "sha256:" + encoded;
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is required for reason-code replay", exception);
    }
  }

  private static void requireNonBlank(String value, String field, List<String> details) {
    if (value == null || value.trim().isEmpty()) {
      details.add(field + " must be a non-empty string");
    }
  }

  private static String normalizeCode(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static boolean same(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  public record DraftReasonCodeCommand(
      String tenantId,
      String code,
      String category,
      String severity,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String internalLabel,
      boolean borrowerSafeLabel,
      boolean borrowerSafeApproved,
      String description,
      List<String> citations,
      List<String> ruleMappings,
      List<String> localeText,
      String successorCode,
      String actorId,
      String idempotencyKey,
      String correlationId) {}

  public record ApprovalCommand(
      String approverId,
      boolean borrowerSafeApproved,
      String approvalComment,
      OffsetDateTime approvedAt,
      String correlationId) {}

  public record DeprecationCommand(String successorCode, String actorId, String correlationId) {}

  public record ResolveReasonCodeRequest(
      String tenantId,
      String code,
      LocalDate asOfDate,
      String audience,
      String locale,
      boolean replay,
      String correlationId) {}

  public record ComplianceReasonCodeVersion(
      String tenantId,
      String code,
      String category,
      int version,
      String status,
      String severity,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String internalLabel,
      boolean borrowerSafeLabel,
      boolean borrowerSafeApproved,
      String description,
      List<String> citations,
      List<String> ruleMappings,
      List<String> localeText,
      String successorCode,
      String createdBy,
      String approvedBy,
      String approvalComment,
      OffsetDateTime approvedAt,
      String correlationId,
      String hash) {
    public ComplianceReasonCodeVersion {
      citations = citations == null ? List.of() : List.copyOf(citations);
      ruleMappings = ruleMappings == null ? List.of() : List.copyOf(ruleMappings);
      localeText = localeText == null ? List.of() : List.copyOf(localeText);
    }

    boolean isEffectiveFor(LocalDate date) {
      return date != null && !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    String borrowerSafeTextFor(String locale) {
      return ComplianceReasonCodeCatalog.borrowerSafeText(localeText, locale);
    }

    ComplianceReasonCodeVersion withApproval(
        String newStatus,
        boolean legalApproved,
        String newApprovedBy,
        String newApprovalComment,
        OffsetDateTime newApprovedAt,
        String newCorrelationId) {
      return new ComplianceReasonCodeVersion(
          tenantId,
          code,
          category,
          version,
          newStatus,
          severity,
          effectiveFrom,
          effectiveTo,
          internalLabel,
          borrowerSafeLabel,
          legalApproved,
          description,
          citations,
          ruleMappings,
          localeText,
          successorCode,
          createdBy,
          newApprovedBy,
          newApprovalComment,
          newApprovedAt,
          newCorrelationId,
          hash);
    }

    ComplianceReasonCodeVersion withStatus(String newStatus) {
      return new ComplianceReasonCodeVersion(
          tenantId,
          code,
          category,
          version,
          newStatus,
          severity,
          effectiveFrom,
          effectiveTo,
          internalLabel,
          borrowerSafeLabel,
          borrowerSafeApproved,
          description,
          citations,
          ruleMappings,
          localeText,
          successorCode,
          createdBy,
          approvedBy,
          approvalComment,
          approvedAt,
          correlationId,
          hash);
    }

    ComplianceReasonCodeVersion withDeprecation(String newSuccessorCode, String newCorrelationId) {
      return new ComplianceReasonCodeVersion(
          tenantId,
          code,
          category,
          version,
          DEPRECATED,
          severity,
          effectiveFrom,
          effectiveTo,
          internalLabel,
          borrowerSafeLabel,
          borrowerSafeApproved,
          description,
          citations,
          ruleMappings,
          localeText,
          newSuccessorCode,
          createdBy,
          approvedBy,
          approvalComment,
          approvedAt,
          newCorrelationId,
          hash);
    }
  }

  public record ReasonCodeLifecycleResult(
      ComplianceReasonCodeVersion version,
      List<String> outboxEventTypes,
      String auditAction,
      String auditRef,
      String correlationId) {
    public ReasonCodeLifecycleResult {
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record ReasonCodeResolution(
      String tenantId,
      String code,
      String category,
      int version,
      String status,
      String severity,
      String displayText,
      List<String> citations,
      List<String> ruleMappings,
      String successorCode,
      String replayHash,
      String auditRef,
      String correlationId) {
    public ReasonCodeResolution {
      citations = citations == null ? List.of() : List.copyOf(citations);
      ruleMappings = ruleMappings == null ? List.of() : List.copyOf(ruleMappings);
    }
  }
}

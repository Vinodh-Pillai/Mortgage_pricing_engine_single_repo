package com.wcpe.compliance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class FederalComplianceRuleShell {
  public static final String COMPLETED_EVENT_TYPE = "federal_compliance_rule_shell.completed.v1";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";
  public static final String RESOLVED = "RESOLVED";
  private static final String PUBLISHED = "PUBLISHED";

  private FederalComplianceRuleShell() {}

  public static ComplianceAdvisoryResult evaluate(ComplianceEvaluationRequest request) {
    validateRequest(request);

    List<FederalRulePackVersion> matchingVersions =
        request.rulePackVersions().stream()
            .filter(version -> same(version.tenantId(), request.tenantId()))
            .filter(version -> same(version.rulePackCode(), request.rulePackCode()))
            .filter(version -> same(version.status(), PUBLISHED))
            .filter(version -> version.isEffectiveOn(request.asOfDate()))
            .filter(version -> version.applicability().matches(request.criteria()))
            .sorted(Comparator.comparingInt(FederalRulePackVersion::version).reversed())
            .toList();

    if (matchingVersions.isEmpty()) {
      return failClosed(request, null, List.of("RULE_PACK_NOT_FOUND"));
    }

    if (matchingVersions.size() > 1) {
      return failClosed(request, null, List.of("AMBIGUOUS_RULE_PACK_VERSION"));
    }

    FederalRulePackVersion resolvedVersion = matchingVersions.get(0);
    Set<String> missingThresholdRefs =
        missingThresholdRefs(resolvedVersion.thresholdConfigRefs(), request.availableThresholdConfigRefs());
    if (!missingThresholdRefs.isEmpty()) {
      return failClosed(
          request,
          resolvedVersion,
          missingThresholdRefs.stream()
              .map(ref -> "MISSING_THRESHOLD_CONFIG:" + ref)
              .toList());
    }

    return result(request, resolvedVersion, RESOLVED, resolvedVersion.ruleExpressionRefs(), List.of());
  }

  public static List<String> validatePublishedPeriods(List<FederalRulePackVersion> versions) {
    if (versions == null) {
      return List.of("rulePackVersions must be provided");
    }

    List<FederalRulePackVersion> published =
        versions.stream()
            .filter(version -> version != null && same(version.status(), PUBLISHED))
            .sorted(Comparator.comparing(FederalRulePackVersion::effectiveFrom))
            .toList();
    List<String> errors = new ArrayList<>();

    for (int left = 0; left < published.size(); left++) {
      FederalRulePackVersion current = published.get(left);
      for (int right = left + 1; right < published.size(); right++) {
        FederalRulePackVersion candidate = published.get(right);
        if (!same(current.tenantId(), candidate.tenantId())
            || !same(current.rulePackCode(), candidate.rulePackCode())
            || !current.applicability().equals(candidate.applicability())) {
          continue;
        }
        if (!candidate.effectiveFrom().isAfter(current.effectiveToOrMax())) {
          errors.add(
              "OVERLAPPING_EFFECTIVE_PERIOD:"
                  + current.rulePackCode()
                  + ":"
                  + current.version()
                  + ":"
                  + candidate.version());
        }
      }
    }

    return List.copyOf(errors);
  }

  private static ComplianceAdvisoryResult failClosed(
      ComplianceEvaluationRequest request, FederalRulePackVersion version, List<String> reasons) {
    return result(request, version, POLICY_NOT_SATISFIED, List.of(), reasons);
  }

  private static ComplianceAdvisoryResult result(
      ComplianceEvaluationRequest request,
      FederalRulePackVersion version,
      String status,
      List<String> executableRuleRefs,
      List<String> reasons) {
    RulePackResolution resolution =
        version == null
            ? null
            : new RulePackResolution(
                version.rulePackCode(),
                version.version(),
                version.hash(),
                version.citations(),
                executableRuleRefs);
    String auditRef = auditHash(request, resolution, status, reasons);
    return new ComplianceAdvisoryResult(
        request.requestId(),
        request.tenantId(),
        status,
        resolution,
        reasons,
        auditRef,
        COMPLETED_EVENT_TYPE,
        request.correlationId());
  }

  private static void validateRequest(ComplianceEvaluationRequest request) {
    if (request == null) {
      throw new ComplianceShellValidationError(
          "Federal compliance evaluation request must be an object.", List.of("request"));
    }

    List<String> details = new ArrayList<>();
    requireNonBlank(request.tenantId(), "tenantId", details);
    requireNonBlank(request.requestId(), "requestId", details);
    requireNonBlank(request.actorId(), "actorId", details);
    requireNonBlank(request.rulePackCode(), "rulePackCode", details);
    requireNonBlank(request.correlationId(), "correlationId", details);
    if (request.asOfDate() == null) {
      details.add("asOfDate must be provided");
    }
    if (request.criteria() == null) {
      details.add("criteria must be provided");
    }
    if (request.rulePackVersions() == null) {
      details.add("rulePackVersions must be provided");
    }
    if (request.availableThresholdConfigRefs() == null) {
      details.add("availableThresholdConfigRefs must be provided");
    }

    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError(
          "Federal compliance evaluation request validation failed.", details);
    }
  }

  private static Set<String> missingThresholdRefs(List<String> requiredRefs, Set<String> availableRefs) {
    Set<String> normalizedAvailable = new LinkedHashSet<>();
    for (String availableRef : availableRefs) {
      if (isNonBlank(availableRef)) {
        normalizedAvailable.add(availableRef.trim());
      }
    }

    Set<String> missing = new LinkedHashSet<>();
    for (String requiredRef : requiredRefs) {
      if (!isNonBlank(requiredRef) || !normalizedAvailable.contains(requiredRef.trim())) {
        missing.add(String.valueOf(requiredRef));
      }
    }
    return missing;
  }

  private static void requireNonBlank(String value, String field, List<String> details) {
    if (!isNonBlank(value)) {
      details.add(field + " must be a non-empty string");
    }
  }

  private static boolean isNonBlank(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private static boolean same(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  private static String auditHash(
      ComplianceEvaluationRequest request,
      RulePackResolution resolution,
      String status,
      List<String> reasons) {
    String material =
        request.tenantId()
            + "|"
            + request.requestId()
            + "|"
            + request.actorId()
            + "|"
            + request.rulePackCode()
            + "|"
            + request.asOfDate()
            + "|"
            + request.correlationId()
            + "|"
            + status
            + "|"
            + (resolution == null ? "unresolved" : resolution.rulePackHash())
            + "|"
            + String.join(",", reasons);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder("audit-sha256:");
      for (byte value : hash) {
        encoded.append(String.format(Locale.ROOT, "%02x", value));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is required for compliance audit refs", exception);
    }
  }

  public record ComplianceEvaluationRequest(
      String tenantId,
      String requestId,
      String actorId,
      String rulePackCode,
      LocalDate asOfDate,
      ApplicabilityCriteria criteria,
      List<FederalRulePackVersion> rulePackVersions,
      Set<String> availableThresholdConfigRefs,
      String correlationId) {
    public ComplianceEvaluationRequest {
      rulePackVersions = rulePackVersions == null ? null : List.copyOf(rulePackVersions);
      availableThresholdConfigRefs =
          availableThresholdConfigRefs == null ? null : Set.copyOf(availableThresholdConfigRefs);
    }
  }

  public record ApplicabilityCriteria(
      String productType, String channel, String state, String lienPosition) {
    boolean matches(ApplicabilityCriteria requested) {
      return requested != null
          && fieldMatches(productType, requested.productType())
          && fieldMatches(channel, requested.channel())
          && fieldMatches(state, requested.state())
          && fieldMatches(lienPosition, requested.lienPosition());
    }

    private static boolean fieldMatches(String configured, String requested) {
      return configured == null
          || configured.isBlank()
          || "*".equals(configured.trim())
          || same(configured.trim(), requested == null ? "" : requested.trim());
    }
  }

  public record FederalRulePackVersion(
      String tenantId,
      String rulePackCode,
      int version,
      String status,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      ApplicabilityCriteria applicability,
      List<String> ruleExpressionRefs,
      List<String> thresholdConfigRefs,
      List<String> citations,
      String hash) {
    public FederalRulePackVersion {
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom must be provided");
      }
      applicability = Objects.requireNonNull(applicability, "applicability must be provided");
      ruleExpressionRefs = ruleExpressionRefs == null ? List.of() : List.copyOf(ruleExpressionRefs);
      thresholdConfigRefs = thresholdConfigRefs == null ? List.of() : List.copyOf(thresholdConfigRefs);
      citations = citations == null ? List.of() : List.copyOf(citations);
    }

    boolean isEffectiveOn(LocalDate asOfDate) {
      return asOfDate != null && !asOfDate.isBefore(effectiveFrom) && !asOfDate.isAfter(effectiveToOrMax());
    }

    LocalDate effectiveToOrMax() {
      return effectiveTo == null ? LocalDate.MAX : effectiveTo;
    }
  }

  public record RulePackResolution(
      String rulePackCode,
      int version,
      String rulePackHash,
      List<String> citations,
      List<String> executableRuleRefs) {
    public RulePackResolution {
      citations = citations == null ? List.of() : List.copyOf(citations);
      executableRuleRefs = executableRuleRefs == null ? List.of() : List.copyOf(executableRuleRefs);
    }
  }

  public record ComplianceAdvisoryResult(
      String requestId,
      String tenantId,
      String status,
      RulePackResolution resolution,
      List<String> failClosedReasons,
      String auditRef,
      String outboxEventType,
      String correlationId) {
    public ComplianceAdvisoryResult {
      failClosedReasons = failClosedReasons == null ? List.of() : List.copyOf(failClosedReasons);
    }
  }
}

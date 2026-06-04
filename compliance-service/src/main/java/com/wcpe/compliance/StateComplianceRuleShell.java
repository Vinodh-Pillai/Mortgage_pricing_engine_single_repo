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

public final class StateComplianceRuleShell {
  public static final String COMPLETED_EVENT_TYPE = "state_compliance_shell.completed.v1";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";
  public static final String RESOLVED = "RESOLVED";
  private static final String PUBLISHED = "PUBLISHED";

  private StateComplianceRuleShell() {}

  public static StateComplianceAdvisoryResult resolve(StateComplianceEvaluationRequest request) {
    validateRequest(request);

    if (!isNonBlank(request.propertyState())) {
      return failClosed(request, null, List.of("MISSING_PROPERTY_STATE"));
    }

    String requestedState = normalizeState(request.propertyState());
    List<StateRulePackVersion> matchingVersions =
        request.rulePackVersions().stream()
            .filter(version -> same(version.tenantId(), request.tenantId()))
            .filter(version -> same(version.stateCode(), requestedState))
            .filter(version -> same(version.rulePackCode(), request.rulePackCode()))
            .filter(version -> same(version.status(), PUBLISHED))
            .filter(version -> version.isEffectiveOn(request.asOfDate()))
            .filter(version -> version.applicability().matches(request.criteria()))
            .sorted(Comparator.comparingInt(StateRulePackVersion::version).reversed())
            .toList();

    if (matchingVersions.isEmpty()) {
      return failClosed(request, null, List.of("STATE_RULE_PACK_NOT_FOUND"));
    }

    if (matchingVersions.size() > 1) {
      return failClosed(request, null, List.of("AMBIGUOUS_STATE_RULE_PACK_VERSION"));
    }

    StateRulePackVersion resolvedVersion = matchingVersions.get(0);
    Set<String> missingThresholdRefs =
        missingRefs(resolvedVersion.thresholdConfigRefs(), request.availableThresholdConfigRefs());
    if (!missingThresholdRefs.isEmpty()) {
      return failClosed(
          request,
          resolvedVersion,
          missingThresholdRefs.stream().map(ref -> "MISSING_THRESHOLD_CONFIG:" + ref).toList());
    }

    Set<String> missingFederalRefs =
        missingRefs(resolvedVersion.federalRulePackRefs(), request.availableFederalRulePackRefs());
    if (!missingFederalRefs.isEmpty()) {
      return failClosed(
          request,
          resolvedVersion,
          missingFederalRefs.stream().map(ref -> "MISSING_FEDERAL_PRECEDENCE_REF:" + ref).toList());
    }

    return result(
        request,
        resolvedVersion,
        RESOLVED,
        resolvedVersion.ruleExpressionRefs(),
        List.of());
  }

  public static List<String> validatePublishedPeriods(List<StateRulePackVersion> versions) {
    if (versions == null) {
      return List.of("rulePackVersions must be provided");
    }

    List<StateRulePackVersion> published =
        versions.stream()
            .filter(version -> version != null && same(version.status(), PUBLISHED))
            .sorted(Comparator.comparing(StateRulePackVersion::effectiveFrom))
            .toList();
    List<String> errors = new ArrayList<>();

    for (int left = 0; left < published.size(); left++) {
      StateRulePackVersion current = published.get(left);
      for (int right = left + 1; right < published.size(); right++) {
        StateRulePackVersion candidate = published.get(right);
        if (!same(current.tenantId(), candidate.tenantId())
            || !same(current.stateCode(), candidate.stateCode())
            || !same(current.rulePackCode(), candidate.rulePackCode())
            || !current.applicability().equals(candidate.applicability())) {
          continue;
        }
        if (!candidate.effectiveFrom().isAfter(current.effectiveToOrMax())) {
          errors.add(
              "OVERLAPPING_EFFECTIVE_PERIOD:"
                  + current.stateCode()
                  + ":"
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

  private static StateComplianceAdvisoryResult failClosed(
      StateComplianceEvaluationRequest request, StateRulePackVersion version, List<String> reasons) {
    return result(request, version, POLICY_NOT_SATISFIED, List.of(), reasons);
  }

  private static StateComplianceAdvisoryResult result(
      StateComplianceEvaluationRequest request,
      StateRulePackVersion version,
      String status,
      List<String> executableRuleRefs,
      List<String> reasons) {
    StateRulePackResolution resolution =
        version == null
            ? null
            : new StateRulePackResolution(
                version.stateCode(),
                version.rulePackCode(),
                version.version(),
                version.hash(),
                version.citations(),
                version.thresholdConfigRefs(),
                version.federalRulePackRefs(),
                executableRuleRefs);
    String auditRef = auditHash(request, resolution, status, reasons);
    return new StateComplianceAdvisoryResult(
        request.requestId(),
        request.tenantId(),
        normalizeState(request.propertyState()),
        status,
        resolution,
        reasons,
        auditRef,
        COMPLETED_EVENT_TYPE,
        request.correlationId());
  }

  private static void validateRequest(StateComplianceEvaluationRequest request) {
    if (request == null) {
      throw new ComplianceShellValidationError(
          "State compliance evaluation request must be an object.", List.of("request"));
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
    if (request.availableFederalRulePackRefs() == null) {
      details.add("availableFederalRulePackRefs must be provided");
    }

    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError(
          "State compliance evaluation request validation failed.", details);
    }
  }

  private static Set<String> missingRefs(List<String> requiredRefs, Set<String> availableRefs) {
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

  private static String normalizeState(String stateCode) {
    return stateCode == null ? "" : stateCode.trim().toUpperCase(Locale.ROOT);
  }

  private static String auditHash(
      StateComplianceEvaluationRequest request,
      StateRulePackResolution resolution,
      String status,
      List<String> reasons) {
    String material =
        request.tenantId()
            + "|"
            + request.requestId()
            + "|"
            + request.actorId()
            + "|"
            + normalizeState(request.propertyState())
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
      throw new IllegalStateException("SHA-256 digest is required for state compliance audit refs", exception);
    }
  }

  public record StateComplianceEvaluationRequest(
      String tenantId,
      String requestId,
      String actorId,
      String propertyState,
      String rulePackCode,
      LocalDate asOfDate,
      StateApplicabilityCriteria criteria,
      List<StateRulePackVersion> rulePackVersions,
      Set<String> availableThresholdConfigRefs,
      Set<String> availableFederalRulePackRefs,
      String correlationId) {
    public StateComplianceEvaluationRequest {
      rulePackVersions = rulePackVersions == null ? null : List.copyOf(rulePackVersions);
      availableThresholdConfigRefs =
          availableThresholdConfigRefs == null ? null : Set.copyOf(availableThresholdConfigRefs);
      availableFederalRulePackRefs =
          availableFederalRulePackRefs == null ? null : Set.copyOf(availableFederalRulePackRefs);
    }
  }

  public record StateApplicabilityCriteria(
      String productType, String channel, String lienPosition, String occupancy) {
    boolean matches(StateApplicabilityCriteria requested) {
      return requested != null
          && fieldMatches(productType, requested.productType())
          && fieldMatches(channel, requested.channel())
          && fieldMatches(lienPosition, requested.lienPosition())
          && fieldMatches(occupancy, requested.occupancy());
    }

    private static boolean fieldMatches(String configured, String requested) {
      return configured == null
          || configured.isBlank()
          || "*".equals(configured.trim())
          || same(configured.trim(), requested == null ? "" : requested.trim());
    }
  }

  public record StateRulePackVersion(
      String tenantId,
      String stateCode,
      String rulePackCode,
      int version,
      String status,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      StateApplicabilityCriteria applicability,
      List<String> ruleExpressionRefs,
      List<String> thresholdConfigRefs,
      List<String> federalRulePackRefs,
      List<String> citations,
      List<String> sourceDocumentRefs,
      String hash) {
    public StateRulePackVersion {
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom must be provided");
      }
      if (!isNonBlank(stateCode) || normalizeState(stateCode).length() != 2) {
        throw new IllegalArgumentException("stateCode must be a two-character state code");
      }
      stateCode = normalizeState(stateCode);
      applicability = Objects.requireNonNull(applicability, "applicability must be provided");
      ruleExpressionRefs = ruleExpressionRefs == null ? List.of() : List.copyOf(ruleExpressionRefs);
      thresholdConfigRefs = thresholdConfigRefs == null ? List.of() : List.copyOf(thresholdConfigRefs);
      federalRulePackRefs = federalRulePackRefs == null ? List.of() : List.copyOf(federalRulePackRefs);
      citations = citations == null ? List.of() : List.copyOf(citations);
      sourceDocumentRefs = sourceDocumentRefs == null ? List.of() : List.copyOf(sourceDocumentRefs);
    }

    boolean isEffectiveOn(LocalDate asOfDate) {
      return asOfDate != null && !asOfDate.isBefore(effectiveFrom) && !asOfDate.isAfter(effectiveToOrMax());
    }

    LocalDate effectiveToOrMax() {
      return effectiveTo == null ? LocalDate.MAX : effectiveTo;
    }
  }

  public record StateRulePackResolution(
      String stateCode,
      String rulePackCode,
      int version,
      String rulePackHash,
      List<String> citations,
      List<String> thresholdConfigRefs,
      List<String> federalRulePackRefs,
      List<String> executableRuleRefs) {
    public StateRulePackResolution {
      citations = citations == null ? List.of() : List.copyOf(citations);
      thresholdConfigRefs = thresholdConfigRefs == null ? List.of() : List.copyOf(thresholdConfigRefs);
      federalRulePackRefs = federalRulePackRefs == null ? List.of() : List.copyOf(federalRulePackRefs);
      executableRuleRefs = executableRuleRefs == null ? List.of() : List.copyOf(executableRuleRefs);
    }
  }

  public record StateComplianceAdvisoryResult(
      String requestId,
      String tenantId,
      String stateCode,
      String status,
      StateRulePackResolution resolution,
      List<String> failClosedReasons,
      String auditRef,
      String outboxEventType,
      String correlationId) {
    public StateComplianceAdvisoryResult {
      failClosedReasons = failClosedReasons == null ? List.of() : List.copyOf(failClosedReasons);
    }
  }
}

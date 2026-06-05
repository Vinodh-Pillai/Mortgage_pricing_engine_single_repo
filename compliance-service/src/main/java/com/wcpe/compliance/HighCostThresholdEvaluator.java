package com.wcpe.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class HighCostThresholdEvaluator {
  public static final String COMPLETED_EVENT_TYPE = "HighCostEvaluationCompleted.v1";
  public static final String FAILED_CLOSED_EVENT_TYPE = "HighCostEvaluationFailedClosed.v1";
  public static final String CONFIG_REFERENCED_EVENT_TYPE = "HighCostThresholdConfigReferenced.v1";
  public static final String NOT_HIGH_COST = "not_high_cost";
  public static final String NEAR_THRESHOLD = "near_threshold";
  public static final String HIGH_COST = "high_cost";
  public static final String BLOCKED_MISSING_CONFIG = "blocked_missing_config";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";

  private HighCostThresholdEvaluator() {}

  public static HighCostEvaluationResult evaluate(HighCostEvaluationRequest request) {
    validateRequest(request);

    List<ThresholdConfigVersion> configs = effectiveConfigs(request);
    if (configs.isEmpty()) {
      return failClosed(request, List.of("MISSING_THRESHOLD_CONFIG"));
    }

    List<String> duplicateTests = duplicateTests(configs);
    if (!duplicateTests.isEmpty()) {
      return failClosed(
          request,
          duplicateTests.stream().map(test -> "AMBIGUOUS_RULE_PACK_VERSION:" + test).toList());
    }

    List<CalculationLedgerEntry> ledger = new ArrayList<>();
    List<String> triggeredTests = new ArrayList<>();
    List<String> proximityBands = new ArrayList<>();
    List<String> reasonCodes = new ArrayList<>();
    boolean crossed = false;
    boolean blocking = false;
    boolean near = false;
    int sequence = 1;

    for (ThresholdConfigVersion config : configs) {
      BigDecimal input = request.inputs().valueFor(config.inputType());
      if (input == null) {
        return failClosed(request, List.of("MISSING_" + config.inputType() + "_INPUT"));
      }

      BigDecimal rounded = input.setScale(config.roundingScale(), config.roundingMode());
      boolean thresholdCrossed = compare(rounded, config.comparisonOperator(), config.thresholdValue());
      boolean insideBand = !thresholdCrossed && config.isInsideProximityBand(rounded);
      String outcome = thresholdCrossed ? HIGH_COST : insideBand ? NEAR_THRESHOLD : NOT_HIGH_COST;
      String reasonCode = config.reasonCode() == null ? config.testCode() + ":" + outcome : config.reasonCode();

      if (thresholdCrossed) {
        crossed = true;
        blocking = blocking || config.blocking();
        triggeredTests.add(config.testCode());
      }
      if (insideBand) {
        near = true;
        proximityBands.add(config.testCode() + ":" + config.proximityBandRef());
      }
      reasonCodes.add(reasonCode);
      ledger.add(
          new CalculationLedgerEntry(
              sequence++,
              config.testCode(),
              config.inputType(),
              config.formulaRef(),
              config.configVersionId(),
              input,
              rounded,
              config.comparisonOperator(),
              config.thresholdRef(),
              outcome,
              reasonCode));
    }

    String status = crossed ? HIGH_COST : near ? NEAR_THRESHOLD : NOT_HIGH_COST;
    String advisorySeverity = blocking ? "BLOCKING" : crossed || near ? "WARNING" : "INFO";
    List<RulePackVersionRef> rulePackRefs = configs.stream().map(ThresholdConfigVersion::rulePackRef).toList();
    String resultHash = resultHash(request, configs, ledger, status, reasonCodes);
    String auditRef = "high-cost-audit:" + resultHash;
    return new HighCostEvaluationResult(
        request.requestId(),
        request.tenantId(),
        request.scenarioId(),
        request.quoteId(),
        status,
        advisorySeverity,
        triggeredTests,
        proximityBands,
        ledger,
        reasonCodes,
        rulePackRefs,
        resultHash,
        auditRef,
        List.of(COMPLETED_EVENT_TYPE, CONFIG_REFERENCED_EVENT_TYPE),
        request.correlationId());
  }

  public static HighCostEvaluationResult replay(
      HighCostEvaluationRequest request, String expectedResultHash) {
    HighCostEvaluationResult result = evaluate(request);
    if (!Objects.equals(result.resultHash(), expectedResultHash)) {
      return new HighCostEvaluationResult(
          result.requestId(),
          result.tenantId(),
          result.scenarioId(),
          result.quoteId(),
          POLICY_NOT_SATISFIED,
          "BLOCKING",
          result.triggeredTests(),
          result.proximityBands(),
          result.ledger(),
          append(result.reasonCodes(), "REPLAY_HASH_MISMATCH"),
          result.rulePackVersionRefs(),
          result.resultHash(),
          result.auditRef(),
          List.of(FAILED_CLOSED_EVENT_TYPE),
          result.correlationId());
    }
    return result;
  }

  private static HighCostEvaluationResult failClosed(
      HighCostEvaluationRequest request, List<String> reasonCodes) {
    String resultHash = resultHash(request, List.of(), List.of(), BLOCKED_MISSING_CONFIG, reasonCodes);
    return new HighCostEvaluationResult(
        request.requestId(),
        request.tenantId(),
        request.scenarioId(),
        request.quoteId(),
        BLOCKED_MISSING_CONFIG,
        "BLOCKING",
        List.of(),
        List.of(),
        List.of(),
        reasonCodes,
        List.of(),
        resultHash,
        "high-cost-audit:" + resultHash,
        List.of(FAILED_CLOSED_EVENT_TYPE),
        request.correlationId());
  }

  private static List<ThresholdConfigVersion> effectiveConfigs(HighCostEvaluationRequest request) {
    return request.configVersions().stream()
        .filter(config -> config != null)
        .filter(config -> same(config.tenantId(), request.tenantId()))
        .filter(config -> config.isEffectiveOn(request.asOfDate()))
        .filter(config -> config.matches(request))
        .sorted(Comparator.comparing(ThresholdConfigVersion::testCode))
        .toList();
  }

  private static List<String> duplicateTests(List<ThresholdConfigVersion> configs) {
    Set<String> seen = new LinkedHashSet<>();
    Set<String> duplicate = new LinkedHashSet<>();
    for (ThresholdConfigVersion config : configs) {
      if (!seen.add(config.testCode())) {
        duplicate.add(config.testCode());
      }
    }
    return List.copyOf(duplicate);
  }

  private static boolean compare(BigDecimal input, String operator, BigDecimal threshold) {
    int comparison = input.compareTo(threshold);
    return switch (operator) {
      case ">" -> comparison > 0;
      case ">=" -> comparison >= 0;
      case "<" -> comparison < 0;
      case "<=" -> comparison <= 0;
      case "=" -> comparison == 0;
      default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
    };
  }

  private static void validateRequest(HighCostEvaluationRequest request) {
    if (request == null) {
      throw new ComplianceShellValidationError(
          "High-cost evaluation request must be an object.", List.of("request"));
    }
    List<String> details = new ArrayList<>();
    requireNonBlank(request.tenantId(), "tenantId", details);
    requireNonBlank(request.requestId(), "requestId", details);
    requireNonBlank(request.actorId(), "actorId", details);
    requireNonBlank(request.scenarioId(), "scenarioId", details);
    requireNonBlank(request.productType(), "productType", details);
    requireNonBlank(request.stateCode(), "stateCode", details);
    requireNonBlank(request.correlationId(), "correlationId", details);
    requireNonBlank(request.idempotencyKey(), "idempotencyKey", details);
    if (request.asOfDate() == null) {
      details.add("asOfDate must be provided");
    }
    if (request.inputs() == null) {
      details.add("inputs must be provided");
    }
    if (request.configVersions() == null) {
      details.add("configVersions must be provided");
    }
    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError(
          "High-cost evaluation request validation failed.", details);
    }
  }

  private static String resultHash(
      HighCostEvaluationRequest request,
      List<ThresholdConfigVersion> configs,
      List<CalculationLedgerEntry> ledger,
      String status,
      List<String> reasonCodes) {
    String material =
        request.tenantId()
            + "|"
            + request.scenarioId()
            + "|"
            + request.quoteId()
            + "|"
            + request.asOfDate()
            + "|"
            + request.productType()
            + "|"
            + request.stateCode()
            + "|"
            + status
            + "|"
            + configs.stream().map(ThresholdConfigVersion::hashMaterial).reduce("", (a, b) -> a + b)
            + "|"
            + ledger.stream().map(CalculationLedgerEntry::hashMaterial).reduce("", (a, b) -> a + b)
            + "|"
            + String.join(",", reasonCodes);
    return "sha256:" + sha256(material);
  }

  private static String sha256(String material) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
      StringBuilder encoded = new StringBuilder();
      for (byte value : hash) {
        encoded.append(String.format(Locale.ROOT, "%02x", value));
      }
      return encoded.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is required for high-cost replay", exception);
    }
  }

  private static List<String> append(List<String> values, String value) {
    List<String> copy = new ArrayList<>(values);
    copy.add(value);
    return List.copyOf(copy);
  }

  private static void requireNonBlank(String value, String field, List<String> details) {
    if (value == null || value.trim().isEmpty()) {
      details.add(field + " must be a non-empty string");
    }
  }

  private static boolean same(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  public record HighCostEvaluationRequest(
      String tenantId,
      String requestId,
      String actorId,
      String scenarioId,
      String quoteId,
      LocalDate asOfDate,
      String productType,
      String channel,
      String stateCode,
      String lienPosition,
      String occupancy,
      HighCostScenarioInputs inputs,
      List<ThresholdConfigVersion> configVersions,
      String idempotencyKey,
      String correlationId) {
    public HighCostEvaluationRequest {
      configVersions = configVersions == null ? null : List.copyOf(configVersions);
    }
  }

  public record HighCostScenarioInputs(
      BigDecimal aprSpread, BigDecimal pointsAndFees, BigDecimal prepaymentPenalty) {
    BigDecimal valueFor(String inputType) {
      return switch (inputType) {
        case "APR_SPREAD" -> aprSpread;
        case "POINTS_AND_FEES" -> pointsAndFees;
        case "PREPAYMENT_PENALTY" -> prepaymentPenalty;
        default -> null;
      };
    }
  }

  public record RulePackVersionRef(
      String jurisdiction, String rulePackCode, int version, String configVersionId, String citation) {}

  public record ThresholdConfigVersion(
      String tenantId,
      String configVersionId,
      String testCode,
      String jurisdiction,
      String rulePackCode,
      int rulePackVersion,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String productType,
      String channel,
      String stateCode,
      String lienPosition,
      String occupancy,
      String inputType,
      String formulaRef,
      BigDecimal thresholdValue,
      String comparisonOperator,
      int roundingScale,
      RoundingMode roundingMode,
      BigDecimal proximityBandValue,
      String proximityBandRef,
      boolean blocking,
      String thresholdRef,
      String citation,
      String reasonCode) {
    public ThresholdConfigVersion {
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom must be provided");
      }
      thresholdValue = Objects.requireNonNull(thresholdValue, "thresholdValue must be provided");
      roundingMode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
      comparisonOperator = comparisonOperator == null ? ">=" : comparisonOperator;
      proximityBandValue = proximityBandValue == null ? BigDecimal.ZERO : proximityBandValue;
    }

    boolean isEffectiveOn(LocalDate asOfDate) {
      return asOfDate != null && !asOfDate.isBefore(effectiveFrom) && !asOfDate.isAfter(effectiveToOrMax());
    }

    boolean matches(HighCostEvaluationRequest request) {
      return fieldMatches(productType, request.productType())
          && fieldMatches(channel, request.channel())
          && fieldMatches(stateCode, request.stateCode())
          && fieldMatches(lienPosition, request.lienPosition())
          && fieldMatches(occupancy, request.occupancy());
    }

    boolean isInsideProximityBand(BigDecimal value) {
      if (proximityBandValue.signum() <= 0) {
        return false;
      }
      return switch (comparisonOperator) {
        case ">", ">=" -> thresholdValue.subtract(value).compareTo(proximityBandValue) <= 0;
        case "<", "<=" -> value.subtract(thresholdValue).compareTo(proximityBandValue) <= 0;
        default -> false;
      };
    }

    RulePackVersionRef rulePackRef() {
      return new RulePackVersionRef(
          jurisdiction, rulePackCode, rulePackVersion, configVersionId, citation);
    }

    String hashMaterial() {
      return String.join(
          "|",
          configVersionId,
          testCode,
          String.valueOf(rulePackVersion),
          thresholdValue.toPlainString(),
          comparisonOperator,
          String.valueOf(roundingScale),
          roundingMode.name(),
          proximityBandValue.toPlainString(),
          thresholdRef == null ? "" : thresholdRef);
    }

    LocalDate effectiveToOrMax() {
      return effectiveTo == null ? LocalDate.MAX : effectiveTo;
    }

    private static boolean fieldMatches(String configured, String requested) {
      return configured == null
          || configured.isBlank()
          || "*".equals(configured.trim())
          || same(configured.trim(), requested == null ? "" : requested.trim());
    }
  }

  public record CalculationLedgerEntry(
      int sequence,
      String testCode,
      String inputRef,
      String formulaRef,
      String configVersionId,
      BigDecimal rawValue,
      BigDecimal roundedValue,
      String comparisonOperator,
      String thresholdRef,
      String outcome,
      String reasonCode) {
    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sequence),
          testCode,
          inputRef,
          formulaRef,
          configVersionId,
          rawValue.toPlainString(),
          roundedValue.toPlainString(),
          comparisonOperator,
          thresholdRef,
          outcome,
          reasonCode);
    }
  }

  public record HighCostEvaluationResult(
      String requestId,
      String tenantId,
      String scenarioId,
      String quoteId,
      String status,
      String advisorySeverity,
      List<String> triggeredTests,
      List<String> proximityBands,
      List<CalculationLedgerEntry> ledger,
      List<String> reasonCodes,
      List<RulePackVersionRef> rulePackVersionRefs,
      String resultHash,
      String auditRef,
      List<String> outboxEventTypes,
      String correlationId) {
    public HighCostEvaluationResult {
      triggeredTests = triggeredTests == null ? List.of() : List.copyOf(triggeredTests);
      proximityBands = proximityBands == null ? List.of() : List.copyOf(proximityBands);
      ledger = ledger == null ? List.of() : List.copyOf(ledger);
      reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
      rulePackVersionRefs =
          rulePackVersionRefs == null ? List.of() : List.copyOf(rulePackVersionRefs);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }
}

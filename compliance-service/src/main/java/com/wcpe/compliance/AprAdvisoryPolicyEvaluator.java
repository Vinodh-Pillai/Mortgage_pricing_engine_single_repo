package com.wcpe.compliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

public final class AprAdvisoryPolicyEvaluator {
  public static final String COMPLETED_EVENT_TYPE = "AprAdvisoryCompleted.v1";
  public static final String FAILED_CLOSED_EVENT_TYPE = "AprAdvisoryFailedClosed.v1";
  public static final String CONFIG_REFERENCED_EVENT_TYPE = "AprAdvisoryConfigReferenced.v1";
  public static final String CLEAR = "clear";
  public static final String WARNING = "warning";
  public static final String BLOCKING = "blocking";
  public static final String BLOCKED_MISSING_CONFIG = "blocked_missing_config";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";

  private AprAdvisoryPolicyEvaluator() {}

  public static AprAdvisoryResult evaluate(AprAdvisoryRequest request) {
    validateRequest(request);

    List<AprAdvisoryConfigVersion> configs = effectiveConfigs(request);
    if (configs.isEmpty()) {
      return failClosed(request, List.of("MISSING_APR_FORMULA_CONFIG"));
    }
    if (configs.size() > 1) {
      return failClosed(
          request,
          configs.stream()
              .map(config -> "AMBIGUOUS_APR_FORMULA_CONFIG:" + config.configVersionId())
              .toList());
    }

    AprAdvisoryConfigVersion config = configs.get(0);
    if (request.paymentStreamRef() == null || request.paymentStreamRef().trim().isEmpty()) {
      return failClosed(request, List.of("MISSING_PAYMENT_STREAM"));
    }
    if (config.roundingMode() == null) {
      return failClosed(request, List.of("ROUNDING_CONFIG_MISSING"));
    }
    if (config.feeTreatments().isEmpty() && !request.financeChargeComponents().isEmpty()) {
      return failClosed(request, List.of("MISSING_FEE_TREATMENT_CONFIG"));
    }

    List<String> duplicateFeeTreatments = duplicateFeeTreatments(config.feeTreatments());
    if (!duplicateFeeTreatments.isEmpty()) {
      return failClosed(
          request,
          duplicateFeeTreatments.stream()
              .map(componentCode -> "AMBIGUOUS_FEE_TREATMENT:" + componentCode)
              .toList());
    }

    List<FinanceChargeLedgerEntry> financeChargeLedger = new ArrayList<>();
    BigDecimal includedFinanceChargeTotal = BigDecimal.ZERO;
    int sequence = 1;
    for (FinanceChargeComponent component : sortedComponents(request.financeChargeComponents())) {
      FeeTreatmentConfig treatment = config.treatmentFor(component.componentCode());
      if (treatment == null) {
        return failClosed(request, List.of("INVALID_FEE_COMPONENT:" + component.componentCode()));
      }
      BigDecimal roundedAmount = component.amount().setScale(config.currencyScale(), config.roundingMode());
      if (treatment.included()) {
        includedFinanceChargeTotal = includedFinanceChargeTotal.add(roundedAmount);
      }
      financeChargeLedger.add(
          new FinanceChargeLedgerEntry(
              sequence++,
              component.componentCode(),
              roundedAmount,
              treatment.included(),
              treatment.inclusionRuleRef(),
              component.sourceRef(),
              component.sensitivityClassification()));
    }

    BigDecimal roundedApr = request.apr().setScale(config.aprScale(), config.roundingMode());
    BigDecimal roundedNoteRate = request.noteRate().setScale(config.aprScale(), config.roundingMode());
    BigDecimal spread = roundedApr.subtract(roundedNoteRate).setScale(config.aprScale(), config.roundingMode());
    boolean insideWarningBand = config.isInsideWarningBand(spread);
    String status = insideWarningBand ? (config.blockingWhenWarning() ? BLOCKING : WARNING) : CLEAR;
    List<String> warnings = insideWarningBand ? List.of(config.warningBandRef()) : List.of();
    List<String> reasonCodes =
        insideWarningBand ? List.of(config.warningReasonCode()) : List.of(config.clearReasonCode());
    List<AprLedgerEntry> ledger =
        List.of(
            new AprLedgerEntry(
                1,
                "APR_ADVISORY_SPREAD",
                config.formulaRef(),
                config.configVersionId(),
                request.apr(),
                roundedApr,
                roundedNoteRate,
                spread,
                config.warningBandRef(),
                status,
                config.roundingMode().name()));
    List<AprConfigVersionRef> configVersionRefs = List.of(config.configVersionRef());
    String resultHash =
        resultHash(request, config, ledger, financeChargeLedger, status, warnings, reasonCodes);
    return new AprAdvisoryResult(
        request.requestId(),
        request.tenantId(),
        request.scenarioId(),
        request.quoteId(),
        status,
        roundedApr,
        roundedNoteRate,
        spread,
        includedFinanceChargeTotal,
        financeChargeLedger,
        ledger,
        warnings,
        reasonCodes,
        configVersionRefs,
        resultHash,
        "apr-advisory-audit:" + resultHash,
        List.of(COMPLETED_EVENT_TYPE, CONFIG_REFERENCED_EVENT_TYPE),
        request.correlationId());
  }

  public static AprAdvisoryResult replay(AprAdvisoryRequest request, String expectedResultHash) {
    AprAdvisoryResult result = evaluate(request);
    if (!Objects.equals(result.resultHash(), expectedResultHash)) {
      return new AprAdvisoryResult(
          result.requestId(),
          result.tenantId(),
          result.scenarioId(),
          result.quoteId(),
          POLICY_NOT_SATISFIED,
          result.apr(),
          result.noteRate(),
          result.spread(),
          result.includedFinanceChargeTotal(),
          result.includedFinanceCharges(),
          result.ledger(),
          result.warnings(),
          append(result.reasonCodes(), "APR_REPLAY_HASH_MISMATCH"),
          result.configVersionRefs(),
          result.resultHash(),
          result.auditRef(),
          List.of(FAILED_CLOSED_EVENT_TYPE),
          result.correlationId());
    }
    return result;
  }

  private static AprAdvisoryResult failClosed(AprAdvisoryRequest request, List<String> reasonCodes) {
    String resultHash = resultHash(request, null, List.of(), List.of(), BLOCKED_MISSING_CONFIG, List.of(), reasonCodes);
    return new AprAdvisoryResult(
        request.requestId(),
        request.tenantId(),
        request.scenarioId(),
        request.quoteId(),
        BLOCKED_MISSING_CONFIG,
        request.apr(),
        request.noteRate(),
        null,
        BigDecimal.ZERO,
        List.of(),
        List.of(),
        List.of(),
        reasonCodes,
        List.of(),
        resultHash,
        "apr-advisory-audit:" + resultHash,
        List.of(FAILED_CLOSED_EVENT_TYPE),
        request.correlationId());
  }

  private static List<AprAdvisoryConfigVersion> effectiveConfigs(AprAdvisoryRequest request) {
    return request.configVersions().stream()
        .filter(config -> config != null)
        .filter(config -> same(config.tenantId(), request.tenantId()))
        .filter(config -> config.isEffectiveOn(request.asOfDate()))
        .filter(config -> config.matches(request))
        .sorted(Comparator.comparing(AprAdvisoryConfigVersion::configVersionId))
        .toList();
  }

  private static List<String> duplicateFeeTreatments(List<FeeTreatmentConfig> treatments) {
    Set<String> seen = new LinkedHashSet<>();
    Set<String> duplicate = new LinkedHashSet<>();
    for (FeeTreatmentConfig treatment : treatments) {
      if (!seen.add(normalize(treatment.componentCode()))) {
        duplicate.add(treatment.componentCode());
      }
    }
    return List.copyOf(duplicate);
  }

  private static List<FinanceChargeComponent> sortedComponents(List<FinanceChargeComponent> components) {
    return components.stream()
        .sorted(Comparator.comparing(component -> normalize(component.componentCode())))
        .toList();
  }

  private static void validateRequest(AprAdvisoryRequest request) {
    if (request == null) {
      throw new ComplianceShellValidationError("APR advisory request must be an object.", List.of("request"));
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
    if (request.noteRate() == null) {
      details.add("noteRate must be provided");
    }
    if (request.apr() == null) {
      details.add("apr must be provided");
    }
    if (request.financeChargeComponents() == null) {
      details.add("financeChargeComponents must be provided");
    }
    if (request.configVersions() == null) {
      details.add("configVersions must be provided");
    }
    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError("APR advisory request validation failed.", details);
    }
  }

  private static String resultHash(
      AprAdvisoryRequest request,
      AprAdvisoryConfigVersion config,
      List<AprLedgerEntry> ledger,
      List<FinanceChargeLedgerEntry> financeChargeLedger,
      String status,
      List<String> warnings,
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
            + request.paymentStreamRef()
            + "|"
            + request.productType()
            + "|"
            + request.stateCode()
            + "|"
            + status
            + "|"
            + (config == null ? "" : config.hashMaterial())
            + "|"
            + ledger.stream().map(AprLedgerEntry::hashMaterial).reduce("", (a, b) -> a + b)
            + "|"
            + financeChargeLedger.stream()
                .map(FinanceChargeLedgerEntry::hashMaterial)
                .reduce("", (a, b) -> a + b)
            + "|"
            + String.join(",", warnings)
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
      throw new IllegalStateException("SHA-256 digest is required for APR advisory replay", exception);
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

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record AprAdvisoryRequest(
      String tenantId,
      String requestId,
      String actorId,
      String scenarioId,
      String quoteId,
      LocalDate asOfDate,
      String productType,
      String channel,
      String stateCode,
      String paymentStreamRef,
      BigDecimal noteRate,
      BigDecimal apr,
      List<FinanceChargeComponent> financeChargeComponents,
      List<AprAdvisoryConfigVersion> configVersions,
      String idempotencyKey,
      String correlationId) {
    public AprAdvisoryRequest {
      financeChargeComponents =
          financeChargeComponents == null ? null : List.copyOf(financeChargeComponents);
      configVersions = configVersions == null ? null : List.copyOf(configVersions);
    }
  }

  public record FinanceChargeComponent(
      String componentCode,
      BigDecimal amount,
      String sourceRef,
      String sensitivityClassification) {
    public FinanceChargeComponent {
      componentCode = Objects.requireNonNull(componentCode, "componentCode must be provided");
      amount = Objects.requireNonNull(amount, "amount must be provided");
    }
  }

  public record FeeTreatmentConfig(String componentCode, boolean included, String inclusionRuleRef) {
    public FeeTreatmentConfig {
      componentCode = Objects.requireNonNull(componentCode, "componentCode must be provided");
      inclusionRuleRef =
          Objects.requireNonNull(inclusionRuleRef, "inclusionRuleRef must be provided");
    }

    String hashMaterial() {
      return String.join("|", componentCode, String.valueOf(included), inclusionRuleRef);
    }
  }

  public record AprConfigVersionRef(
      String configVersionId,
      String formulaRef,
      String toleranceConfigRef,
      String roundingRef,
      String warningBandRef) {}

  public record AprAdvisoryConfigVersion(
      String tenantId,
      String configVersionId,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String productType,
      String channel,
      String stateCode,
      String formulaRef,
      String toleranceConfigRef,
      String roundingRef,
      int aprScale,
      int currencyScale,
      RoundingMode roundingMode,
      BigDecimal warningBandValue,
      String warningBandRef,
      boolean blockingWhenWarning,
      String warningReasonCode,
      String clearReasonCode,
      List<FeeTreatmentConfig> feeTreatments) {
    public AprAdvisoryConfigVersion {
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom must be provided");
      }
      feeTreatments = feeTreatments == null ? List.of() : List.copyOf(feeTreatments);
      warningBandValue = warningBandValue == null ? BigDecimal.ZERO : warningBandValue;
      warningReasonCode = warningReasonCode == null ? "APR_WARNING_BAND" : warningReasonCode;
      clearReasonCode = clearReasonCode == null ? "APR_ADVISORY_CLEAR" : clearReasonCode;
    }

    boolean isEffectiveOn(LocalDate asOfDate) {
      return asOfDate != null && !asOfDate.isBefore(effectiveFrom) && !asOfDate.isAfter(effectiveToOrMax());
    }

    boolean matches(AprAdvisoryRequest request) {
      return fieldMatches(productType, request.productType())
          && fieldMatches(channel, request.channel())
          && fieldMatches(stateCode, request.stateCode());
    }

    boolean isInsideWarningBand(BigDecimal spread) {
      return warningBandValue.signum() > 0 && spread.abs().compareTo(warningBandValue) >= 0;
    }

    FeeTreatmentConfig treatmentFor(String componentCode) {
      String normalized = normalize(componentCode);
      return feeTreatments.stream()
          .filter(treatment -> normalize(treatment.componentCode()).equals(normalized))
          .findFirst()
          .orElse(null);
    }

    AprConfigVersionRef configVersionRef() {
      return new AprConfigVersionRef(
          configVersionId, formulaRef, toleranceConfigRef, roundingRef, warningBandRef);
    }

    String hashMaterial() {
      return String.join(
          "|",
          configVersionId,
          formulaRef,
          toleranceConfigRef,
          roundingRef,
          String.valueOf(aprScale),
          String.valueOf(currencyScale),
          roundingMode == null ? "" : roundingMode.name(),
          warningBandValue.toPlainString(),
          warningBandRef == null ? "" : warningBandRef,
          String.valueOf(blockingWhenWarning),
          feeTreatments.stream().map(FeeTreatmentConfig::hashMaterial).reduce("", (a, b) -> a + b));
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

  public record FinanceChargeLedgerEntry(
      int sequence,
      String componentCode,
      BigDecimal amount,
      boolean included,
      String inclusionRuleRef,
      String sourceRef,
      String sensitivityClassification) {
    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sequence),
          componentCode,
          amount.toPlainString(),
          String.valueOf(included),
          inclusionRuleRef,
          sourceRef == null ? "" : sourceRef,
          sensitivityClassification == null ? "" : sensitivityClassification);
    }
  }

  public record AprLedgerEntry(
      int sequence,
      String stepCode,
      String formulaRef,
      String configVersionId,
      BigDecimal rawApr,
      BigDecimal roundedApr,
      BigDecimal roundedNoteRate,
      BigDecimal spread,
      String warningBandRef,
      String outcome,
      String roundingMode) {
    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sequence),
          stepCode,
          formulaRef,
          configVersionId,
          rawApr.toPlainString(),
          roundedApr.toPlainString(),
          roundedNoteRate.toPlainString(),
          spread.toPlainString(),
          warningBandRef,
          outcome,
          roundingMode);
    }
  }

  public record AprAdvisoryResult(
      String requestId,
      String tenantId,
      String scenarioId,
      String quoteId,
      String status,
      BigDecimal apr,
      BigDecimal noteRate,
      BigDecimal spread,
      BigDecimal includedFinanceChargeTotal,
      List<FinanceChargeLedgerEntry> includedFinanceCharges,
      List<AprLedgerEntry> ledger,
      List<String> warnings,
      List<String> reasonCodes,
      List<AprConfigVersionRef> configVersionRefs,
      String resultHash,
      String auditRef,
      List<String> outboxEventTypes,
      String correlationId) {
    public AprAdvisoryResult {
      includedFinanceCharges =
          includedFinanceCharges == null ? List.of() : List.copyOf(includedFinanceCharges);
      ledger = ledger == null ? List.of() : List.copyOf(ledger);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
      reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
      configVersionRefs = configVersionRefs == null ? List.of() : List.copyOf(configVersionRefs);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }
}

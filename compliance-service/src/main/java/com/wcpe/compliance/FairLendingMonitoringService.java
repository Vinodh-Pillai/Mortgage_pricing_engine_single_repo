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

public final class FairLendingMonitoringService {
  public static final String SNAPSHOT_COMPLETED_EVENT_TYPE = "FairLendingSnapshotCompleted.v1";
  public static final String ALERT_RAISED_EVENT_TYPE = "FairLendingAlertRaised.v1";
  public static final String ALERT_REVIEWED_EVENT_TYPE = "FairLendingAlertReviewed.v1";
  public static final String ALERT_RESOLVED_EVENT_TYPE = "FairLendingAlertResolved.v1";
  public static final String SNAPSHOT_FAILED_CLOSED_EVENT_TYPE = "FairLendingSnapshotFailedClosed.v1";
  public static final String COMPLETED_CLEAR = "completed_clear";
  public static final String COMPLETED_ALERT = "completed_alert";
  public static final String BLOCKED_MISSING_CONFIG = "blocked_missing_config";
  public static final String POLICY_NOT_SATISFIED = "POLICY_NOT_SATISFIED";
  public static final String ALERT_RAISED = "raised";
  public static final String ALERT_UNDER_REVIEW = "under_review";
  public static final String ALERT_RESOLVED = "resolved";

  private FairLendingMonitoringService() {}

  public static FairLendingSnapshotResult runSnapshot(FairLendingSnapshotRequest request) {
    validateSnapshotRequest(request);

    List<FairLendingMonitorConfigVersion> configs = effectiveConfigs(request);
    if (configs.isEmpty()) {
      return failClosed(request, List.of("MISSING_MONITOR_CONFIG"));
    }
    if (configs.size() > 1) {
      return failClosed(
          request,
          configs.stream()
              .map(config -> "AMBIGUOUS_MONITOR_CONFIG:" + config.configVersionId())
              .toList());
    }

    FairLendingMonitorConfigVersion config = configs.get(0);
    List<String> configDefects = configDefects(config);
    if (!configDefects.isEmpty()) {
      return failClosed(request, configDefects);
    }
    if (request.sourceCompleteness().sourceEventCount() <= 0) {
      return failClosed(request, List.of("STALE_SOURCE_DATA"));
    }
    if (request.sourceCompleteness().completenessRuleRef() == null
        || request.sourceCompleteness().completenessRuleRef().isBlank()) {
      return failClosed(request, List.of("SOURCE_COMPLETENESS_RULE_MISSING"));
    }

    List<FairLendingMetricResult> metricResults = new ArrayList<>();
    List<FairLendingLedgerEntry> ledger = new ArrayList<>();
    List<FairLendingAlert> alerts = new ArrayList<>();
    List<String> reasonCodes = new ArrayList<>();
    int sequence = 1;
    boolean redacted = request.protectedClassDetailsRequested() && !request.protectedClassDetailAuthorized();

    for (MetricDefinition definition : sortedDefinitions(config.metricDefinitions())) {
      OutcomeMeasure measure = outcomeFor(request.outcomeMeasures(), definition.metricCode());
      if (measure == null) {
        return failClosed(request, List.of("MISSING_OUTCOME_MEASURE:" + definition.metricCode()));
      }
      if (measure.populationCount() < definition.minimumPopulation()) {
        return failClosed(request, List.of("INSUFFICIENT_POPULATION:" + definition.metricCode()));
      }
      BigDecimal roundedValue = measure.value().setScale(definition.scale(), definition.roundingMode());
      boolean thresholdCrossed =
          compare(roundedValue, definition.comparisonOperator(), definition.thresholdValue());
      String reasonCode =
          definition.reasonCode() == null
              ? definition.metricCode() + (thresholdCrossed ? ":ALERT" : ":CLEAR")
              : definition.reasonCode();
      String severity = thresholdCrossed ? definition.alertSeverity() : "none";
      reasonCodes.add(reasonCode);
      metricResults.add(
          new FairLendingMetricResult(
              definition.metricCode(),
              measure.peerGroupKey(),
              measure.comparisonGroupKey(),
              roundedValue,
              definition.thresholdRef(),
              severity,
              reasonCode,
              measure.populationCount(),
              definition.formulaRef(),
              redacted,
              measure.supportingRefs()));
      ledger.add(
          new FairLendingLedgerEntry(
              sequence++,
              definition.metricCode(),
              definition.formulaRef(),
              definition.thresholdRef(),
              request.sourceCompleteness().sourceEventCount(),
              request.sourceCompleteness().completenessRuleRef(),
              roundedValue,
              definition.comparisonOperator(),
              thresholdCrossed ? "alert" : "clear"));
      if (thresholdCrossed) {
        alerts.add(
            new FairLendingAlert(
                request.snapshotId() + ":" + normalize(definition.metricCode()),
                request.tenantId(),
                request.snapshotId(),
                definition.metricCode(),
                severity,
                ALERT_RAISED,
                null,
                null,
                null,
                List.of(ALERT_RAISED_EVENT_TYPE),
                request.correlationId()));
      }
    }

    String status = alerts.isEmpty() ? COMPLETED_CLEAR : COMPLETED_ALERT;
    List<String> eventTypes =
        alerts.isEmpty()
            ? List.of(SNAPSHOT_COMPLETED_EVENT_TYPE)
            : List.of(SNAPSHOT_COMPLETED_EVENT_TYPE, ALERT_RAISED_EVENT_TYPE);
    String resultHash = resultHash(request, config, metricResults, ledger, status, reasonCodes);
    return new FairLendingSnapshotResult(
        request.snapshotId(),
        request.tenantId(),
        status,
        config.configVersionRef(),
        request.periodStart(),
        request.periodEnd(),
        request.sourceCompleteness().sourceEventCount(),
        request.sourceCompleteness().completenessScore(),
        metricResults,
        alerts,
        ledger,
        reasonCodes,
        resultHash,
        "fair-lending-monitoring-audit:" + resultHash,
        eventTypes,
        request.correlationId());
  }

  public static FairLendingSnapshotResult replay(
      FairLendingSnapshotRequest request, String expectedResultHash) {
    FairLendingSnapshotResult result = runSnapshot(request);
    if (!Objects.equals(result.resultHash(), expectedResultHash)) {
      return new FairLendingSnapshotResult(
          result.snapshotId(),
          result.tenantId(),
          POLICY_NOT_SATISFIED,
          result.configVersionRef(),
          result.periodStart(),
          result.periodEnd(),
          result.populationCount(),
          result.dataCompletenessScore(),
          result.metricResults(),
          result.alerts(),
          result.ledger(),
          append(result.reasonCodes(), "FAIR_LENDING_REPLAY_HASH_MISMATCH"),
          result.resultHash(),
          result.auditRef(),
          List.of(SNAPSHOT_FAILED_CLOSED_EVENT_TYPE),
          result.correlationId());
    }
    return result;
  }

  public static FairLendingAlert reviewAlert(
      FairLendingAlert alert, String reviewerId, String assignedTo, String comments) {
    validateAlertAction(alert, reviewerId, comments);
    if (!ALERT_RAISED.equals(alert.status())) {
      throw new ComplianceShellValidationError(
          "Fair lending alert review validation failed.", List.of("ALERT_STATUS_CONFLICT"));
    }
    return new FairLendingAlert(
        alert.alertId(),
        alert.tenantId(),
        alert.snapshotId(),
        alert.metricCode(),
        alert.severity(),
        ALERT_UNDER_REVIEW,
        assignedTo == null || assignedTo.isBlank() ? reviewerId : assignedTo,
        "UNDER_REVIEW",
        comments.trim(),
        List.of(ALERT_REVIEWED_EVENT_TYPE),
        alert.correlationId());
  }

  public static FairLendingAlert resolveAlert(
      FairLendingAlert alert, String reviewerId, String disposition, String comments) {
    validateAlertAction(alert, reviewerId, comments);
    if (!ALERT_UNDER_REVIEW.equals(alert.status())) {
      throw new ComplianceShellValidationError(
          "Fair lending alert resolve validation failed.", List.of("ALERT_STATUS_CONFLICT"));
    }
    if (disposition == null || disposition.isBlank()) {
      throw new ComplianceShellValidationError(
          "Fair lending alert resolve validation failed.", List.of("disposition must be provided"));
    }
    return new FairLendingAlert(
        alert.alertId(),
        alert.tenantId(),
        alert.snapshotId(),
        alert.metricCode(),
        alert.severity(),
        ALERT_RESOLVED,
        alert.assignedTo(),
        disposition.trim(),
        comments.trim(),
        List.of(ALERT_RESOLVED_EVENT_TYPE),
        alert.correlationId());
  }

  private static FairLendingSnapshotResult failClosed(
      FairLendingSnapshotRequest request, List<String> reasonCodes) {
    String resultHash = resultHash(request, null, List.of(), List.of(), BLOCKED_MISSING_CONFIG, reasonCodes);
    return new FairLendingSnapshotResult(
        request.snapshotId(),
        request.tenantId(),
        BLOCKED_MISSING_CONFIG,
        null,
        request.periodStart(),
        request.periodEnd(),
        request.sourceCompleteness() == null ? 0 : request.sourceCompleteness().sourceEventCount(),
        request.sourceCompleteness() == null ? BigDecimal.ZERO : request.sourceCompleteness().completenessScore(),
        List.of(),
        List.of(),
        List.of(),
        reasonCodes,
        resultHash,
        "fair-lending-monitoring-audit:" + resultHash,
        List.of(SNAPSHOT_FAILED_CLOSED_EVENT_TYPE),
        request.correlationId());
  }

  private static List<FairLendingMonitorConfigVersion> effectiveConfigs(
      FairLendingSnapshotRequest request) {
    return request.configVersions().stream()
        .filter(config -> config != null)
        .filter(config -> same(config.tenantId(), request.tenantId()))
        .filter(config -> config.isEffectiveFor(request.periodEnd()))
        .filter(config -> config.matches(request))
        .sorted(Comparator.comparing(FairLendingMonitorConfigVersion::configVersionId))
        .toList();
  }

  private static List<String> configDefects(FairLendingMonitorConfigVersion config) {
    List<String> defects = new ArrayList<>();
    if (!"APPROVED".equalsIgnoreCase(config.status())) {
      defects.add("MONITOR_CONFIG_NOT_APPROVED:" + config.configVersionId());
    }
    if (config.protectedClassPolicyRef() == null || config.protectedClassPolicyRef().isBlank()) {
      defects.add("PROTECTED_CLASS_POLICY_MISSING");
    }
    if (config.metricDefinitions().isEmpty()) {
      defects.add("MISSING_METRIC_DEFINITIONS");
    }
    Set<String> seen = new LinkedHashSet<>();
    Set<String> duplicate = new LinkedHashSet<>();
    for (MetricDefinition definition : config.metricDefinitions()) {
      if (!seen.add(normalize(definition.metricCode()))) {
        duplicate.add(definition.metricCode());
      }
      if (definition.formulaRef() == null || definition.formulaRef().isBlank()) {
        defects.add("METRIC_FORMULA_REF_MISSING:" + definition.metricCode());
      }
      if (definition.thresholdRef() == null || definition.thresholdRef().isBlank()) {
        defects.add("METRIC_THRESHOLD_REF_MISSING:" + definition.metricCode());
      }
      if (definition.thresholdValue() == null) {
        defects.add("METRIC_THRESHOLD_VALUE_MISSING:" + definition.metricCode());
      }
      if (definition.minimumPopulation() <= 0) {
        defects.add("METRIC_MINIMUM_POPULATION_MISSING:" + definition.metricCode());
      }
      if (definition.alertSeverity() == null || definition.alertSeverity().isBlank()) {
        defects.add("METRIC_ALERT_SEVERITY_MISSING:" + definition.metricCode());
      }
    }
    defects.addAll(duplicate.stream().map(metric -> "AMBIGUOUS_METRIC_DEFINITION:" + metric).toList());
    return List.copyOf(defects);
  }

  private static List<MetricDefinition> sortedDefinitions(List<MetricDefinition> definitions) {
    return definitions.stream()
        .sorted(Comparator.comparing(definition -> normalize(definition.metricCode())))
        .toList();
  }

  private static OutcomeMeasure outcomeFor(List<OutcomeMeasure> outcomes, String metricCode) {
    String normalized = normalize(metricCode);
    return outcomes.stream()
        .filter(outcome -> normalize(outcome.metricCode()).equals(normalized))
        .findFirst()
        .orElse(null);
  }

  private static boolean compare(BigDecimal value, String operator, BigDecimal threshold) {
    int comparison = value.compareTo(threshold);
    return switch (operator) {
      case ">" -> comparison > 0;
      case ">=" -> comparison >= 0;
      case "<" -> comparison < 0;
      case "<=" -> comparison <= 0;
      case "=" -> comparison == 0;
      default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
    };
  }

  private static void validateSnapshotRequest(FairLendingSnapshotRequest request) {
    if (request == null) {
      throw new ComplianceShellValidationError(
          "Fair lending snapshot request must be an object.", List.of("request"));
    }
    List<String> details = new ArrayList<>();
    requireNonBlank(request.tenantId(), "tenantId", details);
    requireNonBlank(request.snapshotId(), "snapshotId", details);
    requireNonBlank(request.actorId(), "actorId", details);
    requireNonBlank(request.productType(), "productType", details);
    requireNonBlank(request.channel(), "channel", details);
    requireNonBlank(request.geography(), "geography", details);
    requireNonBlank(request.idempotencyKey(), "idempotencyKey", details);
    requireNonBlank(request.correlationId(), "correlationId", details);
    if (request.periodStart() == null) {
      details.add("periodStart must be provided");
    }
    if (request.periodEnd() == null) {
      details.add("periodEnd must be provided");
    }
    if (request.periodStart() != null
        && request.periodEnd() != null
        && request.periodEnd().isBefore(request.periodStart())) {
      details.add("periodEnd must be on or after periodStart");
    }
    if (request.sourceCompleteness() == null) {
      details.add("sourceCompleteness must be provided");
    }
    if (request.outcomeMeasures() == null) {
      details.add("outcomeMeasures must be provided");
    }
    if (request.configVersions() == null) {
      details.add("configVersions must be provided");
    }
    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError(
          "Fair lending snapshot request validation failed.", details);
    }
  }

  private static void validateAlertAction(FairLendingAlert alert, String reviewerId, String comments) {
    List<String> details = new ArrayList<>();
    if (alert == null) {
      details.add("alert must be provided");
    }
    requireNonBlank(reviewerId, "reviewerId", details);
    requireNonBlank(comments, "comments", details);
    if (!details.isEmpty()) {
      throw new ComplianceShellValidationError("Fair lending alert validation failed.", details);
    }
  }

  private static String resultHash(
      FairLendingSnapshotRequest request,
      FairLendingMonitorConfigVersion config,
      List<FairLendingMetricResult> metricResults,
      List<FairLendingLedgerEntry> ledger,
      String status,
      List<String> reasonCodes) {
    String material =
        request.tenantId()
            + "|"
            + request.snapshotId()
            + "|"
            + request.periodStart()
            + "|"
            + request.periodEnd()
            + "|"
            + request.productType()
            + "|"
            + request.channel()
            + "|"
            + request.geography()
            + "|"
            + status
            + "|"
            + (config == null ? "" : config.hashMaterial())
            + "|"
            + metricResults.stream().map(FairLendingMetricResult::hashMaterial).reduce("", (a, b) -> a + b)
            + "|"
            + ledger.stream().map(FairLendingLedgerEntry::hashMaterial).reduce("", (a, b) -> a + b)
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
      throw new IllegalStateException("SHA-256 digest is required for fair lending replay", exception);
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

  public record FairLendingSnapshotRequest(
      String tenantId,
      String snapshotId,
      String actorId,
      LocalDate periodStart,
      LocalDate periodEnd,
      String productType,
      String channel,
      String geography,
      boolean protectedClassDetailAuthorized,
      boolean protectedClassDetailsRequested,
      SourceCompleteness sourceCompleteness,
      List<OutcomeMeasure> outcomeMeasures,
      List<FairLendingMonitorConfigVersion> configVersions,
      String idempotencyKey,
      String correlationId) {
    public FairLendingSnapshotRequest {
      outcomeMeasures = outcomeMeasures == null ? null : List.copyOf(outcomeMeasures);
      configVersions = configVersions == null ? null : List.copyOf(configVersions);
    }
  }

  public record SourceCompleteness(
      int sourceEventCount,
      BigDecimal completenessScore,
      String completenessRuleRef,
      List<String> sourceRefs) {
    public SourceCompleteness {
      completenessScore = completenessScore == null ? BigDecimal.ZERO : completenessScore;
      sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
    }

    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sourceEventCount),
          completenessScore.toPlainString(),
          completenessRuleRef == null ? "" : completenessRuleRef,
          String.join(",", sourceRefs));
    }
  }

  public record OutcomeMeasure(
      String metricCode,
      String peerGroupKey,
      String comparisonGroupKey,
      BigDecimal value,
      int populationCount,
      List<String> supportingRefs,
      List<String> protectedClassKeys) {
    public OutcomeMeasure {
      metricCode = Objects.requireNonNull(metricCode, "metricCode must be provided");
      value = Objects.requireNonNull(value, "value must be provided");
      supportingRefs = supportingRefs == null ? List.of() : List.copyOf(supportingRefs);
      protectedClassKeys = protectedClassKeys == null ? List.of() : List.copyOf(protectedClassKeys);
    }
  }

  public record FairLendingConfigVersionRef(
      String configVersionId,
      String protectedClassPolicyRef,
      List<String> metricCodes,
      String configHash) {
    public FairLendingConfigVersionRef {
      metricCodes = metricCodes == null ? List.of() : List.copyOf(metricCodes);
    }
  }

  public record FairLendingMonitorConfigVersion(
      String tenantId,
      String configVersionId,
      int version,
      String status,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String productType,
      String channel,
      String geography,
      String protectedClassPolicyRef,
      List<MetricDefinition> metricDefinitions,
      String configHash) {
    public FairLendingMonitorConfigVersion {
      if (effectiveFrom == null) {
        throw new IllegalArgumentException("effectiveFrom must be provided");
      }
      metricDefinitions = metricDefinitions == null ? List.of() : List.copyOf(metricDefinitions);
    }

    boolean isEffectiveFor(LocalDate date) {
      return date != null && !date.isBefore(effectiveFrom) && !date.isAfter(effectiveToOrMax());
    }

    boolean matches(FairLendingSnapshotRequest request) {
      return fieldMatches(productType, request.productType())
          && fieldMatches(channel, request.channel())
          && fieldMatches(geography, request.geography());
    }

    FairLendingConfigVersionRef configVersionRef() {
      return new FairLendingConfigVersionRef(
          configVersionId,
          protectedClassPolicyRef,
          metricDefinitions.stream().map(MetricDefinition::metricCode).sorted().toList(),
          configHash);
    }

    String hashMaterial() {
      return String.join(
          "|",
          configVersionId,
          String.valueOf(version),
          status == null ? "" : status,
          protectedClassPolicyRef == null ? "" : protectedClassPolicyRef,
          metricDefinitions.stream().map(MetricDefinition::hashMaterial).reduce("", (a, b) -> a + b),
          configHash == null ? "" : configHash);
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

  public record MetricDefinition(
      String metricCode,
      String formulaRef,
      String thresholdRef,
      String comparisonOperator,
      BigDecimal thresholdValue,
      int minimumPopulation,
      int scale,
      RoundingMode roundingMode,
      String alertSeverity,
      String reasonCode) {
    public MetricDefinition {
      metricCode = Objects.requireNonNull(metricCode, "metricCode must be provided");
      comparisonOperator = comparisonOperator == null ? ">=" : comparisonOperator;
      roundingMode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
    }

    String hashMaterial() {
      return String.join(
          "|",
          metricCode,
          formulaRef == null ? "" : formulaRef,
          thresholdRef == null ? "" : thresholdRef,
          comparisonOperator,
          thresholdValue == null ? "" : thresholdValue.toPlainString(),
          String.valueOf(minimumPopulation),
          String.valueOf(scale),
          roundingMode.name(),
          alertSeverity == null ? "" : alertSeverity,
          reasonCode == null ? "" : reasonCode);
    }
  }

  public record FairLendingMetricResult(
      String metricCode,
      String peerGroupKey,
      String comparisonGroupKey,
      BigDecimal value,
      String thresholdRef,
      String severity,
      String reasonCode,
      int populationCount,
      String formulaRef,
      boolean protectedClassRedacted,
      List<String> supportingRefs) {
    public FairLendingMetricResult {
      supportingRefs = supportingRefs == null ? List.of() : List.copyOf(supportingRefs);
    }

    String hashMaterial() {
      return String.join(
          "|",
          metricCode,
          peerGroupKey == null ? "" : peerGroupKey,
          comparisonGroupKey == null ? "" : comparisonGroupKey,
          value.toPlainString(),
          thresholdRef,
          severity,
          reasonCode,
          String.valueOf(populationCount),
          formulaRef,
          String.valueOf(protectedClassRedacted),
          String.join(",", supportingRefs));
    }
  }

  public record FairLendingLedgerEntry(
      int sequence,
      String metricCode,
      String formulaRef,
      String thresholdRef,
      int sourceEventCount,
      String completenessRuleRef,
      BigDecimal roundedValue,
      String comparisonOperator,
      String outcome) {
    String hashMaterial() {
      return String.join(
          "|",
          String.valueOf(sequence),
          metricCode,
          formulaRef,
          thresholdRef,
          String.valueOf(sourceEventCount),
          completenessRuleRef,
          roundedValue.toPlainString(),
          comparisonOperator,
          outcome);
    }
  }

  public record FairLendingAlert(
      String alertId,
      String tenantId,
      String snapshotId,
      String metricCode,
      String severity,
      String status,
      String assignedTo,
      String disposition,
      String reviewComments,
      List<String> outboxEventTypes,
      String correlationId) {
    public FairLendingAlert {
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }

  public record FairLendingSnapshotResult(
      String snapshotId,
      String tenantId,
      String status,
      FairLendingConfigVersionRef configVersionRef,
      LocalDate periodStart,
      LocalDate periodEnd,
      int populationCount,
      BigDecimal dataCompletenessScore,
      List<FairLendingMetricResult> metricResults,
      List<FairLendingAlert> alerts,
      List<FairLendingLedgerEntry> ledger,
      List<String> reasonCodes,
      String resultHash,
      String auditRef,
      List<String> outboxEventTypes,
      String correlationId) {
    public FairLendingSnapshotResult {
      dataCompletenessScore = dataCompletenessScore == null ? BigDecimal.ZERO : dataCompletenessScore;
      metricResults = metricResults == null ? List.of() : List.copyOf(metricResults);
      alerts = alerts == null ? List.of() : List.copyOf(alerts);
      ledger = ledger == null ? List.of() : List.copyOf(ledger);
      reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
      outboxEventTypes = outboxEventTypes == null ? List.of() : List.copyOf(outboxEventTypes);
    }
  }
}

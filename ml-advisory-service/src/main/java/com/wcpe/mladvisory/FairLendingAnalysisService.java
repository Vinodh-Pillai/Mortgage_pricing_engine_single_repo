package com.wcpe.mladvisory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FairLendingAnalysisService {
  public static final String PRICING_OUTCOME_RECORDED_EVENT = "PricingOutcomeRecorded.v1";
  public static final String ANALYSIS_COMPLETED_EVENT = "FairLendingAnalysisCompleted.v1";
  public static final String VIOLATION_DETECTED_EVENT = "FairLendingViolationDetected.v1";
  public static final double FOUR_FIFTHS_THRESHOLD = 0.80d;
  public static final double REGRESSION_P_VALUE_THRESHOLD = 0.05d;
  public static final int MINIMUM_GROUP_SAMPLE = 30;

  private final FairLendingOutcomeRepository repository;
  private final Map<UUID, FairLendingReport> reports = new ConcurrentHashMap<>();
  private final List<FairLendingEvent> outboxEvents = new ArrayList<>();

  public FairLendingAnalysisService() {
    this(new InMemoryFairLendingOutcomeRepository());
  }

  public FairLendingAnalysisService(FairLendingOutcomeRepository repository) {
    this.repository = Objects.requireNonNull(repository);
  }

  public PricingOutcome recordPricingOutcome(PricingOutcome outcome) {
    PricingOutcome safe = requireOutcome(outcome);
    repository.save(safe);
    outboxEvents.add(new FairLendingEvent(PRICING_OUTCOME_RECORDED_EVENT, safe.tenantId(), safe.outcomeId().toString(), safe.createdAt()));
    return safe;
  }

  public PricingOutcome recordPricingOutcome(PricingOutcomeRecordedEvent event) {
    Objects.requireNonNull(event, "event is required");
    PricingOutcome outcome = new PricingOutcome(
        event.outcomeId() == null ? UUID.randomUUID() : event.outcomeId(),
        requiredUuid(event.tenantId(), "tenantId"),
        event.runId() == null ? UUID.randomUUID() : event.runId(),
        event.quoteId(),
        event.scenarioId(),
        normalizeGroup(event.applicantRace()),
        normalizeGroup(event.applicantEthnicity()),
        normalizeGroup(event.applicantSex()),
        event.applicantAge(),
        normalizeGroup(event.coApplicantRace()),
        normalizeGroup(event.coApplicantEthnicity()),
        normalizeGroup(event.coApplicantSex()),
        event.fico(),
        event.ltv(),
        event.dti(),
        event.loanAmount(),
        normalizeGroup(event.loanPurpose()),
        normalizeGroup(event.propertyType()),
        normalizeGroup(event.occupancyType()),
        normalizeGroup(event.state()),
        normalizeGroup(event.channel()),
        normalizeGroup(event.productFamily()),
        normalizeGroup(event.investor()),
        event.noteRate(),
        event.price(),
        event.totalLlpaBps(),
        event.marginBps(),
        event.lockPeriodDays(),
        event.pricingDate() == null ? Instant.now() : event.pricingDate(),
        Instant.now());
    return recordPricingOutcome(outcome);
  }

  public FairLendingReport analyze(FairLendingAnalysisRequest request) {
    FairLendingAnalysisRequest safe = request == null ? FairLendingAnalysisRequest.defaults() : request.withDefaults();
    List<PricingOutcome> periodOutcomes = repository.findByTenantAndDateRange(safe.tenantId(), safe.startDate(), safe.endDate());
    List<RegressionResult> regressions = new ArrayList<>();
    List<AIRTable> airTables = new ArrayList<>();
    List<FairLendingViolation> violations = new ArrayList<>();
    List<String> dataQuality = new ArrayList<>();

    for (OutcomeMeasure outcome : safe.outcomes()) {
      double favorableThreshold = favorableThreshold(periodOutcomes, outcome);
      for (ProtectedClass protectedClass : safe.protectedClasses()) {
        AIRTable air = calculateAirTable(periodOutcomes, outcome, protectedClass, favorableThreshold);
        airTables.add(air);
        RegressionResult regression = runProtectedClassRegression(periodOutcomes, outcome, protectedClass, safe.controls());
        regressions.add(regression);
        violations.addAll(detectViolations(outcome, protectedClass, air, regression, safe.marginalEffectThreshold()));
        dataQuality.addAll(air.dataQualityFlags());
        dataQuality.addAll(regression.warnings());
      }
    }

    UUID reportId = UUID.nameUUIDFromBytes((safe.tenantId() + ":" + safe.startDate() + ":" + safe.endDate() + ":" + regressions.hashCode() + ":" + airTables.hashCode()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    FairLendingReport report = new FairLendingReport(reportId, safe.tenantId(), safe.startDate(), safe.endDate(), periodOutcomes.size(), regressions, airTables, violations, recommendations(violations, dataQuality), distinct(dataQuality), Instant.now());
    reports.put(reportId, report);
    outboxEvents.add(new FairLendingEvent(ANALYSIS_COMPLETED_EVENT, safe.tenantId(), reportId.toString(), report.createdAt()));
    for (FairLendingViolation violation : violations) {
      outboxEvents.add(new FairLendingEvent(VIOLATION_DETECTED_EVENT, safe.tenantId(), reportId + ":" + violation.outcome() + ":" + violation.protectedClass() + ":" + violation.group(), report.createdAt()));
    }
    return report;
  }

  public Optional<FairLendingReport> report(UUID reportId) {
    return Optional.ofNullable(reports.get(reportId));
  }

  public List<FairLendingViolation> violations(String tenantId) {
    return reports.values().stream()
        .filter(report -> tenantId == null || report.tenantId().toString().equals(tenantId))
        .flatMap(report -> report.violations().stream())
        .sorted(Comparator.comparing(FairLendingViolation::severity).thenComparing(FairLendingViolation::outcome))
        .toList();
  }

  public List<FairLendingEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public static AIRTable calculateAirTable(List<PricingOutcome> outcomes, OutcomeMeasure outcome, ProtectedClass protectedClass, double favorableThreshold) {
    Map<String, Integer> favorableCounts = new LinkedHashMap<>();
    Map<String, Integer> totalCounts = new LinkedHashMap<>();
    List<String> quality = new ArrayList<>();
    for (PricingOutcome row : outcomes) {
      Optional<String> group = groupValue(row, protectedClass);
      Optional<Double> outcomeValue = outcomeValue(row, outcome);
      if (group.isEmpty() || outcomeValue.isEmpty()) {
        quality.add("missing protected class or outcome excluded for " + protectedClass.name() + "/" + outcome.name());
        continue;
      }
      totalCounts.merge(group.get(), 1, Integer::sum);
      if (isFavorable(outcome, outcomeValue.get(), favorableThreshold)) favorableCounts.merge(group.get(), 1, Integer::sum);
      else favorableCounts.putIfAbsent(group.get(), 0);
    }
    String reference = referenceGroup(protectedClass, totalCounts);
    double referenceRate = selectionRate(favorableCounts.getOrDefault(reference, 0), totalCounts.getOrDefault(reference, 0));
    Map<String, Double> airRatios = new LinkedHashMap<>();
    boolean violation = false;
    for (String group : totalCounts.keySet()) {
      double rate = selectionRate(favorableCounts.getOrDefault(group, 0), totalCounts.getOrDefault(group, 0));
      double air = referenceRate == 0.0d ? 0.0d : rate / referenceRate;
      airRatios.put(group, air);
      if (!group.equals(reference) && totalCounts.getOrDefault(group, 0) >= MINIMUM_GROUP_SAMPLE && air < FOUR_FIFTHS_THRESHOLD) violation = true;
      if (totalCounts.getOrDefault(group, 0) < MINIMUM_GROUP_SAMPLE) quality.add("insufficient sample for " + protectedClass.name() + "/" + group);
    }
    return new AIRTable(outcome.name(), protectedClass.name(), airRatios, favorableCounts, totalCounts, reference, violation, distinct(quality));
  }

  RegressionResult runProtectedClassRegression(List<PricingOutcome> rows, OutcomeMeasure outcome, ProtectedClass protectedClass, List<ControlVariable> controls) {
    List<PricingOutcome> usable = rows.stream()
        .filter(row -> outcomeValue(row, outcome).isPresent() && groupValue(row, protectedClass).isPresent())
        .toList();
    Map<String, Double> coefficients = new LinkedHashMap<>();
    Map<String, Double> pValues = new LinkedHashMap<>();
    Map<String, String> confidenceIntervals = new LinkedHashMap<>();
    List<String> warnings = new ArrayList<>();
    if (usable.size() < MINIMUM_GROUP_SAMPLE) {
      warnings.add("insufficient sample for regression " + protectedClass.name() + "/" + outcome.name());
      return new RegressionResult(outcome.name(), protectedClass.name(), coefficients, pValues, confidenceIntervals, 0.0d, usable.size(), false, warnings);
    }

    Set<String> groups = new HashSet<>();
    usable.forEach(row -> groupValue(row, protectedClass).ifPresent(groups::add));
    String reference = referenceGroup(protectedClass, groups.stream().collect(java.util.stream.Collectors.toMap(group -> group, group -> 1, Integer::sum)));
    for (String group : groups.stream().filter(group -> !group.equals(reference)).sorted().toList()) {
      List<PricingOutcome> groupedRows = usable.stream().filter(row -> reference.equals(groupValue(row, protectedClass).orElse("")) || group.equals(groupValue(row, protectedClass).orElse(""))).toList();
      if (groupedRows.size() < MINIMUM_GROUP_SAMPLE) {
        warnings.add("insufficient sample for regression group " + group);
        continue;
      }
      OlsResult result = ols(groupedRows, outcome, protectedClass, group, controls, warnings);
      coefficients.put(group, result.coefficient());
      pValues.put(group, result.pValue());
      confidenceIntervals.put(group, "[%.6f, %.6f]".formatted(result.lower95(), result.upper95()));
    }
    boolean significant = pValues.values().stream().anyMatch(value -> value < REGRESSION_P_VALUE_THRESHOLD);
    double rSquared = pValues.isEmpty() ? 0.0d : ols(usable, outcome, protectedClass, groups.stream().filter(group -> !group.equals(reference)).sorted().findFirst().orElse(reference), controls, warnings).rSquared();
    return new RegressionResult(outcome.name(), protectedClass.name(), coefficients, pValues, confidenceIntervals, rSquared, usable.size(), significant, distinct(warnings));
  }

  private static OlsResult ols(List<PricingOutcome> rows, OutcomeMeasure outcome, ProtectedClass protectedClass, String targetGroup, List<ControlVariable> controls, List<String> warnings) {
    List<ControlVariable> numericControls = controls == null ? List.of() : controls.stream().filter(ControlVariable::numeric).toList();
    int n = rows.size();
    int p = 2 + numericControls.size();
    if (n <= p) {
      warnings.add("insufficient degrees of freedom after controls");
      return new OlsResult(0.0d, 1.0d, 0.0d, 0.0d, 0.0d);
    }
    double[][] x = new double[n][p];
    double[][] xtx = new double[p][p];
    double[] xty = new double[p];
    double[] y = new double[n];
    for (int i = 0; i < n; i++) {
      PricingOutcome row = rows.get(i);
      y[i] = outcomeValue(row, outcome).orElse(0.0d);
      x[i][0] = 1.0d;
      x[i][1] = targetGroup.equals(groupValue(row, protectedClass).orElse("")) ? 1.0d : 0.0d;
      for (int j = 0; j < numericControls.size(); j++) x[i][j + 2] = numericControl(row, numericControls.get(j));
      for (int a = 0; a < p; a++) {
        xty[a] += x[i][a] * y[i];
        for (int b = 0; b < p; b++) xtx[a][b] += x[i][a] * x[i][b];
      }
    }
    double[][] inv = invert(xtx);
    if (inv == null) {
      warnings.add("perfect collinearity detected; protected-class regression skipped");
      return new OlsResult(0.0d, 1.0d, 0.0d, 0.0d, 0.0d);
    }
    double[] beta = multiply(inv, xty);
    double mean = java.util.Arrays.stream(y).average().orElse(0.0d);
    double sse = 0.0d;
    double sst = 0.0d;
    for (int i = 0; i < n; i++) {
      double fitted = 0.0d;
      for (int j = 0; j < p; j++) fitted += x[i][j] * beta[j];
      sse += Math.pow(y[i] - fitted, 2);
      sst += Math.pow(y[i] - mean, 2);
    }
    double sigma2 = sse / Math.max(1, n - p);
    double se = Math.sqrt(Math.max(0.0d, sigma2 * inv[1][1]));
    double t = se == 0.0d ? 0.0d : Math.abs(beta[1] / se);
    double pValue = 2.0d * (1.0d - normalCdf(t));
    double delta = 1.96d * se;
    double r2 = sst == 0.0d ? 0.0d : Math.max(0.0d, Math.min(1.0d, 1.0d - (sse / sst)));
    return new OlsResult(beta[1], pValue, beta[1] - delta, beta[1] + delta, r2);
  }

  private static List<FairLendingViolation> detectViolations(OutcomeMeasure outcome, ProtectedClass protectedClass, AIRTable air, RegressionResult regression, Double marginalThreshold) {
    List<FairLendingViolation> violations = new ArrayList<>();
    air.airRatios().forEach((group, ratio) -> {
      int sample = air.totalCounts().getOrDefault(group, 0);
      if (!group.equals(air.referenceGroup()) && sample >= MINIMUM_GROUP_SAMPLE && ratio < FOUR_FIFTHS_THRESHOLD) {
        violations.add(new FairLendingViolation(outcome.name(), protectedClass.name(), group, "AIR_FOUR_FIFTHS", ratio, FOUR_FIFTHS_THRESHOLD, "CRITICAL", "Review pricing policy and controls for " + protectedClass.name() + " group " + group));
      }
    });
    regression.pValues().forEach((group, pValue) -> {
      if (pValue < REGRESSION_P_VALUE_THRESHOLD) {
        violations.add(new FairLendingViolation(outcome.name(), protectedClass.name(), group, "REGRESSION_SIGNIFICANT", pValue, REGRESSION_P_VALUE_THRESHOLD, "HIGH", "Review regression coefficient and documented controls for " + group));
      }
    });
    if (marginalThreshold != null) {
      regression.coefficients().forEach((group, coefficient) -> {
        if (Math.abs(coefficient) > marginalThreshold) {
          violations.add(new FairLendingViolation(outcome.name(), protectedClass.name(), group, "MARGINAL_EFFECT", coefficient, marginalThreshold, "MEDIUM", "Review marginal effect threshold evidence for " + group));
        }
      });
    }
    return violations;
  }

  private static double favorableThreshold(List<PricingOutcome> rows, OutcomeMeasure outcome) {
    List<Double> values = rows.stream().map(row -> outcomeValue(row, outcome)).filter(Optional::isPresent).map(Optional::orElseThrow).sorted().toList();
    if (values.isEmpty()) return 0.0d;
    return values.get(values.size() / 2);
  }

  private static boolean isFavorable(OutcomeMeasure outcome, double value, double threshold) {
    return outcome == OutcomeMeasure.PRICE ? value >= threshold : value <= threshold;
  }

  private static Optional<Double> outcomeValue(PricingOutcome row, OutcomeMeasure outcome) {
    return switch (outcome) {
      case NOTE_RATE -> optional(row.noteRate());
      case PRICE -> optional(row.price());
      case TOTAL_LLPA_BPS -> optional(row.totalLlpaBps());
      case MARGIN_BPS -> optional(row.marginBps());
    };
  }

  private static Optional<String> groupValue(PricingOutcome row, ProtectedClass protectedClass) {
    String value = switch (protectedClass) {
      case RACE -> row.applicantRace();
      case ETHNICITY -> row.applicantEthnicity();
      case SEX -> row.applicantSex();
      case AGE -> row.applicantAge() == null ? null : (row.applicantAge() >= 62 ? "AGE_62_PLUS" : "AGE_UNDER_62");
    };
    return value == null || value.isBlank() || "NA".equalsIgnoreCase(value) ? Optional.empty() : Optional.of(value);
  }

  private static String referenceGroup(ProtectedClass protectedClass, Map<String, Integer> counts) {
    String configured = switch (protectedClass) {
      case RACE -> "WHITE";
      case ETHNICITY -> "NON_HISPANIC";
      case SEX -> "MALE";
      case AGE -> "AGE_UNDER_62";
    };
    int total = counts.values().stream().mapToInt(Integer::intValue).sum();
    if (counts.getOrDefault(configured, 0) >= Math.ceil(total * 0.05d)) return configured;
    return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(configured);
  }

  private static double selectionRate(int favorable, int total) {
    return total == 0 ? 0.0d : ((double) favorable) / total;
  }

  private static double numericControl(PricingOutcome row, ControlVariable control) {
    return switch (control) {
      case FICO -> value(row.fico());
      case LTV -> value(row.ltv());
      case DTI -> value(row.dti());
      case LOAN_AMOUNT -> value(row.loanAmount()) / 100000.0d;
      case APPLICANT_AGE -> value(row.applicantAge());
      case APPLICANT_AGE_SQUARED -> Math.pow(value(row.applicantAge()), 2.0d) / 1000.0d;
      default -> 0.0d;
    };
  }

  private static double[][] invert(double[][] matrix) {
    int n = matrix.length;
    double[][] aug = new double[n][n * 2];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) aug[i][j] = matrix[i][j];
      aug[i][i + n] = 1.0d;
    }
    for (int i = 0; i < n; i++) {
      int pivot = i;
      for (int r = i + 1; r < n; r++) if (Math.abs(aug[r][i]) > Math.abs(aug[pivot][i])) pivot = r;
      if (Math.abs(aug[pivot][i]) < 1e-10d) return null;
      double[] tmp = aug[i]; aug[i] = aug[pivot]; aug[pivot] = tmp;
      double divisor = aug[i][i];
      for (int c = 0; c < n * 2; c++) aug[i][c] /= divisor;
      for (int r = 0; r < n; r++) {
        if (r == i) continue;
        double factor = aug[r][i];
        for (int c = 0; c < n * 2; c++) aug[r][c] -= factor * aug[i][c];
      }
    }
    double[][] inv = new double[n][n];
    for (int i = 0; i < n; i++) System.arraycopy(aug[i], n, inv[i], 0, n);
    return inv;
  }

  private static double[] multiply(double[][] matrix, double[] vector) {
    double[] result = new double[vector.length];
    for (int i = 0; i < matrix.length; i++) for (int j = 0; j < vector.length; j++) result[i] += matrix[i][j] * vector[j];
    return result;
  }

  private static double normalCdf(double x) {
    double t = 1.0d / (1.0d + 0.2316419d * Math.abs(x));
    double d = 0.3989423d * Math.exp(-x * x / 2.0d);
    double probability = d * t * (0.3193815d + t * (-0.3565638d + t * (1.781478d + t * (-1.821256d + t * 1.330274d))));
    return x > 0.0d ? 1.0d - probability : probability;
  }

  private static List<String> recommendations(List<FairLendingViolation> violations, List<String> dataQuality) {
    List<String> recommendations = new ArrayList<>();
    if (!violations.isEmpty()) recommendations.add("Review fair-lending policy, pricing controls, and documented business justifications for detected groups.");
    if (!dataQuality.isEmpty()) recommendations.add("Resolve protected-class data quality and sample-size gaps before regulatory export.");
    return recommendations.isEmpty() ? List.of("No fair-lending violations detected for the selected period.") : recommendations;
  }

  private static PricingOutcome requireOutcome(PricingOutcome outcome) {
    Objects.requireNonNull(outcome, "pricing outcome is required");
    Objects.requireNonNull(outcome.outcomeId(), "outcome_id is required");
    Objects.requireNonNull(outcome.tenantId(), "tenant_id is required");
    Objects.requireNonNull(outcome.runId(), "run_id is required");
    Objects.requireNonNull(outcome.pricingDate(), "pricing_date is required");
    return outcome;
  }

  private static UUID requiredUuid(String value, String field) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    return UUID.fromString(value);
  }

  private static String normalizeGroup(String value) {
    return value == null || value.isBlank() ? "NA" : value.trim().toUpperCase().replace('-', '_');
  }

  private static Optional<Double> optional(Number value) {
    return value == null ? Optional.empty() : Optional.of(value.doubleValue());
  }

  private static double value(Number value) {
    return value == null ? 0.0d : value.doubleValue();
  }

  private static List<String> distinct(List<String> values) {
    return values == null ? List.of() : values.stream().filter(Objects::nonNull).distinct().toList();
  }

  private record OlsResult(double coefficient, double pValue, double lower95, double upper95, double rSquared) {}

  public enum OutcomeMeasure { NOTE_RATE, PRICE, TOTAL_LLPA_BPS, MARGIN_BPS }
  public enum ProtectedClass { RACE, ETHNICITY, SEX, AGE }
  public enum ControlVariable {
    FICO(true), LTV(true), DTI(true), LOAN_AMOUNT(true), APPLICANT_AGE(true), APPLICANT_AGE_SQUARED(true),
    LOAN_PURPOSE(false), PROPERTY_TYPE(false), OCCUPANCY_TYPE(false), STATE(false), CHANNEL(false), PRODUCT_FAMILY(false), INVESTOR(false);
    private final boolean numeric;
    ControlVariable(boolean numeric) { this.numeric = numeric; }
    public boolean numeric() { return numeric; }
  }

  public record FairLendingAnalysisRequest(UUID tenantId, LocalDate startDate, LocalDate endDate, List<ProtectedClass> protectedClasses, List<OutcomeMeasure> outcomes, List<ControlVariable> controls, Double marginalEffectThreshold) {
    public FairLendingAnalysisRequest {
      Objects.requireNonNull(tenantId, "tenantId is required");
      startDate = startDate == null ? LocalDate.now(ZoneOffset.UTC).minusDays(30) : startDate;
      endDate = endDate == null ? LocalDate.now(ZoneOffset.UTC) : endDate;
      protectedClasses = protectedClasses == null || protectedClasses.isEmpty() ? List.of(ProtectedClass.RACE, ProtectedClass.ETHNICITY, ProtectedClass.SEX, ProtectedClass.AGE) : List.copyOf(protectedClasses);
      outcomes = outcomes == null || outcomes.isEmpty() ? List.of(OutcomeMeasure.NOTE_RATE, OutcomeMeasure.PRICE, OutcomeMeasure.TOTAL_LLPA_BPS, OutcomeMeasure.MARGIN_BPS) : List.copyOf(outcomes);
      controls = controls == null || controls.isEmpty() ? List.of(ControlVariable.FICO, ControlVariable.LTV, ControlVariable.DTI, ControlVariable.LOAN_AMOUNT, ControlVariable.APPLICANT_AGE, ControlVariable.APPLICANT_AGE_SQUARED) : List.copyOf(controls);
    }
    static FairLendingAnalysisRequest defaults() { return new FairLendingAnalysisRequest(UUID.fromString("00000000-0000-0000-0000-000000000000"), null, null, null, null, null, null); }
    FairLendingAnalysisRequest withDefaults() { return new FairLendingAnalysisRequest(tenantId, startDate, endDate, protectedClasses, outcomes, controls, marginalEffectThreshold); }
  }

  public record PricingOutcome(UUID outcomeId, UUID tenantId, UUID runId, UUID quoteId, UUID scenarioId, String applicantRace, String applicantEthnicity, String applicantSex, Integer applicantAge, String coApplicantRace, String coApplicantEthnicity, String coApplicantSex, Integer fico, Double ltv, Double dti, Double loanAmount, String loanPurpose, String propertyType, String occupancyType, String state, String channel, String productFamily, String investor, Double noteRate, Double price, Integer totalLlpaBps, Integer marginBps, Integer lockPeriodDays, Instant pricingDate, Instant createdAt) {}
  public record PricingOutcomeRecordedEvent(UUID outcomeId, String tenantId, UUID runId, UUID quoteId, UUID scenarioId, String applicantRace, String applicantEthnicity, String applicantSex, Integer applicantAge, String coApplicantRace, String coApplicantEthnicity, String coApplicantSex, Integer fico, Double ltv, Double dti, Double loanAmount, String loanPurpose, String propertyType, String occupancyType, String state, String channel, String productFamily, String investor, Double noteRate, Double price, Integer totalLlpaBps, Integer marginBps, Integer lockPeriodDays, Instant pricingDate) {}
  public record RegressionResult(String outcome, String protectedClass, Map<String, Double> coefficients, Map<String, Double> pValues, Map<String, String> confidenceIntervals, double rSquared, int sampleSize, boolean significantDisparity, List<String> warnings) { public RegressionResult { coefficients = Map.copyOf(coefficients); pValues = Map.copyOf(pValues); confidenceIntervals = Map.copyOf(confidenceIntervals); warnings = warnings == null ? List.of() : List.copyOf(warnings); } }
  public record AIRTable(String outcome, String protectedClass, Map<String, Double> airRatios, Map<String, Integer> favorableCounts, Map<String, Integer> totalCounts, String referenceGroup, boolean fourFifthsViolation, List<String> dataQualityFlags) { public AIRTable { airRatios = Map.copyOf(airRatios); favorableCounts = Map.copyOf(favorableCounts); totalCounts = Map.copyOf(totalCounts); dataQualityFlags = dataQualityFlags == null ? List.of() : List.copyOf(dataQualityFlags); } }
  public record FairLendingViolation(String outcome, String protectedClass, String group, String violationType, double value, double threshold, String severity, String recommendedAction) {}
  public record FairLendingReport(UUID reportId, UUID tenantId, LocalDate startDate, LocalDate endDate, int sampleSize, List<RegressionResult> regressionResults, List<AIRTable> airTables, List<FairLendingViolation> violations, List<String> recommendations, List<String> dataQualityFlags, Instant createdAt) { public FairLendingReport { regressionResults = List.copyOf(regressionResults); airTables = List.copyOf(airTables); violations = List.copyOf(violations); recommendations = List.copyOf(recommendations); dataQualityFlags = List.copyOf(dataQualityFlags); } }
  public record FairLendingEvent(String eventType, UUID tenantId, String aggregateId, Instant occurredAt) {}

  public interface FairLendingOutcomeRepository {
    void save(PricingOutcome outcome);
    List<PricingOutcome> findByTenantAndDateRange(UUID tenantId, LocalDate startDate, LocalDate endDate);
  }

  public static final class InMemoryFairLendingOutcomeRepository implements FairLendingOutcomeRepository {
    private final List<PricingOutcome> outcomes = new ArrayList<>();
    @Override public void save(PricingOutcome outcome) { outcomes.add(requireOutcome(outcome)); }
    @Override public List<PricingOutcome> findByTenantAndDateRange(UUID tenantId, LocalDate startDate, LocalDate endDate) {
      return outcomes.stream().filter(outcome -> outcome.tenantId().equals(tenantId)).filter(outcome -> {
        LocalDate date = outcome.pricingDate().atZone(ZoneOffset.UTC).toLocalDate();
        return (date.isEqual(startDate) || date.isAfter(startDate)) && (date.isEqual(endDate) || date.isBefore(endDate));
      }).toList();
    }
  }
}

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import javax.sql.DataSource;

public final class FairLendingAnalysisService {
  public static final String PRICING_OUTCOME_RECORDED_EVENT = "PricingOutcomeRecorded.v1";
  public static final String ANALYSIS_COMPLETED_EVENT = "FairLendingAnalysisCompleted.v1";
  public static final String VIOLATION_DETECTED_EVENT = "FairLendingViolationDetected.v1";
  public static final double FOUR_FIFTHS_THRESHOLD = 0.80d;
  public static final double REGRESSION_P_VALUE_THRESHOLD = 0.05d;
  public static final int MINIMUM_GROUP_SAMPLE = 30;

  private final FairLendingOutcomeRepository repository;
  private final FairLendingEventRepository eventRepository;
  private final boolean durableReportLookupAvailable;
  private final Map<UUID, FairLendingReport> reports = new HashMap<>();

  public FairLendingAnalysisService() {
    throw failClosedMissingPersistence();
  }

  public FairLendingAnalysisService(DataSource dataSource) {
    this(new JdbcFairLendingOutcomeRepository(dataSource), new JdbcFairLendingEventRepository(dataSource), false);
  }

  public FairLendingAnalysisService(FairLendingOutcomeRepository repository) {
    throw failClosedMissingPersistence();
  }

  FairLendingAnalysisService(FairLendingOutcomeRepository repository, FairLendingEventRepository eventRepository) {
    this(repository, eventRepository, true);
  }

  FairLendingAnalysisService(FairLendingOutcomeRepository repository, FairLendingEventRepository eventRepository, boolean durableReportLookupAvailable) {
    this.repository = Objects.requireNonNull(repository);
    this.eventRepository = Objects.requireNonNull(eventRepository);
    this.durableReportLookupAvailable = durableReportLookupAvailable;
  }

  public PricingOutcome recordPricingOutcome(PricingOutcome outcome) {
    PricingOutcome safe = requireOutcome(outcome);
    repository.save(safe);
    eventRepository.save(new FairLendingEvent(PRICING_OUTCOME_RECORDED_EVENT, safe.tenantId(), safe.outcomeId().toString(), safe.createdAt()));
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
    if (durableReportLookupAvailable) reports.put(reportId, report);
    eventRepository.save(new FairLendingEvent(ANALYSIS_COMPLETED_EVENT, safe.tenantId(), reportId.toString(), report.createdAt()));
    for (FairLendingViolation violation : violations) {
      eventRepository.save(new FairLendingEvent(VIOLATION_DETECTED_EVENT, safe.tenantId(), reportId + ":" + violation.outcome() + ":" + violation.protectedClass() + ":" + violation.group(), report.createdAt()));
    }
    return report;
  }

  public Optional<FairLendingReport> report(UUID reportId) {
    if (!durableReportLookupAvailable) {
      throw new IllegalStateException("Fair-lending report persistence schema is not defined; durable report lookup is disabled rather than using memory fallback.");
    }
    return Optional.ofNullable(reports.get(reportId));
  }

  public List<FairLendingViolation> violations(String tenantId) {
    if (!durableReportLookupAvailable) {
      throw new IllegalStateException("Fair-lending violation report persistence schema is not defined; durable violation lookup is disabled rather than using memory fallback.");
    }
    return reports.values().stream()
        .filter(report -> tenantId == null || report.tenantId().toString().equals(tenantId))
        .flatMap(report -> report.violations().stream())
        .sorted(Comparator.comparing(FairLendingViolation::severity).thenComparing(FairLendingViolation::outcome))
        .toList();
  }

  public List<FairLendingEvent> outboxEvents() {
    return eventRepository.findAll();
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

  public interface FairLendingEventRepository {
    void save(FairLendingEvent event);
    List<FairLendingEvent> findAll();
  }

  public static final class JdbcFairLendingOutcomeRepository implements FairLendingOutcomeRepository {
    private final DataSource dataSource;

    public JdbcFairLendingOutcomeRepository(DataSource dataSource) {
      this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override public void save(PricingOutcome outcome) {
      PricingOutcome safe = requireOutcome(outcome);
      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "insert into fair_lending.pricing_outcome (outcome_id, tenant_id, run_id, quote_id, scenario_id, applicant_race, applicant_ethnicity, applicant_sex, applicant_age, co_applicant_race, co_applicant_ethnicity, co_applicant_sex, fico, ltv, dti, loan_amount, loan_purpose, property_type, occupancy_type, state, channel, product_family, investor, note_rate, price, total_llpa_bps, margin_bps, lock_period_days, pricing_date, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
        statement.setObject(1, safe.outcomeId());
        statement.setObject(2, safe.tenantId());
        statement.setObject(3, safe.runId());
        statement.setObject(4, safe.quoteId());
        statement.setObject(5, safe.scenarioId());
        statement.setString(6, safe.applicantRace());
        statement.setString(7, safe.applicantEthnicity());
        statement.setString(8, safe.applicantSex());
        statement.setObject(9, safe.applicantAge());
        statement.setString(10, safe.coApplicantRace());
        statement.setString(11, safe.coApplicantEthnicity());
        statement.setString(12, safe.coApplicantSex());
        statement.setObject(13, safe.fico());
        statement.setObject(14, safe.ltv());
        statement.setObject(15, safe.dti());
        statement.setObject(16, safe.loanAmount());
        statement.setString(17, safe.loanPurpose());
        statement.setString(18, safe.propertyType());
        statement.setString(19, safe.occupancyType());
        statement.setString(20, safe.state());
        statement.setString(21, safe.channel());
        statement.setString(22, safe.productFamily());
        statement.setString(23, safe.investor());
        statement.setObject(24, safe.noteRate());
        statement.setObject(25, safe.price());
        statement.setObject(26, safe.totalLlpaBps());
        statement.setObject(27, safe.marginBps());
        statement.setObject(28, safe.lockPeriodDays());
        statement.setTimestamp(29, Timestamp.from(safe.pricingDate()));
        statement.setTimestamp(30, Timestamp.from(safe.createdAt() == null ? Instant.now() : safe.createdAt()));
        statement.executeUpdate();
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to persist fair-lending pricing outcome", ex);
      }
    }

    @Override public List<PricingOutcome> findByTenantAndDateRange(UUID tenantId, LocalDate startDate, LocalDate endDate) {
      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "select * from fair_lending.pricing_outcome where tenant_id = ? and pricing_date >= ? and pricing_date < ? order by pricing_date asc, created_at asc")) {
        statement.setObject(1, tenantId);
        statement.setTimestamp(2, Timestamp.from(startDate.atStartOfDay().toInstant(ZoneOffset.UTC)));
        statement.setTimestamp(3, Timestamp.from(endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
        try (ResultSet resultSet = statement.executeQuery()) {
          List<PricingOutcome> outcomes = new ArrayList<>();
          while (resultSet.next()) outcomes.add(readOutcome(resultSet));
          return List.copyOf(outcomes);
        }
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to read fair-lending pricing outcomes", ex);
      }
    }

    private PricingOutcome readOutcome(ResultSet resultSet) throws SQLException {
      return new PricingOutcome(
          (UUID) resultSet.getObject("outcome_id"),
          (UUID) resultSet.getObject("tenant_id"),
          (UUID) resultSet.getObject("run_id"),
          (UUID) resultSet.getObject("quote_id"),
          (UUID) resultSet.getObject("scenario_id"),
          resultSet.getString("applicant_race"),
          resultSet.getString("applicant_ethnicity"),
          resultSet.getString("applicant_sex"),
          (Integer) resultSet.getObject("applicant_age"),
          resultSet.getString("co_applicant_race"),
          resultSet.getString("co_applicant_ethnicity"),
          resultSet.getString("co_applicant_sex"),
          (Integer) resultSet.getObject("fico"),
          doubleValue(resultSet, "ltv"),
          doubleValue(resultSet, "dti"),
          doubleValue(resultSet, "loan_amount"),
          resultSet.getString("loan_purpose"),
          resultSet.getString("property_type"),
          resultSet.getString("occupancy_type"),
          resultSet.getString("state"),
          resultSet.getString("channel"),
          resultSet.getString("product_family"),
          resultSet.getString("investor"),
          doubleValue(resultSet, "note_rate"),
          doubleValue(resultSet, "price"),
          (Integer) resultSet.getObject("total_llpa_bps"),
          (Integer) resultSet.getObject("margin_bps"),
          (Integer) resultSet.getObject("lock_period_days"),
          resultSet.getTimestamp("pricing_date").toInstant(),
          resultSet.getTimestamp("created_at").toInstant());
    }

    private Double doubleValue(ResultSet resultSet, String column) throws SQLException {
      Number value = (Number) resultSet.getObject(column);
      return value == null ? null : value.doubleValue();
    }
  }

  public static final class JdbcFairLendingEventRepository implements FairLendingEventRepository {
    private final DataSource dataSource;

    public JdbcFairLendingEventRepository(DataSource dataSource) {
      this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override public void save(FairLendingEvent event) {
      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement(
              "insert into fair_lending.event_outbox (event_id, event_type, tenant_id, aggregate_id, occurred_at) values (?, ?, ?, ?, ?) on conflict (event_id) do nothing")) {
        statement.setObject(1, UUID.nameUUIDFromBytes((event.eventType() + ":" + event.tenantId() + ":" + event.aggregateId() + ":" + event.occurredAt()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        statement.setString(2, event.eventType());
        statement.setObject(3, event.tenantId());
        statement.setString(4, event.aggregateId());
        statement.setTimestamp(5, Timestamp.from(event.occurredAt()));
        statement.executeUpdate();
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to persist fair-lending event outbox state", ex);
      }
    }

    @Override public List<FairLendingEvent> findAll() {
      try (Connection connection = dataSource.getConnection();
          PreparedStatement statement = connection.prepareStatement("select event_type, tenant_id, aggregate_id, occurred_at from fair_lending.event_outbox order by occurred_at asc")) {
        try (ResultSet resultSet = statement.executeQuery()) {
          List<FairLendingEvent> events = new ArrayList<>();
          while (resultSet.next()) {
            events.add(new FairLendingEvent(resultSet.getString("event_type"), (UUID) resultSet.getObject("tenant_id"), resultSet.getString("aggregate_id"), resultSet.getTimestamp("occurred_at").toInstant()));
          }
          return List.copyOf(events);
        }
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to read fair-lending event outbox state", ex);
      }
    }
  }

  private static IllegalStateException failClosedMissingPersistence() {
    return new IllegalStateException(
        "Fair-lending analysis persistence requires an explicit JDBC/PostgreSQL DataSource; in-memory production fallback is disabled.");
  }
}

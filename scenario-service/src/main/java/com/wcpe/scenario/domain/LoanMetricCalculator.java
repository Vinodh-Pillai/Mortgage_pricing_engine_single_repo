package com.wcpe.scenario.domain;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class LoanMetricCalculator {
  private static final String ROUNDING_RULE = "HALF_UP ratio scale 5, bps scale 4";
  private static final Set<Integer> ACTIVE_TERMS = Set.of(180, 240, 360);
  private static final Set<Integer> ACTIVE_LOCK_PERIODS = Set.of(15, 30, 45, 60);
  private static final Set<String> ACTIVE_PURPOSES = Set.of("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI");
  private static final Set<String> ACTIVE_LIEN_POSITIONS = Set.of("FIRST", "SECOND");
  private static final Set<String> ACTIVE_AMORTIZATION_TYPES = Set.of("FIXED", "ARM");

  LoanMetricResult calculate(LoanStructureRequest request) {
    List<ValidationIssue> issues = validate(request);
    BigDecimal denominator = request == null ? null : request.temporaryPropertyValueForLtv();
    BigDecimal loanAmount = request == null ? BigDecimal.ZERO : defaultZero(request.loanAmount());
    BigDecimal subordinate = request == null ? BigDecimal.ZERO : defaultZero(request.subordinateFinancingAmount());
    BigDecimal helocDrawn = request == null ? BigDecimal.ZERO : defaultZero(request.helocDrawnAmount());
    BigDecimal helocLimit = request == null ? BigDecimal.ZERO : defaultZero(request.helocLimitAmount());

    boolean blocked = issues.stream().anyMatch(issue -> issue.severity() == Severity.BLOCKING);
    List<LoanMetric> metrics = List.of(
        metric("LTV", loanAmount, denominator, blocked),
        metric("CLTV", loanAmount.add(subordinate).add(helocDrawn), denominator, blocked),
        metric("HCLTV", loanAmount.add(subordinate).add(helocLimit), denominator, blocked));
    String qualityStatus = blocked ? "BLOCKED" : issues.isEmpty() ? "COMPLETE" : "WARNING";
    return new LoanMetricResult(traceId(request, metrics, issues), qualityStatus, metrics, issues);
  }

  private List<ValidationIssue> validate(LoanStructureRequest request) {
    if (request == null) return List.of(issue("LOAN_STRUCTURE_REQUIRED", "loanStructure", Severity.BLOCKING, "Loan structure payload is required."));
    List<ValidationIssue> issues = new ArrayList<>();
    if (!ACTIVE_PURPOSES.contains(request.loanPurpose())) issues.add(issue("LOAN_PURPOSE_NOT_ENABLED", "loanPurpose", Severity.BLOCKING, "Loan purpose is not active."));
    if (nonPositive(request.loanAmount())) issues.add(issue("INVALID_LOAN_AMOUNT", "loanAmount", Severity.BLOCKING, "Loan amount must be positive."));
    if (!ACTIVE_LIEN_POSITIONS.contains(request.lienPosition())) issues.add(issue("LIEN_POSITION_NOT_ENABLED", "lienPosition", Severity.BLOCKING, "Lien position is not active."));
    if (!ACTIVE_TERMS.contains(request.termMonths())) issues.add(issue("TERM_NOT_ENABLED", "termMonths", Severity.BLOCKING, "Unsupported term."));
    if (!ACTIVE_AMORTIZATION_TYPES.contains(request.amortizationType())) issues.add(issue("AMORTIZATION_TYPE_NOT_ENABLED", "amortizationType", Severity.BLOCKING, "Amortization type is not active."));
    if (negative(request.subordinateFinancingAmount())) issues.add(issue("INVALID_SUBORDINATE_FINANCING", "subordinateFinancingAmount", Severity.BLOCKING, "Subordinate financing cannot be negative."));
    if (negative(request.helocDrawnAmount())) issues.add(issue("INVALID_HELOC_DRAWN", "helocDrawnAmount", Severity.BLOCKING, "HELOC drawn amount cannot be negative."));
    if (negative(request.helocLimitAmount())) issues.add(issue("INVALID_HELOC_LIMIT", "helocLimitAmount", Severity.BLOCKING, "HELOC limit amount cannot be negative."));
    if (defaultZero(request.helocLimitAmount()).compareTo(defaultZero(request.helocDrawnAmount())) < 0) issues.add(issue("INVALID_COMBINED_LTV", "helocLimitAmount", Severity.BLOCKING, "HELOC limit cannot be below drawn amount."));
    if (!ACTIVE_LOCK_PERIODS.contains(request.requestedLockPeriodDays())) issues.add(issue("LOCK_PERIOD_NOT_ENABLED", "requestedLockPeriodDays", Severity.BLOCKING, "Unsupported lock period."));
    if (nonPositive(request.temporaryPropertyValueForLtv())) issues.add(issue("MISSING_LTV_DENOMINATOR", "temporaryPropertyValueForLtv", Severity.BLOCKING, "Property value is required for ratios."));
    return issues;
  }

  private LoanMetric metric(String code, BigDecimal numerator, BigDecimal denominator, boolean blocked) {
    if (blocked || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
      return new LoanMetric(code, null, null, money(numerator), denominator == null ? null : money(denominator), ROUNDING_RULE, "BLOCKED");
    }
    BigDecimal ratio = numerator.divide(denominator, 10, RoundingMode.HALF_UP).setScale(5, RoundingMode.HALF_UP);
    BigDecimal bps = ratio.multiply(new BigDecimal("10000")).setScale(4, RoundingMode.HALF_UP);
    return new LoanMetric(code, ratio, bps, money(numerator), money(denominator), ROUNDING_RULE, "COMPLETE");
  }

  private UUID traceId(LoanStructureRequest request, List<LoanMetric> metrics, List<ValidationIssue> issues) {
    return UUID.nameUUIDFromBytes((String.valueOf(request) + metrics + issues).getBytes(StandardCharsets.UTF_8));
  }

  private static BigDecimal money(BigDecimal value) { return defaultZero(value).setScale(2, RoundingMode.HALF_UP); }
  private static BigDecimal defaultZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
  private static boolean nonPositive(BigDecimal value) { return value == null || value.compareTo(BigDecimal.ZERO) <= 0; }
  private static boolean negative(BigDecimal value) { return value != null && value.compareTo(BigDecimal.ZERO) < 0; }
  private static ValidationIssue issue(String code, String field, Severity severity, String message) { return new ValidationIssue(code, field, severity, message); }
}

package com.wcpe.eligibility.service;

import com.wcpe.eligibility.domain.models.EligibilityFailure;
import com.wcpe.eligibility.domain.models.InvestorEligibilityBatchEvaluationRequest;
import com.wcpe.eligibility.domain.models.InvestorEligibilityBatchEvaluationResult;
import com.wcpe.eligibility.domain.models.InvestorEligibilityEvaluationRequest;
import com.wcpe.eligibility.domain.models.InvestorEligibilityMatrixRow;
import com.wcpe.eligibility.domain.models.InvestorEligibilityResult;
import com.wcpe.eligibility.domain.models.LoanScenario;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class InvestorEligibilityEvaluationService {

    public InvestorEligibilityResult evaluate(UUID investorId, LoanScenario scenario, LocalDate quoteDate, List<InvestorEligibilityMatrixRow> rows) {
        return evaluate(new InvestorEligibilityEvaluationRequest(investorId, null, scenario, quoteDate, rows));
    }

    public InvestorEligibilityResult evaluate(InvestorEligibilityEvaluationRequest request) {
        LoanScenario scenario = request.scenario();
        LocalDate quoteDate = request.quoteDate() == null ? LocalDate.now() : request.quoteDate();
        List<InvestorEligibilityMatrixRow> activeRows = activeRows(request.matrixRows(), quoteDate);
        if (activeRows.isEmpty()) {
            return new InvestorEligibilityResult(true, stringId(request.investorId()), request.investorName(), List.of(), List.of("NO_ACTIVE_MATRIX_ROW"), Map.of());
        }

        List<InvestorEligibilityMatrixRow> candidates = activeRows.stream()
            .filter(row -> matches(row.loanPurpose(), scenario.loanPurpose()))
            .filter(row -> matches(row.propertyType(), scenario.propertyType()))
            .filter(row -> matches(row.occupancyType(), scenario.occupancyType()))
            .toList();

        if (candidates.isEmpty()) {
            return new InvestorEligibilityResult(false, stringId(request.investorId()), request.investorName(), dimensionMatchFailures(activeRows, scenario), List.of(), Map.of());
        }

        InvestorEligibilityMatrixRow effective = mostRestrictive(candidates);
        Thresholds thresholds = applyOverlays(effective, scenario);
        List<EligibilityFailure> failures = failures(scenario, thresholds);
        return new InvestorEligibilityResult(failures.isEmpty(), stringId(request.investorId()), request.investorName(), failures, List.of(), thresholds.toMap());
    }

    public InvestorEligibilityBatchEvaluationResult batchEvaluate(InvestorEligibilityBatchEvaluationRequest request) {
        List<InvestorEligibilityResult> results = request.investors().stream()
            .map(investor -> evaluate(new InvestorEligibilityEvaluationRequest(investor.investorId(), investor.investorName(), request.scenario(), request.quoteDate(), investor.matrixRows())))
            .toList();
        return new InvestorEligibilityBatchEvaluationResult(results);
    }

    private List<InvestorEligibilityMatrixRow> activeRows(List<InvestorEligibilityMatrixRow> rows, LocalDate quoteDate) {
        return (rows == null ? List.<InvestorEligibilityMatrixRow>of() : rows).stream()
            .filter(InvestorEligibilityMatrixRow::active)
            .filter(row -> row.effectiveDate() == null || !quoteDate.isBefore(row.effectiveDate()))
            .filter(row -> row.expirationDate() == null || !quoteDate.isAfter(row.expirationDate()))
            .toList();
    }

    private InvestorEligibilityMatrixRow mostRestrictive(List<InvestorEligibilityMatrixRow> rows) {
        Integer minFico = null;
        Integer maxFico = null;
        BigDecimal maxLtv = null;
        BigDecimal maxCltv = null;
        BigDecimal maxDti = null;
        BigDecimal minLoan = null;
        BigDecimal maxLoan = null;
        List<String> states = new ArrayList<>();
        List<String> counties = new ArrayList<>();
        Map<String, Object> overlays = new LinkedHashMap<>();
        InvestorEligibilityMatrixRow first = rows.get(0);
        for (InvestorEligibilityMatrixRow row : rows) {
            minFico = max(minFico, row.minFico());
            maxFico = min(maxFico, row.maxFico());
            maxLtv = min(maxLtv, row.maxLtv());
            maxCltv = min(maxCltv, row.maxCltv());
            maxDti = min(maxDti, row.maxDti());
            minLoan = max(minLoan, row.minLoanAmount());
            maxLoan = min(maxLoan, row.maxLoanAmount());
            if (row.allowedStates() != null && !row.allowedStates().isEmpty()) {
                if (states.isEmpty()) states.addAll(row.allowedStates()); else states.retainAll(row.allowedStates());
            }
            if (row.excludedCounties() != null) counties.addAll(row.excludedCounties());
            if (row.overlays() != null) overlays.putAll(row.overlays());
        }
        return new InvestorEligibilityMatrixRow(first.id(), first.loanPurpose(), first.propertyType(), first.occupancyType(), minFico, maxFico, maxLtv, maxCltv, maxDti, minLoan, maxLoan, List.copyOf(states), counties.stream().distinct().toList(), overlays, first.effectiveDate(), first.expirationDate(), true);
    }

    private Thresholds applyOverlays(InvestorEligibilityMatrixRow row, LoanScenario scenario) {
        Thresholds thresholds = new Thresholds(row.minFico(), row.maxFico(), row.maxLtv(), row.maxCltv(), row.maxDti(), row.minLoanAmount(), row.maxLoanAmount(), safeList(row.allowedStates()), safeList(row.excludedCounties()), List.of(), List.of(), List.of());
        Map<String, Object> overlays = row.overlays() == null ? Map.of() : row.overlays();
        thresholds.minFico = max(thresholds.minFico, asInt(overlays.get("minFico")));
        thresholds.maxFico = min(thresholds.maxFico, asInt(overlays.get("maxFico")));
        thresholds.maxLtv = min(thresholds.maxLtv, decimal(overlays.get("maxLtv")));
        thresholds.maxCltv = min(thresholds.maxCltv, decimal(overlays.get("maxCltv")));
        thresholds.maxDti = min(thresholds.maxDti, decimal(overlays.get("maxDti")));
        thresholds.minLoanAmount = max(thresholds.minLoanAmount, decimal(overlays.get("minLoanAmount")));
        thresholds.maxLoanAmount = min(thresholds.maxLoanAmount, decimal(overlays.get("maxLoanAmount")));
        thresholds.allowedLoanPurposes = list(overlays.get("allowedLoanPurposes"));
        thresholds.allowedPropertyTypes = list(overlays.get("allowedPropertyTypes"));
        thresholds.allowedOccupancyTypes = list(overlays.get("allowedOccupancyTypes"));
        thresholds.excludedCounties = concat(thresholds.excludedCounties, list(overlays.get("excludedCounties")));
        if (overlays.get("purposeMaxLtv") instanceof Map<?, ?> purposeMaxLtv) thresholds.maxLtv = min(thresholds.maxLtv, decimal(purposeMaxLtv.get(scenario.loanPurpose())));
        if (overlays.get("purposeLoanAmountMax") instanceof Map<?, ?> purposeLoanAmountMax) thresholds.maxLoanAmount = min(thresholds.maxLoanAmount, decimal(purposeLoanAmountMax.get(scenario.loanPurpose())));
        if (overlays.get("highLtvMaxDti") instanceof Map<?, ?> highLtv && scenario.ltv() != null) {
            BigDecimal trigger = decimal(highLtv.get("triggerLtv"));
            if (trigger != null && scenario.ltv().compareTo(trigger) >= 0) thresholds.maxDti = min(thresholds.maxDti, decimal(highLtv.get("maxDti")));
        }
        return thresholds;
    }

    private List<EligibilityFailure> failures(LoanScenario scenario, Thresholds thresholds) {
        List<EligibilityFailure> failures = new ArrayList<>();
        if (thresholds.minFico != null && scenario.fico() != null && scenario.fico() < thresholds.minFico) failures.add(failure("FICO", "MIN_FICO", scenario.fico(), thresholds.minFico));
        if (thresholds.maxFico != null && scenario.fico() != null && scenario.fico() > thresholds.maxFico) failures.add(failure("FICO", "MAX_FICO", scenario.fico(), thresholds.maxFico));
        if (exceeds(scenario.ltv(), thresholds.maxLtv)) failures.add(failure("LTV", "MAX_LTV", scenario.ltv(), thresholds.maxLtv));
        if (exceeds(scenario.cltv(), thresholds.maxCltv)) failures.add(failure("CLTV", "MAX_CLTV", scenario.cltv(), thresholds.maxCltv));
        if (exceeds(scenario.dti(), thresholds.maxDti)) failures.add(failure("DTI", "MAX_DTI", scenario.dti(), thresholds.maxDti));
        if (!thresholds.allowedLoanPurposes.isEmpty() && !contains(thresholds.allowedLoanPurposes, scenario.loanPurpose())) failures.add(failure("LOAN_PURPOSE", "LOAN_PURPOSE_INELIGIBLE", scenario.loanPurpose(), thresholds.allowedLoanPurposes));
        if (!thresholds.allowedPropertyTypes.isEmpty() && !contains(thresholds.allowedPropertyTypes, scenario.propertyType())) failures.add(failure("PROPERTY_TYPE", "PROPERTY_TYPE_INELIGIBLE", scenario.propertyType(), thresholds.allowedPropertyTypes));
        if (!thresholds.allowedOccupancyTypes.isEmpty() && !contains(thresholds.allowedOccupancyTypes, scenario.occupancyType())) failures.add(failure("OCCUPANCY", "OCCUPANCY_INELIGIBLE", scenario.occupancyType(), thresholds.allowedOccupancyTypes));
        if (!thresholds.allowedStates.isEmpty() && !contains(thresholds.allowedStates, scenario.state())) failures.add(failure("STATE", "STATE_NOT_ALLOWED", scenario.state(), thresholds.allowedStates));
        if (contains(thresholds.excludedCounties, scenario.county())) failures.add(failure("COUNTY", "COUNTY_EXCLUDED", scenario.county(), thresholds.excludedCounties));
        if (below(scenario.loanAmount(), thresholds.minLoanAmount)) failures.add(failure("LOAN_AMOUNT", "MIN_LOAN_AMOUNT", scenario.loanAmount(), thresholds.minLoanAmount));
        if (exceeds(scenario.loanAmount(), thresholds.maxLoanAmount)) failures.add(failure("LOAN_AMOUNT", "MAX_LOAN_AMOUNT", scenario.loanAmount(), thresholds.maxLoanAmount));
        return failures;
    }

    private List<EligibilityFailure> dimensionMatchFailures(List<InvestorEligibilityMatrixRow> rows, LoanScenario scenario) {
        List<EligibilityFailure> failures = new ArrayList<>();
        if (rows.stream().noneMatch(row -> matches(row.loanPurpose(), scenario.loanPurpose()))) failures.add(failure("LOAN_PURPOSE", "LOAN_PURPOSE_INELIGIBLE", scenario.loanPurpose(), rows.stream().map(InvestorEligibilityMatrixRow::loanPurpose).distinct().toList()));
        if (rows.stream().noneMatch(row -> matches(row.propertyType(), scenario.propertyType()))) failures.add(failure("PROPERTY_TYPE", "PROPERTY_TYPE_INELIGIBLE", scenario.propertyType(), rows.stream().map(InvestorEligibilityMatrixRow::propertyType).distinct().toList()));
        if (rows.stream().noneMatch(row -> matches(row.occupancyType(), scenario.occupancyType()))) failures.add(failure("OCCUPANCY", "OCCUPANCY_INELIGIBLE", scenario.occupancyType(), rows.stream().map(InvestorEligibilityMatrixRow::occupancyType).distinct().toList()));
        return failures.isEmpty() ? List.of(failure("MATRIX", "NO_MATCHING_MATRIX_ROW", "scenario", "active matrix row")) : failures;
    }

    private EligibilityFailure failure(String dimension, String code, Object actual, Object required) { return new EligibilityFailure(dimension, code, dimension + " failed " + code, actual, required); }
    private static boolean matches(String expected, String actual) { return expected == null || actual == null || expected.equalsIgnoreCase(actual); }
    private static boolean contains(List<String> values, String value) { return value != null && values.stream().anyMatch(v -> v.equalsIgnoreCase(value)); }
    private static boolean exceeds(BigDecimal actual, BigDecimal max) { return actual != null && max != null && actual.compareTo(max) > 0; }
    private static boolean below(BigDecimal actual, BigDecimal min) { return actual != null && min != null && actual.compareTo(min) < 0; }
    private static Integer max(Integer a, Integer b) { if (a == null) return b; if (b == null) return a; return Math.max(a, b); }
    private static Integer min(Integer a, Integer b) { if (a == null) return b; if (b == null) return a; return Math.min(a, b); }
    private static BigDecimal max(BigDecimal a, BigDecimal b) { return a == null ? b : b == null ? a : a.max(b); }
    private static BigDecimal min(BigDecimal a, BigDecimal b) { return a == null ? b : b == null ? a : a.min(b); }
    private static String stringId(UUID value) { return value == null ? null : value.toString(); }
    private static List<String> safeList(List<String> values) { return values == null ? List.of() : values; }
    private static List<String> concat(List<String> left, List<String> right) { List<String> merged = new ArrayList<>(left); merged.addAll(right); return merged.stream().distinct().toList(); }
    private static Integer asInt(Object value) { return value == null ? null : Integer.valueOf(value.toString()); }
    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(value.toString()); }
    @SuppressWarnings("unchecked") private static List<String> list(Object value) { return value instanceof List<?> l ? l.stream().map(Object::toString).toList() : List.of(); }

    private static final class Thresholds {
        Integer minFico; Integer maxFico; BigDecimal maxLtv; BigDecimal maxCltv; BigDecimal maxDti; BigDecimal minLoanAmount; BigDecimal maxLoanAmount; List<String> allowedStates; List<String> excludedCounties; List<String> allowedLoanPurposes; List<String> allowedPropertyTypes; List<String> allowedOccupancyTypes;
        Thresholds(Integer minFico, Integer maxFico, BigDecimal maxLtv, BigDecimal maxCltv, BigDecimal maxDti, BigDecimal minLoanAmount, BigDecimal maxLoanAmount, List<String> allowedStates, List<String> excludedCounties, List<String> allowedLoanPurposes, List<String> allowedPropertyTypes, List<String> allowedOccupancyTypes) {
            this.minFico = minFico; this.maxFico = maxFico; this.maxLtv = maxLtv; this.maxCltv = maxCltv; this.maxDti = maxDti; this.minLoanAmount = minLoanAmount; this.maxLoanAmount = maxLoanAmount; this.allowedStates = allowedStates; this.excludedCounties = excludedCounties; this.allowedLoanPurposes = allowedLoanPurposes; this.allowedPropertyTypes = allowedPropertyTypes; this.allowedOccupancyTypes = allowedOccupancyTypes;
        }
        Map<String, Object> toMap() { Map<String, Object> map = new LinkedHashMap<>(); map.put("min_fico", minFico); map.put("max_fico", maxFico); map.put("max_ltv", maxLtv); map.put("max_cltv", maxCltv); map.put("max_dti", maxDti); map.put("min_loan_amount", minLoanAmount); map.put("max_loan_amount", maxLoanAmount); map.put("allowed_states", allowedStates); map.put("excluded_counties", excludedCounties); map.put("allowed_loan_purposes", allowedLoanPurposes); map.put("allowed_property_types", allowedPropertyTypes); map.put("allowed_occupancy_types", allowedOccupancyTypes); return map; }
    }
}

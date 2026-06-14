package com.wcpe.eligibility.nonqm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NonQmEligibilityModels {
    private NonQmEligibilityModels() {}

    public enum NonQmProductType {
        DSCR("DSCR"),
        BANK_STATEMENT("BANK_STATEMENT"),
        ASSET_DEPLETION("ASSET_DEPLETION"),
        NO_RATIO("NO_RATIO"),
        FOREIGN_NATIONAL("FOREIGN_NATIONAL"),
        ITIN("ITIN"),
        TEN_NINETY_NINE_ONLY("1099_ONLY");

        private final String wireValue;

        NonQmProductType(String wireValue) { this.wireValue = wireValue; }

        @JsonValue public String wireValue() { return wireValue; }

        @JsonCreator
        public static NonQmProductType from(Object value) {
            if (value == null) throw new IllegalArgumentException("productType is required");
            String normalized = value.toString().trim().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_').replace('/', '_');
            if (normalized.equals("1099") || normalized.equals("1099_ONLY") || normalized.equals("P_AND_L_1099")) {
                return TEN_NINETY_NINE_ONLY;
            }
            return NonQmProductType.valueOf(normalized);
        }
    }

    public enum EligibilityDecision { ELIGIBLE, INELIGIBLE, REFER }
    public enum EligibilitySeverity { HARD_STOP, WARNING, CONDITION_ONLY }
    public enum Operator { EQ, IN, GTE, LTE, RANGE, EXISTS, NOT_EXISTS }
    public enum RuleSetSource { CATALOG, OPTIMAL_BLUE, POLLY, LOANPASS, REQUEST }

    public record NonQmEligibilityRequest(
        String productCode,
        String investorCode,
        String channelCode,
        LocalDate quoteDate,
        ScenarioFacts scenario,
        ProductDefinition productDefinition,
        NonQmEligibilityRuleSet ruleSet
    ) {}

    public record ProductDefinition(
        String productCode,
        String productType,
        String investorCode,
        String channelCode,
        Map<String, Object> attributes,
        NonQmEligibilityRuleSet eligibilityRuleSet
    ) {}

    public record NonQmEligibilityRuleSet(
        String ruleSetId,
        String productCode,
        String productType,
        String investorCode,
        String channelCode,
        int version,
        Instant effectiveStart,
        Instant effectiveEnd,
        List<EligibilityRule> rules,
        RuleSetSource source,
        String sourceSystemRef
    ) {}

    public record EligibilityRule(
        String ruleId,
        int priority,
        EligibilitySeverity severity,
        List<EligibilityCondition> when,
        EligibilityDecision decision,
        String reasonCode,
        String displayMessage,
        Map<String, String> ppeFieldRefs
    ) {}

    public record EligibilityCondition(
        String factPath,
        Operator operator,
        Object configuredValue,
        String valueSourceRef
    ) {}

    public record ScenarioFacts(
        RentSchedule rentSchedule,
        HousingExpense housingExpense,
        NonQmScenarioFacts nonQm,
        BorrowerFacts borrower,
        PropertyFacts property
    ) {}

    public record RentSchedule(BigDecimal grossMonthlyRent) {}
    public record HousingExpense(BigDecimal proposedPitia) {}

    public record NonQmScenarioFacts(
        BankStatementInput bankStatement,
        AssetDepletionInput assetDepletion,
        ForeignNationalFacts foreignNational,
        ItinFacts itin,
        TenNinetyNineFacts tenNinetyNine,
        NoRatioFacts noRatio
    ) {}

    public record BankStatementInput(
        String statementType,
        Integer monthCount,
        List<BankStatementMonth> monthlyStatements,
        BigDecimal expenseFactor
    ) {}

    public record BankStatementMonth(String month, BigDecimal eligibleDeposits, Boolean outlier) {}
    public record AssetDepletionInput(BigDecimal eligibleVerifiedAssets, Integer monthsRemaining) {}
    public record ForeignNationalFacts(Boolean foreignNational, Boolean visaPresent, String passportNumberLast4, Boolean usCreditFilePresent, Boolean foreignCreditReferencesPresent) {}
    public record ItinFacts(String itinLast4, Boolean ssnPresent) {}
    public record TenNinetyNineFacts(Integer documentAgeDays, BigDecimal qualifyingMonthlyIncome, Boolean attestationPresent) {}
    public record NoRatioFacts(Boolean incomeExcludedFromDti) {}
    public record BorrowerFacts(Integer fico, String citizenshipStatus) {}
    public record PropertyFacts(String state, String occupancyType) {}

    public record RuleOutcome(
        String ruleId,
        EligibilityDecision decision,
        boolean passed,
        EligibilitySeverity severity,
        String reasonCode,
        String displayMessage,
        List<String> missingFacts,
        Map<String, String> ppeFieldRefs
    ) {}

    public record NonQmEligibilityResult(
        String productCode,
        EligibilityDecision decision,
        boolean eligible,
        int ruleSetVersion,
        Map<String, Object> calculatedFacts,
        List<RuleOutcome> outcomes,
        List<String> missingFacts,
        String auditHash
    ) {}

    public record PpeRuleSetImportRequest(
        RuleSetSource source,
        String sourceSystemRef,
        String productCode,
        String productType,
        String investorCode,
        String channelCode,
        int version,
        List<Map<String, Object>> rules
    ) {}

    public record PpeRuleSetExportResponse(String format, String ruleSetId, Map<String, Object> payload) {}
}

package com.wcpe.eligibility.nonqm;

import com.wcpe.eligibility.domain.hashing.Hashing;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NonQmEligibilityService {
    private final Map<String, NonQmEligibilityRuleSet> importedRuleSets = new ConcurrentHashMap<>();

    public NonQmEligibilityResult evaluate(NonQmEligibilityRequest request) {
        Objects.requireNonNull(request, "request is required");
        ProductDefinition product = request.productDefinition();
        NonQmEligibilityRuleSet ruleSet = resolveRuleSet(request, product);
        String productCode = firstNonBlank(request.productCode(), product == null ? null : product.productCode(), ruleSet == null ? null : ruleSet.productCode());
        if (ruleSet == null) {
            RuleOutcome missing = new RuleOutcome("NON_QM_RULE_SET_MISSING", EligibilityDecision.INELIGIBLE, false, EligibilitySeverity.HARD_STOP,
                "NON_QM_RULE_SET_MISSING", "No active Non-QM eligibility rule set was supplied by catalog metadata.", List.of("nonQm.ruleSet"), Map.of());
            return result(productCode, 0, Map.of(), List.of(missing), List.of("nonQm.ruleSet"));
        }

        NonQmProductType productType = NonQmProductType.from(firstNonBlank(ruleSet.productType(), product == null ? null : product.productType()));
        FactAssembly facts = assembleFacts(productType, request.scenario(), product);
        List<RuleOutcome> outcomes = evaluateRules(ruleSet, facts.facts(), facts.missingFacts());
        if ((ruleSet.rules() == null || ruleSet.rules().isEmpty())) {
            outcomes = List.of(new RuleOutcome("NON_QM_RULES_EMPTY", EligibilityDecision.INELIGIBLE, false, EligibilitySeverity.HARD_STOP,
                "NON_QM_RULES_EMPTY", "The active Non-QM rule set contains no rules.", List.of("nonQm.rules"), Map.of()));
        }
        return result(firstNonBlank(productCode, ruleSet.productCode()), ruleSet.version(), facts.facts(), outcomes, facts.missingFacts());
    }

    public NonQmEligibilityRuleSet importRuleSet(PpeRuleSetImportRequest request) {
        RuleSetSource source = request.source() == null ? RuleSetSource.REQUEST : request.source();
        String ruleSetId = source.name().toLowerCase(Locale.ROOT) + ":" + request.productCode() + ":" + request.investorCode() + ":" + request.channelCode() + ":v" + request.version();
        List<EligibilityRule> rules = request.rules() == null ? List.of() : request.rules().stream()
            .map(this::normalizePpeRule)
            .sorted(Comparator.comparingInt(EligibilityRule::priority))
            .toList();
        NonQmEligibilityRuleSet ruleSet = new NonQmEligibilityRuleSet(ruleSetId, request.productCode(), request.productType(), request.investorCode(),
            request.channelCode(), request.version(), Instant.EPOCH, null, rules, source, request.sourceSystemRef());
        importedRuleSets.put(ruleSetId, ruleSet);
        return ruleSet;
    }

    public PpeRuleSetExportResponse exportRuleSet(String ruleSetId, String format) {
        NonQmEligibilityRuleSet ruleSet = importedRuleSets.get(ruleSetId);
        if (ruleSet == null) throw new IllegalArgumentException("Unknown ruleSetId: " + ruleSetId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", format == null ? ruleSet.source().name() : format.toUpperCase(Locale.ROOT));
        payload.put("sourceSystemRef", ruleSet.sourceSystemRef());
        payload.put("productCode", ruleSet.productCode());
        payload.put("productType", ruleSet.productType());
        payload.put("investorCode", ruleSet.investorCode());
        payload.put("channelCode", ruleSet.channelCode());
        payload.put("version", ruleSet.version());
        payload.put("rules", ruleSet.rules().stream().map(rule -> Map.of(
            "ruleId", rule.ruleId(),
            "priority", rule.priority(),
            "severity", rule.severity().name(),
            "decision", rule.decision().name(),
            "reasonCode", rule.reasonCode(),
            "conditions", rule.when(),
            "ppeFieldRefs", rule.ppeFieldRefs() == null ? Map.of() : rule.ppeFieldRefs()
        )).toList());
        return new PpeRuleSetExportResponse(payload.get("format").toString(), ruleSetId, payload);
    }

    private EligibilityRule normalizePpeRule(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        Map<String, String> refs = raw.get("ppeFieldRefs") instanceof Map<?, ?> m
            ? m.entrySet().stream().collect(LinkedHashMap::new, (out, entry) -> out.put(entry.getKey().toString(), String.valueOf(entry.getValue())), LinkedHashMap::putAll)
            : new LinkedHashMap<>();
        EligibilityCondition condition = new EligibilityCondition(
            string(raw.get("factPath")),
            Operator.valueOf(string(raw.getOrDefault("operator", "EXISTS")).toUpperCase(Locale.ROOT)),
            raw.get("value"),
            string(raw.get("valueSourceRef"))
        );
        return new EligibilityRule(
            firstNonBlank(string(raw.get("ruleId")), "PPE-" + Math.abs(raw.hashCode())),
            integer(raw.get("priority"), 100),
            EligibilitySeverity.valueOf(string(raw.getOrDefault("severity", "HARD_STOP")).toUpperCase(Locale.ROOT)),
            List.of(condition),
            EligibilityDecision.valueOf(string(raw.getOrDefault("decision", "ELIGIBLE")).toUpperCase(Locale.ROOT)),
            firstNonBlank(string(raw.get("reasonCode")), "PPE_RULE"),
            string(raw.get("displayMessage")),
            refs
        );
    }

    private NonQmEligibilityRuleSet resolveRuleSet(NonQmEligibilityRequest request, ProductDefinition product) {
        if (request.ruleSet() != null) return request.ruleSet();
        if (product != null && product.eligibilityRuleSet() != null) return product.eligibilityRuleSet();
        return null;
    }

    private FactAssembly assembleFacts(NonQmProductType productType, ScenarioFacts scenario, ProductDefinition product) {
        Map<String, Object> facts = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        facts.put("nonQm.productType", productType.wireValue());
        facts.put("product.productCode", product == null ? null : product.productCode());
        switch (productType) {
            case DSCR -> dscr(scenario, facts, missing);
            case BANK_STATEMENT -> bankStatement(scenario, product, facts, missing);
            case ASSET_DEPLETION -> assetDepletion(scenario, product, facts, missing);
            case NO_RATIO -> noRatio(scenario, facts, missing);
            case FOREIGN_NATIONAL -> foreignNational(scenario, facts, missing);
            case ITIN -> itin(scenario, facts, missing);
            case TEN_NINETY_NINE_ONLY -> tenNinetyNine(scenario, facts, missing);
        }
        return new FactAssembly(facts, missing.stream().distinct().toList());
    }

    private void dscr(ScenarioFacts scenario, Map<String, Object> facts, List<String> missing) {
        BigDecimal rent = scenario == null || scenario.rentSchedule() == null ? null : scenario.rentSchedule().grossMonthlyRent();
        BigDecimal pitia = scenario == null || scenario.housingExpense() == null ? null : scenario.housingExpense().proposedPitia();
        facts.put("nonQm.dscr.grossMonthlyRent", rent);
        facts.put("nonQm.dscr.proposedPitia", pitia);
        if (rent == null || pitia == null || pitia.signum() <= 0) {
            missing.add("nonQm.dscr.ratio");
            missing.add("DSCR_RENT_OR_PITIA_MISSING");
            return;
        }
        facts.put("nonQm.dscr.ratio", rent.divide(pitia, 4, RoundingMode.HALF_UP));
    }

    private void bankStatement(ScenarioFacts scenario, ProductDefinition product, Map<String, Object> facts, List<String> missing) {
        BankStatementInput input = scenario == null || scenario.nonQm() == null ? null : scenario.nonQm().bankStatement();
        if (input == null) { missing.add("nonQm.bankStatement"); return; }
        Integer months = input.monthCount();
        List<BankStatementMonth> statements = input.monthlyStatements() == null ? List.of() : input.monthlyStatements();
        facts.put("nonQm.bankStatement.statementType", input.statementType());
        facts.put("nonQm.bankStatement.monthCount", months);
        if (months == null || (months != 12 && months != 24) || statements.size() < months) missing.add("nonQm.bankStatement.months");
        else facts.put("nonQm.bankStatement.months", months);
        BigDecimal expenseFactor = input.expenseFactor() == null ? decimal(attribute(product, "bankStatement.expenseFactor")) : input.expenseFactor();
        if (expenseFactor == null) missing.add("nonQm.bankStatement.expenseFactor");
        BigDecimal deposits = statements.stream().map(BankStatementMonth::eligibleDeposits).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        facts.put("nonQm.bankStatement.eligibleDeposits", deposits);
        facts.put("nonQm.bankStatement.hasOutlierDeposits", statements.stream().anyMatch(month -> Boolean.TRUE.equals(month.outlier())));
        if (months != null && months > 0 && expenseFactor != null) {
            facts.put("nonQm.bankStatement.qualifyingMonthlyIncome", deposits.multiply(BigDecimal.ONE.subtract(expenseFactor)).divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP));
        }
    }

    private void assetDepletion(ScenarioFacts scenario, ProductDefinition product, Map<String, Object> facts, List<String> missing) {
        AssetDepletionInput input = scenario == null || scenario.nonQm() == null ? null : scenario.nonQm().assetDepletion();
        if (input == null) { missing.add("nonQm.assetDepletion"); return; }
        BigDecimal assets = input.eligibleVerifiedAssets();
        Integer months = input.monthsRemaining() == null ? integer(attribute(product, "assetDepletion.monthsRemaining"), null) : input.monthsRemaining();
        facts.put("nonQm.assetDepletion.eligibleVerifiedAssets", assets);
        facts.put("nonQm.assetDepletion.monthsRemaining", months);
        if (assets == null) missing.add("nonQm.assetDepletion.eligibleVerifiedAssets");
        if (months == null || months <= 0) missing.add("nonQm.assetDepletion.monthsRemaining");
        if (assets != null && months != null && months > 0) facts.put("nonQm.assetDepletion.qualifyingMonthlyIncome", assets.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP));
    }

    private void noRatio(ScenarioFacts scenario, Map<String, Object> facts, List<String> missing) {
        NoRatioFacts input = scenario == null || scenario.nonQm() == null ? null : scenario.nonQm().noRatio();
        if (input == null || input.incomeExcludedFromDti() == null) missing.add("nonQm.noRatio.incomeExcludedFromDti");
        facts.put("nonQm.noRatio.incomeExcludedFromDti", input == null ? null : input.incomeExcludedFromDti());
    }

    private void foreignNational(ScenarioFacts scenario, Map<String, Object> facts, List<String> missing) {
        ForeignNationalFacts input = scenario == null || scenario.nonQm() == null ? null : scenario.nonQm().foreignNational();
        if (input == null) { missing.add("nonQm.foreignNational"); return; }
        facts.put("nonQm.foreignNational.isForeignNational", input.foreignNational());
        facts.put("nonQm.foreignNational.visaPresent", input.visaPresent());
        facts.put("nonQm.foreignNational.passportLast4Present", input.passportNumberLast4() != null && !input.passportNumberLast4().isBlank());
        facts.put("nonQm.foreignNational.usCreditFilePresent", input.usCreditFilePresent());
        facts.put("nonQm.foreignNational.foreignCreditReferencesPresent", input.foreignCreditReferencesPresent());
        if (!Boolean.TRUE.equals(input.foreignNational())) missing.add("nonQm.foreignNational.isForeignNational");
    }

    private void itin(ScenarioFacts scenario, Map<String, Object> facts, List<String> missing) {
        ItinFacts input = scenario == null || scenario.nonQm() == null ? null : scenario.nonQm().itin();
        if (input == null) { missing.add("nonQm.itin"); return; }
        boolean itinPresent = input.itinLast4() != null && !input.itinLast4().isBlank();
        facts.put("nonQm.itin.present", itinPresent);
        facts.put("nonQm.itin.ssnPresent", input.ssnPresent());
        if (!itinPresent) missing.add("nonQm.itin.present");
    }

    private void tenNinetyNine(ScenarioFacts scenario, Map<String, Object> facts, List<String> missing) {
        TenNinetyNineFacts input = scenario == null || scenario.nonQm() == null ? null : scenario.nonQm().tenNinetyNine();
        if (input == null) { missing.add("nonQm.1099"); return; }
        facts.put("nonQm.1099.documentAgeDays", input.documentAgeDays());
        facts.put("nonQm.1099.qualifyingMonthlyIncome", input.qualifyingMonthlyIncome());
        facts.put("nonQm.1099.attestationPresent", input.attestationPresent());
        if (input.documentAgeDays() == null) missing.add("nonQm.1099.documentAgeDays");
        if (input.qualifyingMonthlyIncome() == null) missing.add("nonQm.1099.qualifyingMonthlyIncome");
    }

    private List<RuleOutcome> evaluateRules(NonQmEligibilityRuleSet ruleSet, Map<String, Object> facts, List<String> assembledMissing) {
        List<RuleOutcome> outcomes = new ArrayList<>();
        for (EligibilityRule rule : safeRules(ruleSet)) {
            List<String> missingForRule = new ArrayList<>();
            boolean passed = true;
            for (EligibilityCondition condition : rule.when() == null ? List.<EligibilityCondition>of() : rule.when()) {
                Object actual = facts.get(condition.factPath());
                if (actual == null || assembledMissing.contains(condition.factPath())) missingForRule.add(condition.factPath());
                if (!matches(condition, actual)) passed = false;
            }
            EligibilityDecision decision = passed && missingForRule.isEmpty() ? rule.decision() : failureDecision(rule.severity());
            outcomes.add(new RuleOutcome(rule.ruleId(), decision, passed && missingForRule.isEmpty(), rule.severity(), rule.reasonCode(), rule.displayMessage(), missingForRule.stream().distinct().toList(), rule.ppeFieldRefs() == null ? Map.of() : rule.ppeFieldRefs()));
        }
        return outcomes;
    }

    private NonQmEligibilityResult result(String productCode, int version, Map<String, Object> facts, List<RuleOutcome> outcomes, List<String> assembledMissing) {
        boolean hardStop = outcomes.stream().anyMatch(outcome -> !outcome.passed() && outcome.severity() == EligibilitySeverity.HARD_STOP);
        boolean refer = outcomes.stream().anyMatch(outcome -> outcome.decision() == EligibilityDecision.REFER || (!outcome.passed() && outcome.severity() == EligibilitySeverity.WARNING));
        EligibilityDecision decision = hardStop ? EligibilityDecision.INELIGIBLE : refer ? EligibilityDecision.REFER : EligibilityDecision.ELIGIBLE;
        List<String> missing = new ArrayList<>(assembledMissing == null ? List.of() : assembledMissing);
        outcomes.forEach(outcome -> missing.addAll(outcome.missingFacts()));
        String auditHash = Hashing.sha256(productCode + "|" + version + "|" + facts + "|" + outcomes);
        return new NonQmEligibilityResult(productCode, decision, decision != EligibilityDecision.INELIGIBLE, version, facts, outcomes, missing.stream().distinct().toList(), auditHash);
    }

    private static List<EligibilityRule> safeRules(NonQmEligibilityRuleSet ruleSet) {
        return ruleSet.rules() == null ? List.of() : ruleSet.rules().stream().sorted(Comparator.comparingInt(EligibilityRule::priority)).toList();
    }

    private static EligibilityDecision failureDecision(EligibilitySeverity severity) { return severity == EligibilitySeverity.WARNING ? EligibilityDecision.REFER : EligibilityDecision.INELIGIBLE; }

    private static boolean matches(EligibilityCondition condition, Object actual) {
        return switch (condition.operator()) {
            case EXISTS -> actual != null;
            case NOT_EXISTS -> actual == null;
            case EQ -> actual != null && normalize(actual).equals(normalize(condition.configuredValue()));
            case IN -> actual != null && condition.configuredValue() instanceof List<?> values && values.stream().map(NonQmEligibilityService::normalize).anyMatch(normalize(actual)::equals);
            case GTE -> actual != null && decimal(actual).compareTo(decimal(condition.configuredValue())) >= 0;
            case LTE -> actual != null && decimal(actual).compareTo(decimal(condition.configuredValue())) <= 0;
            case RANGE -> actual != null && condition.configuredValue() instanceof List<?> range && range.size() == 2 && decimal(actual).compareTo(decimal(range.get(0))) >= 0 && decimal(actual).compareTo(decimal(range.get(1))) <= 0;
        };
    }

    private static String normalize(Object value) { return value == null ? "" : value.toString().trim().toUpperCase(Locale.ROOT); }
    private static String string(Object value) { return value == null ? null : value.toString(); }
    private static String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return null; }
    private static Object attribute(ProductDefinition product, String key) { return product == null || product.attributes() == null ? null : product.attributes().get(key); }
    private static Integer integer(Object value, Integer fallback) { return value == null ? fallback : Integer.valueOf(value.toString()); }
    private static BigDecimal decimal(Object value) { return value == null ? null : value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString()); }

    private record FactAssembly(Map<String, Object> facts, List<String> missingFacts) {}
}

package com.wcpe.underwriting.nonqm;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Configurable first-pass Non-QM AUS and conditions engine.
 *
 * <p>The engine consumes scenario, eligibility, and pricing facts. Domain thresholds are supplied through rule-set
 * config facts on the request or rule set. Missing configuration produces findings instead of invented defaults.</p>
 */
public final class NonQmUnderwritingApi {
  private final RuleSetResolver ruleSetResolver;
  private final ConditionFactAssembler factAssembler;
  private final PredicateEvaluator predicateEvaluator;
  private final TemplateRenderer templateRenderer;
  private final ConditionDeduplicator deduplicator;
  private final RiskAssessmentService riskAssessmentService;
  private final AusDecisionService decisionService;

  public NonQmUnderwritingApi() {
    this(new DefaultRuleSetResolver());
  }

  public NonQmUnderwritingApi(RuleSetResolver ruleSetResolver) {
    this.ruleSetResolver = Objects.requireNonNull(ruleSetResolver);
    this.factAssembler = new ConditionFactAssembler();
    this.predicateEvaluator = new PredicateEvaluator();
    this.templateRenderer = new TemplateRenderer();
    this.deduplicator = new ConditionDeduplicator();
    this.riskAssessmentService = new RiskAssessmentService();
    this.decisionService = new AusDecisionService();
  }

  public UnderwritingResult evaluate(UnderwritingRequest request) {
    validateRequest(request);
    NonQmConditionRuleSet rules = ruleSetResolver.resolve(request.productType(), request.investorCode(),
        request.channelCode(), request.ruleAsOf(), request.ruleConfig());
    FactMap facts = factAssembler.from(request, rules);
    List<GeneratedCondition> generated = rules.rules().stream()
        .filter(rule -> predicateEvaluator.matches(rule.predicates(), facts))
        .map(rule -> templateRenderer.render(rule, facts, rules))
        .toList();

    RiskAssessment risk = riskAssessmentService.assess(facts, request.productType());
    List<GeneratedCondition> merged = deduplicator.merge(appendRiskConditions(generated, risk));
    AusDecision decision = decisionService.decide(request, risk, merged);
    String auditHash = stableHash("non-qm-aus", rules.ruleSetId(), rules.version(), facts.values(), merged, decision);

    UnderwritingFindingsReport report = UnderwritingFindingsReport.from(request, rules, facts, risk, decision, merged,
        auditHash);
    return new UnderwritingResult(UUID.nameUUIDFromBytes((request.tenantId() + ":" + auditHash).getBytes(StandardCharsets.UTF_8)),
        request.tenantId(), request.scenarioId(), request.productCode(), request.productType(), decision,
        rules.ruleSetId(), rules.version(), merged, risk, auditHash, report, request.correlationId());
  }

  private static List<GeneratedCondition> appendRiskConditions(List<GeneratedCondition> generated, RiskAssessment risk) {
    List<GeneratedCondition> all = new ArrayList<>(generated);
    all.addAll(risk.creditRisk().conditions());
    all.addAll(risk.propertyRisk().conditions());
    all.addAll(risk.incomeVerification().conditions());
    return all;
  }

  private static void validateRequest(UnderwritingRequest request) {
    requireNonNull(request, "request is required");
    requireText(request.tenantId(), "tenant_id is required");
    requireText(request.scenarioId(), "scenario_id is required");
    requireNonNull(request.productType(), "product_type is required");
    requireText(request.investorCode(), "investor_code is required");
    requireText(request.channelCode(), "channel_code is required");
  }

  public interface RuleSetResolver {
    NonQmConditionRuleSet resolve(NonQmProductType productType, String investorCode, String channelCode,
        Instant asOf, Map<String, String> requestConfig);
  }

  public enum NonQmProductType { DSCR, BANK_STATEMENT, ASSET_DEPLETION, NO_RATIO, FOREIGN_NATIONAL, ITIN, ONE099_ONLY }
  public enum EligibilityStatus { ELIGIBLE, REFER, INELIGIBLE, BLOCKED }
  public enum PricingStatus { PRICED, BLOCKED, MISSING }
  public enum AusDecision { APPROVE, REFER, DECLINE }
  public enum ConditionCategory { BORROWER, PROPERTY, INCOME, CREDIT, COLLATERAL, PRICING, ELIGIBILITY }
  public enum ConditionSeverity { HARD_STOP, REQUIRED_PRIOR_TO_DOCS, PRIOR_TO_FUNDING, PRIOR_TO_PURCHASE, ADVISORY }
  public enum PredicateOperator { PRESENT, MISSING, EQUALS, NOT_EQUALS, LESS_THAN_CONFIG, PRICING_STATUS, ELIGIBILITY_STATUS, PRODUCT_TYPE }
  public enum RiskLevel { LOW, MODERATE, HIGH, INCOMPLETE }

  public record UnderwritingRequest(String tenantId, String scenarioId, NonQmProductType productType,
      String productCode, String investorCode, String channelCode, Instant ruleAsOf, Map<String, String> scenarioFacts,
      EligibilityOutcome eligibilityOutcome, PricingContext pricingContext, Map<String, String> ruleConfig,
      String correlationId) {
    public UnderwritingRequest {
      scenarioFacts = scenarioFacts == null ? Map.of() : Map.copyOf(scenarioFacts);
      ruleConfig = ruleConfig == null ? Map.of() : Map.copyOf(ruleConfig);
      productCode = firstNonBlank(productCode, productType == null ? "NON_QM" : productType.name());
      ruleAsOf = ruleAsOf == null ? Instant.now() : ruleAsOf;
      correlationId = firstNonBlank(correlationId, "underwriting:" + scenarioId);
    }
  }

  public record EligibilityOutcome(EligibilityStatus status, String eligibilityRef, String reasonCode,
      List<String> warningCodes) {
    public EligibilityOutcome {
      warningCodes = warningCodes == null ? List.of() : List.copyOf(warningCodes);
      status = status == null ? EligibilityStatus.REFER : status;
      eligibilityRef = firstNonBlank(eligibilityRef, "eligibility:missing");
      reasonCode = firstNonBlank(reasonCode, status.name());
    }

    boolean hardStop() {
      return status == EligibilityStatus.INELIGIBLE || status == EligibilityStatus.BLOCKED;
    }
  }

  public record PricingContext(PricingStatus status, String priceResultHash, String rateSheetId, int rateSheetVersion,
      String investorProductCode, List<String> blockerCodes, List<String> versionRefs, Map<String, String> pricingFacts) {
    public PricingContext {
      status = status == null ? PricingStatus.MISSING : status;
      blockerCodes = blockerCodes == null ? List.of() : List.copyOf(blockerCodes);
      versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
      pricingFacts = pricingFacts == null ? Map.of() : Map.copyOf(pricingFacts);
    }
  }

  public record NonQmConditionRuleSet(String ruleSetId, NonQmProductType productType, String investorCode,
      String channelCode, int version, Map<String, String> config, List<ConditionRule> rules) {
    public NonQmConditionRuleSet {
      requireText(ruleSetId, "rule_set_id is required");
      requireNonNull(productType, "product_type is required");
      investorCode = firstNonBlank(investorCode, "ANY");
      channelCode = firstNonBlank(channelCode, "ANY");
      config = config == null ? Map.of() : Map.copyOf(config);
      rules = rules == null ? List.of() : List.copyOf(rules);
    }
  }

  public record ConditionRule(String ruleId, int priority, ConditionCategory category, ConditionSeverity severity,
      List<ConditionPredicate> predicates, ConditionTemplate template, List<String> losTags) {
    public ConditionRule {
      requireText(ruleId, "rule_id is required");
      requireNonNull(category, "condition category is required");
      requireNonNull(severity, "condition severity is required");
      predicates = predicates == null ? List.of() : List.copyOf(predicates);
      requireNonNull(template, "condition template is required");
      losTags = losTags == null ? List.of() : List.copyOf(losTags);
    }
  }

  public record ConditionPredicate(String factKey, PredicateOperator operator, String expectedValue, String configKey) {
    public ConditionPredicate {
      requireNonNull(operator, "predicate operator is required");
    }
  }

  public record ConditionTemplate(String title, String text, String ownerRole, boolean borrowerVisible,
      List<String> evidenceRefKeys) {
    public ConditionTemplate {
      requireText(title, "condition title is required");
      requireText(text, "condition text is required");
      ownerRole = firstNonBlank(ownerRole, "UNDERWRITER");
      evidenceRefKeys = evidenceRefKeys == null ? List.of() : List.copyOf(evidenceRefKeys);
    }
  }

  public record GeneratedCondition(String conditionId, String ruleId, ConditionCategory category,
      ConditionSeverity severity, String title, String text, String ownerRole, boolean borrowerVisible,
      List<String> evidenceRefs, List<String> losTags) {
    public GeneratedCondition {
      conditionId = firstNonBlank(conditionId, UUID.nameUUIDFromBytes((ruleId + ":" + title + ":" + text)
          .getBytes(StandardCharsets.UTF_8)).toString());
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
      losTags = losTags == null ? List.of() : List.copyOf(losTags);
    }
  }

  public record UnderwritingResult(UUID underwritingId, String tenantId, String scenarioId, String productCode,
      NonQmProductType productType, AusDecision decision, String ruleSetId, int ruleSetVersion,
      List<GeneratedCondition> conditions, RiskAssessment riskAssessment, String auditHash,
      UnderwritingFindingsReport findingsReport, String correlationId) {
    public UnderwritingResult {
      conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
  }

  public record RiskAssessment(IncomeVerificationResult incomeVerification, CreditRiskResult creditRisk,
      PropertyRiskResult propertyRisk) {}

  public record IncomeVerificationResult(RiskLevel riskLevel, List<GeneratedCondition> conditions,
      List<String> verifiedMethods, List<String> missingFacts) {
    public IncomeVerificationResult {
      conditions = conditions == null ? List.of() : List.copyOf(conditions);
      verifiedMethods = verifiedMethods == null ? List.of() : List.copyOf(verifiedMethods);
      missingFacts = missingFacts == null ? List.of() : List.copyOf(missingFacts);
    }
  }

  public record CreditRiskResult(RiskLevel riskLevel, List<GeneratedCondition> conditions, List<String> evidenceRefs,
      List<String> missingFacts) {
    public CreditRiskResult {
      conditions = conditions == null ? List.of() : List.copyOf(conditions);
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
      missingFacts = missingFacts == null ? List.of() : List.copyOf(missingFacts);
    }
  }

  public record PropertyRiskResult(RiskLevel riskLevel, List<GeneratedCondition> conditions, List<String> evidenceRefs,
      List<String> missingFacts) {
    public PropertyRiskResult {
      conditions = conditions == null ? List.of() : List.copyOf(conditions);
      evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
      missingFacts = missingFacts == null ? List.of() : List.copyOf(missingFacts);
    }
  }

  public record UnderwritingFindingsReport(String scenarioId, NonQmProductType productType, AusDecision decision,
      String ruleSetRef, Map<ConditionSeverity, Long> countsBySeverity, Map<ConditionCategory, Long> countsByCategory,
      List<String> incomeFindings, List<String> creditFindings, List<String> propertyFindings, List<String> pricingRefs,
      String auditHash) {
    static UnderwritingFindingsReport from(UnderwritingRequest request, NonQmConditionRuleSet rules, FactMap facts,
        RiskAssessment risk, AusDecision decision, List<GeneratedCondition> conditions, String auditHash) {
      Map<ConditionSeverity, Long> bySeverity = conditions.stream().collect(Collectors.groupingBy(
          GeneratedCondition::severity, LinkedHashMap::new, Collectors.counting()));
      Map<ConditionCategory, Long> byCategory = conditions.stream().collect(Collectors.groupingBy(
          GeneratedCondition::category, LinkedHashMap::new, Collectors.counting()));
      PricingContext pricing = request.pricingContext();
      List<String> pricingRefs = pricing == null ? List.of() : pricing.versionRefs();
      return new UnderwritingFindingsReport(request.scenarioId(), request.productType(), decision,
          rules.ruleSetId() + ":v" + rules.version(), bySeverity, byCategory,
          List.of("income_risk=" + risk.incomeVerification().riskLevel(), "verified=" + risk.incomeVerification().verifiedMethods()),
          List.of("credit_risk=" + risk.creditRisk().riskLevel(), "evidence=" + risk.creditRisk().evidenceRefs()),
          List.of("property_risk=" + risk.propertyRisk().riskLevel(), "evidence=" + risk.propertyRisk().evidenceRefs()),
          pricingRefs, auditHash);
    }
  }

  static final class DefaultRuleSetResolver implements RuleSetResolver {
    @Override
    public NonQmConditionRuleSet resolve(NonQmProductType productType, String investorCode, String channelCode,
        Instant asOf, Map<String, String> requestConfig) {
      Map<String, String> config = new LinkedHashMap<>();
      if (requestConfig != null) {
        config.putAll(requestConfig);
      }
      List<ConditionRule> rules = new ArrayList<>();
      commonRules(rules);
      switch (productType) {
        case DSCR -> dscrRules(rules);
        case BANK_STATEMENT -> bankStatementRules(rules);
        case ASSET_DEPLETION -> assetDepletionRules(rules);
        case NO_RATIO -> noRatioRules(rules);
        case FOREIGN_NATIONAL -> foreignNationalRules(rules);
        case ITIN -> itinRules(rules);
        case ONE099_ONLY -> one099Rules(rules);
      }
      String investor = firstNonBlank(investorCode, "ANY");
      String channel = firstNonBlank(channelCode, "ANY");
      return new NonQmConditionRuleSet("nonqm-aus-" + productType.name().toLowerCase(Locale.ROOT) + "-" + investor
          + "-" + channel, productType, investor, channel, 1, config, rules);
    }

    private static void commonRules(List<ConditionRule> rules) {
      rules.add(rule("ELIG-HARD-STOP", 1, ConditionCategory.ELIGIBILITY, ConditionSeverity.HARD_STOP,
          List.of(p("eligibility.status", PredicateOperator.ELIGIBILITY_STATUS, "INELIGIBLE", null)),
          "Eligibility hard stop", "Resolve eligibility hard-stop reason {{eligibility.reasonCode}} before approval.",
          List.of("eligibility.ref")));
      rules.add(rule("PRICING-BLOCKED", 2, ConditionCategory.PRICING, ConditionSeverity.HARD_STOP,
          List.of(p("pricing.status", PredicateOperator.PRICING_STATUS, "BLOCKED", null)),
          "Pricing is blocked", "Resolve Non-QM pricing blockers {{pricing.blockers}} before issuing AUS approval.",
          List.of("pricing.resultHash", "pricing.rateSheetId")));
      rules.add(rule("PRICING-MISSING", 20, ConditionCategory.PRICING, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("pricing.status", PredicateOperator.PRICING_STATUS, "MISSING", null)),
          "Pricing result required", "Attach current Non-QM pricing result and rate sheet version for underwriting audit.",
          List.of("pricing.resultHash")));
    }

    private static void dscrRules(List<ConditionRule> rules) {
      rules.add(rule("DSCR-LEASE-001", 100, ConditionCategory.INCOME, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("productType", PredicateOperator.PRODUCT_TYPE, "DSCR", null)),
          "DSCR lease or rent support", "Provide executed lease, market rent schedule, PITIA support, and DSCR calculation evidence.",
          List.of("nonQm.dscr.ratio", "income.rental.evidenceRef", "property.taxInsurance.evidenceRef")));
      rules.add(rule("DSCR-BORROWER-EXP-001", 110, ConditionCategory.BORROWER, ConditionSeverity.ADVISORY,
          List.of(p("borrower.experience", PredicateOperator.MISSING, null, null)),
          "Borrower experience not documented", "Document borrower real estate investment experience when required by the selected program configuration.",
          List.of("borrower.experience")));
    }

    private static void bankStatementRules(List<ConditionRule> rules) {
      rules.add(rule("BANK-STMT-MONTHS-001", 100, ConditionCategory.INCOME, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("nonQm.bankStatement.monthCount", PredicateOperator.LESS_THAN_CONFIG, null, "bankStatement.requiredMonths")),
          "Additional bank statements required", "Provide additional bank statements or alternate income documentation to satisfy configured statement-month requirement.",
          List.of("nonQm.bankStatement.monthCount", "bankStatement.requiredMonths")));
      rules.add(rule("BANK-STMT-DEPOSITS-001", 110, ConditionCategory.INCOME, ConditionSeverity.PRIOR_TO_FUNDING,
          List.of(p("income.depositAnalysisRef", PredicateOperator.MISSING, null, null)),
          "Deposit analysis required", "Complete bank-statement deposit sourcing, ownership, expense-factor, and trend analysis.",
          List.of("income.depositAnalysisRef")));
    }

    private static void assetDepletionRules(List<ConditionRule> rules) {
      rules.add(rule("ASSET-DEPLETION-SUPPORT-001", 95, ConditionCategory.INCOME, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("productType", PredicateOperator.PRODUCT_TYPE, "ASSET_DEPLETION", null)),
          "Asset depletion support", "Document eligible asset type, depletion method, liquidity, and seasoning evidence used for income automation.",
          List.of("assets.verificationRef", "assetType", "seasoningBand")));
      rules.add(rule("ASSET-DEPLETION-001", 100, ConditionCategory.INCOME, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("assets.verificationRef", PredicateOperator.MISSING, null, null)),
          "Asset verification required", "Verify eligible asset type, seasoning, liquidity, and retirement access evidence for asset depletion income.",
          List.of("assets.verificationRef", "assetType", "seasoningBand")));
    }

    private static void noRatioRules(List<ConditionRule> rules) {
      rules.add(rule("NO-RATIO-OCCUPANCY-001", 100, ConditionCategory.BORROWER, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("occupancy", PredicateOperator.PRESENT, null, null)),
          "No-ratio occupancy certification", "Confirm occupancy and no-ratio program certification; do not collect unsupported income ratios.",
          List.of("occupancy", "pricing.resultHash")));
    }

    private static void foreignNationalRules(List<ConditionRule> rules) {
      rules.add(rule("FOREIGN-NATIONAL-001", 100, ConditionCategory.CREDIT, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("productType", PredicateOperator.PRODUCT_TYPE, "FOREIGN_NATIONAL", null)),
          "Foreign national credit package", "Collect passport/visa, international credit profile, reserves, and country-tier support required by configuration.",
          List.of("countryTier", "creditProfile")));
    }

    private static void itinRules(List<ConditionRule> rules) {
      rules.add(rule("ITIN-001", 100, ConditionCategory.BORROWER, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("itinStatus", PredicateOperator.PRESENT, null, null)),
          "ITIN documentation", "Validate ITIN status, borrower identity, credit profile, and program-specific residency documentation.",
          List.of("itinStatus", "credit.fico")));
    }

    private static void one099Rules(List<ConditionRule> rules) {
      rules.add(rule("1099-INCOME-001", 100, ConditionCategory.INCOME, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS,
          List.of(p("documentType", PredicateOperator.EQUALS, "1099", null)),
          "1099 income package", "Provide 1099 consistency analysis, business-history support, and no-tax-return waiver evidence.",
          List.of("documentType", "businessHistoryBand", "income.1099AnalysisRef")));
    }

    private static ConditionRule rule(String id, int priority, ConditionCategory category, ConditionSeverity severity,
        List<ConditionPredicate> predicates, String title, String text, List<String> evidenceKeys) {
      return new ConditionRule(id, priority, category, severity, predicates,
          new ConditionTemplate(title, text, "UNDERWRITER", severity != ConditionSeverity.HARD_STOP, evidenceKeys),
          List.of("NON_QM", category.name(), severity.name()));
    }

    private static ConditionPredicate p(String factKey, PredicateOperator operator, String expected, String configKey) {
      return new ConditionPredicate(factKey, operator, expected, configKey);
    }
  }

  static final class ConditionFactAssembler {
    FactMap from(UnderwritingRequest request, NonQmConditionRuleSet rules) {
      Map<String, String> facts = new LinkedHashMap<>(request.scenarioFacts());
      facts.put("productType", request.productType().name());
      facts.put("investorCode", request.investorCode());
      facts.put("channelCode", request.channelCode());
      rules.config().forEach((key, value) -> facts.put("config." + key, value));
      EligibilityOutcome eligibility = request.eligibilityOutcome() == null
          ? new EligibilityOutcome(EligibilityStatus.REFER, "eligibility:missing", "ELIGIBILITY_MISSING", List.of())
          : request.eligibilityOutcome();
      facts.put("eligibility.status", eligibility.status().name());
      facts.put("eligibility.ref", eligibility.eligibilityRef());
      facts.put("eligibility.reasonCode", eligibility.reasonCode());
      facts.put("eligibility.warnings", String.join(",", eligibility.warningCodes()));

      PricingContext pricing = request.pricingContext() == null
          ? new PricingContext(PricingStatus.MISSING, null, null, 0, null, List.of(), List.of(), Map.of())
          : request.pricingContext();
      facts.put("pricing.status", pricing.status().name());
      facts.put("pricing.resultHash", firstNonBlank(pricing.priceResultHash(), "pricing:missing"));
      facts.put("pricing.rateSheetId", firstNonBlank(pricing.rateSheetId(), "rate-sheet:missing"));
      facts.put("pricing.blockers", String.join(",", pricing.blockerCodes()));
      pricing.pricingFacts().forEach(facts::putIfAbsent);
      return new FactMap(facts);
    }
  }

  record FactMap(Map<String, String> values) {
    FactMap {
      values = values == null ? Map.of() : Map.copyOf(values);
    }
    String get(String key) { return values.get(key); }
    boolean present(String key) { return get(key) != null && !get(key).isBlank(); }
  }

  static final class PredicateEvaluator {
    boolean matches(List<ConditionPredicate> predicates, FactMap facts) {
      return predicates.stream().allMatch(predicate -> matches(predicate, facts));
    }

    private boolean matches(ConditionPredicate predicate, FactMap facts) {
      String actual = facts.get(predicate.factKey());
      return switch (predicate.operator()) {
        case PRESENT -> actual != null && !actual.isBlank();
        case MISSING -> actual == null || actual.isBlank();
        case EQUALS, PRODUCT_TYPE, PRICING_STATUS, ELIGIBILITY_STATUS -> Objects.equals(predicate.expectedValue(), actual);
        case NOT_EQUALS -> !Objects.equals(predicate.expectedValue(), actual);
        case LESS_THAN_CONFIG -> lessThanConfig(actual, facts.get("config." + predicate.configKey()));
      };
    }

    private boolean lessThanConfig(String actual, String configured) {
      if (configured == null || configured.isBlank()) {
        return true;
      }
      BigDecimal actualNumber = decimal(actual);
      BigDecimal configuredNumber = decimal(configured);
      return actualNumber == null || configuredNumber == null || actualNumber.compareTo(configuredNumber) < 0;
    }
  }

  static final class TemplateRenderer {
    GeneratedCondition render(ConditionRule rule, FactMap facts, NonQmConditionRuleSet rules) {
      String rendered = rule.template().text();
      for (Map.Entry<String, String> entry : facts.values().entrySet()) {
        rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
      }
      List<String> evidence = rule.template().evidenceRefKeys().stream()
          .map(key -> key + "=" + firstNonBlank(facts.get(key), facts.get("config." + key), "missing"))
          .toList();
      String conditionId = UUID.nameUUIDFromBytes((rules.ruleSetId() + ":" + rule.ruleId() + ":" + evidence)
          .getBytes(StandardCharsets.UTF_8)).toString();
      return new GeneratedCondition(conditionId, rule.ruleId(), rule.category(), rule.severity(),
          rule.template().title(), rendered, rule.template().ownerRole(), rule.template().borrowerVisible(),
          evidence, rule.losTags());
    }
  }

  static final class ConditionDeduplicator {
    List<GeneratedCondition> merge(List<GeneratedCondition> conditions) {
      Map<String, GeneratedCondition> byKey = new LinkedHashMap<>();
      for (GeneratedCondition condition : conditions) {
        String key = condition.category() + ":" + condition.title() + ":" + condition.text();
        GeneratedCondition existing = byKey.get(key);
        if (existing == null) {
          byKey.put(key, condition);
        } else {
          List<String> evidence = new ArrayList<>(existing.evidenceRefs());
          condition.evidenceRefs().stream().filter(ref -> !evidence.contains(ref)).forEach(evidence::add);
          byKey.put(key, new GeneratedCondition(existing.conditionId(), existing.ruleId(), existing.category(),
              highest(existing.severity(), condition.severity()), existing.title(), existing.text(), existing.ownerRole(),
              existing.borrowerVisible(), evidence, existing.losTags()));
        }
      }
      return byKey.values().stream().sorted(Comparator
          .comparing(GeneratedCondition::severity, Comparator.comparingInt(ConditionDeduplicator::rank))
          .thenComparing(GeneratedCondition::category)
          .thenComparing(GeneratedCondition::title))
          .toList();
    }

    private static ConditionSeverity highest(ConditionSeverity left, ConditionSeverity right) {
      return rank(left) <= rank(right) ? left : right;
    }

    private static int rank(ConditionSeverity severity) {
      return switch (severity) {
        case HARD_STOP -> 0;
        case REQUIRED_PRIOR_TO_DOCS -> 1;
        case PRIOR_TO_FUNDING -> 2;
        case PRIOR_TO_PURCHASE -> 3;
        case ADVISORY -> 4;
      };
    }
  }

  static final class RiskAssessmentService {
    RiskAssessment assess(FactMap facts, NonQmProductType productType) {
      return new RiskAssessment(income(facts, productType), credit(facts), property(facts));
    }

    private IncomeVerificationResult income(FactMap facts, NonQmProductType productType) {
      List<String> verified = new ArrayList<>();
      List<String> missing = new ArrayList<>();
      switch (productType) {
        case DSCR -> requireOrVerify("income.rental.evidenceRef", "rental-income", facts, verified, missing);
        case BANK_STATEMENT -> requireOrVerify("income.depositAnalysisRef", "bank-statement-analysis", facts, verified, missing);
        case ASSET_DEPLETION -> requireOrVerify("assets.verificationRef", "asset-depletion", facts, verified, missing);
        case ONE099_ONLY -> requireOrVerify("income.1099AnalysisRef", "1099-analysis", facts, verified, missing);
        default -> verified.add(productType.name().toLowerCase(Locale.ROOT) + "-income-method-not-applicable");
      }
      RiskLevel level = missing.isEmpty() ? RiskLevel.LOW : RiskLevel.INCOMPLETE;
      List<GeneratedCondition> conditions = missing.stream().map(fact -> riskCondition("INCOME-MISSING-" + fact,
          ConditionCategory.INCOME, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS, "Income verification missing",
          "Provide configured income verification evidence for " + fact + ".", fact)).toList();
      return new IncomeVerificationResult(level, conditions, verified, missing);
    }

    private CreditRiskResult credit(FactMap facts) {
      List<String> required = List.of("credit.fico", "credit.tradelines", "credit.housingHistory");
      List<String> missing = required.stream().filter(fact -> !facts.present(fact)).toList();
      List<String> evidence = required.stream().filter(facts::present).map(fact -> fact + "=" + facts.get(fact)).toList();
      List<GeneratedCondition> conditions = missing.stream().map(fact -> riskCondition("CREDIT-MISSING-" + fact,
          ConditionCategory.CREDIT, ConditionSeverity.REQUIRED_PRIOR_TO_DOCS, "Credit risk evidence missing",
          "Provide configured credit evidence for " + fact + ".", fact)).toList();
      return new CreditRiskResult(missing.isEmpty() ? RiskLevel.LOW : RiskLevel.INCOMPLETE, conditions, evidence, missing);
    }

    private PropertyRiskResult property(FactMap facts) {
      List<String> required = List.of("property.appraisalRef", "property.condition", "property.type");
      List<String> missing = required.stream().filter(fact -> !facts.present(fact)).toList();
      List<String> evidence = required.stream().filter(facts::present).map(fact -> fact + "=" + facts.get(fact)).toList();
      List<GeneratedCondition> conditions = missing.stream().map(fact -> riskCondition("PROPERTY-MISSING-" + fact,
          ConditionCategory.PROPERTY, ConditionSeverity.PRIOR_TO_FUNDING, "Property risk evidence missing",
          "Provide appraisal, property condition, and property type evidence for " + fact + ".", fact)).toList();
      return new PropertyRiskResult(missing.isEmpty() ? RiskLevel.LOW : RiskLevel.INCOMPLETE, conditions, evidence, missing);
    }

    private static void requireOrVerify(String fact, String method, FactMap facts, List<String> verified, List<String> missing) {
      if (facts.present(fact)) {
        verified.add(method + ":" + facts.get(fact));
      } else {
        missing.add(fact);
      }
    }

    private static GeneratedCondition riskCondition(String ruleId, ConditionCategory category, ConditionSeverity severity,
        String title, String text, String evidenceKey) {
      return new GeneratedCondition(null, ruleId, category, severity, title, text, "UNDERWRITER", true,
          List.of(evidenceKey + "=missing"), List.of("NON_QM", category.name(), severity.name()));
    }
  }

  static final class AusDecisionService {
    AusDecision decide(UnderwritingRequest request, RiskAssessment risk, List<GeneratedCondition> conditions) {
      EligibilityOutcome eligibility = request.eligibilityOutcome();
      PricingContext pricing = request.pricingContext();
      boolean hardStop = (eligibility != null && eligibility.hardStop())
          || (pricing != null && pricing.status() == PricingStatus.BLOCKED)
          || conditions.stream().anyMatch(condition -> condition.severity() == ConditionSeverity.HARD_STOP);
      if (hardStop) {
        return AusDecision.DECLINE;
      }
      boolean refer = eligibility == null || eligibility.status() == EligibilityStatus.REFER
          || pricing == null || pricing.status() == PricingStatus.MISSING
          || risk.incomeVerification().riskLevel() == RiskLevel.INCOMPLETE
          || risk.creditRisk().riskLevel() == RiskLevel.INCOMPLETE
          || risk.propertyRisk().riskLevel() == RiskLevel.INCOMPLETE;
      return refer ? AusDecision.REFER : AusDecision.APPROVE;
    }
  }

  private static BigDecimal decimal(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new NonQmUnderwritingException(message);
    }
  }

  private static void requireNonNull(Object value, String message) {
    if (value == null) {
      throw new NonQmUnderwritingException(message);
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String stableHash(Object... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Object value : values) {
        digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public static class NonQmUnderwritingException extends RuntimeException {
    public NonQmUnderwritingException(String message) {
      super(message);
    }
  }
}

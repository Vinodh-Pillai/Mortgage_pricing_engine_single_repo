package com.wcpe.scenario.domain;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

public class Scenario {
  private final UUID tenantId;
  private final UUID scenarioId;
  private final UUID lineageId;
  private int version;
  private ScenarioStatus status;
  private final String quoteIntent;
  private final String channel;
  private String scenarioName;
  private final String externalLoanId;
  private final String sourceSystem;
  private final Map<String, Object> rawFacts = new LinkedHashMap<>();
  private final Map<String, Object> normalizedFacts = new LinkedHashMap<>();
  private final Map<String, Object> derivedFields = new LinkedHashMap<>();
  private final Set<String> completedSections = new LinkedHashSet<>();
  private final List<ValidationIssue> validationIssues = new ArrayList<>();
  private final List<VersionManifest> versions = new ArrayList<>();
  private String replayHash;

  public Scenario(UUID tenantId, String quoteIntent, String channel, String scenarioName, String externalLoanId, String sourceSystem, Map<String, Object> initialFacts) {
    this.tenantId = tenantId;
    this.scenarioId = UUID.randomUUID();
    this.lineageId = scenarioId;
    this.version = 1;
    this.status = ScenarioStatus.DRAFT_INCOMPLETE;
    this.quoteIntent = quoteIntent;
    this.channel = channel;
    this.scenarioName = scenarioName;
    this.externalLoanId = externalLoanId;
    this.sourceSystem = sourceSystem;
    if (initialFacts != null) rawFacts.putAll(initialFacts);
    validateDraft();
    snapshot("CREATE_DRAFT");
  }

  private Scenario(Scenario source, String scenarioName, Map<String, Object> overrides) {
    this.tenantId = source.tenantId;
    this.scenarioId = UUID.randomUUID();
    this.lineageId = source.lineageId;
    this.version = 1;
    this.status = ScenarioStatus.DRAFT_INCOMPLETE;
    this.quoteIntent = source.quoteIntent;
    this.channel = source.channel;
    this.scenarioName = scenarioName == null || scenarioName.isBlank() ? source.scenarioName + " clone" : scenarioName;
    this.externalLoanId = source.externalLoanId;
    this.sourceSystem = source.sourceSystem;
    this.rawFacts.putAll(source.rawFacts);
    this.normalizedFacts.putAll(source.normalizedFacts);
    this.derivedFields.putAll(source.derivedFields);
    if (overrides != null) this.rawFacts.putAll(overrides);
    validateAll();
    snapshot("CLONE_SCENARIO");
  }

  private Scenario(UUID tenantId, UUID scenarioId, UUID lineageId, int version, ScenarioStatus status, String quoteIntent, String channel,
      String scenarioName, String externalLoanId, String sourceSystem, Map<String, Object> rawFacts, Map<String, Object> normalizedFacts,
      Map<String, Object> derivedFields, List<ValidationIssue> validationIssues, List<VersionManifest> versions, String replayHash) {
    this.tenantId = tenantId;
    this.scenarioId = scenarioId;
    this.lineageId = lineageId;
    this.version = version;
    this.status = status;
    this.quoteIntent = quoteIntent;
    this.channel = channel;
    this.scenarioName = scenarioName;
    this.externalLoanId = externalLoanId;
    this.sourceSystem = sourceSystem;
    if (rawFacts != null) this.rawFacts.putAll(rawFacts);
    if (normalizedFacts != null) this.normalizedFacts.putAll(normalizedFacts);
    if (derivedFields != null) this.derivedFields.putAll(derivedFields);
    if (validationIssues != null) this.validationIssues.addAll(validationIssues);
    if (versions != null) this.versions.addAll(versions);
    this.replayHash = replayHash;
    for (String section : List.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS")) {
      if (!this.validationIssues.stream().anyMatch(i -> i.fieldPath().startsWith(section) || i.code().contains(section)) && this.rawFacts.containsKey(sectionKey(section))) {
        this.completedSections.add(section);
      }
    }
  }

  static Scenario rehydrate(UUID tenantId, UUID scenarioId, UUID lineageId, int version, ScenarioStatus status, String quoteIntent, String channel,
      String scenarioName, String externalLoanId, String sourceSystem, Map<String, Object> rawFacts, Map<String, Object> normalizedFacts,
      Map<String, Object> derivedFields, List<ValidationIssue> validationIssues, List<VersionManifest> versions, String replayHash) {
    return new Scenario(tenantId, scenarioId, lineageId, version, status, quoteIntent, channel, scenarioName, externalLoanId, sourceSystem,
        rawFacts, normalizedFacts, derivedFields, validationIssues, versions, replayHash);
  }

  public static Scenario cloneOf(Scenario source, String scenarioName, Map<String, Object> overrides) {
    return new Scenario(source, scenarioName, overrides);
  }

  public void updateBorrowers(BorrowerCreditRequest request) {
    requireVersion(request.scenarioVersion());
    List<BorrowerCredit> borrowers = Optional.ofNullable(request.borrowers()).orElse(List.of());
    List<ValidationIssue> issues = new ArrayList<>();
    long primaryCount = borrowers.stream().filter(b -> "PRIMARY".equals(b.borrowerRole())).count();
    if (borrowers.isEmpty()) issues.add(issue("MISSING_BORROWER", "borrowers", Severity.BLOCKING, "At least one borrower is required."));
    if (primaryCount != 1) issues.add(issue("DUPLICATE_PRIMARY_BORROWER", "borrowers.borrowerRole", Severity.BLOCKING, "Exactly one primary borrower is required."));
    List<Integer> scores = new ArrayList<>();
    for (int i = 0; i < borrowers.size(); i++) {
      BorrowerCredit b = borrowers.get(i);
      if (b.creditScore() != null && (b.creditScore() < 300 || b.creditScore() > 850)) issues.add(issue("CREDIT_SCORE_OUT_OF_RANGE", "borrowers[" + i + "].creditScore", Severity.BLOCKING, "Credit score must be 300..850."));
      if ("AVAILABLE".equals(b.creditStatus()) && b.creditScore() == null) issues.add(issue("MISSING_CREDIT_SCORE", "borrowers[" + i + "].creditScore", Severity.BLOCKING, "Available credit requires a score."));
      if (b.creditScoreDate() != null && b.creditScoreDate().isAfter(LocalDate.now())) issues.add(issue("FUTURE_CREDIT_SCORE_DATE", "borrowers[" + i + "].creditScoreDate", Severity.BLOCKING, "Credit score date cannot be in the future."));
      if (b.creditScoreDate() != null && b.creditScoreDate().isBefore(LocalDate.now().minusDays(120))) issues.add(issue("STALE_CREDIT_SCORE", "borrowers[" + i + "].creditScoreDate", Severity.WARNING, "Credit score is older than 120 days."));
      if (b.creditScore() != null) scores.add(b.creditScore());
    }
    rawFacts.put("borrowers", borrowers);
    if (!scores.isEmpty()) derivedFields.put("representativeCreditScore", Collections.min(scores));
    replaceSectionIssues("BORROWER_CREDIT", issues);
    completeWhenNoBlocking("BORROWER_CREDIT", issues);
    bump("UPDATE_BORROWER_CREDIT");
  }

  public void updateLoan(LoanStructureRequest r) {
    requireVersion(r.scenarioVersion());
    List<ValidationIssue> issues = new ArrayList<>();
    if (nonPositive(r.loanAmount())) issues.add(issue("INVALID_LOAN_AMOUNT", "loanAmount", Severity.BLOCKING, "Loan amount must be positive."));
    if (nonPositive(r.temporaryPropertyValueForLtv())) issues.add(issue("MISSING_LTV_DENOMINATOR", "temporaryPropertyValueForLtv", Severity.BLOCKING, "Property value is required for ratios."));
    if (!Set.of(180, 240, 360).contains(r.termMonths())) issues.add(issue("TERM_NOT_ENABLED", "termMonths", Severity.BLOCKING, "Unsupported term."));
    if (!Set.of(15, 30, 45, 60).contains(r.requestedLockPeriodDays())) issues.add(issue("LOCK_PERIOD_NOT_ENABLED", "requestedLockPeriodDays", Severity.BLOCKING, "Unsupported lock period."));
    rawFacts.put("loanStructure", r);
    if (issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKING)) {
      BigDecimal denominator = r.temporaryPropertyValueForLtv();
      BigDecimal sub = defaultZero(r.subordinateFinancingAmount());
      BigDecimal drawn = defaultZero(r.helocDrawnAmount());
      BigDecimal limit = defaultZero(r.helocLimitAmount());
      putRatio("ltv", r.loanAmount().divide(denominator, 5, RoundingMode.HALF_UP));
      putRatio("cltv", r.loanAmount().add(sub).add(drawn).divide(denominator, 5, RoundingMode.HALF_UP));
      putRatio("hcltv", r.loanAmount().add(sub).add(limit).divide(denominator, 5, RoundingMode.HALF_UP));
    }
    replaceSectionIssues("LOAN_STRUCTURE", issues);
    completeWhenNoBlocking("LOAN_STRUCTURE", issues);
    bump("UPDATE_LOAN_STRUCTURE");
  }

  public void updateProperty(PropertyRequest r) {
    requireVersion(r.scenarioVersion());
    List<ValidationIssue> issues = new ArrayList<>();
    if (!Set.of("TX", "CA", "FL", "NY", "AZ", "CO").contains(r.propertyState())) issues.add(issue("MARKET_NOT_ACTIVE", "propertyState", Severity.BLOCKING, "State is not active."));
    if (!Set.of("SINGLE_FAMILY", "CONDO", "TOWNHOME", "PUD", "MULTI_UNIT").contains(r.propertyType())) issues.add(issue("PROPERTY_TYPE_NOT_ENABLED", "propertyType", Severity.BLOCKING, "Unsupported property type."));
    if (r.units() < 1 || r.units() > 4) issues.add(issue("INVALID_UNIT_COUNT", "units", Severity.BLOCKING, "Units must be 1..4."));
    if (r.propertyZip() == null || !r.propertyZip().matches("\\d{5}(-?\\d{4})?")) issues.add(issue("INVALID_ZIP", "propertyZip", Severity.BLOCKING, "ZIP must be 5 or 9 digits."));
    if (r.propertyCounty() == null || r.propertyCounty().isBlank()) issues.add(issue("MISSING_COUNTY", "propertyCounty", Severity.WARNING, "County should be supplied for compliance."));
    rawFacts.put("property", r);
    if (issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKING)) {
      derivedFields.put("propertyEligibilityFlags", List.of(r.units() + "_UNIT", r.occupancyType(), r.propertyType()));
    }
    replaceSectionIssues("PROPERTY", issues);
    completeWhenNoBlocking("PROPERTY", issues);
    bump("UPDATE_PROPERTY");
  }

  public void updateIncomeAssets(IncomeAssetRequest r) {
    requireVersion(r.scenarioVersion());
    List<ValidationIssue> issues = new ArrayList<>();
    if (nonPositive(r.monthlyIncome())) issues.add(issue("INVALID_MONTHLY_INCOME", "monthlyIncome", Severity.BLOCKING, "Monthly income must be positive."));
    if (r.monthlyDebt() != null && r.monthlyDebt().compareTo(BigDecimal.ZERO) < 0) issues.add(issue("INVALID_MONTHLY_DEBT", "monthlyDebt", Severity.BLOCKING, "Monthly debt cannot be negative."));
    rawFacts.put("incomeAssets", r);
    if (issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKING)) {
      BigDecimal dti = defaultZero(r.monthlyDebt()).divide(r.monthlyIncome(), 5, RoundingMode.HALF_UP);
      putRatio("dti", dti);
      derivedFields.put("reserveAmount", defaultZero(r.liquidAssets()));
      derivedFields.put("selfEmployed", r.selfEmployed());
      derivedFields.put("giftFunds", r.giftFunds());
    }
    replaceSectionIssues("INCOME_ASSETS", issues);
    completeWhenNoBlocking("INCOME_ASSETS", issues);
    bump("UPDATE_INCOME_ASSETS");
  }

  public void normalize() {
    validateAll();
    normalizedFacts.clear();
    normalizedFacts.put("scenarioId", scenarioId.toString());
    normalizedFacts.put("quoteIntent", quoteIntent);
    normalizedFacts.put("channel", channel);
    normalizedFacts.put("completedSections", completedSections);
    normalizedFacts.put("derivedFields", derivedFields);
    normalizedFacts.put("rawFacts", rawFacts);
    derivedFields.put("scenarioHash", Hashing.sha256(normalizedFacts.toString()));
    status = validationIssues.stream().anyMatch(i -> i.severity() == Severity.BLOCKING) ? ScenarioStatus.DRAFT_INCOMPLETE : ScenarioStatus.NORMALIZED;
    bump("NORMALIZE_SCENARIO");
  }

  public void submit() {
    validateAll();
    if (validationIssues.stream().anyMatch(i -> i.severity() == Severity.BLOCKING)) throw new ScenarioException(HttpStatus.UNPROCESSABLE_ENTITY, "SCENARIO_NOT_READY", "Scenario has blocking validation issues.", validationIssues);
    status = ScenarioStatus.READY_FOR_ELIGIBILITY;
    bump("SUBMIT_SCENARIO");
  }

  private void validateDraft() {
    validationIssues.clear();
    if (!Set.of("PURCHASE", "RATE_TERM_REFI", "CASH_OUT_REFI", "SCENARIO_ANALYSIS").contains(quoteIntent)) validationIssues.add(issue("REQUIRED_FIELD_MISSING", "quoteIntent", Severity.BLOCKING, "Valid quote intent is required."));
    if (!Set.of("RETAIL", "WHOLESALE", "CORRESPONDENT", "CONSUMER_DIRECT", "PARTNER_API").contains(channel)) validationIssues.add(issue("CHANNEL_NOT_ENABLED", "channel", Severity.BLOCKING, "Channel is not enabled."));
    if (sourceSystem == null || sourceSystem.isBlank()) validationIssues.add(issue("REQUIRED_FIELD_MISSING", "sourceSystem", Severity.BLOCKING, "Source system is required."));
  }

  private void validateAll() {
    validateDraft();
    for (String section : List.of("BORROWER_CREDIT", "LOAN_STRUCTURE", "PROPERTY", "INCOME_ASSETS")) {
      if (!completedSections.contains(section)) validationIssues.add(issue("MISSING_" + section, section, Severity.BLOCKING, section + " is required."));
    }
  }

  private void bump(String reason) {
    version++;
    snapshot(reason);
  }

  private void snapshot(String reason) {
    replayHash = Hashing.sha256(tenantId + ":" + scenarioId + ":" + version + ":" + rawFacts + ":" + normalizedFacts + ":" + derivedFields + ":" + validationIssues);
    versions.add(new VersionManifest(version, reason, replayHash, Instant.now()));
  }

  private void requireVersion(int expected) {
    if (expected != version) throw new ScenarioException(HttpStatus.CONFLICT, "SCENARIO_VERSION_CONFLICT", "Expected version " + version + " but got " + expected + ".", List.of());
  }

  private void replaceSectionIssues(String section, List<ValidationIssue> issues) {
    validationIssues.removeIf(i -> i.code().contains(section) || i.fieldPath().startsWith(section) || completedSections.contains(section));
    validationIssues.addAll(issues);
  }

  private void completeWhenNoBlocking(String section, List<ValidationIssue> issues) {
    if (issues.stream().noneMatch(i -> i.severity() == Severity.BLOCKING)) completedSections.add(section); else completedSections.remove(section);
  }

  private void putRatio(String name, BigDecimal value) {
    derivedFields.put(name, value.setScale(5, RoundingMode.HALF_UP));
    derivedFields.put(name + "Bps", value.multiply(new BigDecimal("10000")).setScale(4, RoundingMode.HALF_UP));
  }

  private static BigDecimal defaultZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
  private static boolean nonPositive(BigDecimal value) { return value == null || value.compareTo(BigDecimal.ZERO) <= 0; }
  private static ValidationIssue issue(String code, String field, Severity severity, String message) { return new ValidationIssue(code, field, severity, message); }

  private static String sectionKey(String section) {
    return switch (section) {
      case "BORROWER_CREDIT" -> "borrowers";
      case "LOAN_STRUCTURE" -> "loanStructure";
      case "PROPERTY" -> "property";
      case "INCOME_ASSETS" -> "incomeAssets";
      default -> section;
    };
  }

  public UUID tenantId() { return tenantId; }
  public UUID scenarioId() { return scenarioId; }
  public UUID lineageId() { return lineageId; }
  public int version() { return version; }
  public ScenarioStatus status() { return status; }
  public String quoteIntent() { return quoteIntent; }
  public String channel() { return channel; }
  public String scenarioName() { return scenarioName; }
  public String externalLoanId() { return externalLoanId; }
  public String sourceSystem() { return sourceSystem; }
  public Set<String> completedSections() { return Collections.unmodifiableSet(completedSections); }
  public List<ValidationIssue> validationIssues() { return Collections.unmodifiableList(validationIssues); }
  public Map<String, Object> derivedFields() { return Collections.unmodifiableMap(derivedFields); }
  public Map<String, Object> rawFacts() { return Collections.unmodifiableMap(rawFacts); }
  public Map<String, Object> normalizedFacts() { return Collections.unmodifiableMap(normalizedFacts); }
  public List<VersionManifest> versions() { return Collections.unmodifiableList(versions); }
  public String replayHash() { return replayHash; }
}

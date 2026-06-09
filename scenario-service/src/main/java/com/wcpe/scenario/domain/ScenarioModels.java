package com.wcpe.scenario.domain;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

enum ScenarioStatus { DRAFT_INCOMPLETE, READY_FOR_ELIGIBILITY, NORMALIZED, SUBMITTED, REJECTED }

enum Severity { BLOCKING, WARNING }

record ValidationIssue(String code, String fieldPath, Severity severity, String message) {}

record CreateScenarioRequest(String quoteIntent, String channel, String scenarioName, String externalLoanId, String sourceSystem, Map<String, Object> initialFacts) {}

record BorrowerCreditRequest(int scenarioVersion, List<BorrowerCredit> borrowers) {}

record BorrowerCredit(String borrowerExternalId, String borrowerRole, boolean occupiesProperty, String creditStatus, Integer creditScore,
    String creditScoreSource, LocalDate creditScoreDate) {}

record LoanStructureRequest(int scenarioVersion, String loanPurpose, BigDecimal loanAmount, String lienPosition, int termMonths,
    String amortizationType, BigDecimal subordinateFinancingAmount, BigDecimal helocDrawnAmount, BigDecimal helocLimitAmount,
    int requestedLockPeriodDays, BigDecimal temporaryPropertyValueForLtv) {}

record LoanMetric(String metricCode, BigDecimal ratioValue, BigDecimal bpsValue, BigDecimal numeratorAmount,
    BigDecimal denominatorAmount, String roundingRule, String qualityStatus) {}

record LoanMetricResult(UUID calculationTraceId, String qualityStatus, List<LoanMetric> metrics, List<ValidationIssue> issues) {}

record LoanStructureResponse(UUID scenarioId, int scenarioVersion, String loanStructureStatus, Map<String, BigDecimal> metrics,
    UUID calculationTraceId, int blockingIssueCount, int warningIssueCount, UUID auditPackageId, List<ValidationIssue> validationIssues) {}

record PropertyRequest(int scenarioVersion, String propertyState, String propertyCounty, String propertyZip, String propertyType,
    String occupancyType, int units, BigDecimal purchasePrice, BigDecimal appraisedValue, Map<String, Object> collateralFlags) {}

record IncomeAssetRequest(int scenarioVersion, BigDecimal monthlyIncome, BigDecimal monthlyDebt, BigDecimal liquidAssets,
    String incomeVerificationType, boolean selfEmployed, boolean giftFunds) {}

record CloneScenarioRequest(String scenarioName, Map<String, Object> overrides) {}

record BatchImportRequest(List<CreateScenarioRequest> scenarios) {}

record BatchImportResponse(UUID importId, int acceptedCount, int rejectedCount, List<ScenarioResponse> scenarios, List<ValidationIssue> issues) {}

record ChannelSubmissionProfile(String channel, Set<String> requiredSections, Set<String> allowedQuoteIntents, int maxBatchSize) {}

record ScenarioResponse(UUID scenarioId, int scenarioVersion, ScenarioStatus status, String quoteIntent, String channel,
    Set<String> completedSections, int blockingIssueCount, int warningIssueCount, Map<String, Object> derivedFields, UUID auditPackageId,
    String replayHash, List<ValidationIssue> validationIssues) {}

record VersionManifest(int version, String reason, String hash, Instant createdAtUtc) {}

record EventRecord(UUID eventId, UUID tenantId, UUID scenarioId, String eventType, int eventVersion, String correlationId,
    Instant occurredAt, Map<String, Object> payload) {}

record AuditRecord(UUID auditPackageId, UUID tenantId, UUID scenarioId, String action, String correlationId, Instant occurredAt, String replayHash) {}

record ScenarioIntakeMetadata(UUID tenantId, String dependencyStatus, List<ScenarioIntakeFieldGroup> fieldGroups,
    List<String> decisionControls, List<ValidationIssue> validationIssues, String auditPackageId, String replayHashRef,
    String correlationId) {}

record ScenarioIntakeFieldGroup(String groupId, String label, String helpText, List<ScenarioIntakeField> fields) {}

record ScenarioIntakeField(String fieldId, String label, String groupId, String dataType, boolean required,
    String helpText, String sourceRef, String decisionQuality, List<String> validationMessages) {}

final class ScenarioIntakeMetadataCatalog {
  private ScenarioIntakeMetadataCatalog() {}

  static ScenarioIntakeMetadata metadata(UUID tenantId, String correlationId) {
    return new ScenarioIntakeMetadata(tenantId, "METADATA_AVAILABLE", List.of(
        new ScenarioIntakeFieldGroup("scenario-identity", "Scenario identity",
            "Capture identifiers and channel context before quote decisions.",
            List.of(field("scenarioName", "Scenario name", "scenario-identity", "text", false,
                    "Optional label for support and audit review.", "scenario-service", "VERIFIED", List.of()),
                field("channel", "Channel", "scenario-identity", "text", false,
                    "Originating channel captured as a scenario fact.", "submission-profile", "UNKNOWN",
                    List.of("Submission profile configuration decides whether this field is required.")),
                field("externalLoanId", "External loan id", "scenario-identity", "text", false,
                    "Caller-provided loan reference for replay correlation.", "scenario-service", "VERIFIED", List.of()),
                field("sourceSystem", "Source system", "scenario-identity", "text", false,
                    "Optional upstream source reference for audit correlation.", "scenario-service", "VERIFIED", List.of()))),
        new ScenarioIntakeFieldGroup("borrower-loan-property", "Borrower, loan, and property facts",
            "Capture borrower, loan, and property facts without calculating eligibility, rates, fees, or pricing.",
            List.of(field("borrowerCreditStatus", "Borrower credit status", "borrower-loan-property", "text", false,
                    "Credit status fact supplied by borrower intake or a configured source.", "borrower-credit", "UNKNOWN", List.of()),
                field("creditScore", "Credit score", "borrower-loan-property", "number", false,
                    "Optional score fact; representative score policy runs downstream.", "borrower-credit", "UNKNOWN", List.of()),
                field("loanPurpose", "Loan purpose", "borrower-loan-property", "text", false,
                    "Loan purpose fact for downstream validation.", "loan-structure", "UNKNOWN", List.of()),
                field("loanAmount", "Loan amount", "borrower-loan-property", "number", false,
                    "Requested amount captured as a fact; ratios are not calculated here.", "loan-structure", "UNKNOWN", List.of()),
                field("propertyState", "Property state", "borrower-loan-property", "text", false,
                    "State fact for downstream configured validation.", "property", "UNKNOWN", List.of()),
                field("occupancyType", "Occupancy type", "borrower-loan-property", "text", false,
                    "Occupancy fact for downstream configured validation.", "property", "UNKNOWN", List.of()))),
        new ScenarioIntakeFieldGroup("income-assets", "Income and assets",
            "Capture optional income and asset facts without deriving capacity or pricing.",
            List.of(field("monthlyIncome", "Monthly income", "income-assets", "number", false,
                    "Optional income fact for downstream validation.", "income-assets", "UNKNOWN", List.of()),
                field("liquidAssets", "Liquid assets", "income-assets", "number", false,
                    "Optional asset fact for downstream validation.", "income-assets", "UNKNOWN", List.of())))),
        List.of("Block submit when scenario validation issues are blocking.",
            "Carry audit package and replay hash references with intake state.",
            "Do not calculate pricing or eligibility in intake metadata."),
        List.of(new ValidationIssue("SCENARIO_METADATA_REVIEW_REQUIRED", "scenarioFacts", Severity.WARNING,
            "Review backend-owned scenario facts before submitting downstream quote decisions.")),
        "created-after-draft-scenario", "computed-after-draft-scenario", correlationId);
  }

  private static ScenarioIntakeField field(String fieldId, String label, String groupId, String dataType, boolean required,
      String helpText, String sourceRef, String decisionQuality, List<String> validationMessages) {
    return new ScenarioIntakeField(fieldId, label, groupId, dataType, required, helpText, sourceRef, decisionQuality,
        validationMessages);
  }
}

record BorrowerCreditResponse(UUID scenarioId, int scenarioVersion, String creditReadinessStatus, Integer representativeCreditScore,
    String representativeCreditScoreRule, int borrowerCount, int blockingIssueCount, int warningIssueCount,
    List<String> updatedSections, UUID auditPackageId) {}

final class ScenarioException extends RuntimeException {
  private final HttpStatus status;
  private final String code;
  private final List<ValidationIssue> fieldErrors;

  public ScenarioException(HttpStatus status, String code, String message, List<ValidationIssue> fieldErrors) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
  }

  public HttpStatus status() { return status; }
  public String code() { return code; }
  public List<ValidationIssue> fieldErrors() { return fieldErrors; }
}

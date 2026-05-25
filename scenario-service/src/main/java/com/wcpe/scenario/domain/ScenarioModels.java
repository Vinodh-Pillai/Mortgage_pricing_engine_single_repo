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

record ReplayPackage(UUID scenarioId, int scenarioVersion, String schemaVersion, boolean redactionApplied, ScenarioStatus status,
    List<VersionManifest> versionManifest, Map<String, Object> rawInputSnapshot, Map<String, Object> normalizedSnapshot,
    List<ValidationIssue> validationIssues, List<EventRecord> eventReferences, UUID auditPackageId) {}

record VersionManifest(int version, String reason, String hash, Instant createdAtUtc) {}

record EventRecord(UUID eventId, UUID tenantId, UUID scenarioId, String eventType, int eventVersion, String correlationId,
    Instant occurredAt, Map<String, Object> payload) {}

record AuditRecord(UUID auditPackageId, UUID tenantId, UUID scenarioId, String action, String correlationId, Instant occurredAt, String replayHash) {}

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

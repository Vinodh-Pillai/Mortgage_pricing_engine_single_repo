package com.wcpe.ratefeed.domain;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * RateFeed domain models, statuses, request/response types, and exception class.
 */
public final class RateFeedModels {

  private RateFeedModels() {}

  // ── Status enums ────────────────────────────────────────────────────────────

  public enum BatchStatus {
    UPLOADED, PARSING, VALIDATED, ACTIVE, SUPERSEDED, REJECTED;

    static BatchStatus from(String value) {
      if (value == null) return UPLOADED;
      try { return valueOf(value.toUpperCase(Locale.ROOT)); }
      catch (IllegalArgumentException e) { return UPLOADED; }
    }
  }

  public enum RateSheetStatus {
    DRAFT, PARSING, VALIDATED, PENDING_APPROVAL, APPROVED, SCHEDULED, ACTIVE, PUBLISHED, SUPERSEDED, REJECTED, ROLLBACK_PUBLISHED;

    Set<RateSheetStatus> allowedTransitions() {
      return switch (this) {
        case DRAFT -> Set.of(PARSING);
        case PARSING -> Set.of(VALIDATED, REJECTED);
        case VALIDATED -> Set.of(PENDING_APPROVAL, ACTIVE, REJECTED);
        case PENDING_APPROVAL -> Set.of(APPROVED, REJECTED);
        case APPROVED -> Set.of(SCHEDULED, PUBLISHED, ACTIVE, REJECTED);
        case SCHEDULED -> Set.of(PUBLISHED, ACTIVE, REJECTED);
        case ACTIVE -> Set.of(SUPERSEDED);
        case PUBLISHED -> Set.of(SUPERSEDED, ROLLBACK_PUBLISHED);
        case ROLLBACK_PUBLISHED -> Set.of(SUPERSEDED);
        case SUPERSEDED -> Set.of();
        case REJECTED -> Set.of();
      };
    }

    boolean isTerminal() { return this == REJECTED || this == SUPERSEDED; }
    boolean canActivate() { return this == VALIDATED; }
    boolean canReject() { return this == VALIDATED || this == PARSING; }
  }

  public enum PublishWorkflowDecision { APPROVE, REJECT }

  // ── Existing records (unchanged) ────────────────────────────────────────────

  public record UploadSessionRequest(UUID investorId, UUID channelId, UUID feedFormatId, String sourceType, Instant effectiveAt, String timezone, String fileName, String contentType, long contentLengthBytes, UUID supersedesBatchId, String notes) {}
  public record UploadSessionResponse(UUID uploadSessionId, String uploadUrl, long maxBytes, Instant expiresAt, Map<String, String> requiredHeaders, String status, String resultHash) {}
  public record CompleteUploadRequest(String fileSha256, String storageObjectId, String scanResultId, String scanStatus) {}
  public record CompleteUploadResponse(UUID batchId, String status, UUID rawFileId, UUID parserCommandId, Map<String, String> links, String resultHash) {}

  public record BatchResponse(UUID batchId, UUID uploadSessionId, UUID investorId, UUID channelId, UUID feedFormatId, String sourceType, BatchStatus status, Instant effectiveAt, String timezone, UUID rawFileId, String fileSha256, String fileName, String contentType, long contentLengthBytes, UUID supersedesBatchId, String uploadedBy, String correlationId, String resultHash) {}

  // ── Domain records ──────────────────────────────────────────────────────────

  public record RatePricePoint(
    UUID sheetId,
    BigDecimal noteRate,
    int lockPeriod,
    BigDecimal basePrice,
    BigDecimal discountPoints,  // nullable
    BigDecimal yieldIndex,       // nullable
    int gridPosition
  ) {}

  public record RateSheet(
    UUID sheetId,
    UUID tenantId,
    UUID investorId,
    UUID channelId,
    String productCode,
    int version,
    RateSheetStatus status,
    Instant effectiveAt,
    Instant effectiveUntil,           // nullable
    String fileSha256,
    String gridHash,                  // nullable before PARSING
    String gridPointsJson,
    int rowCount,
    String resultHash,
    Instant createdAt,
    String createdBy,
    Instant activatedAt,             // nullable
    String activatedBy,              // nullable
    Instant rejectedAt,             // nullable
    String rejectedBy,              // nullable
    String rejectionReason,          // nullable
    Instant updatedAt
  ) {}

  public record ActivationAudit(
    UUID auditId,
    UUID sheetId,
    int version,
    String actorId,
    String correlationId,
    Instant activatedAt,
    String approvalReference,        // nullable
    String gridHashBefore,           // nullable for first activation
    String gridHashAfter,
    String notes                      // nullable
  ) {}

  // ── Validation records ──────────────────────────────────────────────────────

  public record ValidationErrorDetail(
    String code,
    String message,
    Integer row,
    String field
  ) {}

  public record ValidationWarningDetail(
    String code,
    String message
  ) {}

  public record ValidationResultDetail(
    int rowCount,
    int pointCount,
    String gridHash,
    List<ValidationErrorDetail> errors,
    List<ValidationWarningDetail> warnings,
    int duplicatePairs,
    int missingCells,
    int outOfRange,
    boolean valid
  ) {}

  public record ValidationResultResponse(
    UUID sheetId,
    String status,
    ValidationResultDetail validationResult
  ) {}

  public record ValidateRequest(
    String idempotencyKey,
    boolean strict
  ) {}

  // ── Controller request/response records ────────────────────────────────────

  public record ImportRequest(
    String fileName,
    UUID investorId,
    UUID channelId,
    String productCode,
    Instant effectiveAt,
    String notes,
    String idempotencyKey
  ) {}

  public record ImportResponse(
    UUID sheetId,
    UUID batchId,
    String status,
    int version,
    String gridHash,
    String resultHash,
    Map<String, String> links
  ) {}

  public record ActivateRequest(
    String idempotencyKey,
    String approvalReference,
    Instant effectiveUntil,
    String notes
  ) {}

  public record ActivateResponse(
    UUID sheetId,
    int version,
    String status,
    Instant activatedAt,
    String activatedBy,
    Map<String, Instant> effectiveWindow,
    String gridHash,
    UUID auditId,
    Integer supersededVersion,
    UUID supersededSheetId
  ) {}

  public record ResolvedSheet(
    UUID sheetId,
    int version,
    String gridHash,
    int pointCount,
    String resultHash
  ) {}

  public record ResolveResponse(
    UUID sheetId,
    int version,
    UUID investorId,
    UUID channelId,
    String productCode,
    int lockPeriod,
    Instant effectiveAt,
    String gridHash,
    int pointCount,
    Instant resolutionTimestamp,
    String resultHash
  ) {}

  public record GridResponse(
    UUID sheetId,
    int version,
    String gridHash,
    List<RatePricePoint> pricePoints,
    int pointCount
  ) {}

  public record PriceLookupResponse(
    BigDecimal noteRate,
    int lockPeriod,
    BigDecimal basePrice,
    BigDecimal discountPoints,
    String match,
    String resultHash
  ) {}

  public record SheetDetailResponse(
    UUID sheetId,
    UUID investorId,
    UUID channelId,
    String productCode,
    int version,
    String status,
    String gridHash,
    Map<String, Instant> effectiveWindow,
    ActivationAudit activationAudit,
    UUID supersededBy,
    Integer previousVersion
  ) {}

  public record SheetListResponse(List<SheetSummary> sheets, int count) {}

  public record SheetSummary(
    UUID sheetId,
    UUID investorId,
    UUID channelId,
    String productCode,
    int version,
    String status,
    String gridHash,
    Instant effectiveAt,
    Instant createdAt
  ) {}

  public record RejectRequest(
    String idempotencyKey,
    String reason,
    List<Map<String, Object>> validationErrors
  ) {}

  public record RejectResponse(
    UUID sheetId,
    String status,
    Instant rejectedAt,
    String rejectedBy,
    String reason
  ) {}

  public record MatchType(String value) {
    public static final MatchType EXACT = new MatchType("exact");
    public static final MatchType INTERPOLATED = new MatchType("interpolated");
    public static final MatchType NEAREST = new MatchType("nearest");
  }

  // ── Hardening records (version list, replay) ───────────────────────────────

  public record SheetVersionResponse(UUID sheetId, int version, UUID investorId, UUID channelId, String productCode, String status, String gridHash, Instant effectiveAt, Instant createdAt) {}
  public record SheetVersionsResponse(List<SheetVersionResponse> sheets, int count) {}

  public record ReplayRequest(UUID investorId, UUID channelId, String productCode, int lockPeriod, Instant asOfDate, Integer sheetVersion) {}
  public record ReplayResult(UUID replayId, UUID sheetId, int version, String inputHash, String outputHash, List<RatePricePoint> pricePoints, int pointCount, String status, Instant replayedAt) {}

  // ── PII-04-S01: Batch list endpoint ────────────────────────────────────────

  public record BatchListSummary(
    UUID batchId,
    UUID investorId,
    UUID channelId,
    UUID feedFormatId,
    String sourceType,
    Instant effectiveAt,
    String timezone,
    String status,
    String fileName,
    String uploadedBy,
    Instant createdAt,
    Integer rowCount,
    Integer errorCount
  ) {}

  public record BatchListResponse(List<BatchListSummary> batches, int count) {}

  public record ParseBatchRequest(String parseMode, String expectedFileSha256, String csvContent) {}
  public record ParseBatchResponse(UUID parseJobId, UUID batchId, String status) {}
  public record ParsedFieldResult(int rowNumber, String fieldName, String rawValue, String normalizedCandidate, String severity, String errorCode, String message) {}
  public record ParseResultPage(List<ParsedFieldResult> rows, int page, int size, long total) {}

  public record NormalizeBatchRequest(String expectedParseResultHash, UUID normalizationProfileId) {}
  public record NormalizeBatchResponse(UUID normalizationJobId, String status) {}
  public record NormalizedEntryResponse(
    UUID entryId,
    UUID batchId,
    int sourceRowId,
    String canonicalProductKey,
    String programKey,
    BigDecimal ratePercent,
    BigDecimal pricePoints,
    int lockPeriodDays,
    String adjustmentType,
    BigDecimal adjustmentValue,
    String adjustmentUnit,
    Map<String, Object> dimensions,
    Map<String, Object> rawAttributes,
    Map<String, Object> mappingRefs,
    String severity,
    String message,
    String mappingVersion
  ) {}
  public record NormalizedEntryPage(List<NormalizedEntryResponse> entries, int page, int size, long total) {}

  public record CreateRateSheetVersionRequest(
    UUID batchId,
    String productKey,
    String versionLabel,
    Instant effectiveFrom,
    Instant effectiveTo,
    String lineageReasonCode,
    UUID parentVersionId,
    String notes
  ) {}

  public record RateSheetVersionCreatedResponse(
    UUID rateSheetVersionId,
    int versionNumber,
    String status,
    List<ValidationWarningDetail> conflicts
  ) {}

  public record RateSheetVersionSummary(
    UUID rateSheetVersionId,
    int versionNumber,
    String versionLabel,
    String status,
    UUID investorId,
    UUID channelId,
    String productKey,
    Instant effectiveFrom,
    Instant effectiveTo,
    UUID batchId,
    Instant createdAt,
    String resultHash
  ) {}

  public record RateSheetVersionListResponse(List<RateSheetVersionSummary> versions, int count) {}

  public record RateSheetVersionResolveResponse(
    UUID rateSheetVersionId,
    int versionNumber,
    UUID investorId,
    UUID channelId,
    String productKey,
    Instant effectiveFrom,
    Instant effectiveTo,
    String status,
    String resultHash
  ) {}

  public record SubmitApprovalRequest(String changeSummary, Instant effectiveFrom, Instant effectiveTo) {}
  public record ApprovalDecisionRequest(PublishWorkflowDecision decision, String reasonCode, String comment) {}
  public record PublishRateSheetRequest(Instant publishAt, String expectedValidationResultHash, String expectedVersionHash) {}
  public record RollbackRateSheetRequest(UUID targetVersionId, String reasonCode, String comment) {}
  public record PublishWorkflowStateResponse(
    UUID rateSheetVersionId,
    String status,
    String approvalStatus,
    String submittedBy,
    String approvedBy,
    Instant submittedAt,
    Instant approvedAt,
    Instant publishedAt,
    UUID targetVersionId,
    String eventType,
    String auditAction,
    String cacheInvalidationCommandId,
    String resultHash,
    List<ValidationWarningDetail> warnings
  ) {}

  // ── PII-04-S08: Rate sheet cache invalidation records ─────────────────────

  public enum CacheInvalidationReason { PUBLISH, ROLLBACK, GOVERNANCE_CHANGE, MANUAL_RETRY }
  public enum CacheInvalidationStatus { PENDING, COMPLETED, PARTIAL, FAILED, RETRYING, BROKER_UNAVAILABLE }

  public record RateSheetCacheInvalidationRequest(
    CacheInvalidationReason reason,
    UUID versionId,
    UUID investorId,
    UUID channelId,
    Instant effectiveAt,
    String expectedVersionHash
  ) {}

  public record RateSheetCacheInvalidationResponse(
    UUID cacheInvalidationId,
    String status,
    List<String> affectedPatterns
  ) {}

  public record RateSheetCacheInvalidationDetailResponse(
    UUID cacheInvalidationId,
    UUID tenantId,
    UUID versionId,
    CacheInvalidationReason reason,
    String status,
    List<String> affectedPatterns,
    String requestedBy,
    Instant createdAt,
    Instant completedAt,
    int retryCount,
    String lastErrorCode,
    String correlationId
  ) {}

  public record CacheInvalidationAck(
    UUID ackId,
    UUID cacheInvalidationId,
    String consumerName,
    String consumerInstance,
    String status,
    Instant ackedAt,
    Map<String, Object> details
  ) {}

  // ── PII-04-S06: Validation job request/response records ──

  public enum ValidationFindingSeverity { BLOCKER, WARNING, INFO }

  public enum ValidationJobStatus { RUNNING, PASSED, PASSED_WITH_WARNINGS, FAILED, STALE }

  public record ValidateBatchRequest(String expectedNormalizationHash, UUID validationProfileId) {}

  public record ValidateBatchResponse(UUID validationJobId, String status) {}

  public record ValidationFindingRecord(
    UUID findingId,
    UUID entryId,
    ValidationFindingSeverity severity,
    String ruleCode,
    String ruleVersion,
    String fieldName,
    String messageCode,
    Map<String, Object> messageParams,
    String remediationCode,
    Integer sourceRowNumber,
    Instant createdAt
  ) {}

  public record ValidationReportResponse(
    UUID validationJobId,
    UUID batchId,
    String status,
    String profileVersion,
    Instant startedAt,
    Instant completedAt,
    int blockingErrorCount,
    int warningCount,
    String resultHash,
    List<ValidationFindingRecord> findings
  ) {}

  public record ValidationProfileConfig(
    String profileId,
    String profileVersion,
    Map<String, Object> ruleConfigs
  ) {}

  // ── PII-04-S07: Local OCR rate sheet intake records ──

  public record StartOcrExtractionRequest(String expectedFileSha256, UUID ocrProfileId) {}

  public record OcrExtractionResponse(UUID ocrExtractionId, UUID batchId, String status, String resultHash) {}

  public record ReviewOcrCellRequest(String reviewedText) {}

  public record OcrCellReviewResponse(UUID ocrExtractionId, UUID cellId, String status, String resultHash) {}

  public record ApproveOcrExtractionRequest(String expectedResultHash) {}

  public record OcrApprovalResponse(UUID ocrExtractionId, UUID batchId, String status, String parserHandoffArtifact, String resultHash) {}

  // ── PII-04-S09: Partner investor feed API records ─────────────────────────

  public record PartnerFileSubmissionMetadata(
    String investorExternalKey,
    String channelExternalKey,
    String feedFormatExternalKey,
    String schemaVersion,
    Instant effectiveAt,
    String timezone,
    String sourceSystem
  ) {}

  public record PartnerStructuredSubmissionRequest(
    String investorExternalKey,
    String channelExternalKey,
    String feedFormatExternalKey,
    String schemaVersion,
    Instant effectiveAt,
    String timezone,
    Map<String, String> metadata,
    List<Map<String, Object>> rows
  ) {}

  public record PartnerSubmissionResponse(UUID submissionId, UUID batchId, String status, String statusUrl, Instant receivedAt, String resultHash) {}

  public record PartnerSubmissionStatusResponse(
    UUID submissionId,
    UUID batchId,
    String status,
    String tenantExternalKey,
    String investorExternalKey,
    String channelExternalKey,
    String feedFormat,
    String schemaVersion,
    Instant receivedAt,
    String resultHash
  ) {}

  // ── PII-04-S10: Ingestion Audit Report records ────────────────────────────

  public enum AuditRedactionLevel { STANDARD, ELEVATED }
  public enum AuditReportFormat { JSON, CSV, PDF }
  public enum ReplayVerificationStatus { PASSED, FAILED }

  public record AuditTimelineEvent(
    UUID auditEventId,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    String actorId,
    String actorType,
    String correlationId,
    String causationId,
    Instant occurredAt,
    String sourceService,
    String beforeHash,
    String afterHash,
    Map<String, Object> evidenceRefs,
    String resultHash,
    AuditRedactionLevel redactionLevel
  ) {}

  public record AuditTimelinePage(
    List<AuditTimelineEvent> events,
    int page,
    int size,
    long totalEvents
  ) {}

  public record AuditTimelineRequest(
    Instant from,
    Instant to,
    UUID investorId,
    UUID channelId,
    UUID batchId,
    UUID versionId,
    String actorId,
    String eventType,
    String status,
    String correlationId,
    int page,
    int size
  ) {}

  public record AuditReportResponse(
    UUID snapshotId,
    UUID batchId,
    UUID versionId,
    String status,
    int eventCount,
    String snapshotHash,
    Instant generatedAt,
    String generatedBy,
    Map<String, String> links
  ) {}

  public record AuditExportRequest(
    AuditReportFormat format,
    boolean includeRawValues,
    String reasonCode
  ) {}

  public record AuditExportResponse(
    UUID snapshotId,
    String format,
    String status,
    String storageObjectId,
    String snapshotHash,
    Instant retentionUntil,
    Map<String, String> links
  ) {}

  public record VerifyReplayRequest(
    String expectedHash,
    String correlationId
  ) {}

  public record VerifyReplayResponse(
    UUID verificationId,
    UUID batchId,
    String expectedHash,
    String actualHash,
    ReplayVerificationStatus status,
    String mismatchClassification,
    Instant verifiedAt
  ) {}

  // ── Exception class ─────────────────────────────────────────────────────────

  public static final class RateFeedException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RateFeedException(HttpStatus status, String code, String message) {
      super(message);
      this.status = status;
      this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
  }
}

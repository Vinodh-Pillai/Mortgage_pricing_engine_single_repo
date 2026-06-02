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
    DRAFT, PARSING, VALIDATED, ACTIVE, SUPERSEDED, REJECTED;

    Set<RateSheetStatus> allowedTransitions() {
      return switch (this) {
        case DRAFT -> Set.of(PARSING);
        case PARSING -> Set.of(VALIDATED, REJECTED);
        case VALIDATED -> Set.of(ACTIVE, REJECTED);
        case ACTIVE -> Set.of(SUPERSEDED);
        case SUPERSEDED -> Set.of();
        case REJECTED -> Set.of();
      };
    }

    boolean isTerminal() { return this == REJECTED || this == SUPERSEDED; }
    boolean canActivate() { return this == VALIDATED; }
    boolean canReject() { return this == VALIDATED || this == PARSING; }
  }

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

package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.activation.ActivationService;
import com.wcpe.ratefeed.activation.VersionManager;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.parser.RateSheetParser;
import com.wcpe.ratefeed.validation.RateSheetValidator;
import com.wcpe.ratefeed.resolution.GridLookup;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.role.RateFeedRoles;
import com.wcpe.ratefeed.service.ReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// Static imports for nested types in RateFeedModels
import static com.wcpe.ratefeed.domain.RateFeedModels.*;
import static com.wcpe.ratefeed.domain.RateFeedModels.RateSheetStatus;

@Service
class RateFeedService {
  private static final long MAX_BYTES = 25L * 1024L * 1024L;
  private static final int MAX_STORAGE_OBJECT_ID_LENGTH = 512;
  private static final int MAX_SCAN_RESULT_ID_LENGTH = 256;
  private static final String LOCAL_UPLOAD_PREFIX = "local://rate-feed-upload/";
  private static final String LOCAL_SYNTHETIC_PREFIX = "local://synthetic/";

  private final RateFeedRepository repository;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final RateSheetParser parser = new RateSheetParser();
  private final RateSheetValidator validator = new RateSheetValidator();
  private final ActivationService activationService;
  private final VersionManager versionManager;
  private final RateResolver rateResolver;
  private final GridLookup gridLookup;
  private final AuditService auditService;
  private final ReplayService replayService;

  RateFeedService(RateFeedRepository repository, JdbcTemplate jdbc, ObjectMapper mapper,
                   ActivationService activationService, VersionManager versionManager,
                   RateResolver rateResolver, GridLookup gridLookup, AuditService auditService,
                   ReplayService replayService) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.activationService = activationService;
    this.versionManager = versionManager;
    this.rateResolver = rateResolver;
    this.gridLookup = gridLookup;
    this.auditService = auditService;
    this.replayService = replayService;
  }

  // ── Existing session lifecycle ──

  @Transactional
  UploadSessionResponse createSession(UUID tenantId, UploadSessionRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_UPLOAD);
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "createUploadSession");
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, UploadSessionResponse.class, () -> {
      // V-005: Consolidate redundant validation — single unified check per field
      validateRequest(request);
      UUID sessionId = UUID.randomUUID();
      Instant expiresAt = Instant.now().plus(Duration.ofMinutes(30));
      String resultHash = Hashing.sha256(repository.json(Map.of("uploadSessionId", sessionId, "fileName", request.fileName(), "effectiveAt", request.effectiveAt().toString())));
      UploadSessionResponse response = new UploadSessionResponse(sessionId, "local://rate-feed-upload/" + sessionId, MAX_BYTES, expiresAt, Map.of("x-upload-sha256", "required-on-complete"), "OPEN", resultHash);
      repository.saveSession(tenantId, sessionId, request, actor(actor), correlation(correlationId), Hashing.sha256(repository.json(idempotencyIdentity)), response);
      return response;
    });
  }

  @Transactional
  CompleteUploadResponse complete(UUID tenantId, UUID sessionId, CompleteUploadRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_UPLOAD);
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "completeUploadSession");
    idempotencyIdentity.put("uploadSessionId", sessionId);
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, CompleteUploadResponse.class, () -> {
      validateCompletion(request);
      RateFeedRepository.UploadSessionRow session = repository.session(tenantId, sessionId);
      if (!"OPEN".equals(session.status())) throw new RateFeedException(HttpStatus.CONFLICT, "UPLOAD_SESSION_NOT_OPEN", "Upload session is not open.");
      if (Instant.now().isAfter(session.expiresAt())) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "UPLOAD_SESSION_EXPIRED", "Upload session has expired.");
      return repository.complete(tenantId, session, request, actor(actor), correlation(correlationId));
    });
  }

  BatchResponse batch(UUID tenantId, UUID batchId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    return repository.batch(tenantId, batchId);
  }

  // ── V-005: Consolidated validation (replaces redundant null checks) ──

  /**
   * V-005 fix: Unified field validation — null check is sufficient for UUID fields.
   * UUID.toString() never returns blank, so the old duplicate null+blank check is removed.
   */
  private void validateRequest(UploadSessionRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    requireUuid("INVESTOR_REQUIRED", "Investor ID is required for governance tracking.", request.investorId());
    requireUuid("CHANNEL_REQUIRED", "Channel ID is required for governance tracking.", request.channelId());
    requireUuid("FEED_FORMAT_REQUIRED", "Feed format ID is required for governance tracking.", request.feedFormatId());

    // Non-UUID required fields
    if (request.effectiveAt() == null) throw validation("EFFECTIVE_TIME_REQUIRED", "Effective time is required.");
    if (request.timezone() == null || request.timezone().isBlank()) throw validation("EFFECTIVE_TIME_REQUIRED", "Timezone is required.");
    try { ZoneId.of(request.timezone()); } catch (DateTimeException ex) { throw validation("MALFORMED_METADATA", "Timezone metadata is not valid."); }
    if (request.fileName() == null || request.fileName().isBlank()) throw validation("FILE_NAME_REQUIRED", "File name is required.");
    rejectFormulaMetadata("fileName", request.fileName());
    rejectFormulaMetadata("notes", request.notes());
    rejectFormulaMetadata("sourceType", request.sourceType());
    if (!request.fileName().toLowerCase(Locale.ROOT).endsWith(".csv")) throw new RateFeedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Only CSV rate sheets are supported.");
    if (!Set.of("text/csv", "application/csv", "application/vnd.ms-excel").contains(normalizeMediaType(request.contentType()))) throw new RateFeedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type.");
    if (request.contentLengthBytes() <= 0) throw validation("CONTENT_LENGTH_REQUIRED", "Content length must be positive.");
    if (request.contentLengthBytes() > MAX_BYTES) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_TOO_LARGE", "File exceeds maximum size.");
    if (request.sourceType() == null || request.sourceType().isBlank()) throw validation("SOURCE_TYPE_REQUIRED", "Source type is required.");
  }

  /** Unified UUID validation — null check alone is sufficient. */
  private static void requireUuid(String code, String message, UUID value) {
    if (value == null) throw validation(code, message);
  }

  private void validateCompletion(CompleteUploadRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.storageObjectId() == null || request.storageObjectId().isBlank()) throw validation("STORAGE_OBJECT_REQUIRED", "Storage object id is required.");
    if (request.fileSha256() == null || !request.fileSha256().matches("^[0-9a-fA-F]{64}$")) throw validation("INVALID_FILE_HASH", "fileSha256 must be a 64 character SHA-256 hex value.");
    rejectFormulaMetadata("storageObjectId", request.storageObjectId());
    validateStorageObjectId(request.storageObjectId());
    validateOptionalObjectReference("scanResultId", request.scanResultId(), MAX_SCAN_RESULT_ID_LENGTH);
    String scanStatus = Optional.ofNullable(request.scanStatus()).orElse("CLEAN").trim().toUpperCase(Locale.ROOT);
    if (!Set.of("CLEAN", "PASS", "PASSED").contains(scanStatus)) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "MALWARE_SCAN_FAILED", "Malware scan did not pass.");
  }

  private static void validateStorageObjectId(String storageObjectId) {
    validateOptionalObjectReference("storageObjectId", storageObjectId, MAX_STORAGE_OBJECT_ID_LENGTH);
    if (storageObjectId.contains("?") || storageObjectId.contains("#") || storageObjectId.contains("@"))
      throw validation("INVALID_STORAGE_OBJECT_REFERENCE", "storageObjectId must not contain query/fragment/credential material.");
    if (storageObjectId.startsWith(LOCAL_UPLOAD_PREFIX)) {
      String suffix = storageObjectId.substring(LOCAL_UPLOAD_PREFIX.length());
      try { UUID.fromString(suffix); return; }
      catch (IllegalArgumentException ex) { throw validation("INVALID_STORAGE_OBJECT_REFERENCE", "local upload storageObjectId must end with a UUID."); }
    }
    if (storageObjectId.startsWith(LOCAL_SYNTHETIC_PREFIX)) {
      String suffix = storageObjectId.substring(LOCAL_SYNTHETIC_PREFIX.length());
      if (!suffix.isBlank() && suffix.matches("^[A-Za-z0-9][A-Za-z0-9._/-]{0,240}$") && !suffix.contains("..")) return;
    }
    throw validation("INVALID_STORAGE_OBJECT_REFERENCE", "storageObjectId must be an allowed local/dev reference.");
  }

  private static void validateOptionalObjectReference(String field, String value, int maxLength) {
    if (value == null || value.isBlank()) return;
    if (value.length() > maxLength) throw validation("OBJECT_REFERENCE_TOO_LONG", field + " exceeds allowed length.");
    for (int i = 0; i < value.length(); i++)
      if (Character.isISOControl(value.charAt(i))) throw validation("INVALID_OBJECT_REFERENCE", field + " contains control characters.");
    String lower = value.toLowerCase(Locale.ROOT);
    if (lower.startsWith("http://") || lower.startsWith("https://")) throw validation("INVALID_OBJECT_REFERENCE", field + " must not persist URLs.");
    if (lower.contains("secret") || lower.contains("token=") || lower.contains("signature="))
      throw validation("INVALID_OBJECT_REFERENCE", field + " must not contain secret material.");
  }

  // ── New delegate methods for rate sheet lifecycle ──

  /**
   * G-001 / GAP-04: Import CSV rate sheet → PARSING.
   *
   * Governance gap (GAP-04): Suspended investor/product validation requires catalog service integration.
   * Current state: fail-open acceptance with metadata presence/format validation only.
   * Before product closure: catalog integration contract OR static suspended list must be added.
   * Risk: Pricing produced for suspended entities — mitigated by PII-05 independent investor/product status check.
   *
   * Error codes: SUSPENDED_INVESTOR (409), SUSPENDED_PRODUCT (409) — reserved for catalog integration.
   */
  @Transactional
  public RateFeedModels.ImportResponse importRateSheet(UUID tenantId, MultipartFile csvFile, Map<String, String> metadata, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_UPLOAD);

    String fileName = Optional.ofNullable(metadata.get("fileName")).filter(s -> !s.isBlank()).orElse(csvFile.getOriginalFilename());
    String investorIdStr = metadata.get("investorId");
    String channelIdStr = metadata.get("channelId");
    String productCode = Optional.ofNullable(metadata.get("productCode")).filter(s -> !s.isBlank()).orElseThrow(() -> validation("PRODUCT_CODE_REQUIRED", "productCode is required in import metadata."));
    String effectiveAtStr = metadata.get("effectiveAt");

    if (investorIdStr == null || investorIdStr.isBlank()) throw validation("INVESTOR_REQUIRED", "investorId is required in import metadata.");
    if (channelIdStr == null || channelIdStr.isBlank()) throw validation("CHANNEL_REQUIRED", "channelId is required in import metadata.");
    if (effectiveAtStr == null || effectiveAtStr.isBlank()) throw validation("EFFECTIVE_TIME_REQUIRED", "effectiveAt is required in import metadata.");

    UUID investorId;
    try { investorId = UUID.fromString(investorIdStr); } catch (IllegalArgumentException ex) { throw validation("INVALID_INVESTOR_ID", "investorId must be a valid UUID."); }
    UUID channelId;
    try { channelId = UUID.fromString(channelIdStr); } catch (IllegalArgumentException ex) { throw validation("INVALID_CHANNEL_ID", "channelId must be a valid UUID."); }
    Instant effectiveAt;
    try { effectiveAt = Instant.parse(effectiveAtStr); } catch (Exception ex) { throw validation("MALFORMED_EFFECTIVE_AT", "effectiveAt must be an ISO-8601 instant."); }

    // Reject formula-like metadata
    rejectFormulaMetadata("fileName", fileName);
    rejectFormulaMetadata("productCode", productCode);

    // CSV-only media type check
    String csvName = Optional.ofNullable(fileName).orElse(csvFile.getOriginalFilename());
    if (csvName != null && !csvName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
      throw new RateFeedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Only CSV rate sheets are supported.");
    }

    // Parse CSV
    RateSheetParser.ParseResult parseResult;
    try {
      UUID sheetIdPlaceholder = UUID.randomUUID();
      RateSheetParser.ParseContext ctx = new RateSheetParser.ParseContext(sheetIdPlaceholder, investorId, channelId, productCode, effectiveAt);
      parseResult = parser.parse(csvFile.getInputStream(), ctx);
    } catch (Exception e) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST, "PARSER_ERROR", e.getMessage());
    }

    if (parseResult.pricePoints().isEmpty()) {
      throw new RateFeedException(HttpStatus.BAD_REQUEST, "EMPTY_RATE_SHEET", "Parsed rate sheet contains no valid data rows.");
    }

    // Create rate_sheet record
    UUID sheetId = UUID.randomUUID();
    int version = versionManager.nextVersion(tenantId, investorId, channelId, productCode);
    String fileSha256 = Hashing.sha256(csvFile.getOriginalFilename() + ":" + csvFile.getSize());
    String gridHash = parseResult.gridHash();
    UUID batchId = UUID.randomUUID();

    jdbc.update("insert into rate_feed.rate_sheet(sheet_id,tenant_id,investor_id,channel_id,product_code,version,status,effective_at,file_sha256,grid_hash,row_count,result_hash,created_by,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,now())",
        sheetId, tenantId, investorId, channelId, productCode, version, "PARSING", Timestamp.from(effectiveAt), fileSha256, gridHash, parseResult.rowCount(),
        Hashing.sha256(sheetId.toString()), actor, Timestamp.from(effectiveAt));

    // Insert price points
    int pos = 0;
    for (RatePricePoint pp : parseResult.pricePoints()) {
      jdbc.update("insert into rate_feed.rate_price_point(sheet_id,note_rate,lock_period,base_price,discount_points,yield_index,grid_position) values (?,?,?,?,?,?,?)",
          sheetId, pp.noteRate(), pp.lockPeriod(), pp.basePrice(), pp.discountPoints(), pp.yieldIndex(), pos);
      pos++;
    }

    String resultHash = Hashing.sha256("import:" + sheetId + ":" + version + ":" + gridHash);

    auditService.emit(sheetId, tenantId, "RATE_SHEET_IMPORTED", actor, correlationId, null, gridHash, version);

    return new RateFeedModels.ImportResponse(sheetId, batchId, "PARSING", version, gridHash, resultHash,
        Map.of("validate", "/api/v1/tenants/" + tenantId + "/rate-sheets/" + sheetId + "/validate",
               "self", "/api/v1/tenants/" + tenantId + "/rate-sheets/" + sheetId));
  }

  /**
   * G-005: Validate parsed rate sheet → VALIDATED or keep PARSING on warnings only.
   * Structural failures (empty grid, parse errors) block validation.
   * Semantic warnings (negative price, out-of-range) are advisory only.
   *
   * Per GAP-03: validation only blocks on structural failures, not semantic warnings.
   */
  @Transactional
  public RateFeedModels.ValidationResultResponse validateRateSheet(UUID sheetId, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_UPLOAD);

    RateSheet sheet = getSheet(sheetId);
    if (sheet == null || sheet.status() != RateSheetStatus.PARSING) {
      throw new RateFeedException(HttpStatus.CONFLICT, "SHEET_NOT_PARSING",
          "Sheet must be in PARSING status to validate. Current: " +
          (sheet != null ? sheet.status().toString() : "NOT_FOUND"));
    }

    // Get price points
    List<RatePricePoint> points = jdbc.query("SELECT * FROM rate_feed.rate_price_point WHERE sheet_id=? ORDER BY note_rate, lock_period",
        (rs, row) -> new RatePricePoint(rs.getObject("sheet_id", UUID.class), rs.getBigDecimal("note_rate"),
            rs.getInt("lock_period"), rs.getBigDecimal("base_price"), rs.getBigDecimal("discount_points"),
            rs.getBigDecimal("yield_index"), rs.getInt("grid_position")), sheetId);

    String gridHash = sheet.gridHash();
    RateFeedModels.ValidationResultResponse result = validator.validate(points, gridHash);

    // Only structural errors block validation; semantic warnings are advisory
    if (result.validationResult().valid()) {
      int rowCount = jdbc.update("update rate_feed.rate_sheet set status='VALIDATED', updated_at=now() where sheet_id=? and status='PARSING'", sheetId);
      if (rowCount == 0) {
        throw new RateFeedException(HttpStatus.CONFLICT, "SHEET_STATUS_CHANGED",
            "Sheet status changed concurrently; retry validation.");
      }
    }
    // If not valid, sheet stays in PARSING; caller can decide to reject

    return result;
  }

  @Transactional
  public RateFeedModels.ActivateResponse activate(UUID sheetId, RateFeedModels.ActivateRequest request, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_ACTIVATE);
    ActivationService.ActivateResult res = activationService.activate(sheetId, request, actor, correlationId);
    return new RateFeedModels.ActivateResponse(res.sheetId(), res.version(), res.status(), res.activatedAt(),
        res.activatedBy(), res.effectiveWindow(), res.gridHash(), res.auditId(), null, res.supersededSheetId());
  }

  @Transactional
  public RateFeedModels.RejectResponse reject(UUID sheetId, RateFeedModels.RejectRequest request, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_ACTIVATE);
    ActivationService.RejectResult res = activationService.reject(sheetId, request, actor, correlationId);
    return new RateFeedModels.RejectResponse(res.sheetId(), res.status(), res.rejectedAt(), res.rejectedBy(), res.reason());
  }

  /**
   * G-003: Resolve highest-version ACTIVE sheet in effective window.
   *
   * Tenant scoping: tenantId is required on every resolution query.
   * PII-05 consumption contract: resolves active grid by (tenantId, investorId, channelId, productCode, resolutionTimestamp).
   * Resolution: find version where effectiveAt <= resolutionTimestamp < effectiveUntil (or effectiveUntil IS NULL) and status == ACTIVE.
   */
  public RateFeedModels.ResolveResponse resolve(UUID tenantId, UUID investorId, UUID channelId, String productCode, int lockPeriod, Instant resolutionTimestamp) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    if (tenantId == null) throw validation("TENANT_REQUIRED", "tenantId is required for tenant-scoped resolution.");
    if (investorId == null) throw validation("INVESTOR_REQUIRED", "investorId is required for resolution.");
    if (channelId == null) throw validation("CHANNEL_REQUIRED", "channelId is required for resolution.");
    if (productCode == null || productCode.isBlank()) throw validation("PRODUCT_CODE_REQUIRED", "productCode is required for resolution.");
    if (lockPeriod <= 0) throw validation("LOCK_PERIOD_REQUIRED", "lockPeriod must be greater than zero for resolution.");
    if (resolutionTimestamp == null) throw validation("RESOLUTION_TIMESTAMP_REQUIRED", "resolutionTimestamp is required.");

    var resolved = rateResolver.resolve(tenantId, investorId, channelId, productCode, lockPeriod, resolutionTimestamp);
    if (resolved.isEmpty()) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "NO_ACTIVE_RATE_SHEET",
          "No active rate sheet for investorId=" + investorId + " channelId=" + channelId + " productCode=" + productCode + " at " + resolutionTimestamp);
    }

    // Get full sheet details
    RateSheet sheet = fullSheet(resolved.get().sheetId());
    if (sheet == null) throw new RateFeedException(HttpStatus.NOT_FOUND, "SHEET_NOT_FOUND", "Sheet not found.");

    String resultHash = Hashing.sha256(sheet.sheetId().toString() + ":" + resolutionTimestamp.toString());
    return new RateFeedModels.ResolveResponse(sheet.sheetId(), sheet.version(), sheet.investorId(),
        sheet.channelId(), sheet.productCode(), lockPeriod, sheet.effectiveAt(), sheet.gridHash(),
        sheet.rowCount(), resolutionTimestamp, resultHash);
  }

  public RateFeedModels.GridResponse grid(UUID sheetId, int version) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    RateSheet sheet = getSheet(sheetId);
    if (sheet == null) throw new RateFeedException(HttpStatus.NOT_FOUND, "SHEET_NOT_FOUND", "Sheet not found.");
    if (sheet.version() != version) throw new RateFeedException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Version not found.");

    List<RatePricePoint> points = jdbc.query("SELECT * FROM rate_feed.rate_price_point WHERE sheet_id=? ORDER BY note_rate, lock_period",
        (rs, row) -> new RatePricePoint(rs.getObject("sheet_id", UUID.class), rs.getBigDecimal("note_rate"),
            rs.getInt("lock_period"), rs.getBigDecimal("base_price"), rs.getBigDecimal("discount_points"),
            rs.getBigDecimal("yield_index"), rs.getInt("grid_position")), sheetId);

    return new RateFeedModels.GridResponse(sheetId, version, sheet.gridHash(), points, points.size());
  }

  public RateFeedModels.PriceLookupResponse price(UUID sheetId, int version, BigDecimal noteRate, int lockPeriod, boolean interpolate) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    RateSheet sheet = getSheet(sheetId);
    if (sheet == null) throw new RateFeedException(HttpStatus.NOT_FOUND, "SHEET_NOT_FOUND", "Sheet not found.");
    if (sheet.version() != version) throw new RateFeedException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Version not found.");
    if (lockPeriod <= 0) throw validation("LOCK_PERIOD_REQUIRED", "lockPeriod must be greater than zero for price lookup.");

    GridLookup.PriceResult res = gridLookup.lookup(sheetId, noteRate, lockPeriod, interpolate);
    return new RateFeedModels.PriceLookupResponse(noteRate, lockPeriod, res.basePrice(), res.discountPoints(),
        res.match(), res.resultHash());
  }

  public RateFeedModels.SheetDetailResponse sheetDetails(UUID sheetId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    RateSheet sheet = fullSheet(sheetId);
    if (sheet == null) throw new RateFeedException(HttpStatus.NOT_FOUND, "SHEET_NOT_FOUND", "Sheet not found.");

    // Activation audit
    RateFeedModels.ActivationAudit audit = null;
    try {
      audit = jdbc.queryForObject("SELECT * FROM rate_feed.activation_audit WHERE sheet_id=? ORDER BY version DESC LIMIT 1",
          (rs, row) -> new RateFeedModels.ActivationAudit(rs.getObject("audit_id", UUID.class), rs.getObject("sheet_id", UUID.class),
              rs.getInt("version"), rs.getString("actor_id"), rs.getString("correlation_id"),
              rs.getTimestamp("activated_at").toInstant(), rs.getString("approval_reference"),
              rs.getString("grid_hash_before"), rs.getString("grid_hash_after"), rs.getString("notes")), sheetId);
    } catch (Exception ignored) {}

    return new RateFeedModels.SheetDetailResponse(sheet.sheetId(), sheet.investorId(), sheet.channelId(),
        sheet.productCode(), sheet.version(), sheet.status().toString(), sheet.gridHash(),
        Map.of("from", sheet.effectiveAt(), "until", sheet.effectiveUntil()), audit, null, null);
  }

  public RateFeedModels.SheetListResponse listSheets(UUID tenantId, UUID investorId, UUID channelId, String status) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    StringBuilder sql = new StringBuilder("SELECT * FROM rate_feed.rate_sheet WHERE tenant_id=?");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    if (investorId != null) { sql.append(" AND investor_id=?"); params.add(investorId); }
    if (channelId != null) { sql.append(" AND channel_id=?"); params.add(channelId); }
    if (status != null) { sql.append(" AND status=?"); params.add(status); }
    sql.append(" ORDER BY version DESC");

    List<RateFeedModels.SheetSummary> summaries = jdbc.query(sql.toString(),
        (rs, row) -> new RateFeedModels.SheetSummary(rs.getObject("sheet_id", UUID.class), rs.getObject("investor_id", UUID.class),
            rs.getObject("channel_id", UUID.class), rs.getString("product_code"), rs.getInt("version"),
            rs.getString("status"), rs.getString("grid_hash"), rs.getTimestamp("effective_at").toInstant(),
            rs.getTimestamp("created_at").toInstant()), params.toArray());

    return new RateFeedModels.SheetListResponse(summaries, summaries.size());
  }

  /**
   * PII-04-S01: List rate feed batches for the tenant.
   * Supports optional filters for status, investorId, channelId.
   * Read-only users (RATE_FEED_VIEW) can list but cannot upload.
   */
  public RateFeedModels.BatchListResponse listBatches(UUID tenantId, UUID investorId, UUID channelId, String status) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    StringBuilder sql = new StringBuilder(
        "SELECT batch_id, investor_id, channel_id, feed_format_id, source_type, effective_at, timezone, status, file_name, uploaded_by, created_at " +
        "FROM rate_feed.rate_feed_batch WHERE tenant_id=");
    List<Object> params = new ArrayList<>();

    sql.append("?");
    params.add(tenantId);
    if (investorId != null) { sql.append(" AND investor_id=?"); params.add(investorId); }
    if (channelId != null) { sql.append(" AND channel_id=?"); params.add(channelId); }
    if (status != null && !status.isBlank()) { sql.append(" AND status=?"); params.add(status); }
    sql.append(" ORDER BY created_at DESC");

    List<RateFeedModels.BatchListSummary> summaries = jdbc.query(sql.toString(),
        (rs, row) -> new RateFeedModels.BatchListSummary(
            rs.getObject("batch_id", UUID.class),
            rs.getObject("investor_id", UUID.class),
            rs.getObject("channel_id", UUID.class),
            rs.getObject("feed_format_id", UUID.class),
            rs.getString("source_type"),
            rs.getTimestamp("effective_at") != null ? rs.getTimestamp("effective_at").toInstant() : null,
            rs.getString("timezone"),
            rs.getString("status"),
            rs.getString("file_name"),
            rs.getString("uploaded_by"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
            null, // rowCount - not stored on batch table; available on associated sheet after parsing
            null  // errorCount - not stored on batch table
        ), params.toArray());

    return new RateFeedModels.BatchListResponse(summaries, summaries.size());
  }

  /**
   * Hardening: Version list endpoint — query by optional filters.
   */
  public RateFeedModels.SheetVersionsResponse listVersions(UUID investorId, UUID channelId, String productCode) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    StringBuilder sql = new StringBuilder("SELECT sheet_id, version, investor_id, channel_id, product_code, status, grid_hash, effective_at, created_at " +
        "FROM rate_feed.rate_sheet WHERE 1=1");
    List<Object> params = new ArrayList<>();
    if (investorId != null) { sql.append(" AND investor_id = ?"); params.add(investorId); }
    if (channelId != null) { sql.append(" AND channel_id = ?"); params.add(channelId); }
    if (productCode != null && !productCode.isBlank()) { sql.append(" AND product_code = ?"); params.add(productCode); }
    sql.append(" ORDER BY version DESC");

    List<RateFeedModels.SheetVersionResponse> versions = jdbc.query(sql.toString(),
        (rs, row) -> new RateFeedModels.SheetVersionResponse(
            rs.getObject("sheet_id", UUID.class),
            rs.getInt("version"),
            rs.getObject("investor_id", UUID.class),
            rs.getObject("channel_id", UUID.class),
            rs.getString("product_code"),
            rs.getString("status"),
            rs.getString("grid_hash"),
            rs.getTimestamp("effective_at") != null ? rs.getTimestamp("effective_at").toInstant() : null,
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null),
        params.toArray());

    return new RateFeedModels.SheetVersionsResponse(versions, versions.size());
  }

  private RateSheet getSheet(UUID sheetId) {
    try {
      return jdbc.queryForObject("select * from rate_feed.rate_sheet where sheet_id=?", new Object[]{sheetId}, sheetMapper());
    } catch (Exception e) { return null; }
  }

  private RateSheet fullSheet(UUID sheetId) {
    return getSheet(sheetId);
  }

  /**
   * Hardening: Replay a historical rate sheet by version and as-of date.
   */
  public RateFeedModels.ReplayResult replay(RateFeedModels.ReplayRequest request, String actor, String correlationId) {
    return replayService.replay(request, actor, correlationId);
  }

  private RowMapper<RateSheet> sheetMapper() {
    return (rs, row) -> new RateSheet(rs.getObject("sheet_id", UUID.class), rs.getObject("tenant_id", UUID.class),
        rs.getObject("investor_id", UUID.class), rs.getObject("channel_id", UUID.class),
        rs.getString("product_code"), rs.getInt("version"),
        RateSheetStatus.valueOf(rs.getString("status")),
        rs.getTimestamp("effective_at").toInstant(),
        rs.getTimestamp("effective_until") != null ? rs.getTimestamp("effective_until").toInstant() : null,
        rs.getString("file_sha256"), rs.getString("grid_hash"), rs.getString("grid_points"),
        rs.getInt("row_count"), rs.getString("result_hash"),
        rs.getTimestamp("created_at").toInstant(), rs.getString("created_by"),
        rs.getTimestamp("activated_at") != null ? rs.getTimestamp("activated_at").toInstant() : null, rs.getString("activated_by"),
        rs.getTimestamp("rejected_at") != null ? rs.getTimestamp("rejected_at").toInstant() : null, rs.getString("rejected_by"),
        rs.getString("rejection_reason"), rs.getTimestamp("updated_at").toInstant());
  }

  // ── Shared helpers ──

  static String normalizeMediaType(String contentType) {
    if (contentType == null) return "";
    return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
  }

  static void rejectFormulaMetadata(String field, String value) {
    if (value == null) return;
    String trimmed = value.stripLeading();
    if (trimmed.isEmpty()) return;
    char first = trimmed.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r' || first == '\n')
      throw validation("FORMULA_INJECTION_RISK", field + " contains spreadsheet formula-like metadata.");
  }

  private static RateFeedException validation(String code, String message) { return new RateFeedException(HttpStatus.BAD_REQUEST, code, message); }
  private static String actor(String actor) { return actor == null || actor.isBlank() ? "system" : actor; }
  private static String correlation(String correlationId) { return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId; }
  private static void requireRole(String role) { if (!RequestContext.hasRole(role)) throw new RateFeedException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", role + " role is required."); }
}

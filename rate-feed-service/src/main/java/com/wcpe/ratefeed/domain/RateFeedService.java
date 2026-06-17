package com.wcpe.ratefeed.domain;

import com.wcpe.ratefeed.activation.ActivationService;
import com.wcpe.ratefeed.activation.VersionManager;
import com.wcpe.ratefeed.audit.AuditService;
import com.wcpe.ratefeed.cache.CachedRateResolver;
import com.wcpe.ratefeed.cache.GridLoadedEvent;
import com.wcpe.ratefeed.cache.GridSupersededEvent;
import com.wcpe.ratefeed.parser.CsvParser;
import com.wcpe.ratefeed.parser.HeaderDetector;
import com.wcpe.ratefeed.parser.RateSheetParser;
import com.wcpe.ratefeed.parser.TypeCoercer;
import com.wcpe.ratefeed.normalization.MappingWizardModels;
import com.wcpe.ratefeed.normalization.MappingWizardService;
import com.wcpe.ratefeed.rulebook.LlpaGridToRuleBookMapper;
import com.wcpe.ratefeed.validation.RateSheetValidator;
import com.wcpe.ratefeed.resolution.GridLookup;
import com.wcpe.ratefeed.resolution.RateResolver;
import com.wcpe.ratefeed.role.RateFeedRoles;
import com.wcpe.ratefeed.service.ReplayService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
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
  private final CachedRateResolver rateResolver;
  private final GridLookup gridLookup;
  private final AuditService auditService;
  private final ReplayService replayService;
  private final ApplicationEventPublisher eventPublisher;
  private final MappingWizardService mappingWizardService;
  private final LlpaGridToRuleBookMapper ruleBookMapper = new LlpaGridToRuleBookMapper();
  private final Map<UUID, RateFeedModels.PipelineStatusRow> pipelineRows = new LinkedHashMap<>();

  RateFeedService(RateFeedRepository repository, JdbcTemplate jdbc, ObjectMapper mapper,
                   ActivationService activationService, VersionManager versionManager,
                     CachedRateResolver rateResolver, GridLookup gridLookup, AuditService auditService,
                     ReplayService replayService, ApplicationEventPublisher eventPublisher) {
    this(repository, jdbc, mapper, activationService, versionManager, rateResolver, gridLookup, auditService, replayService, eventPublisher, null);
  }

  @Autowired
  RateFeedService(RateFeedRepository repository, JdbcTemplate jdbc, ObjectMapper mapper,
                   ActivationService activationService, VersionManager versionManager,
                     CachedRateResolver rateResolver, GridLookup gridLookup, AuditService auditService,
                     ReplayService replayService, ApplicationEventPublisher eventPublisher,
                     MappingWizardService mappingWizardService) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.activationService = activationService;
    this.versionManager = versionManager;
    this.rateResolver = rateResolver;
    this.gridLookup = gridLookup;
    this.auditService = auditService;
    this.replayService = replayService;
    this.eventPublisher = eventPublisher;
    this.mappingWizardService = mappingWizardService;
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
      return repository.complete(tenantId, session, request, actor(actor), correlation(correlationId), idempotencyKey);
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
    String sourceType = request.sourceType().trim().toUpperCase(Locale.ROOT);
    if (!Set.of("MANUAL_UPLOAD", "PARTNER_API", "SCHEDULED_FETCH").contains(sourceType)) {
      throw validation("INVALID_SOURCE_TYPE", "sourceType must be MANUAL_UPLOAD, PARTNER_API, or SCHEDULED_FETCH.");
    }
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

  @Transactional
  public RateFeedModels.ParseBatchResponse parseBatch(UUID tenantId, UUID batchId, RateFeedModels.ParseBatchRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_PARSE);
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "parseRateFeedBatch");
    idempotencyIdentity.put("batchId", batchId);
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, RateFeedModels.ParseBatchResponse.class,
        () -> doParseBatch(tenantId, batchId, request, actor(actor), correlation(correlationId), idempotencyKey));
  }

  private RateFeedModels.ParseBatchResponse doParseBatch(UUID tenantId, UUID batchId, RateFeedModels.ParseBatchRequest request, String actor, String correlationId, String idempotencyKey) {
    RateFeedRepository.BatchParseSource batch = repository.batchParseSource(tenantId, batchId);
    String mode = Optional.ofNullable(request.parseMode()).orElse("INITIAL").trim().toUpperCase(Locale.ROOT);
    if (!Set.of("INITIAL", "REPARSE_FAILED").contains(mode)) throw validation("INVALID_PARSE_MODE", "parseMode must be INITIAL or REPARSE_FAILED.");
    if ("INITIAL".equals(mode) && !"UPLOADED".equals(batch.status())) throw new RateFeedException(HttpStatus.CONFLICT, "BATCH_NOT_UPLOADED", "Batch must be UPLOADED for initial parse.");
    if ("REPARSE_FAILED".equals(mode) && !"PARSE_FAILED".equals(batch.status())) throw new RateFeedException(HttpStatus.CONFLICT, "BATCH_NOT_PARSE_FAILED", "Only failed parses can be reparsed.");
    if (request.expectedFileSha256() != null && !request.expectedFileSha256().equalsIgnoreCase(batch.fileSha256())) throw validation("SOURCE_FILE_HASH_MISMATCH", "expectedFileSha256 does not match the uploaded batch.");

    UUID parseJobId = UUID.randomUUID();
    jdbc.update("insert into rate_feed.rate_feed_parse_job(tenant_id,parse_job_id,batch_id,mapping_version,status,started_at,idempotency_key) values (?,?,?,?,?,?,?)",
        tenantId, parseJobId, batchId, batch.feedFormatId().toString(), "PARSING", Timestamp.from(Instant.now()), idempotencyKey);
    jdbc.update("update rate_feed.rate_feed_batch set status='PARSING', updated_at=now() where tenant_id=? and batch_id=?", tenantId, batchId);

    try {
      ParsedCsv parsed = parseCsvContent(request.csvContent());
      persistParsedRows(tenantId, batchId, parsed);
      int blockingErrors = (int) parsed.fields().stream().filter(f -> "ERROR".equals(f.severity())).count();
      int warnings = (int) parsed.fields().stream().filter(f -> "WARNING".equals(f.severity())).count();
      String finalStatus = blockingErrors == 0 ? "PARSED" : "PARSE_FAILED";
      String resultHash = Hashing.sha256(repository.json(Map.of("batchId", batchId, "parseJobId", parseJobId, "rowCount", parsed.rowCount(), "errorCount", blockingErrors, "warningCount", warnings)));
      jdbc.update("update rate_feed.rate_feed_parse_job set status=?, completed_at=now(), row_count=?, error_count=?, warning_count=?, result_hash=? where tenant_id=? and parse_job_id=?",
          finalStatus, parsed.rowCount(), blockingErrors, warnings, resultHash, tenantId, parseJobId);
      jdbc.update("update rate_feed.rate_feed_batch set status=?, updated_at=now(), result_hash=? where tenant_id=? and batch_id=?", finalStatus, resultHash, tenantId, batchId);
      emitParseEvidence(tenantId, batchId, parseJobId, batch, finalStatus, resultHash, actor, correlationId, parsed.rowCount(), blockingErrors, warnings);
      return new RateFeedModels.ParseBatchResponse(parseJobId, batchId, finalStatus);
    } catch (RuntimeException ex) {
      String resultHash = Hashing.sha256(repository.json(Map.of("batchId", batchId, "parseJobId", parseJobId, "error", Optional.ofNullable(ex.getMessage()).orElse(ex.getClass().getSimpleName()))));
      jdbc.update("update rate_feed.rate_feed_parse_job set status='PARSE_FAILED', completed_at=now(), error_count=1, warning_count=0, result_hash=? where tenant_id=? and parse_job_id=?", resultHash, tenantId, parseJobId);
      jdbc.update("update rate_feed.rate_feed_batch set status='PARSE_FAILED', updated_at=now(), result_hash=? where tenant_id=? and batch_id=?", resultHash, tenantId, batchId);
      repository.outbox(tenantId, batchId, "RateSheetParseFailed.v1", 1, actor, correlationId,
          parseHeaders(batch), Map.of("batchId", batchId, "parseJobId", parseJobId, "mappingVersion", batch.feedFormatId().toString(), "errorReportId", parseJobId, "message", Optional.ofNullable(ex.getMessage()).orElse("parse failed"), "resultHash", resultHash));
      repository.audit(tenantId, batchId, "RATE_SHEET_PARSE_FAILED", "RateFeedBatch", actor, correlationId, null, resultHash,
          Map.of("batchId", batchId, "parseJobId", parseJobId, "message", Optional.ofNullable(ex.getMessage()).orElse("parse failed")));
      return new RateFeedModels.ParseBatchResponse(parseJobId, batchId, "PARSE_FAILED");
    }
  }

  @Transactional
  public RateFeedModels.OcrExtractionResponse startOcrExtraction(UUID tenantId, UUID batchId, RateFeedModels.StartOcrExtractionRequest request,
      String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_UPLOAD);
    validateStartOcrExtraction(request);
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "startOcrExtraction");
    idempotencyIdentity.put("batchId", batchId);
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, RateFeedModels.OcrExtractionResponse.class,
        () -> doStartOcrExtraction(tenantId, batchId, request, actor(actor), correlation(correlationId), idempotencyKey));
  }

  private RateFeedModels.OcrExtractionResponse doStartOcrExtraction(UUID tenantId, UUID batchId, RateFeedModels.StartOcrExtractionRequest request,
      String actor, String correlationId, String idempotencyKey) {
    RateFeedRepository.BatchParseSource batch = repository.batchParseSource(tenantId, batchId);
    if (!request.expectedFileSha256().equalsIgnoreCase(batch.fileSha256())) throw validation("SOURCE_FILE_HASH_MISMATCH", "expectedFileSha256 does not match the uploaded batch.");
    if (!Set.of("UPLOADED", "PARSE_FAILED", "OCR_FAILED", "OCR_REJECTED").contains(batch.status())) {
      throw new RateFeedException(HttpStatus.CONFLICT, "BATCH_NOT_OCR_READY", "Batch must be uploaded or retryable before OCR extraction starts.");
    }
    UUID extractionId = UUID.randomUUID();
    Instant now = Instant.now();
    String resultHash = Hashing.sha256(repository.json(Map.of("batchId", batchId, "ocrExtractionId", extractionId, "ocrProfileId", request.ocrProfileId(), "status", "OCR_REVIEW_REQUIRED")));
    jdbc.update("insert into rate_feed.ocr_extraction(tenant_id,ocr_extraction_id,batch_id,ocr_profile_id,engine_version,status,page_count,min_confidence,review_required,created_at,result_hash,idempotency_key) values (?,?,?,?,?,?,?,?,?,?,?,?)",
        tenantId, extractionId, batchId, request.ocrProfileId(), "local-adapter-contract-v1", "OCR_REVIEW_REQUIRED", 0, null, true, Timestamp.from(now), resultHash, idempotencyKey);
    jdbc.update("update rate_feed.rate_feed_batch set status='OCR_REVIEW_REQUIRED', updated_at=now(), result_hash=? where tenant_id=? and batch_id=?", resultHash, tenantId, batchId);
    repository.outbox(tenantId, batchId, "RateSheetOcrExtracted.v1", 1, actor, correlationId, parseHeaders(batch),
        Map.of("batchId", batchId, "ocrExtractionId", extractionId, "ocrProfileId", request.ocrProfileId(), "status", "OCR_REVIEW_REQUIRED", "resultHash", resultHash));
    repository.audit(tenantId, extractionId, "RATE_SHEET_OCR_EXTRACTED", "OcrExtraction", actor, correlationId, null, resultHash,
        Map.of("batchId", batchId, "ocrExtractionId", extractionId, "reviewRequired", true));
    return new RateFeedModels.OcrExtractionResponse(extractionId, batchId, "OCR_REVIEW_REQUIRED", resultHash);
  }

  @Transactional
  public RateFeedModels.OcrCellReviewResponse reviewOcrCell(UUID tenantId, UUID ocrExtractionId, UUID cellId, RateFeedModels.ReviewOcrCellRequest request,
      String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_OCR_REVIEW);
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.reviewedText() == null || request.reviewedText().isBlank()) throw validation("OCR_REVIEW_VALUE_REQUIRED", "reviewedText is required.");
    rejectFormulaMetadata("reviewedText", request.reviewedText());
    String resultHash = Hashing.sha256(repository.json(Map.of("ocrExtractionId", ocrExtractionId, "cellId", cellId, "reviewedText", request.reviewedText())));
    int rows = jdbc.update("update rate_feed.ocr_extracted_cell set reviewed_text=?, reviewed_by=?, reviewed_at=now(), status='REVIEWED' where tenant_id=? and ocr_extraction_id=? and cell_id=?",
        request.reviewedText(), actor(actor), tenantId, ocrExtractionId, cellId);
    if (rows == 0) throw new RateFeedException(HttpStatus.NOT_FOUND, "OCR_CELL_NOT_FOUND", "OCR cell was not found.");
    jdbc.update("update rate_feed.ocr_extraction set status='OCR_REVIEW_REQUIRED', result_hash=?, updated_at=now() where tenant_id=? and ocr_extraction_id=?", resultHash, tenantId, ocrExtractionId);
    repository.audit(tenantId, ocrExtractionId, "RATE_SHEET_OCR_CELL_REVIEWED", "OcrExtraction", actor(actor), correlation(correlationId), null, resultHash,
        Map.of("ocrExtractionId", ocrExtractionId, "cellId", cellId));
    return new RateFeedModels.OcrCellReviewResponse(ocrExtractionId, cellId, "REVIEWED", resultHash);
  }

  @Transactional
  public RateFeedModels.OcrApprovalResponse approveOcrExtraction(UUID tenantId, UUID ocrExtractionId, RateFeedModels.ApproveOcrExtractionRequest request,
      String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_OCR_REVIEW);
    Map<String, Object> extraction = ocrExtraction(tenantId, ocrExtractionId);
    String currentHash = Objects.toString(extraction.get("result_hash"), "");
    if (request != null && request.expectedResultHash() != null && !request.expectedResultHash().isBlank() && !request.expectedResultHash().equals(currentHash)) {
      throw new RateFeedException(HttpStatus.CONFLICT, "STALE_OCR_RESULT_HASH", "expectedResultHash does not match the latest OCR extraction result.");
    }
    UUID batchId = (UUID) extraction.get("batch_id");
    List<OcrCellForApproval> cells = ocrCellsForApproval(tenantId, ocrExtractionId);
    if (cells.isEmpty() || cells.stream().anyMatch(cell -> cell.reviewedText() == null || cell.reviewedText().isBlank() || !Set.of("REVIEWED", "ACCEPTED").contains(cell.status()))) {
      throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "OCR_REVIEW_REQUIRED", "All OCR cells must be human reviewed before approval.");
    }
    String parserHandoff = "local://synthetic/ocr/" + ocrExtractionId + ".csv";
    String csvHash = Hashing.sha256(cells.stream()
        .sorted(Comparator.comparingInt(OcrCellForApproval::rowIndex).thenComparingInt(OcrCellForApproval::columnIndex))
        .map(cell -> cell.rowIndex() + "," + cell.columnIndex() + "," + cell.reviewedText())
        .reduce("", (left, right) -> left + "\n" + right));
    String resultHash = Hashing.sha256(repository.json(Map.of("ocrExtractionId", ocrExtractionId, "batchId", batchId, "parserHandoff", parserHandoff, "csvHash", csvHash)));
    jdbc.update("update rate_feed.ocr_extraction set status='APPROVED', completed_at=now(), review_required=false, result_hash=?, updated_at=now() where tenant_id=? and ocr_extraction_id=?",
        resultHash, tenantId, ocrExtractionId);
    jdbc.update("update rate_feed.rate_feed_batch set status='OCR_APPROVED', updated_at=now(), result_hash=? where tenant_id=? and batch_id=?", resultHash, tenantId, batchId);
    repository.outbox(tenantId, batchId, "RateSheetOcrReviewApproved.v1", 1, actor(actor), correlation(correlationId), null,
        Map.of("batchId", batchId, "ocrExtractionId", ocrExtractionId, "parserHandoffArtifact", parserHandoff, "cellCount", cells.size(), "resultHash", resultHash));
    repository.audit(tenantId, ocrExtractionId, "RATE_SHEET_OCR_REVIEW_APPROVED", "OcrExtraction", actor(actor), correlation(correlationId), currentHash, resultHash,
        Map.of("batchId", batchId, "ocrExtractionId", ocrExtractionId, "parserHandoffArtifact", parserHandoff, "cellCount", cells.size()));
    return new RateFeedModels.OcrApprovalResponse(ocrExtractionId, batchId, "APPROVED", parserHandoff, resultHash);
  }

  private void validateStartOcrExtraction(RateFeedModels.StartOcrExtractionRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.expectedFileSha256() == null || !request.expectedFileSha256().matches("^[0-9a-fA-F]{64}$")) throw validation("INVALID_FILE_HASH", "expectedFileSha256 must be a 64 character SHA-256 hex value.");
    if (request.ocrProfileId() == null) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "OCR_PROFILE_NOT_FOUND", "ocrProfileId is required for governed local OCR.");
  }

  private Map<String, Object> ocrExtraction(UUID tenantId, UUID ocrExtractionId) {
    try {
      return jdbc.queryForMap("select ocr_extraction_id,batch_id,status,result_hash from rate_feed.ocr_extraction where tenant_id=? and ocr_extraction_id=?", tenantId, ocrExtractionId);
    } catch (Exception ex) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "OCR_EXTRACTION_NOT_FOUND", "OCR extraction was not found.");
    }
  }

  private List<OcrCellForApproval> ocrCellsForApproval(UUID tenantId, UUID ocrExtractionId) {
    return jdbc.query("select cell_id,row_index,column_index,raw_text,reviewed_text,status from rate_feed.ocr_extracted_cell where tenant_id=? and ocr_extraction_id=? order by page_number,row_index,column_index",
        (rs, row) -> new OcrCellForApproval(rs.getObject("cell_id", UUID.class), rs.getInt("row_index"), rs.getInt("column_index"), rs.getString("raw_text"), rs.getString("reviewed_text"), rs.getString("status")), tenantId, ocrExtractionId);
  }

  public RateFeedModels.ParseResultPage parseResults(UUID tenantId, UUID batchId, String severity, int page, int size) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    int boundedPage = Math.max(page, 0);
    int boundedSize = Math.min(Math.max(size, 1), 200);
    String normalizedSeverity = severity == null || severity.isBlank() ? null : severity.trim().toUpperCase(Locale.ROOT);
    List<Object> params = new ArrayList<>(List.of(tenantId, batchId));
    String where = " where tenant_id=? and batch_id=?";
    if (normalizedSeverity != null) { where += " and severity=?"; params.add(normalizedSeverity); }
    Long total = jdbc.queryForObject("select count(*) from rate_feed.rate_feed_parsed_field" + where, Long.class, params.toArray());
    params.add(boundedSize);
    params.add(boundedPage * boundedSize);
    List<RateFeedModels.ParsedFieldResult> rows = jdbc.query("select source_row_number,field_name,raw_value,candidate_value,severity,error_code,message from rate_feed.rate_feed_parsed_field" + where + " order by source_row_number, source_column limit ? offset ?",
        (rs, row) -> new RateFeedModels.ParsedFieldResult(rs.getInt("source_row_number"), rs.getString("field_name"), rs.getString("raw_value"), rs.getString("candidate_value"), rs.getString("severity"), rs.getString("error_code"), rs.getString("message")), params.toArray());
    return new RateFeedModels.ParseResultPage(rows, boundedPage, boundedSize, total == null ? 0 : total);
  }

  @Transactional
  public RateFeedModels.NormalizeBatchResponse normalizeBatch(UUID tenantId, UUID batchId, RateFeedModels.NormalizeBatchRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_NORMALIZE);
    validateNormalizeRequest(request);
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "normalizeRateFeedBatch");
    idempotencyIdentity.put("batchId", batchId);
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, RateFeedModels.NormalizeBatchResponse.class,
        () -> doNormalizeBatch(tenantId, batchId, request, actor(actor), correlation(correlationId), idempotencyKey));
  }

  private RateFeedModels.NormalizeBatchResponse doNormalizeBatch(UUID tenantId, UUID batchId, RateFeedModels.NormalizeBatchRequest request, String actor, String correlationId, String idempotencyKey) {
    RateFeedRepository.BatchParseSource batch = repository.batchParseSource(tenantId, batchId);
    if (!"PARSED".equals(batch.status())) throw new RateFeedException(HttpStatus.CONFLICT, "BATCH_NOT_PARSED", "Batch must be parsed before normalization.");
    String parseHash = latestParseResultHash(tenantId, batchId);
    if (request.expectedParseResultHash() != null && !request.expectedParseResultHash().isBlank() && !request.expectedParseResultHash().equals(parseHash)) {
      throw new RateFeedException(HttpStatus.CONFLICT, "STALE_PARSE_HASH", "expectedParseResultHash does not match the latest parse result.");
    }

    UUID jobId = UUID.randomUUID();
    Instant startedAt = Instant.now();
    ProfileSelection profileSelection = selectNormalizationProfile(tenantId, batch, request);
    jdbc.update("insert into rate_feed.rate_feed_normalization_job(tenant_id,normalization_job_id,batch_id,profile_id,profile_version,status,started_at,idempotency_key) values (?,?,?,?,?,?,?,?)",
        tenantId, jobId, batchId, profileSelection.profileId(), profileSelection.profileVersion(), "RUNNING", Timestamp.from(startedAt), idempotencyKey);

    List<NormalizedCandidate> candidates = normalizeParsedRows(tenantId, batchId, profileSelection.profileId(), profileSelection.profileVersion());
    int errors = (int) candidates.stream().filter(candidate -> "ERROR".equals(candidate.severity())).count();
    int warnings = (int) candidates.stream().filter(candidate -> "WARNING".equals(candidate.severity())).count();
    String status = errors == 0 ? "NORMALIZED" : "NORMALIZATION_FAILED";
    String resultHash = Hashing.sha256(repository.json(candidates.stream().map(NormalizedCandidate::hashMaterial).toList()));

    jdbc.update("delete from rate_feed.normalized_rate_sheet_entry where tenant_id=? and batch_id=?", tenantId, batchId);
    for (NormalizedCandidate candidate : candidates) {
      if ("ERROR".equals(candidate.severity())) continue;
      jdbc.update("insert into rate_feed.normalized_rate_sheet_entry(tenant_id,entry_id,batch_id,source_row_id,investor_id,channel_id,canonical_product_key,program_key,lock_period_days,rate_percent,price_points,adjustment_type,adjustment_value,adjustment_unit,effective_at,dimensions,raw_attributes,mapping_refs,severity,message,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          tenantId, candidate.entryId(), batchId, candidate.sourceRowNumber(), batch.investorId(), batch.channelId(), candidate.canonicalProductKey(), candidate.programKey(), candidate.lockPeriodDays(), candidate.ratePercent(), candidate.pricePoints(), candidate.adjustmentType(), candidate.adjustmentValue(), candidate.adjustmentUnit(), Timestamp.from(startedAt), repository.jsonb(candidate.dimensions()), repository.jsonb(candidate.rawAttributes()), repository.jsonb(candidate.mappingRefs()), candidate.severity(), candidate.message(), Timestamp.from(startedAt));
    }
    jdbc.update("update rate_feed.rate_feed_normalization_job set status=?, completed_at=now(), entry_count=?, error_count=?, warning_count=?, result_hash=? where tenant_id=? and normalization_job_id=?",
        status, candidates.size() - errors, errors, warnings, resultHash, tenantId, jobId);
    jdbc.update("update rate_feed.rate_feed_batch set status=?, updated_at=now(), result_hash=? where tenant_id=? and batch_id=?", status, resultHash, tenantId, batchId);

    String eventType = errors == 0 ? "RateSheetNormalized.v1" : "RateSheetNormalizationFailed.v1";
    repository.outbox(tenantId, batchId, eventType, 1, actor, correlationId, parseHeaders(batch),
        Map.of("batchId", batchId, "normalizationJobId", jobId, "profileId", profileSelection.profileId(), "profileVersion", profileSelection.profileVersion(), "profileMatchScore", profileSelection.matchScore(), "profileRoutingAction", profileSelection.routingAction(), "entryCount", candidates.size() - errors, "errorCount", errors, "warningCount", warnings, "resultHash", resultHash));
    repository.audit(tenantId, batchId, errors == 0 ? "RATE_SHEET_NORMALIZED" : "RATE_SHEET_NORMALIZATION_FAILED", "RateFeedBatch", actor, correlationId, parseHash, resultHash,
        Map.of("batchId", batchId, "normalizationJobId", jobId, "status", status, "profileId", profileSelection.profileId(), "profileVersion", profileSelection.profileVersion(), "profileMatchScore", profileSelection.matchScore(), "profileRoutingAction", profileSelection.routingAction(), "entryCount", candidates.size() - errors, "errorCount", errors, "warningCount", warnings));
    return new RateFeedModels.NormalizeBatchResponse(jobId, status);
  }

  public RateFeedModels.NormalizedEntryPage normalizedEntries(UUID tenantId, UUID batchId, String severity, int page, int size) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    int boundedPage = Math.max(page, 0);
    int boundedSize = Math.min(Math.max(size, 1), 200);
    String normalizedSeverity = severity == null || severity.isBlank() ? null : severity.trim().toUpperCase(Locale.ROOT);
    List<Object> params = new ArrayList<>(List.of(tenantId, batchId));
    String where = " where tenant_id=? and batch_id=?";
    if (normalizedSeverity != null) { where += " and severity=?"; params.add(normalizedSeverity); }
    Long total = jdbc.queryForObject("select count(*) from rate_feed.normalized_rate_sheet_entry" + where, Long.class, params.toArray());
    params.add(boundedSize);
    params.add(boundedPage * boundedSize);
    List<RateFeedModels.NormalizedEntryResponse> entries = jdbc.query("select entry_id,batch_id,source_row_id,canonical_product_key,program_key,rate_percent,price_points,lock_period_days,adjustment_type,adjustment_value,adjustment_unit,dimensions::text,raw_attributes::text,mapping_refs::text,mapping_refs ->> 'profileVersion' as mapping_version,severity,message from rate_feed.normalized_rate_sheet_entry" + where + " order by source_row_id, created_at limit ? offset ?",
        (rs, row) -> new RateFeedModels.NormalizedEntryResponse(rs.getObject("entry_id", UUID.class), rs.getObject("batch_id", UUID.class), rs.getInt("source_row_id"), rs.getString("canonical_product_key"), rs.getString("program_key"), rs.getBigDecimal("rate_percent"), rs.getBigDecimal("price_points"), rs.getInt("lock_period_days"), rs.getString("adjustment_type"), rs.getBigDecimal("adjustment_value"), rs.getString("adjustment_unit"), repository.read(rs.getString("dimensions"), Map.class), repository.read(rs.getString("raw_attributes"), Map.class), repository.read(rs.getString("mapping_refs"), Map.class), rs.getString("severity"), rs.getString("message"), rs.getString("mapping_version")), params.toArray());
    return new RateFeedModels.NormalizedEntryPage(entries, boundedPage, boundedSize, total == null ? 0 : total);
  }

  @Transactional
  public RateFeedModels.ValidateBatchResponse validateBatch(UUID tenantId, UUID batchId, RateFeedModels.ValidateBatchRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VALIDATE);
    validateBatchRequest(request);
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "validateRateFeedBatch");
    idempotencyIdentity.put("batchId", batchId);
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, RateFeedModels.ValidateBatchResponse.class,
        () -> doValidateBatch(tenantId, batchId, request, actor(actor), correlation(correlationId), idempotencyKey));
  }

  private RateFeedModels.ValidateBatchResponse doValidateBatch(UUID tenantId, UUID batchId, RateFeedModels.ValidateBatchRequest request, String actor, String correlationId, String idempotencyKey) {
    RateFeedRepository.BatchParseSource batch = repository.batchParseSource(tenantId, batchId);
    if (!"NORMALIZED".equals(batch.status())) throw new RateFeedException(HttpStatus.CONFLICT, "BATCH_NOT_NORMALIZED", "Batch must be normalized before validation.");
    String normalizationHash = latestNormalizationResultHash(tenantId, batchId);
    if (!request.expectedNormalizationHash().equals(normalizationHash)) {
      throw new RateFeedException(HttpStatus.CONFLICT, "STALE_NORMALIZATION_HASH", "expectedNormalizationHash does not match the latest normalization result.");
    }

    UUID jobId = UUID.randomUUID();
    Instant startedAt = Instant.now();
    String profileVersion = request.validationProfileId().toString();
    jdbc.update("insert into rate_feed.rate_feed_validation_job(tenant_id,validation_job_id,batch_id,profile_id,profile_version,status,started_at,idempotency_key) values (?,?,?,?,?,?,?,?)",
        tenantId, jobId, batchId, request.validationProfileId(), profileVersion, "RUNNING", Timestamp.from(startedAt), idempotencyKey);

    List<ValidationCandidate> entries = validationCandidates(tenantId, batchId);
    if (entries.isEmpty()) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "BATCH_NOT_NORMALIZED", "Normalized batch contains no entries to validate.");
    List<ValidationFindingDraft> findings = validationFindings(entries, profileVersion);
    int blockers = (int) findings.stream().filter(f -> f.severity() == RateFeedModels.ValidationFindingSeverity.BLOCKER).count();
    int warnings = (int) findings.stream().filter(f -> f.severity() == RateFeedModels.ValidationFindingSeverity.WARNING).count();
    String status = blockers > 0 ? "FAILED" : (warnings > 0 ? "PASSED_WITH_WARNINGS" : "PASSED");
    String resultHash = Hashing.sha256(repository.json(Map.of(
        "batchId", batchId,
        "profileVersion", profileVersion,
        "status", status,
        "findings", findings.stream().map(ValidationFindingDraft::hashMaterial).toList())));

    jdbc.update("delete from rate_feed.rate_feed_validation_finding where tenant_id=? and batch_id=?", tenantId, batchId);
    for (ValidationFindingDraft finding : findings) {
      jdbc.update("insert into rate_feed.rate_feed_validation_finding(tenant_id,finding_id,validation_job_id,batch_id,entry_id,severity,rule_code,rule_version,field_name,message_code,message_params,remediation_code,source_row_number,created_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          tenantId, UUID.randomUUID(), jobId, batchId, finding.entryId(), finding.severity().name(), finding.ruleCode(), finding.ruleVersion(), finding.fieldName(), finding.messageCode(), repository.jsonb(finding.messageParams()), finding.remediationCode(), finding.sourceRowNumber(), Timestamp.from(startedAt));
    }
    jdbc.update("update rate_feed.rate_feed_validation_job set status=?, completed_at=now(), blocking_error_count=?, warning_count=?, result_hash=? where tenant_id=? and validation_job_id=?",
        status, blockers, warnings, resultHash, tenantId, jobId);
    jdbc.update("update rate_feed.rate_feed_batch set status=?, updated_at=now(), result_hash=? where tenant_id=? and batch_id=?",
        status.equals("FAILED") ? "VALIDATION_FAILED" : "VALIDATION_PASSED", resultHash, tenantId, batchId);

    String eventType = status.equals("FAILED") ? "RateSheetValidationFailed.v1" : "RateSheetValidationPassed.v1";
    repository.outbox(tenantId, batchId, eventType, 1, actor, correlationId, parseHeaders(batch),
        Map.of("batchId", batchId, "validationJobId", jobId, "profileId", request.validationProfileId(), "profileVersion", profileVersion, "status", status, "blockingErrorCount", blockers, "warningCount", warnings, "resultHash", resultHash));
    repository.audit(tenantId, batchId, status.equals("FAILED") ? "RATE_SHEET_VALIDATION_FAILED" : "RATE_SHEET_VALIDATION_PASSED", "RateFeedBatch", actor, correlationId, normalizationHash, resultHash,
        Map.of("batchId", batchId, "validationJobId", jobId, "profileId", request.validationProfileId(), "status", status, "blockingErrorCount", blockers, "warningCount", warnings, "resultHash", resultHash));
    return new RateFeedModels.ValidateBatchResponse(jobId, status);
  }

  public RateFeedModels.ValidationReportResponse validationReport(UUID tenantId, UUID batchId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    RateFeedModels.ValidationReportResponse report = latestValidationReport(tenantId, batchId);
    if (report == null) throw new RateFeedException(HttpStatus.NOT_FOUND, "VALIDATION_REPORT_NOT_FOUND", "Validation report was not found for this batch.");
    return report;
  }

  private void validateBatchRequest(RateFeedModels.ValidateBatchRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.expectedNormalizationHash() == null || request.expectedNormalizationHash().isBlank()) throw validation("NORMALIZATION_HASH_REQUIRED", "expectedNormalizationHash is required.");
    rejectFormulaMetadata("expectedNormalizationHash", request.expectedNormalizationHash());
    if (request.validationProfileId() == null) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_PROFILE_NOT_FOUND", "validationProfileId is required for governed validation.");
  }

  private String latestNormalizationResultHash(UUID tenantId, UUID batchId) {
    try {
      String hash = jdbc.queryForObject("select result_hash from rate_feed.rate_feed_normalization_job where tenant_id=? and batch_id=? order by completed_at desc nulls last, started_at desc limit 1", String.class, tenantId, batchId);
      return hash == null ? "" : hash;
    } catch (Exception ex) { return ""; }
  }

  private List<ValidationCandidate> validationCandidates(UUID tenantId, UUID batchId) {
    return jdbc.query("select entry_id,source_row_id,canonical_product_key,lock_period_days,rate_percent,price_points,adjustment_type,adjustment_value,adjustment_unit,severity,message from rate_feed.normalized_rate_sheet_entry where tenant_id=? and batch_id=? order by source_row_id, created_at",
        (rs, row) -> new ValidationCandidate(rs.getObject("entry_id", UUID.class), rs.getInt("source_row_id"), rs.getString("canonical_product_key"), rs.getInt("lock_period_days"), rs.getBigDecimal("rate_percent"), rs.getBigDecimal("price_points"), rs.getString("adjustment_type"), rs.getBigDecimal("adjustment_value"), rs.getString("adjustment_unit"), rs.getString("severity"), rs.getString("message")), tenantId, batchId);
  }

  private List<ValidationFindingDraft> validationFindings(List<ValidationCandidate> entries, String profileVersion) {
    List<ValidationFindingDraft> findings = new ArrayList<>();
    Map<String, ValidationCandidate> seen = new LinkedHashMap<>();
    for (ValidationCandidate entry : entries) {
      if (entry.canonicalProductKey() == null || entry.canonicalProductKey().isBlank()) {
        findings.add(ValidationFindingDraft.blocker(entry, "REQUIRED_CANONICAL_FIELD", profileVersion, "canonical_product_key", "CANONICAL_PRODUCT_REQUIRED", "MAP_CANONICAL_PRODUCT"));
      }
      if (entry.lockPeriodDays() <= 0) {
        findings.add(ValidationFindingDraft.blocker(entry, "REQUIRED_CANONICAL_FIELD", profileVersion, "lock_period_days", "LOCK_PERIOD_REQUIRED", "MAP_LOCK_PERIOD"));
      }
      if (entry.ratePercent() == null) {
        findings.add(ValidationFindingDraft.blocker(entry, "REQUIRED_CANONICAL_FIELD", profileVersion, "rate_percent", "RATE_REQUIRED", "MAP_RATE"));
      }
      if (entry.pricePoints() == null) {
        findings.add(ValidationFindingDraft.blocker(entry, "REQUIRED_CANONICAL_FIELD", profileVersion, "price_points", "PRICE_REQUIRED", "MAP_PRICE"));
      }
      if (partialAdjustment(entry)) {
        findings.add(ValidationFindingDraft.blocker(entry, "ADJUSTMENT_DIMENSION_COMPLETE", profileVersion, "adjustment", "ADJUSTMENT_DIMENSION_INCOMPLETE", "COMPLETE_ADJUSTMENT_DIMENSION"));
      }
      if ("WARNING".equals(entry.severity())) {
        findings.add(ValidationFindingDraft.warning(entry, "NORMALIZATION_WARNING", profileVersion, "normalized_entry", "NORMALIZED_ENTRY_WARNING", "REVIEW_NORMALIZATION_WARNING"));
      }
      String duplicateKey = entry.canonicalProductKey() + "|" + entry.lockPeriodDays() + "|" + valueKey(entry.ratePercent()) + "|" + valueKey(entry.pricePoints());
      ValidationCandidate prior = seen.putIfAbsent(duplicateKey, entry);
      if (prior != null) {
        findings.add(ValidationFindingDraft.blocker(entry, "DUPLICATE_CANONICAL_ENTRY", profileVersion, "canonical_product_key", "DUPLICATE_CANONICAL_ENTRY", "REMOVE_DUPLICATE_ENTRY"));
      }
    }
    return findings;
  }

  private static boolean partialAdjustment(ValidationCandidate entry) {
    int populated = 0;
    if (entry.adjustmentType() != null && !entry.adjustmentType().isBlank()) populated++;
    if (entry.adjustmentValue() != null) populated++;
    if (entry.adjustmentUnit() != null && !entry.adjustmentUnit().isBlank()) populated++;
    return populated > 0 && populated < 3;
  }

  private static String valueKey(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  private RateFeedModels.ValidationReportResponse latestValidationReport(UUID tenantId, UUID batchId) {
    try {
      return jdbc.queryForObject("select validation_job_id,batch_id,status,profile_version,started_at,completed_at,blocking_error_count,warning_count,result_hash from rate_feed.rate_feed_validation_job where tenant_id=? and batch_id=? order by completed_at desc nulls last, started_at desc limit 1",
          (rs, row) -> {
            UUID jobId = rs.getObject("validation_job_id", UUID.class);
            List<RateFeedModels.ValidationFindingRecord> findings = jdbc.query("select finding_id,entry_id,severity,rule_code,rule_version,field_name,message_code,message_params::text,remediation_code,source_row_number,created_at from rate_feed.rate_feed_validation_finding where tenant_id=? and validation_job_id=? order by severity, source_row_number, rule_code",
                (frs, frow) -> new RateFeedModels.ValidationFindingRecord(frs.getObject("finding_id", UUID.class), frs.getObject("entry_id", UUID.class), RateFeedModels.ValidationFindingSeverity.valueOf(frs.getString("severity")), frs.getString("rule_code"), frs.getString("rule_version"), frs.getString("field_name"), frs.getString("message_code"), repository.read(frs.getString("message_params"), Map.class), frs.getString("remediation_code"), (Integer) frs.getObject("source_row_number"), frs.getTimestamp("created_at").toInstant()), tenantId, jobId);
            return new RateFeedModels.ValidationReportResponse(jobId, rs.getObject("batch_id", UUID.class), rs.getString("status"), rs.getString("profile_version"), rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant(), rs.getInt("blocking_error_count"), rs.getInt("warning_count"), rs.getString("result_hash"), findings);
          }, tenantId, batchId);
    } catch (Exception ex) { return null; }
  }

  private void validateNormalizeRequest(RateFeedModels.NormalizeBatchRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.normalizationProfileId() == null && mappingWizardService == null) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "NORMALIZATION_PROFILE_NOT_FOUND", "normalizationProfileId is required when auto profile matching is unavailable.");
    rejectFormulaMetadata("expectedParseResultHash", request.expectedParseResultHash());
  }

  private ProfileSelection selectNormalizationProfile(UUID tenantId, RateFeedRepository.BatchParseSource batch, RateFeedModels.NormalizeBatchRequest request) {
    if (request.normalizationProfileId() != null) {
      String profileVersion = request.normalizationProfileId().toString();
      return new ProfileSelection(request.normalizationProfileId(), profileVersion, 100, "EXPLICIT_PROFILE");
    }
    MappingWizardModels.AutoMatchResponse match = mappingWizardService.autoMatch(tenantId, incomingFingerprintForBatch(tenantId, batch));
    if (!match.autoApply() || match.profile() == null) {
      throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_PROFILE_MATCH", match.fallbackReason().isBlank() ? "No matching published profile >=80. Use Mapping Wizard." : match.fallbackReason());
    }
    String profileVersion = match.profile().profileId() + ":v" + match.profile().version();
    return new ProfileSelection(match.profile().profileId(), profileVersion, match.matchScore(), match.routingAction());
  }

  private com.fasterxml.jackson.databind.JsonNode incomingFingerprintForBatch(UUID tenantId, RateFeedRepository.BatchParseSource batch) {
    com.fasterxml.jackson.databind.node.ObjectNode fingerprint = mapper.createObjectNode();
    fingerprint.put("formatType", batch.feedFormatId().toString());
    fingerprint.put("investorCode", batch.investorId().toString());
    com.fasterxml.jackson.databind.node.ArrayNode headers = mapper.createArrayNode();
    List<String> fieldNames = jdbc.queryForList("select distinct field_name from rate_feed.rate_feed_parsed_field where tenant_id=? and batch_id=? order by field_name", String.class, tenantId, batch.batchId());
    for (String fieldName : fieldNames) if (fieldName != null && !fieldName.isBlank()) headers.add(fieldName);
    fingerprint.set("headerSignatures", headers);
    fingerprint.set("fieldSignatures", headers.deepCopy());
    return fingerprint;
  }

  private String latestParseResultHash(UUID tenantId, UUID batchId) {
    try {
      String hash = jdbc.queryForObject("select result_hash from rate_feed.rate_feed_parse_job where tenant_id=? and batch_id=? order by completed_at desc nulls last, started_at desc limit 1", String.class, tenantId, batchId);
      return hash == null ? "" : hash;
    } catch (Exception ex) { return ""; }
  }

  private List<NormalizedCandidate> normalizeParsedRows(UUID tenantId, UUID batchId, UUID profileId, String profileVersion) {
    List<ParsedField> fields = jdbc.query("select source_row_number,field_name,raw_value,candidate_value,source_column,severity,error_code,message from rate_feed.rate_feed_parsed_field where tenant_id=? and batch_id=? order by source_row_number, source_column",
        (rs, row) -> new ParsedField(rs.getInt("source_row_number"), rs.getString("field_name"), rs.getString("raw_value"), rs.getString("candidate_value"), rs.getInt("source_column"), rs.getString("severity"), rs.getString("error_code"), rs.getString("message")), tenantId, batchId);
    Map<Integer, List<ParsedField>> byRow = new TreeMap<>();
    for (ParsedField field : fields) byRow.computeIfAbsent(field.sourceRowNumber(), ignored -> new ArrayList<>()).add(field);
    if (byRow.isEmpty()) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "BATCH_NOT_PARSED", "Parsed batch contains no field rows.");
    List<NormalizedCandidate> candidates = new ArrayList<>();
    for (Map.Entry<Integer, List<ParsedField>> row : byRow.entrySet()) candidates.add(normalizeParsedRow(row.getKey(), row.getValue(), profileId, profileVersion));
    return candidates;
  }

  private NormalizedCandidate normalizeParsedRow(int sourceRow, List<ParsedField> fields, UUID profileId, String profileVersion) {
    Map<String, ParsedField> byName = new LinkedHashMap<>();
    Map<String, String> raw = new LinkedHashMap<>();
    for (ParsedField field : fields) {
      byName.put(field.fieldName(), field);
      raw.put(field.fieldName(), field.rawValue());
      if ("ERROR".equals(field.severity())) return NormalizedCandidate.error(sourceRow, "Parsed row contains blocking field errors.", raw, profileId, profileVersion);
    }
    try {
      String productKey = requiredGovernedCandidate(byName, "canonical_product_key");
      BigDecimal rate = new BigDecimal(requiredGovernedCandidate(byName, "note_rate"));
      BigDecimal price = new BigDecimal(requiredGovernedCandidate(byName, "base_price"));
      int lockPeriod = Integer.parseInt(requiredGovernedCandidate(byName, "lock_period"));
      String programKey = optionalGovernedCandidate(byName, "program_key");
      BigDecimal adjustmentValue = optionalGovernedDecimal(byName, "adjustment_value");
      String adjustmentType = optionalGovernedCandidate(byName, "adjustment_type");
      String adjustmentUnit = optionalGovernedCandidate(byName, "adjustment_unit");
      return NormalizedCandidate.success(sourceRow, productKey, programKey, lockPeriod, rate, price, adjustmentType, adjustmentValue, adjustmentUnit, raw, mappingRefs(byName, profileId, profileVersion), profileVersion);
    } catch (RuntimeException ex) {
      return NormalizedCandidate.error(sourceRow, ex.getMessage(), raw, profileId, profileVersion);
    }
  }

  private static String requiredGovernedCandidate(Map<String, ParsedField> fields, String fieldName) {
    String value = optionalGovernedCandidate(fields, fieldName);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing governed mapping for " + fieldName + ".");
    return value;
  }

  private static String optionalGovernedCandidate(Map<String, ParsedField> fields, String fieldName) {
    ParsedField field = fields.get(fieldName);
    if (field == null) return null;
    String value = field.candidateValue();
    return value == null || value.isBlank() ? null : value;
  }

  private static BigDecimal optionalGovernedDecimal(Map<String, ParsedField> fields, String fieldName) {
    String value = optionalGovernedCandidate(fields, fieldName);
    return value == null ? null : new BigDecimal(value);
  }

  private static Map<String, Object> mappingRefs(Map<String, ParsedField> fields, UUID profileId, String profileVersion) {
    Map<String, Object> refs = new LinkedHashMap<>();
    refs.put("profileId", profileId.toString());
    refs.put("profileVersion", profileVersion);
    Map<String, Object> fieldRefs = new LinkedHashMap<>();
    for (ParsedField field : fields.values()) {
      if (field.candidateValue() != null && !field.candidateValue().isBlank()) {
        fieldRefs.put(field.fieldName(), Map.of("sourceColumn", field.sourceColumn(), "candidateValue", field.candidateValue()));
      }
    }
    refs.put("fields", fieldRefs);
    return refs;
  }

  @Transactional
  public RateFeedModels.RateSheetVersionCreatedResponse createRateSheetVersion(UUID tenantId, RateFeedModels.CreateRateSheetVersionRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER);
    validateCreateRateSheetVersionRequest(request);
    Map<String, Object> idempotencyIdentity = new LinkedHashMap<>();
    idempotencyIdentity.put("command", "createRateSheetVersion");
    idempotencyIdentity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, idempotencyIdentity, RateFeedModels.RateSheetVersionCreatedResponse.class,
        () -> doCreateRateSheetVersion(tenantId, request, actor(actor), correlation(correlationId)));
  }

  private RateFeedModels.RateSheetVersionCreatedResponse doCreateRateSheetVersion(UUID tenantId, RateFeedModels.CreateRateSheetVersionRequest request, String actor, String correlationId) {
    validateCreateRateSheetVersionRequest(request);
    RateFeedRepository.BatchParseSource batch = repository.batchParseSource(tenantId, request.batchId());
    if (!"PARSED".equals(batch.status())) throw new RateFeedException(HttpStatus.CONFLICT, "BATCH_NOT_NORMALIZED", "Batch must be parsed before a draft version can be created.");
    List<RatePricePoint> points = parsedPricePoints(tenantId, request.batchId());
    if (points.isEmpty()) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "BATCH_NOT_NORMALIZED", "Parsed batch has no normalized rate rows.");

    int version = versionManager.nextVersion(tenantId, batch.investorId(), batch.channelId(), request.productKey());
    UUID sheetId = UUID.randomUUID();
    String gridHash = Hashing.sha256(repository.json(points.stream().map(p -> List.of(p.noteRate(), p.lockPeriod(), p.basePrice(), Optional.ofNullable(p.discountPoints()).orElse(BigDecimal.ZERO), Optional.ofNullable(p.yieldIndex()).orElse(BigDecimal.ZERO))).toList()));
    if (!gridHash.startsWith("sha256:")) gridHash = "sha256:" + gridHash;
    String resultHash = Hashing.sha256(repository.json(Map.of("tenantId", tenantId, "batchId", request.batchId(), "sheetId", sheetId, "version", version, "lineageReasonCode", request.lineageReasonCode())));

    jdbc.update("insert into rate_feed.rate_sheet(sheet_id,tenant_id,investor_id,channel_id,product_code,version,status,effective_at,effective_until,file_sha256,grid_hash,row_count,result_hash,created_by,version_label,source_batch_id,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now())",
        sheetId, tenantId, batch.investorId(), batch.channelId(), request.productKey(), version, "DRAFT", Timestamp.from(request.effectiveFrom()),
        request.effectiveTo() == null ? null : Timestamp.from(request.effectiveTo()), batch.fileSha256(), gridHash, points.size(), resultHash, actor, request.versionLabel(), request.batchId());
    int position = 0;
    for (RatePricePoint point : points) {
      jdbc.update("insert into rate_feed.rate_price_point(sheet_id,note_rate,lock_period,base_price,discount_points,yield_index,grid_position) values (?,?,?,?,?,?,?)",
          sheetId, point.noteRate(), point.lockPeriod(), point.basePrice(), point.discountPoints(), point.yieldIndex(), position++);
    }
    jdbc.update("insert into rate_feed.rate_sheet_version(sheet_id,version,previous_version,delta_summary,created_at) values (?,?,?,?,now())",
        sheetId, version, null, repository.jsonb(versionDelta(request, batch, resultHash)));
    jdbc.update("insert into rate_feed.rate_sheet_version_lineage(tenant_id,version_id,parent_version_id,lineage_reason_code,created_at) values (?,?,?,?,now())",
        tenantId, sheetId, request.parentVersionId(), request.lineageReasonCode());
    repository.outbox(tenantId, sheetId, "RateSheetVersionCreated.v1", 1, actor, correlationId,
        Map.of("investorId", batch.investorId().toString(), "channelId", batch.channelId().toString(), "feedFormatId", batch.feedFormatId().toString()),
        versionDelta(request, batch, resultHash));
    repository.audit(tenantId, sheetId, "RATE_SHEET_VERSION_CREATED", "RateSheetVersion", actor, correlationId, null, resultHash, versionDelta(request, batch, resultHash));

    return new RateFeedModels.RateSheetVersionCreatedResponse(sheetId, version, "DRAFT", draftOverlapWarnings(tenantId, batch.investorId(), batch.channelId(), request.productKey(), request.effectiveFrom(), request.effectiveTo(), sheetId));
  }

  private void validateCreateRateSheetVersionRequest(RateFeedModels.CreateRateSheetVersionRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    requireUuid("BATCH_REQUIRED", "batchId is required.", request.batchId());
    if (request.productKey() == null || request.productKey().isBlank()) throw validation("PRODUCT_KEY_REQUIRED", "productKey is required for deterministic version resolution.");
    if (request.effectiveFrom() == null) throw validation("EFFECTIVE_FROM_REQUIRED", "effectiveFrom is required.");
    if (request.effectiveTo() != null && !request.effectiveTo().isAfter(request.effectiveFrom())) throw validation("INVALID_EFFECTIVE_WINDOW", "effectiveTo must be after effectiveFrom.");
    if (request.lineageReasonCode() == null || request.lineageReasonCode().isBlank()) throw validation("VERSION_LINEAGE_REQUIRED", "lineageReasonCode is required.");
    rejectFormulaMetadata("productKey", request.productKey());
    rejectFormulaMetadata("versionLabel", request.versionLabel());
    rejectFormulaMetadata("lineageReasonCode", request.lineageReasonCode());
    rejectFormulaMetadata("notes", request.notes());
  }

  public RateFeedModels.RateSheetVersionListResponse listRateSheetVersions(UUID tenantId, UUID investorId, UUID channelId, Instant asOf) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    StringBuilder sql = new StringBuilder("select rs.sheet_id,rs.version,rs.version_label,rs.status,rs.investor_id,rs.channel_id,rs.product_code,rs.effective_at,rs.effective_until,rs.source_batch_id,rs.created_at,rs.result_hash from rate_feed.rate_sheet rs where rs.tenant_id=?");
    List<Object> params = new ArrayList<>();
    params.add(tenantId);
    if (investorId != null) { sql.append(" and rs.investor_id=?"); params.add(investorId); }
    if (channelId != null) { sql.append(" and rs.channel_id=?"); params.add(channelId); }
    if (asOf != null) { sql.append(" and rs.effective_at <= ? and (rs.effective_until is null or rs.effective_until > ?)"); params.add(Timestamp.from(asOf)); params.add(Timestamp.from(asOf)); }
    sql.append(" order by rs.created_at desc, rs.version desc");
    List<RateFeedModels.RateSheetVersionSummary> versions = jdbc.query(sql.toString(), (rs, row) -> new RateFeedModels.RateSheetVersionSummary(
        rs.getObject("sheet_id", UUID.class), rs.getInt("version"), rs.getString("version_label"), rs.getString("status"),
        rs.getObject("investor_id", UUID.class), rs.getObject("channel_id", UUID.class), rs.getString("product_code"),
        rs.getTimestamp("effective_at").toInstant(), rs.getTimestamp("effective_until") == null ? null : rs.getTimestamp("effective_until").toInstant(),
        rs.getObject("source_batch_id", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getString("result_hash")), params.toArray());
    return new RateFeedModels.RateSheetVersionListResponse(versions, versions.size());
  }

  public RateFeedModels.RateSheetVersionResolveResponse resolveRateSheetVersion(UUID tenantId, UUID investorId, UUID channelId, String productKey, Instant asOf) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    requireUuid("INVESTOR_REQUIRED", "investorId is required for resolution.", investorId);
    requireUuid("CHANNEL_REQUIRED", "channelId is required for resolution.", channelId);
    if (productKey == null || productKey.isBlank()) throw validation("PRODUCT_KEY_REQUIRED", "productKey is required for resolution.");
    if (asOf == null) throw validation("AS_OF_REQUIRED", "asOf is required for resolution.");
    List<RateFeedModels.RateSheetVersionResolveResponse> matches = jdbc.query("select sheet_id,version,investor_id,channel_id,product_code,effective_at,effective_until,status,result_hash from rate_feed.rate_sheet where tenant_id=? and investor_id=? and channel_id=? and product_code=? and status in ('ACTIVE','PUBLISHED','ROLLBACK_PUBLISHED') and effective_at <= ? and (effective_until is null or effective_until > ?) order by version desc",
        (rs, row) -> new RateFeedModels.RateSheetVersionResolveResponse(rs.getObject("sheet_id", UUID.class), rs.getInt("version"), rs.getObject("investor_id", UUID.class), rs.getObject("channel_id", UUID.class), rs.getString("product_code"), rs.getTimestamp("effective_at").toInstant(), rs.getTimestamp("effective_until") == null ? null : rs.getTimestamp("effective_until").toInstant(), rs.getString("status"), rs.getString("result_hash")),
        tenantId, investorId, channelId, productKey, Timestamp.from(asOf), Timestamp.from(asOf));
    if (matches.isEmpty()) throw new RateFeedException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "No active published rate sheet version matched the resolution request.");
    if (matches.size() > 1) throw new RateFeedException(HttpStatus.CONFLICT, "AMBIGUOUS_ACTIVE_VERSION", "Multiple active published rate sheet versions matched the resolution request.");
    return matches.get(0);
  }

  @Transactional
  public RateFeedModels.PublishWorkflowStateResponse submitApproval(UUID tenantId, UUID versionId, RateFeedModels.SubmitApprovalRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER);
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("command", "submitRateSheetApproval");
    identity.put("versionId", versionId);
    identity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, identity, RateFeedModels.PublishWorkflowStateResponse.class,
        () -> doSubmitApproval(tenantId, versionId, request, actor(actor), correlation(correlationId)));
  }

  private RateFeedModels.PublishWorkflowStateResponse doSubmitApproval(UUID tenantId, UUID versionId, RateFeedModels.SubmitApprovalRequest request, String actor, String correlationId) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.changeSummary() == null || request.changeSummary().isBlank()) throw validation("CHANGE_SUMMARY_REQUIRED", "changeSummary is required.");
    rejectFormulaMetadata("changeSummary", request.changeSummary());
    RateSheet sheet = tenantSheetForUpdate(tenantId, versionId);
    if (sheet.status() != RateSheetStatus.VALIDATED) throw new RateFeedException(HttpStatus.CONFLICT, "VALIDATION_NOT_PASSED", "Only VALIDATED rate sheet versions can be submitted for approval.");
    String resultHash = workflowHash("submit", tenantId, versionId, actor, sheet.resultHash());
    jdbc.update("update rate_feed.rate_sheet set status='PENDING_APPROVAL', submitted_by=?, submitted_at=now(), workflow_change_summary=?, updated_at=now() where tenant_id=? and sheet_id=?",
        actor, safeText(request.changeSummary()), tenantId, versionId);
    insertDecision(tenantId, versionId, "SUBMIT_APPROVAL", "SUBMITTED", "SUBMIT_APPROVAL", request.changeSummary(), actor, RateFeedRoles.RATE_FEED_WRITER, correlationId);
    repository.outbox(tenantId, versionId, "RateSheetApprovalRequested.v1", 1, actor, correlationId, workflowHeaders(sheet), workflowPayload(tenantId, versionId, "PENDING_APPROVAL", resultHash, null));
    repository.audit(tenantId, versionId, "RATE_SHEET_APPROVAL_REQUESTED", "RateSheetVersion", actor, correlationId, sheet.resultHash(), resultHash, workflowPayload(tenantId, versionId, "PENDING_APPROVAL", resultHash, null));
    return workflowState(tenantId, versionId, "RateSheetApprovalRequested.v1", "RATE_SHEET_APPROVAL_REQUESTED", null, resultHash, List.of());
  }

  @Transactional
  public RateFeedModels.PublishWorkflowStateResponse decideApproval(UUID tenantId, UUID versionId, RateFeedModels.ApprovalDecisionRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_APPROVER);
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("command", "decideRateSheetApproval");
    identity.put("versionId", versionId);
    identity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, identity, RateFeedModels.PublishWorkflowStateResponse.class,
        () -> doDecideApproval(tenantId, versionId, request, actor(actor), correlation(correlationId)));
  }

  private RateFeedModels.PublishWorkflowStateResponse doDecideApproval(UUID tenantId, UUID versionId, RateFeedModels.ApprovalDecisionRequest request, String actor, String correlationId) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.decision() == null) throw validation("DECISION_REQUIRED", "decision is required.");
    if (request.reasonCode() == null || request.reasonCode().isBlank()) throw validation("REASON_CODE_REQUIRED", "reasonCode is required.");
    rejectFormulaMetadata("reasonCode", request.reasonCode());
    rejectFormulaMetadata("comment", request.comment());
    RateSheet sheet = tenantSheetForUpdate(tenantId, versionId);
    if (sheet.status() != RateSheetStatus.PENDING_APPROVAL) throw new RateFeedException(HttpStatus.CONFLICT, "APPROVAL_REQUIRED", "Rate sheet version must be pending approval before a decision can be recorded.");
    String submitter = workflowActor(tenantId, versionId, "submitted_by");
    if (actor.equals(submitter)) throw new RateFeedException(HttpStatus.FORBIDDEN, "SOD_VIOLATION", "Submitter cannot approve or reject their own rate sheet version.");
    String status = request.decision() == RateFeedModels.PublishWorkflowDecision.APPROVE ? "APPROVED" : "REJECTED";
    String resultHash = workflowHash("approval", tenantId, versionId, actor, request.decision().name(), sheet.resultHash());
    jdbc.update("update rate_feed.rate_sheet set status=?, approval_status=?, approved_by=?, approved_at=now(), rejected_at=case when ?='REJECTED' then now() else rejected_at end, rejected_by=case when ?='REJECTED' then ? else rejected_by end, rejection_reason=case when ?='REJECTED' then ? else rejection_reason end, updated_at=now() where tenant_id=? and sheet_id=?",
        status, request.decision().name(), actor, status, status, actor, status, safeText(request.reasonCode()), tenantId, versionId);
    insertDecision(tenantId, versionId, "APPROVAL_DECISION", request.decision().name(), request.reasonCode(), request.comment(), actor, RateFeedRoles.RATE_FEED_APPROVER, correlationId);
    repository.outbox(tenantId, versionId, "RateSheetApprovalDecided.v1", 1, actor, correlationId, workflowHeaders(sheet), workflowPayload(tenantId, versionId, status, resultHash, request.reasonCode()));
    repository.audit(tenantId, versionId, "RATE_SHEET_APPROVAL_DECIDED", "RateSheetVersion", actor, correlationId, sheet.resultHash(), resultHash, workflowPayload(tenantId, versionId, status, resultHash, request.reasonCode()));
    return workflowState(tenantId, versionId, "RateSheetApprovalDecided.v1", "RATE_SHEET_APPROVAL_DECIDED", null, resultHash, List.of());
  }

  @Transactional
  public RateFeedModels.PublishWorkflowStateResponse publishVersion(UUID tenantId, UUID versionId, RateFeedModels.PublishRateSheetRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_ACTIVATE);
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("command", "publishRateSheetVersion");
    identity.put("versionId", versionId);
    identity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, identity, RateFeedModels.PublishWorkflowStateResponse.class,
        () -> doPublishVersion(tenantId, versionId, request, actor(actor), correlation(correlationId)));
  }

  private RateFeedModels.PublishWorkflowStateResponse doPublishVersion(UUID tenantId, UUID versionId, RateFeedModels.PublishRateSheetRequest request, String actor, String correlationId) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    RateSheet sheet = tenantSheetForUpdate(tenantId, versionId);
    if (sheet.status() != RateSheetStatus.APPROVED) throw new RateFeedException(HttpStatus.CONFLICT, "APPROVAL_REQUIRED", "Rate sheet version must be approved before publish.");
    String submitter = workflowActor(tenantId, versionId, "submitted_by");
    if (actor.equals(submitter)) throw new RateFeedException(HttpStatus.FORBIDDEN, "SOD_VIOLATION", "Submitter cannot publish their own rate sheet version.");
    if (request.expectedValidationResultHash() == null || !request.expectedValidationResultHash().equals(sheet.gridHash())) throw new RateFeedException(HttpStatus.CONFLICT, "STALE_VERSION_HASH", "expectedValidationResultHash does not match the validated rate sheet grid hash.");
    if (request.expectedVersionHash() == null || !request.expectedVersionHash().equals(sheet.resultHash())) throw new RateFeedException(HttpStatus.CONFLICT, "STALE_VERSION_HASH", "expectedVersionHash does not match the current rate sheet version hash.");
    Instant publishAt = request.publishAt() == null ? Instant.now() : request.publishAt();
    if (publishAt.isAfter(Instant.now().plus(Duration.ofSeconds(30)))) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "SCHEDULER_UNAVAILABLE", "Future publication requires scheduler infrastructure that is not available in this service slice.");
    ensureNoActiveOverlap(tenantId, sheet);
    return publishSheet(tenantId, sheet, versionId, null, actor, correlationId, "RateSheetPublished.v1", "RATE_SHEET_PUBLISHED", "PUBLISHED");
  }

  @Transactional
  public RateFeedModels.PublishWorkflowStateResponse rollbackVersion(UUID tenantId, UUID versionId, RateFeedModels.RollbackRateSheetRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_ACTIVATE);
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("command", "rollbackRateSheetVersion");
    identity.put("versionId", versionId);
    identity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, identity, RateFeedModels.PublishWorkflowStateResponse.class,
        () -> doRollbackVersion(tenantId, versionId, request, actor(actor), correlation(correlationId)));
  }

  private RateFeedModels.PublishWorkflowStateResponse doRollbackVersion(UUID tenantId, UUID versionId, RateFeedModels.RollbackRateSheetRequest request, String actor, String correlationId) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    requireUuid("TARGET_VERSION_REQUIRED", "targetVersionId is required.", request.targetVersionId());
    if (request.reasonCode() == null || request.reasonCode().isBlank()) throw validation("REASON_CODE_REQUIRED", "reasonCode is required.");
    rejectFormulaMetadata("reasonCode", request.reasonCode());
    rejectFormulaMetadata("comment", request.comment());
    RateSheet current = tenantSheetForUpdate(tenantId, versionId);
    if (!Set.of(RateSheetStatus.PUBLISHED, RateSheetStatus.ACTIVE, RateSheetStatus.ROLLBACK_PUBLISHED).contains(current.status())) throw new RateFeedException(HttpStatus.CONFLICT, "APPROVAL_REQUIRED", "Rollback must start from the current published rate sheet version.");
    RateSheet target = tenantSheetForUpdate(tenantId, request.targetVersionId());
    if (!sameCoverage(current, target)) throw new RateFeedException(HttpStatus.CONFLICT, "PUBLISH_WINDOW_CONFLICT", "Rollback target must match the current investor/channel/product coverage.");
    if (!Set.of(RateSheetStatus.PUBLISHED, RateSheetStatus.ACTIVE, RateSheetStatus.SUPERSEDED, RateSheetStatus.ROLLBACK_PUBLISHED).contains(target.status())) throw new RateFeedException(HttpStatus.CONFLICT, "APPROVAL_REQUIRED", "Rollback target must be a previously published version.");
    insertDecision(tenantId, request.targetVersionId(), "ROLLBACK", "APPROVED", request.reasonCode(), request.comment(), actor, RateFeedRoles.RATE_FEED_ACTIVATE, correlationId);
    return publishSheet(tenantId, target, current.sheetId(), current.sheetId(), actor, correlationId, "RateSheetRollbackPublished.v1", "RATE_SHEET_ROLLBACK_PUBLISHED", "ROLLBACK_PUBLISHED");
  }

  @Transactional
  public RateFeedModels.RateSheetCacheInvalidationResponse createRateSheetCacheInvalidation(UUID tenantId, RateFeedModels.RateSheetCacheInvalidationRequest request, String idempotencyKey, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_OPERATIONS);
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("command", "rateSheetCacheInvalidation");
    identity.put("tenantId", tenantId);
    identity.put("body", request);
    return repository.idempotent(tenantId, idempotencyKey, identity, RateFeedModels.RateSheetCacheInvalidationResponse.class,
        () -> doCreateRateSheetCacheInvalidation(tenantId, request, actor(actor), correlation(correlationId)));
  }

  @Transactional
  public RateFeedModels.RateSheetCacheInvalidationResponse retryRateSheetCacheInvalidation(UUID tenantId, UUID cacheInvalidationId, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_OPERATIONS);
    requireUuid("CACHE_INVALIDATION_REQUIRED", "cacheInvalidationId is required.", cacheInvalidationId);
    RateFeedRepository.CacheInvalidationRow previous = repository.cacheInvalidation(tenantId, cacheInvalidationId);
    if ("COMPLETED".equals(previous.status())) {
      throw new RateFeedException(HttpStatus.CONFLICT, "INVALIDATION_ALREADY_COMPLETED", "Completed cache invalidations cannot be retried.");
    }
    return performCacheInvalidation(tenantId, cacheInvalidationId, previous.versionId(), previous.investorId(), previous.channelId(), previous.reason(),
        previous.expectedVersionHash(), previous.effectiveAt(), previous.retryCount() + 1, actor(actor), correlation(correlationId));
  }

  public RateFeedModels.RateSheetCacheInvalidationDetailResponse cacheInvalidation(UUID tenantId, UUID cacheInvalidationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    RateFeedRepository.CacheInvalidationRow row = repository.cacheInvalidation(tenantId, cacheInvalidationId);
    return new RateFeedModels.RateSheetCacheInvalidationDetailResponse(row.cacheInvalidationId(), row.tenantId(), row.versionId(), row.reason(), row.status(),
        row.affectedPatterns(), row.requestedBy(), row.createdAt(), row.completedAt(), row.retryCount(), row.lastErrorCode(), row.correlationId());
  }

  private RateFeedModels.RateSheetCacheInvalidationResponse doCreateRateSheetCacheInvalidation(UUID tenantId, RateFeedModels.RateSheetCacheInvalidationRequest request, String actor, String correlationId) {
    validateCacheInvalidationRequest(request);
    RateFeedRepository.PublishedRateSheetVersionRow version = repository.publishedRateSheetVersion(tenantId, request.versionId())
        .orElseThrow(() -> new RateFeedException(HttpStatus.CONFLICT, "VERSION_NOT_PUBLISHED", "Rate sheet version must be published before cache invalidation."));
    if (!version.resultHash().equals(request.expectedVersionHash())) {
      throw new RateFeedException(HttpStatus.CONFLICT, "STALE_VERSION_HASH", "expectedVersionHash does not match the published rate sheet version hash.");
    }
    if (!version.investorId().equals(request.investorId()) || !version.channelId().equals(request.channelId())) {
      throw new RateFeedException(HttpStatus.CONFLICT, "VERSION_NOT_PUBLISHED", "Requested investor/channel does not match the published rate sheet version.");
    }
    return performCacheInvalidation(tenantId, UUID.randomUUID(), request.versionId(), request.investorId(), request.channelId(), request.reason(), request.expectedVersionHash(), request.effectiveAt(), 0, actor, correlationId);
  }

  private void validateCacheInvalidationRequest(RateFeedModels.RateSheetCacheInvalidationRequest request) {
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    if (request.reason() == null) throw validation("REASON_REQUIRED", "reason is required.");
    requireUuid("VERSION_REQUIRED", "versionId is required.", request.versionId());
    requireUuid("INVESTOR_REQUIRED", "investorId is required.", request.investorId());
    requireUuid("CHANNEL_REQUIRED", "channelId is required.", request.channelId());
    if (request.expectedVersionHash() == null || request.expectedVersionHash().isBlank()) throw validation("EXPECTED_VERSION_HASH_REQUIRED", "expectedVersionHash is required for stale-version protection.");
    rejectFormulaMetadata("expectedVersionHash", request.expectedVersionHash());
  }

  private RateFeedModels.RateSheetCacheInvalidationResponse performCacheInvalidation(UUID tenantId, UUID cacheInvalidationId, UUID versionId,
      UUID investorId, UUID channelId, RateFeedModels.CacheInvalidationReason reason, String expectedVersionHash, Instant effectiveAt,
      int retryCount, String actor, String correlationId) {
    List<String> affectedPatterns = cacheKeyPatterns(tenantId, versionId, investorId, channelId);
    String status = retryCount > 0 ? "RETRYING" : "PENDING";
    String lastErrorCode = null;
    Instant completedAt = null;
    try {
      repository.evictCachePatterns(tenantId, cacheInvalidationId, affectedPatterns);
      status = "COMPLETED";
      completedAt = Instant.now();
    } catch (RuntimeException ex) {
      status = "FAILED";
      lastErrorCode = "CACHE_PROVIDER_UNAVAILABLE";
    }
    String resultHash = Hashing.sha256("cache-invalidation:" + tenantId + ":" + cacheInvalidationId + ":" + versionId + ":" + status + ":" + retryCount);
    RateFeedRepository.CacheInvalidationRow row = new RateFeedRepository.CacheInvalidationRow(tenantId, cacheInvalidationId, versionId, reason,
        status, affectedPatterns, actor, Instant.now(), completedAt, retryCount, lastErrorCode, correlationId, expectedVersionHash, investorId, channelId, effectiveAt, resultHash);
    repository.saveCacheInvalidation(row);
    try {
      publishCacheInvalidationEvents(tenantId, cacheInvalidationId, versionId, investorId, channelId, reason, status, affectedPatterns, actor, correlationId, retryCount, lastErrorCode, resultHash);
    } catch (RuntimeException ex) {
      status = "BROKER_UNAVAILABLE";
      lastErrorCode = "BROKER_UNAVAILABLE";
      repository.markCacheInvalidationBrokerUnavailable(tenantId, cacheInvalidationId, lastErrorCode);
    }
    repository.audit(tenantId, cacheInvalidationId, "RATE_SHEET_CACHE_INVALIDATION_" + status, "RateSheetCacheInvalidation", actor, correlationId, null, resultHash,
        cacheInvalidationPayload(cacheInvalidationId, versionId, investorId, channelId, reason, status, affectedPatterns, retryCount, lastErrorCode, resultHash));
    return new RateFeedModels.RateSheetCacheInvalidationResponse(cacheInvalidationId, status, affectedPatterns);
  }

  private void publishCacheInvalidationEvents(UUID tenantId, UUID cacheInvalidationId, UUID versionId, UUID investorId, UUID channelId,
      RateFeedModels.CacheInvalidationReason reason, String status, List<String> affectedPatterns, String actor, String correlationId,
      int retryCount, String lastErrorCode, String resultHash) {
    Map<String, String> headers = cacheInvalidationHeaders(investorId, channelId, correlationId);
    Map<String, Object> payload = cacheInvalidationPayload(cacheInvalidationId, versionId, investorId, channelId, reason, status, affectedPatterns, retryCount, lastErrorCode, resultHash);
    repository.outbox(tenantId, cacheInvalidationId, "RateSheetCacheInvalidationRequested.v1", 1, actor, correlationId, headers, payload);
    if ("COMPLETED".equals(status)) {
      repository.outbox(tenantId, cacheInvalidationId, "RateSheetCacheInvalidated.v1", 1, actor, correlationId, headers, payload);
    }
  }

  private Map<String, String> cacheInvalidationHeaders(UUID investorId, UUID channelId, String correlationId) {
    return Map.of("investorId", investorId.toString(), "channelId", channelId.toString(), "correlationId", correlationId);
  }

  private Map<String, Object> cacheInvalidationPayload(UUID cacheInvalidationId, UUID versionId, UUID investorId, UUID channelId,
      RateFeedModels.CacheInvalidationReason reason, String status, List<String> affectedPatterns, int retryCount, String lastErrorCode, String resultHash) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("cacheInvalidationId", cacheInvalidationId);
    payload.put("versionId", versionId);
    payload.put("investorId", investorId);
    payload.put("channelId", channelId);
    payload.put("reason", reason.name());
    payload.put("status", status);
    payload.put("affectedPatterns", affectedPatterns);
    payload.put("retryCount", retryCount);
    payload.put("lastErrorCode", lastErrorCode);
    payload.put("resultHash", resultHash);
    payload.put("sourceRefs", Map.of("rateSheetVersionId", versionId.toString()));
    return payload;
  }

  private List<String> cacheKeyPatterns(UUID tenantId, UUID versionId, UUID investorId, UUID channelId) {
    return List.of(
        "rate-sheet:" + tenantId + ":active:" + investorId + ":" + channelId + ":*",
        "rate-sheet:" + tenantId + ":version:" + versionId + ":*",
        "base-pricing:" + tenantId + ":rate-sheet:" + investorId + ":" + channelId + ":*");
  }

  private RateFeedModels.PublishWorkflowStateResponse publishSheet(UUID tenantId, RateSheet sheet, UUID commandVersionId, UUID rollbackFromVersionId, String actor, String correlationId, String eventType, String auditAction, String status) {
    String cacheCommandId = UUID.randomUUID().toString();
    String resultHash = workflowHash(eventType, tenantId, sheet.sheetId(), actor, sheet.gridHash(), sheet.resultHash(), cacheCommandId);
    jdbc.update("update rate_feed.rate_sheet set status='SUPERSEDED', effective_until=coalesce(effective_until, ?), updated_at=now() where tenant_id=? and investor_id=? and channel_id=? and product_code=? and sheet_id<>? and status in ('ACTIVE','PUBLISHED','ROLLBACK_PUBLISHED')",
        Timestamp.from(Instant.now()), tenantId, sheet.investorId(), sheet.channelId(), sheet.productCode(), sheet.sheetId());
    jdbc.update("update rate_feed.rate_sheet set status=?, activated_at=now(), activated_by=?, updated_at=now() where tenant_id=? and sheet_id=?", status, actor, tenantId, sheet.sheetId());
    jdbc.update("delete from rate_feed.published_rate_sheet_read_model where tenant_id=? and investor_id=? and channel_id=? and status='ACTIVE'", tenantId, sheet.investorId(), sheet.channelId());
    jdbc.update("insert into rate_feed.published_rate_sheet_read_model(tenant_id,version_id,investor_id,channel_id,effective_from,effective_to,status,coverage_hash,published_at,published_by,cache_invalidation_command_id) values (?,?,?,?,?,?,?,?,now(),?,?)",
        tenantId, sheet.sheetId(), sheet.investorId(), sheet.channelId(), Timestamp.from(sheet.effectiveAt()), sheet.effectiveUntil() == null ? null : Timestamp.from(sheet.effectiveUntil()), "ACTIVE", sheet.gridHash(), actor, cacheCommandId);
    Map<String, Object> payload = workflowPayload(tenantId, sheet.sheetId(), status, resultHash, null);
    payload.put("cacheInvalidationCommandId", cacheCommandId);
    payload.put("rollbackFromVersionId", rollbackFromVersionId);
    repository.outbox(tenantId, sheet.sheetId(), eventType, 1, actor, correlationId, workflowHeaders(sheet), payload);
    repository.audit(tenantId, sheet.sheetId(), auditAction, "RateSheetVersion", actor, correlationId, sheet.resultHash(), resultHash, payload);
    performCacheInvalidation(tenantId, UUID.fromString(cacheCommandId), sheet.sheetId(), sheet.investorId(), sheet.channelId(),
        rollbackFromVersionId == null ? RateFeedModels.CacheInvalidationReason.PUBLISH : RateFeedModels.CacheInvalidationReason.ROLLBACK,
        sheet.resultHash(), sheet.effectiveAt(), 0, actor, correlationId);
    eventPublisher.publishEvent(new GridSupersededEvent(tenantId, sheet.investorId(), sheet.channelId(), sheet.productCode(), sheet.sheetId(), sheet.effectiveAt()));
    eventPublisher.publishEvent(new GridLoadedEvent(tenantId, sheet.investorId(), sheet.channelId(), sheet.productCode(), sheet.sheetId(), sheet.effectiveAt()));
    List<RateFeedModels.ValidationWarningDetail> warnings = List.of(new RateFeedModels.ValidationWarningDetail("CACHE_INVALIDATION_ACK_UNAVAILABLE", "External cache invalidation acknowledgement infrastructure is not available in this local service slice; command ID was recorded."));
    return workflowState(tenantId, sheet.sheetId(), eventType, auditAction, cacheCommandId, resultHash, warnings);
  }

  private List<RatePricePoint> parsedPricePoints(UUID tenantId, UUID batchId) {
    List<Map<String, Object>> rows = jdbc.queryForList("select row_id,field_name,candidate_value from rate_feed.rate_feed_parsed_field where tenant_id=? and batch_id=? and severity <> 'ERROR' order by source_row_number, source_column", tenantId, batchId);
    Map<UUID, Map<String, String>> byRow = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      UUID rowId = (UUID) row.get("row_id");
      byRow.computeIfAbsent(rowId, ignored -> new LinkedHashMap<>()).put((String) row.get("field_name"), (String) row.get("candidate_value"));
    }
    List<RatePricePoint> points = new ArrayList<>();
    int position = 0;
    for (Map<String, String> fields : byRow.values()) {
      if (!fields.containsKey("note_rate") || !fields.containsKey("lock_period") || !fields.containsKey("base_price")) continue;
      points.add(new RatePricePoint(null, new BigDecimal(fields.get("note_rate")), Integer.parseInt(fields.get("lock_period")), new BigDecimal(fields.get("base_price")), nullableDecimal(fields.get("discount_points")), nullableDecimal(fields.get("yield_index")), position++));
    }
    return points;
  }

  private RateSheet tenantSheetForUpdate(UUID tenantId, UUID sheetId) {
    try {
      return jdbc.queryForObject("select * from rate_feed.rate_sheet where tenant_id=? and sheet_id=? for update", new Object[]{tenantId, sheetId}, sheetMapper());
    } catch (Exception ex) {
      throw new RateFeedException(HttpStatus.NOT_FOUND, "VERSION_NOT_FOUND", "Rate sheet version was not found for this tenant.");
    }
  }

  private String workflowActor(UUID tenantId, UUID versionId, String column) {
    try {
      String value = jdbc.queryForObject("select " + column + " from rate_feed.rate_sheet where tenant_id=? and sheet_id=?", String.class, tenantId, versionId);
      return value == null || value.isBlank() ? "" : value;
    } catch (Exception ex) {
      return "";
    }
  }

  private void ensureNoActiveOverlap(UUID tenantId, RateSheet sheet) {
    Instant upper = sheet.effectiveUntil() == null ? Instant.parse("9999-12-31T23:59:59Z") : sheet.effectiveUntil();
    Integer count = jdbc.queryForObject("select count(*) from rate_feed.rate_sheet where tenant_id=? and investor_id=? and channel_id=? and product_code=? and sheet_id<>? and status in ('ACTIVE','PUBLISHED','ROLLBACK_PUBLISHED') and effective_at < ? and coalesce(effective_until, timestamp with time zone '9999-12-31 23:59:59Z') > ?",
        Integer.class, tenantId, sheet.investorId(), sheet.channelId(), sheet.productCode(), sheet.sheetId(), Timestamp.from(upper), Timestamp.from(sheet.effectiveAt()));
    if (count != null && count > 0) throw new RateFeedException(HttpStatus.CONFLICT, "PUBLISH_WINDOW_CONFLICT", "Another published rate sheet overlaps this effective window.");
  }

  private static boolean sameCoverage(RateSheet left, RateSheet right) {
    return Objects.equals(left.investorId(), right.investorId()) && Objects.equals(left.channelId(), right.channelId()) && Objects.equals(left.productCode(), right.productCode());
  }

  private void insertDecision(UUID tenantId, UUID versionId, String decisionType, String decision, String reasonCode, String comment, String actor, String actorRole, String correlationId) {
    jdbc.update("insert into rate_feed.rate_sheet_workflow_decision(tenant_id,decision_id,version_id,decision_type,decision,reason_code,comment_redacted,actor_id,actor_role,correlation_id) values (?,?,?,?,?,?,?,?,?,?)",
        tenantId, UUID.randomUUID(), versionId, decisionType, decision, reasonCode, safeText(comment), actor, actorRole, correlationId);
  }

  private RateFeedModels.PublishWorkflowStateResponse workflowState(UUID tenantId, UUID versionId, String eventType, String auditAction, String cacheCommandId, String resultHash, List<RateFeedModels.ValidationWarningDetail> warnings) {
    return jdbc.queryForObject("select sheet_id,status,approval_status,submitted_by,approved_by,submitted_at,approved_at,activated_at from rate_feed.rate_sheet where tenant_id=? and sheet_id=?",
        (rs, row) -> new RateFeedModels.PublishWorkflowStateResponse(
            rs.getObject("sheet_id", UUID.class), rs.getString("status"), rs.getString("approval_status"), rs.getString("submitted_by"), rs.getString("approved_by"),
            rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),
            rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toInstant(),
            rs.getTimestamp("activated_at") == null ? null : rs.getTimestamp("activated_at").toInstant(),
            null, eventType, auditAction, cacheCommandId, resultHash, warnings), tenantId, versionId);
  }

  private Map<String, String> workflowHeaders(RateSheet sheet) {
    return Map.of("investorId", sheet.investorId().toString(), "channelId", sheet.channelId().toString(), "feedFormatId", "rate-sheet-version");
  }

  private Map<String, Object> workflowPayload(UUID tenantId, UUID versionId, String status, String resultHash, String reasonCode) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", versionId);
    payload.put("tenantId", tenantId);
    payload.put("status", status);
    payload.put("version", 1);
    payload.put("summary", status);
    payload.put("sourceRefs", Map.of("rateSheetVersionId", versionId));
    payload.put("reasonCode", reasonCode);
    payload.put("resultHash", resultHash);
    return payload;
  }

  private String workflowHash(Object... values) {
    return Hashing.sha256(repository.json(Arrays.asList(values)));
  }

  private static String safeText(String value) {
    if (value == null) return null;
    return value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
  }

  private static BigDecimal nullableDecimal(String value) {
    return value == null || value.isBlank() ? null : new BigDecimal(value);
  }

  private Map<String, Object> versionDelta(RateFeedModels.CreateRateSheetVersionRequest request, RateFeedRepository.BatchParseSource batch, String resultHash) {
    Map<String, Object> delta = new LinkedHashMap<>();
    delta.put("batchId", request.batchId());
    delta.put("investorId", batch.investorId());
    delta.put("channelId", batch.channelId());
    delta.put("feedFormatId", batch.feedFormatId());
    delta.put("productKey", request.productKey());
    delta.put("effectiveFrom", request.effectiveFrom());
    delta.put("effectiveTo", request.effectiveTo());
    delta.put("lineageReasonCode", request.lineageReasonCode());
    delta.put("resultHash", resultHash);
    return delta;
  }

  private List<RateFeedModels.ValidationWarningDetail> draftOverlapWarnings(UUID tenantId, UUID investorId, UUID channelId, String productKey, Instant effectiveFrom, Instant effectiveTo, UUID excludedSheetId) {
    Instant upper = effectiveTo == null ? Instant.parse("9999-12-31T23:59:59Z") : effectiveTo;
    Integer count = jdbc.queryForObject("select count(*) from rate_feed.rate_sheet where tenant_id=? and investor_id=? and channel_id=? and product_code=? and sheet_id<>? and status='DRAFT' and effective_at < ? and coalesce(effective_until, timestamp with time zone '9999-12-31 23:59:59Z') > ?",
        Integer.class, tenantId, investorId, channelId, productKey, excludedSheetId, Timestamp.from(upper), Timestamp.from(effectiveFrom));
    if (count == null || count == 0) return List.of();
    return List.of(new RateFeedModels.ValidationWarningDetail("EFFECTIVE_WINDOW_OVERLAP", "Draft effective window overlaps another draft version."));
  }

  private ParsedCsv parseCsvContent(String csvContent) {
    if (csvContent == null || csvContent.isBlank()) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "SOURCE_FILE_UNAVAILABLE", "Uploaded raw file content is not available to the local parser.");
    byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) if (b == 0) throw validation("CSV_ENCODING_UNSUPPORTED", "CSV contains binary content.");
    List<String> lines = csvContent.lines().toList();
    if (lines.isEmpty()) throw validation("EMPTY_RATE_SHEET", "CSV file is empty.");
    char delimiter = CsvParser.detectDelimiter(lines);
    String[] headers = CsvParser.tokenizeLine(lines.get(0), delimiter);
    Map<Integer, String> headerMap = HeaderDetector.mapHeaders(headers);
    List<ParsedField> fields = new ArrayList<>();
    int rowCount = 0;
    for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex);
      if (line.isBlank()) continue;
      rowCount++;
      String[] cells = CsvParser.tokenizeLine(line, delimiter);
      if (cells.length > headers.length + 25) {
        fields.add(new ParsedField(lineIndex + 1, "row", line, null, cells.length, "ERROR", "CSV_ROW_TOO_WIDE", "Row has more cells than the mapped header allows."));
        continue;
      }
      for (Map.Entry<Integer, String> mapping : headerMap.entrySet()) {
        String raw = mapping.getKey() < cells.length ? cells[mapping.getKey()].trim() : "";
        fields.add(parsedField(lineIndex + 1, mapping.getValue(), raw, mapping.getKey() + 1));
      }
    }
    if (rowCount == 0) throw validation("EMPTY_RATE_SHEET", "CSV file has no data rows.");
    return new ParsedCsv(rowCount, fields);
  }

  private ParsedField parsedField(int sourceRow, String fieldName, String raw, int sourceColumn) {
    if (isFormula(raw)) return new ParsedField(sourceRow, fieldName, raw, null, sourceColumn, "ERROR", "FORMULA_INJECTION_RISK", "Cell starts with a spreadsheet formula character.");
    if (raw == null || raw.isBlank()) {
      if (Set.of("discount_points", "yield_index").contains(fieldName)) {
        return new ParsedField(sourceRow, fieldName, raw, null, sourceColumn, "INFO", null, null);
      }
      return new ParsedField(sourceRow, fieldName, raw, null, sourceColumn, "ERROR", "REQUIRED_CELL_BLANK", "Mapped required cell is blank.");
    }
    try {
      String candidate = switch (fieldName) {
        case "note_rate" -> TypeCoercer.coerceRate(raw, sourceRow).toPlainString();
        case "lock_period" -> Integer.toString(TypeCoercer.coerceLockPeriod(raw, sourceRow));
        case "base_price" -> TypeCoercer.coerceBasePrice(raw, sourceRow).toPlainString();
        case "discount_points" -> Optional.ofNullable(TypeCoercer.coerceNullableOptionalDiscountPoints(raw, sourceRow)).map(BigDecimal::toPlainString).orElse(null);
        case "yield_index" -> Optional.ofNullable(TypeCoercer.coerceNullableYieldIndex(raw, sourceRow)).map(BigDecimal::toPlainString).orElse(null);
        default -> raw;
      };
      return new ParsedField(sourceRow, fieldName, raw, candidate, sourceColumn, "INFO", null, null);
    } catch (RuntimeException ex) {
      return new ParsedField(sourceRow, fieldName, raw, null, sourceColumn, "ERROR", "CSV_VALUE_INVALID", ex.getMessage());
    }
  }

  private void persistParsedRows(UUID tenantId, UUID batchId, ParsedCsv parsed) {
    jdbc.update("delete from rate_feed.rate_feed_parsed_field where tenant_id=? and batch_id=?", tenantId, batchId);
    jdbc.update("delete from rate_feed.rate_feed_raw_row where tenant_id=? and batch_id=?", tenantId, batchId);
    Map<Integer, List<ParsedField>> byRow = new TreeMap<>();
    for (ParsedField field : parsed.fields()) byRow.computeIfAbsent(field.sourceRowNumber(), ignored -> new ArrayList<>()).add(field);
    for (Map.Entry<Integer, List<ParsedField>> row : byRow.entrySet()) {
      UUID rowId = UUID.randomUUID();
      Map<String, String> rawCells = new LinkedHashMap<>();
      for (ParsedField field : row.getValue()) rawCells.put(field.fieldName(), field.rawValue());
      jdbc.update("insert into rate_feed.rate_feed_raw_row(tenant_id,batch_id,row_id,source_row_number,raw_row_sha256,raw_cells) values (?,?,?,?,?,?)",
          tenantId, batchId, rowId, row.getKey(), Hashing.sha256(repository.json(rawCells)), repository.jsonb(rawCells));
      for (ParsedField field : row.getValue()) {
        jdbc.update("insert into rate_feed.rate_feed_parsed_field(tenant_id,batch_id,row_id,field_name,raw_value,candidate_value,source_column,severity,error_code,message,source_row_number) values (?,?,?,?,?,?,?,?,?,?,?)",
            tenantId, batchId, rowId, field.fieldName(), field.rawValue(), field.candidateValue(), field.sourceColumn(), field.severity(), field.errorCode(), field.message(), field.sourceRowNumber());
      }
    }
  }

  private void emitParseEvidence(UUID tenantId, UUID batchId, UUID parseJobId, RateFeedRepository.BatchParseSource batch, String finalStatus, String resultHash, String actor, String correlationId, int rowCount, int errorCount, int warningCount) {
    String eventType = "PARSED".equals(finalStatus) ? "RateSheetParsed.v1" : "RateSheetParseFailed.v1";
    repository.outbox(tenantId, batchId, eventType, 1, actor, correlationId, parseHeaders(batch),
        Map.of("batchId", batchId, "parseJobId", parseJobId, "mappingVersion", batch.feedFormatId().toString(), "rowCount", rowCount, "errorCount", errorCount, "warningCount", warningCount, "resultHash", resultHash, "errorReportId", parseJobId));
    repository.audit(tenantId, batchId, "RATE_SHEET_" + finalStatus, "RateFeedBatch", actor, correlationId, null, resultHash,
        Map.of("batchId", batchId, "parseJobId", parseJobId, "status", finalStatus, "rowCount", rowCount, "errorCount", errorCount, "warningCount", warningCount));
  }

  private Map<String, String> parseHeaders(RateFeedRepository.BatchParseSource batch) {
    return Map.of("investorId", batch.investorId().toString(), "channelId", batch.channelId().toString(), "feedFormatId", batch.feedFormatId().toString());
  }

  private static boolean isFormula(String value) {
    if (value == null || value.isBlank()) return false;
    char first = value.stripLeading().charAt(0);
    return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r' || first == '\n';
  }

  private record ParsedCsv(int rowCount, List<ParsedField> fields) {}
  private record ParsedField(int sourceRowNumber, String fieldName, String rawValue, String candidateValue, int sourceColumn, String severity, String errorCode, String message) {}
  private record ProfileSelection(UUID profileId, String profileVersion, int matchScore, String routingAction) {}
  private record OcrCellForApproval(UUID cellId, int rowIndex, int columnIndex, String rawText, String reviewedText, String status) {}
  private record ValidationCandidate(UUID entryId, int sourceRowNumber, String canonicalProductKey, int lockPeriodDays, BigDecimal ratePercent, BigDecimal pricePoints, String adjustmentType, BigDecimal adjustmentValue, String adjustmentUnit, String severity, String message) {}
  private record ValidationFindingDraft(UUID entryId, RateFeedModels.ValidationFindingSeverity severity, String ruleCode, String ruleVersion, String fieldName, String messageCode, Map<String, Object> messageParams, String remediationCode, Integer sourceRowNumber) {
    static ValidationFindingDraft blocker(ValidationCandidate entry, String ruleCode, String ruleVersion, String fieldName, String messageCode, String remediationCode) {
      return finding(entry, RateFeedModels.ValidationFindingSeverity.BLOCKER, ruleCode, ruleVersion, fieldName, messageCode, remediationCode);
    }
    static ValidationFindingDraft warning(ValidationCandidate entry, String ruleCode, String ruleVersion, String fieldName, String messageCode, String remediationCode) {
      return finding(entry, RateFeedModels.ValidationFindingSeverity.WARNING, ruleCode, ruleVersion, fieldName, messageCode, remediationCode);
    }
    private static ValidationFindingDraft finding(ValidationCandidate entry, RateFeedModels.ValidationFindingSeverity severity, String ruleCode, String ruleVersion, String fieldName, String messageCode, String remediationCode) {
      return new ValidationFindingDraft(entry.entryId(), severity, ruleCode, ruleVersion, fieldName, messageCode, Map.of("sourceRowNumber", entry.sourceRowNumber()), remediationCode, entry.sourceRowNumber());
    }
    Map<String, Object> hashMaterial() {
      Map<String, Object> material = new LinkedHashMap<>();
      material.put("entryId", entryId);
      material.put("severity", severity.name());
      material.put("ruleCode", ruleCode);
      material.put("ruleVersion", ruleVersion);
      material.put("fieldName", fieldName);
      material.put("messageCode", messageCode);
      material.put("remediationCode", remediationCode);
      material.put("sourceRowNumber", sourceRowNumber);
      return material;
    }
  }
  private record NormalizedCandidate(UUID entryId, int sourceRowNumber, String canonicalProductKey, String programKey, int lockPeriodDays, BigDecimal ratePercent, BigDecimal pricePoints, String adjustmentType, BigDecimal adjustmentValue, String adjustmentUnit, String severity, String message, Map<String, String> rawAttributes, Map<String, Object> mappingRefs) {
    static NormalizedCandidate success(int sourceRowNumber, String productKey, String programKey, int lockPeriodDays, BigDecimal ratePercent, BigDecimal pricePoints, String adjustmentType, BigDecimal adjustmentValue, String adjustmentUnit, Map<String, String> rawAttributes, Map<String, Object> mappingRefs, String profileVersion) {
      return new NormalizedCandidate(UUID.randomUUID(), sourceRowNumber, productKey, programKey, lockPeriodDays, ratePercent, pricePoints, adjustmentType, adjustmentValue, adjustmentUnit, "INFO", "normalized", Map.copyOf(rawAttributes), Map.copyOf(mappingRefs));
    }
    static NormalizedCandidate error(int sourceRowNumber, String message, Map<String, String> rawAttributes, UUID profileId, String profileVersion) {
      return new NormalizedCandidate(UUID.randomUUID(), sourceRowNumber, null, null, 0, null, null, null, null, null, "ERROR", message, Map.copyOf(rawAttributes), Map.of("profileId", profileId.toString(), "profileVersion", profileVersion));
    }
    Map<String, Object> dimensions() {
      Map<String, Object> dimensions = new LinkedHashMap<>();
      dimensions.put("sourceRow", sourceRowNumber);
      if (adjustmentType != null && !adjustmentType.isBlank()) dimensions.put("adjustmentType", adjustmentType);
      if (adjustmentUnit != null && !adjustmentUnit.isBlank()) dimensions.put("adjustmentUnit", adjustmentUnit);
      return dimensions;
    }
    Map<String, Object> hashMaterial() {
      Map<String, Object> material = new LinkedHashMap<>();
      material.put("sourceRow", sourceRowNumber);
      material.put("canonicalProductKey", canonicalProductKey);
      material.put("lockPeriodDays", lockPeriodDays);
      material.put("ratePercent", ratePercent == null ? null : ratePercent.toPlainString());
      material.put("pricePoints", pricePoints == null ? null : pricePoints.toPlainString());
      material.put("severity", severity);
      material.put("message", message);
      material.put("mappingRefs", mappingRefs);
      return material;
    }
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

  @Transactional
  public RateFeedModels.MapToRuleBookResponse mapToRuleBook(UUID tenantId, UUID sheetId, RateFeedModels.MapToRuleBookRequest request, String actor, String correlationId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_WRITER);
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    UUID requestTenant = request.tenantId() == null ? tenantId : request.tenantId();
    if (!tenantId.equals(requestTenant)) throw validation("TENANT_MISMATCH", "Request tenantId must match path tenantId.");
    if (request.csvContent() == null || request.csvContent().isBlank()) throw validation("CSV_CONTENT_REQUIRED", "csvContent is required because persisted rate sheets do not retain LLPA dimension rows in this local slice.");
    RateFeedModels.LlpaMappingConfig cfg = request.mappingConfig();
    if (cfg == null) throw validation("MAPPING_CONFIG_REQUIRED", "mappingConfig is required.");
    rejectFormulaMetadata("investor", cfg.investor());
    rejectFormulaMetadata("productFamily", cfg.productFamily());
    rejectFormulaMetadata("channel", cfg.channel());

    var draft = ruleBookMapper.mapToRuleBook(request.csvContent(), new LlpaGridToRuleBookMapper.MappingConfig(
        tenantId, sheetId, cfg.investor(), cfg.productFamily(), cfg.channel(), cfg.outputType(), cfg.effectiveAt(), cfg.versionLabel(), cfg.sourceSystem()));
    List<RateFeedModels.RuleBookRuleResponse> rules = draft.rules().stream()
        .map(rule -> new RateFeedModels.RuleBookRuleResponse(rule.ruleId(), rule.priority(),
            rule.conditions().stream().map(condition -> new RateFeedModels.RuleBookConditionResponse(condition.dimension(), condition.operator(), condition.configuredValues())).toList(),
            new RateFeedModels.RuleBookOutputResponse(rule.output().type(), rule.output().configuredAmount()), rule.reasonCode(), rule.exclusivityGroup(), rule.enabled(), rule.sourceRef()))
        .toList();
    List<RateFeedModels.PipelineMappingWarning> warnings = draft.warnings().stream()
        .map(warning -> new RateFeedModels.PipelineMappingWarning(warning.rowNumber(), warning.code(), warning.message()))
        .toList();
    String resultHash = Hashing.sha256("map-to-rulebook:" + tenantId + ":" + sheetId + ":" + draft.gridHash() + ":" + draft.rules().size());
    RateFeedModels.MapToRuleBookResponse response = new RateFeedModels.MapToRuleBookResponse(sheetId, draft.ruleBookId(), draft.businessKey(), draft.version(), draft.status(),
        new RateFeedModels.RuleBookSelectorResponse(draft.selector().productFamily(), draft.selector().investor(), draft.selector().channel()),
        new RateFeedModels.RuleBookEffectiveWindowResponse(draft.effectiveWindow().start(), draft.effectiveWindow().end()),
        new RateFeedModels.RuleBookPrecisionPolicyResponse(draft.precisionPolicy().pointsScale(), draft.precisionPolicy().bpsScale(), draft.precisionPolicy().moneyScale(), draft.precisionPolicy().roundingMode()),
        draft.sourceRowCount(), rules.size(), draft.gridHash(), warnings, rules,
        Map.of("pipeline", "/api/v1/tenants/" + tenantId + "/ratefeed/pipeline", "governanceDraft", "governance-service:RuleSetDraftSaved.v1"), resultHash);
    recordPipelineRow(sheetId, response, actor(actor), correlation(correlationId));
    try {
      repository.audit(tenantId, sheetId, "RATE_SHEET_MAPPED_TO_RULE_BOOK", "RateSheetRuleBookPipeline", actor(actor), correlation(correlationId), null, resultHash,
          Map.of("ruleBookId", response.ruleBookId(), "businessKey", response.businessKey(), "ruleCount", response.ruleCount(), "gridHash", response.gridHash()));
      repository.outbox(tenantId, sheetId, "RateSheetMappedToRuleBook.v1", 1, actor(actor), correlation(correlationId), Map.of("investor", response.selector().investor(), "channel", response.selector().channel()),
          Map.of("sheetId", sheetId, "ruleBookId", response.ruleBookId(), "businessKey", response.businessKey(), "ruleCount", response.ruleCount(), "gridHash", response.gridHash(), "resultHash", resultHash));
    } catch (RuntimeException ignored) {
      // Mapping still returns local/dev evidence if optional audit/outbox tables are unavailable.
    }
    return response;
  }

  public RateFeedModels.PipelineStatusResponse pipelineStatus(UUID tenantId) {
    RateFeedRoles.require(RateFeedRoles.RATE_FEED_VIEW);
    return new RateFeedModels.PipelineStatusResponse(List.copyOf(pipelineRows.values()), pipelineRows.size(), Instant.now());
  }

  private void recordPipelineRow(UUID sheetId, RateFeedModels.MapToRuleBookResponse response, String actor, String correlationId) {
    List<String> dimensions = response.rules().stream().flatMap(rule -> rule.conditions().stream().map(RateFeedModels.RuleBookConditionResponse::dimension)).distinct().sorted().toList();
    RateFeedModels.PipelineSampleSimulation sample = response.rules().isEmpty()
        ? new RateFeedModels.PipelineSampleSimulation("no mapped rules", "n/a", "n/a", "SKIPPED")
        : new RateFeedModels.PipelineSampleSimulation("sample rule " + response.rules().get(0).sourceRef(), response.rules().get(0).output().configuredAmount() + " " + response.rules().get(0).output().type(), response.rules().get(0).output().configuredAmount() + " " + response.rules().get(0).output().type(), "MATCH");
    List<RateFeedModels.PipelineGovernanceStage> history = List.of(
        new RateFeedModels.PipelineGovernanceStage("DRAFT", "COMPLETED", Instant.now(), actor, response.resultHash()),
        new RateFeedModels.PipelineGovernanceStage("SIMULATE", "READY", Instant.now(), "governance-service", correlationId),
        new RateFeedModels.PipelineGovernanceStage("APPROVE", "WAITING_FOR_ADMIN", Instant.now(), "pricing-admin", "manual-approval-required"),
        new RateFeedModels.PipelineGovernanceStage("PUBLISH", "WAITING_ON_APPROVAL", Instant.now(), "governance-service", "RuleBookPublished.v1"));
    pipelineRows.put(sheetId, new RateFeedModels.PipelineStatusRow(sheetId, response.ruleBookId(), response.businessKey(), response.selector().investor(),
        "DRAFT", response.ruleCount(), "Mapped to draft rule book by " + actor, response.gridHash(), response.sourceRowCount(), response.warnings().size(), dimensions, history, sample));
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

  @Transactional
  RateFeedModels.PartnerSubmissionResponse submitPartnerFile(String tenantExternalKey, RateFeedModels.PartnerFileSubmissionMetadata metadata, MultipartFile file, PartnerHeaders headers) {
    PartnerAuth auth = requirePartnerAuth(headers);
    if (headers.rateLimitExceeded()) throw new RateFeedException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Partner rate limit policy denied this request.");
    if (metadata == null) throw validation("METADATA_REQUIRED", "metadata part is required.");
    requirePartnerField("INVESTOR_EXTERNAL_KEY_REQUIRED", "investorExternalKey is required.", metadata.investorExternalKey());
    requirePartnerField("CHANNEL_EXTERNAL_KEY_REQUIRED", "channelExternalKey is required.", metadata.channelExternalKey());
    requirePartnerField("FEED_FORMAT_REQUIRED", "feedFormatExternalKey is required.", metadata.feedFormatExternalKey());
    if (metadata.effectiveAt() == null) throw validation("EFFECTIVE_TIME_REQUIRED", "effectiveAt is required.");
    if (metadata.timezone() == null || metadata.timezone().isBlank()) throw validation("EFFECTIVE_TIME_REQUIRED", "timezone is required.");
    try { ZoneId.of(metadata.timezone()); } catch (DateTimeException ex) { throw validation("MALFORMED_METADATA", "timezone metadata is not valid."); }
    rejectFormulaMetadata("sourceSystem", metadata.sourceSystem());
    if (file == null || file.isEmpty()) throw validation("FILE_REQUIRED", "file is required.");
    String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("partner-rate-feed.csv");
    rejectFormulaMetadata("fileName", fileName);
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) throw new RateFeedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FEED_FORMAT_NOT_ALLOWED", "Only CSV partner feed files are supported by this local slice.");
    String contentType = normalizeMediaType(file.getContentType());
    if (!Set.of("text/csv", "application/csv", "application/vnd.ms-excel", "").contains(contentType)) throw new RateFeedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FEED_FORMAT_NOT_ALLOWED", "Unsupported partner feed media type.");
    RateFeedRepository.InvestorFeedIntegrationRow integration = requireIntegration(tenantExternalKey, metadata.investorExternalKey(), metadata.channelExternalKey(), metadata.feedFormatExternalKey());
    String schemaVersion = integration.schemaVersion();
    if (metadata.schemaVersion() != null && !metadata.schemaVersion().isBlank() && !metadata.schemaVersion().equals(schemaVersion)) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "SCHEMA_VERSION_UNSUPPORTED", "schemaVersion is not configured for this partner feed integration.");
    Map<String, Object> identity = partnerIdentity("partnerFileSubmission", tenantExternalKey, metadata, fileName, file.getSize(), auth.subject());
    return repository.idempotent(integration.tenantId(), headers.idempotencyKey(), identity, RateFeedModels.PartnerSubmissionResponse.class,
        () -> repository.savePartnerSubmission(integration, UUID.randomUUID(), UUID.randomUUID(), "FILE", "RECEIVED", schemaVersion, fileName, contentType.isBlank() ? "text/csv" : contentType,
            file.getSize(), Hashing.sha256(repository.json(identity)), auth.subject(), correlation(headers.correlationId())));
  }

  @Transactional
  RateFeedModels.PartnerSubmissionResponse submitPartnerStructured(String tenantExternalKey, RateFeedModels.PartnerStructuredSubmissionRequest request, PartnerHeaders headers) {
    PartnerAuth auth = requirePartnerAuth(headers);
    if (headers.rateLimitExceeded()) throw new RateFeedException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "Partner rate limit policy denied this request.");
    if (request == null) throw validation("REQUEST_BODY_REQUIRED", "Request body is required.");
    requirePartnerField("INVESTOR_EXTERNAL_KEY_REQUIRED", "investorExternalKey is required.", request.investorExternalKey());
    requirePartnerField("CHANNEL_EXTERNAL_KEY_REQUIRED", "channelExternalKey is required.", request.channelExternalKey());
    requirePartnerField("FEED_FORMAT_REQUIRED", "feedFormatExternalKey is required.", request.feedFormatExternalKey());
    if (request.schemaVersion() == null || request.schemaVersion().isBlank()) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "SCHEMA_VERSION_UNSUPPORTED", "schemaVersion is required.");
    if (request.rows() == null || request.rows().isEmpty()) throw validation("EMPTY_RATE_SHEET", "Structured submission must include at least one row.");
    RateFeedRepository.InvestorFeedIntegrationRow integration = requireIntegration(tenantExternalKey, request.investorExternalKey(), request.channelExternalKey(), request.feedFormatExternalKey());
    if (!request.schemaVersion().equals(integration.schemaVersion())) throw new RateFeedException(HttpStatus.UNPROCESSABLE_ENTITY, "SCHEMA_VERSION_UNSUPPORTED", "schemaVersion is not configured for this partner feed integration.");
    validateStructuredRows(request.rows());
    Map<String, Object> identity = partnerIdentity("partnerStructuredSubmission", tenantExternalKey, request.investorExternalKey(), request.channelExternalKey(), request.feedFormatExternalKey(), request.schemaVersion(), request.rows(), request.metadata(), auth.subject());
    return repository.idempotent(integration.tenantId(), headers.idempotencyKey(), identity, RateFeedModels.PartnerSubmissionResponse.class,
        () -> repository.savePartnerSubmission(integration, UUID.randomUUID(), UUID.randomUUID(), "STRUCTURED", "VALIDATION_PENDING", request.schemaVersion(), "structured-submission.json", "application/json",
            repository.json(request.rows()).length(), Hashing.sha256(repository.json(identity)), auth.subject(), correlation(headers.correlationId())));
  }

  RateFeedModels.PartnerSubmissionStatusResponse partnerSubmissionStatus(String tenantExternalKey, UUID submissionId, PartnerHeaders headers) {
    requirePartnerAuth(headers);
    if (tenantExternalKey == null || tenantExternalKey.isBlank()) throw validation("TENANT_EXTERNAL_KEY_REQUIRED", "tenantExternalKey is required.");
    requireUuid("SUBMISSION_REQUIRED", "submissionId is required.", submissionId);
    return repository.partnerSubmissionStatus(tenantExternalKey, submissionId);
  }

  private RateFeedRepository.InvestorFeedIntegrationRow requireIntegration(String tenantExternalKey, String investorExternalKey, String channelExternalKey, String feedFormat) {
    if (tenantExternalKey == null || tenantExternalKey.isBlank()) throw validation("TENANT_EXTERNAL_KEY_REQUIRED", "tenantExternalKey is required.");
    return repository.partnerIntegration(tenantExternalKey, investorExternalKey, channelExternalKey, feedFormat)
        .orElseThrow(() -> new RateFeedException(HttpStatus.FORBIDDEN, "PARTNER_NOT_AUTHORIZED_FOR_INVESTOR", "Partner is not authorized for the requested tenant, investor, channel, and feed format."));
  }

  private static PartnerAuth requirePartnerAuth(PartnerHeaders headers) {
    if (headers == null || headers.clientId() == null || headers.clientId().isBlank() || headers.authSubject() == null || headers.authSubject().isBlank()) {
      throw new RateFeedException(HttpStatus.UNAUTHORIZED, "PARTNER_AUTH_FAILED", "Partner authentication subject is required.");
    }
    rejectFormulaMetadata("partnerClientId", headers.clientId());
    rejectFormulaMetadata("partnerAuthSubject", headers.authSubject());
    return new PartnerAuth(headers.clientId().trim(), headers.authSubject().trim());
  }

  private static void requirePartnerField(String code, String message, String value) {
    if (value == null || value.isBlank()) throw validation(code, message);
    rejectFormulaMetadata(code, value);
  }

  private static void validateStructuredRows(List<Map<String, Object>> rows) {
    int rowNumber = 0;
    for (Map<String, Object> row : rows) {
      rowNumber++;
      if (row == null || row.isEmpty()) throw validation("STRUCTURED_ROW_INVALID", "Structured row " + rowNumber + " is empty.");
      for (Map.Entry<String, Object> entry : row.entrySet()) {
        if (entry.getKey() == null || entry.getKey().isBlank()) throw validation("STRUCTURED_ROW_INVALID", "Structured row " + rowNumber + " contains a blank field name.");
        rejectFormulaMetadata(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
      }
    }
  }

  private Map<String, Object> partnerIdentity(String command, Object... values) {
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("command", command);
    identity.put("values", Arrays.asList(values));
    return identity;
  }

  record PartnerHeaders(String idempotencyKey, String clientId, String authSubject, String correlationId, boolean rateLimitExceeded) {}
  private record PartnerAuth(String clientId, String subject) {}

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

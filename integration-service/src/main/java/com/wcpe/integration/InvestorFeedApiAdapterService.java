package com.wcpe.integration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InvestorFeedApiAdapterService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/investor-api-adapters";
  public static final String WRITE_PERMISSION = "integrations.investor-adapter.write";
  public static final String READ_PERMISSION = "integrations.investor-adapter.read";
  public static final String SERVICE_ACCOUNT_PERMISSION = "integrations.investor-adapter.callback";
  public static final String RUN_STARTED_EVENT_TYPE = "integration.investor-feed-run.started.v1";
  public static final String RUN_NORMALIZED_EVENT_TYPE = "integration.investor-feed-run.normalized.v1";
  public static final String RUN_FAILED_EVENT_TYPE = "integration.investor-feed-run.failed.v1";
  public static final String RECORD_NORMALIZED_EVENT_TYPE = "integration.investor-feed-record.normalized.v1";
  public static final String AUDIT_ACTION = "INVESTOR_FEED_API_ADAPTER_COMPLETED";

  private static final Set<String> SUPPORTED_FEED_TYPES = Set.of("CATALOG", "PRICE_SHEET", "ELIGIBILITY");

  private final Clock clock;
  private final InvestorApiClient investorApiClient;
  private final Set<String> egressAllowedHosts;
  private final Map<String, InvestorFeedNormalizer> normalizers = new HashMap<>();
  private final Map<String, InvestorApiAdapter> adapters = new HashMap<>();
  private final Map<String, InvestorFeedImportRun> runs = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final List<InvestorFeedRecord> stagingRecords = new ArrayList<>();
  private final List<InvestorFeedOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<InvestorFeedAuditRecord> auditRecords = new ArrayList<>();

  public InvestorFeedApiAdapterService(InvestorApiClient investorApiClient, Set<String> egressAllowedHosts) {
    this(Clock.systemUTC(), investorApiClient, egressAllowedHosts);
  }

  public InvestorFeedApiAdapterService(Clock clock, InvestorApiClient investorApiClient, Set<String> egressAllowedHosts) {
    this.clock = clock;
    this.investorApiClient = investorApiClient;
    this.egressAllowedHosts = egressAllowedHosts == null ? Set.of() : Set.copyOf(egressAllowedHosts);
  }

  public void registerNormalizer(String investorId, String schemaVersion, InvestorFeedNormalizer normalizer) {
    normalizers.put(strategyKey(investorId, schemaVersion), normalizer);
  }

  public InvestorAdapterResult configureAdapter(ConfigureAdapterCommand command) {
    InvestorFeedError validation = validateConfigureCommand(command);
    if (validation != null) {
      return InvestorAdapterResult.failure(validation);
    }
    String requestHash = hash(canonicalConfigureCommand(command));
    String idempotencyKey = command.tenantId() + ":configure:" + command.idempotencyKey();
    InvestorAdapterResult replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    Instant now = clock.instant();
    InvestorApiAdapter adapter =
        new InvestorApiAdapter(
            command.tenantId(),
            command.adapterId(),
            command.investorId(),
            urlHash(command.baseUrl()),
            command.authCredentialRef(),
            command.enabled() ? AdapterStatus.ACTIVE : AdapterStatus.PAUSED,
            command.schemaVersion(),
            command.pollSchedule(),
            command.rateLimitPerMinute(),
            List.copyOf(command.feedTypes()),
            command.effectiveFrom(),
            1,
            now,
            now,
            command.actorId(),
            command.correlationId());
    adapters.put(adapterKey(adapter.tenantId(), adapter.adapterId()), adapter);

    InvestorAdapterResponse response =
        new InvestorAdapterResponse(
            adapter.adapterId(),
            adapter.status().name(),
            adapter.version(),
            Map.of("investorId", adapter.investorId(), "schemaVersion", adapter.schemaVersion()),
            List.of(),
            deterministicId(adapter.tenantId(), adapter.adapterId(), "audit", "configure"),
            command.correlationId());
    writeAudit(command.actorId(), null, adapter, "configure");
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return InvestorAdapterResult.success(response);
  }

  public InvestorAdapterResult triggerSync(SyncInvestorFeedCommand command) {
    InvestorFeedError validation = validateSyncCommand(command);
    if (validation != null) {
      return InvestorAdapterResult.failure(validation);
    }
    InvestorApiAdapter adapter = adapters.get(adapterKey(command.tenantId(), command.adapterId()));
    if (adapter == null) {
      return InvestorAdapterResult.failure(error("404", "NOT_FOUND", "Investor API adapter was not found", command.correlationId(), false));
    }
    if (adapter.status() != AdapterStatus.ACTIVE || !adapter.feedTypes().contains(command.feedType())) {
      return InvestorAdapterResult.failure(error("422", "POLICY_NOT_SATISFIED", "Adapter is not active for the requested feed type", command.correlationId(), false));
    }
    InvestorFeedNormalizer normalizer = normalizers.get(strategyKey(adapter.investorId(), adapter.schemaVersion()));
    if (normalizer == null) {
      return InvestorAdapterResult.failure(error("422", "POLICY_NOT_SATISFIED", "Investor schema normalizer is required", command.correlationId(), false));
    }

    String requestHash = hash(command.tenantId() + ":" + command.adapterId() + ":" + command.feedType() + ":" + command.cursor());
    String idempotencyKey = command.tenantId() + ":sync:" + command.idempotencyKey();
    InvestorAdapterResult replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    String runId = deterministicId(command.tenantId(), command.adapterId(), command.feedType(), command.idempotencyKey());
    Instant startedAt = clock.instant();
    InvestorFeedImportRun requested =
        new InvestorFeedImportRun(
            command.tenantId(),
            runId,
            command.adapterId(),
            command.feedType(),
            RunStatus.REQUESTED,
            command.cursor(),
            "",
            "",
            0,
            0,
            "",
            startedAt,
            null,
            command.actorId(),
            command.correlationId());
    runs.put(runKey(command.tenantId(), runId), requested);
    writeOutbox(RUN_STARTED_EVENT_TYPE, adapter, requested, command.actorId(), command.idempotencyKey(), Map.of("feedType", command.feedType()));

    try {
      InvestorFeedPayload payload = fetchPayload(command, adapter);
      if (!adapter.schemaVersion().equals(payload.schemaVersion())) {
        throw new InvestorApiException("SCHEMA_VERSION_MISMATCH", false);
      }
      List<InvestorFeedRecord> normalized = normalizer.normalize(requested, payload.records());
      String sourceChecksum = checksum(payload.checksumSource(), payload.records());
      String normalizedChecksum = checksum("normalized", normalized.stream().map(InvestorFeedRecord::normalizedJson).toList());
      stagingRecords.addAll(normalized);
      InvestorFeedImportRun completed =
          new InvestorFeedImportRun(
              requested.tenantId(),
              requested.runId(),
              requested.adapterId(),
              requested.feedType(),
              RunStatus.PUBLISHED,
              payload.nextCursor(),
              sourceChecksum,
              normalizedChecksum,
              payload.records().size(),
              normalized.size(),
              "",
              requested.startedAt(),
              clock.instant(),
              requested.actorId(),
              requested.correlationId());
      runs.put(runKey(completed.tenantId(), completed.runId()), completed);
      writeOutbox(RUN_NORMALIZED_EVENT_TYPE, adapter, completed, command.actorId(), command.idempotencyKey(), Map.of("normalizedChecksum", normalizedChecksum));
      for (InvestorFeedRecord record : normalized) {
        writeRecordOutbox(adapter, completed, record, command.actorId(), command.idempotencyKey());
      }
      investorApiClient.acknowledgeReceipt(adapter, completed);
      writeAudit(command.actorId(), adapter, adapter, "sync");
      InvestorAdapterResponse response = response(completed, adapter, List.of());
      idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
      return InvestorAdapterResult.success(response);
    } catch (InvestorApiException exception) {
      InvestorFeedImportRun failed =
          new InvestorFeedImportRun(
              requested.tenantId(),
              requested.runId(),
              requested.adapterId(),
              requested.feedType(),
              RunStatus.FAILED,
              requested.cursor(),
              "",
              "",
              0,
              0,
              exception.failureClass(),
              requested.startedAt(),
              clock.instant(),
              requested.actorId(),
              requested.correlationId());
      runs.put(runKey(failed.tenantId(), failed.runId()), failed);
      writeOutbox(RUN_FAILED_EVENT_TYPE, adapter, failed, command.actorId(), command.idempotencyKey(), Map.of("failureClass", exception.failureClass()));
      writeAudit(command.actorId(), adapter, adapter, "failed-sync");
      InvestorAdapterResponse response = response(failed, adapter, List.of(exception.failureClass()));
      idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
      return InvestorAdapterResult.success(response);
    }
  }

  public InvestorAdapterResult receiveCallback(InvestorCallbackCommand command) {
    if (command == null || !isUuid(command.tenantId()) || isBlank(command.adapterId()) || isBlank(command.actorId()) || isBlank(command.correlationId())) {
      return InvestorAdapterResult.failure(error("400", "VALIDATION_FAILED", "tenantId, adapterId, actorId, and correlationId are required", command == null ? "" : command.correlationId(), false));
    }
    if (!SERVICE_ACCOUNT_PERMISSION.equals(command.permission())) {
      return InvestorAdapterResult.failure(error("403", "TENANT_ACCESS_DENIED", "Service-account callback permission is required", command.correlationId(), false));
    }
    return triggerSync(new SyncInvestorFeedCommand(command.tenantId(), command.adapterId(), command.feedType(), command.cursor(), command.idempotencyKey(), command.actorId(), command.correlationId()));
  }

  public InvestorAdapterResult fetchRun(String tenantId, String adapterId, String runId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(adapterId) || isBlank(runId)) {
      return InvestorAdapterResult.failure(error("400", "VALIDATION_FAILED", "tenantId, adapterId, and runId are required", correlationId, false));
    }
    InvestorFeedImportRun run = runs.get(runKey(tenantId, runId));
    InvestorApiAdapter adapter = adapters.get(adapterKey(tenantId, adapterId));
    if (run == null || adapter == null || !run.adapterId().equals(adapterId)) {
      return InvestorAdapterResult.failure(error("404", "NOT_FOUND", "Investor feed run was not found", correlationId, false));
    }
    return InvestorAdapterResult.success(response(run, adapter, run.errorSummary().isBlank() ? List.of() : List.of(run.errorSummary())));
  }

  public List<InvestorApiAdapter> adaptersForTenant(String tenantId) {
    return adapters.values().stream().filter(adapter -> adapter.tenantId().equals(tenantId)).sorted(Comparator.comparing(InvestorApiAdapter::adapterId)).toList();
  }

  public List<InvestorFeedImportRun> runsForTenant(String tenantId) {
    return runs.values().stream().filter(run -> run.tenantId().equals(tenantId)).sorted(Comparator.comparing(InvestorFeedImportRun::startedAt)).toList();
  }

  public List<InvestorFeedRecord> stagingRecordsForRun(String tenantId, String runId) {
    return stagingRecords.stream().filter(record -> record.tenantId().equals(tenantId) && record.runId().equals(runId)).toList();
  }

  public List<InvestorFeedOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<InvestorFeedAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private InvestorFeedPayload fetchPayload(SyncInvestorFeedCommand command, InvestorApiAdapter adapter) {
    return switch (command.feedType()) {
      case "CATALOG" -> investorApiClient.fetchCatalog(adapter, command.cursor());
      case "PRICE_SHEET" -> investorApiClient.fetchPriceSheet(adapter, command.cursor());
      case "ELIGIBILITY" -> investorApiClient.fetchEligibility(adapter, command.cursor());
      default -> throw new InvestorApiException("UNSUPPORTED_FEED_TYPE", false);
    };
  }

  private InvestorFeedError validateConfigureCommand(ConfigureAdapterCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    if (!isUuid(command.tenantId()) || isBlank(command.adapterId()) || isBlank(command.investorId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, adapterId, and investorId are required", command.correlationId(), false);
    }
    if (isBlank(command.actorId())) {
      return error("401", "UNAUTHENTICATED", "actorId is required", command.correlationId(), false);
    }
    if (isBlank(command.idempotencyKey())) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key is required", command.correlationId(), false);
    }
    if (isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "correlationId is required", "", false);
    }
    if (isBlank(command.authCredentialRef()) || looksLikeRawSecret(command.authCredentialRef())) {
      return error("400", "VALIDATION_FAILED", "Credential reference is required and raw credentials are not accepted", command.correlationId(), false);
    }
    if (!isAllowedHttpsUrl(command.baseUrl())) {
      return error("422", "POLICY_NOT_SATISFIED", "Investor API base URL must use TLS and an egress-allowed host", command.correlationId(), false);
    }
    if (isBlank(command.schemaVersion()) || !normalizers.containsKey(strategyKey(command.investorId(), command.schemaVersion()))) {
      return error("422", "POLICY_NOT_SATISFIED", "Supported investor schema normalizer is required", command.correlationId(), false);
    }
    if (command.feedTypes() == null || command.feedTypes().isEmpty() || !SUPPORTED_FEED_TYPES.containsAll(new HashSet<>(command.feedTypes()))) {
      return error("400", "VALIDATION_FAILED", "feedTypes must include supported investor feed types", command.correlationId(), false);
    }
    if (command.rateLimitPerMinute() <= 0 || command.effectiveFrom() == null) {
      return error("400", "VALIDATION_FAILED", "rateLimit and effectiveFrom are required", command.correlationId(), false);
    }
    return null;
  }

  private InvestorFeedError validateSyncCommand(SyncInvestorFeedCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    if (!isUuid(command.tenantId()) || isBlank(command.adapterId()) || isBlank(command.feedType())) {
      return error("400", "VALIDATION_FAILED", "tenantId, adapterId, and feedType are required", command.correlationId(), false);
    }
    if (isBlank(command.actorId())) {
      return error("401", "UNAUTHENTICATED", "actorId is required", command.correlationId(), false);
    }
    if (isBlank(command.idempotencyKey())) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key is required", command.correlationId(), false);
    }
    if (isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "correlationId is required", "", false);
    }
    if (!SUPPORTED_FEED_TYPES.contains(command.feedType())) {
      return error("400", "VALIDATION_FAILED", "Unsupported investor feed type", command.correlationId(), false);
    }
    return null;
  }

  private InvestorAdapterResult replayOrConflict(String idempotencyKey, String requestHash, String correlationId) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return InvestorAdapterResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different investor adapter body", correlationId, false));
    }
    return InvestorAdapterResult.success(existing.response());
  }

  private InvestorAdapterResponse response(InvestorFeedImportRun run, InvestorApiAdapter adapter, List<String> validationMessages) {
    return new InvestorAdapterResponse(
        adapter.adapterId(),
        run.status().name(),
        adapter.version(),
        Map.of(
            "runId", run.runId(),
            "investorId", adapter.investorId(),
            "feedType", run.feedType(),
            "recordCount", String.valueOf(run.normalizedCount()),
            "normalizedChecksum", run.normalizedChecksum()),
        List.copyOf(validationMessages),
        deterministicId(run.tenantId(), run.runId(), "audit", "sync"),
        run.correlationId());
  }

  private void writeOutbox(String eventType, InvestorApiAdapter adapter, InvestorFeedImportRun run, String actorId, String idempotencyKey, Map<String, String> payload) {
    Map<String, String> safePayload = new HashMap<>(payload);
    safePayload.put("runId", run.runId());
    safePayload.put("adapterId", adapter.adapterId());
    safePayload.put("investorId", adapter.investorId());
    safePayload.put("status", run.status().name());
    outboxEvents.add(
        new InvestorFeedOutboxEvent(
            deterministicId(run.tenantId(), run.runId(), eventType, String.valueOf(outboxEvents.size() + 1)),
            eventType,
            1,
            run.tenantId(),
            run.tenantId() + ":" + adapter.investorId() + ":" + run.feedType(),
            actorId,
            run.correlationId(),
            run.correlationId(),
            idempotencyKey,
            hash(canonicalMap(safePayload)),
            run.completedAt() == null ? run.startedAt() : run.completedAt(),
            safePayload));
  }

  private void writeRecordOutbox(InvestorApiAdapter adapter, InvestorFeedImportRun run, InvestorFeedRecord record, String actorId, String idempotencyKey) {
    Map<String, String> payload =
        Map.of(
            "investorId", adapter.investorId(),
            "feedType", run.feedType(),
            "recordId", record.recordId(),
            "recordType", record.recordType(),
            "normalizedDataHash", hash(canonicalMap(record.normalizedJson())),
            "schemaVersion", adapter.schemaVersion(),
            "reasonCodes", String.join(",", record.reasonCodes()));
    outboxEvents.add(
        new InvestorFeedOutboxEvent(
            deterministicId(run.tenantId(), run.runId(), record.recordId(), String.valueOf(outboxEvents.size() + 1)),
            RECORD_NORMALIZED_EVENT_TYPE,
            1,
            run.tenantId(),
            run.tenantId() + ":" + adapter.investorId() + ":" + run.feedType(),
            actorId,
            run.correlationId(),
            run.correlationId(),
            idempotencyKey,
            hash(canonicalMap(payload)),
            run.completedAt() == null ? clock.instant() : run.completedAt(),
            payload));
  }

  private void writeAudit(String actorId, InvestorApiAdapter before, InvestorApiAdapter after, String actionSuffix) {
    auditRecords.add(
        new InvestorFeedAuditRecord(
            deterministicId(after.tenantId(), after.adapterId(), "audit", actionSuffix, String.valueOf(auditRecords.size() + 1)),
            after.tenantId(),
            after.adapterId(),
            actorId,
            AUDIT_ACTION,
            before == null ? "" : hash(before.status() + ":" + before.schemaVersion()),
            hash(after.status() + ":" + after.schemaVersion() + ":" + actionSuffix),
            after.correlationId(),
            hash(after.adapterId() + ":" + after.status() + ":" + after.schemaVersion() + ":" + actionSuffix),
            clock.instant()));
  }

  private boolean isAllowedHttpsUrl(String baseUrl) {
    try {
      URI uri = URI.create(baseUrl);
      return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && egressAllowedHosts.contains(uri.getHost().toLowerCase());
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private boolean looksLikeRawSecret(String value) {
    String lower = value.toLowerCase();
    return lower.contains("secret=") || lower.contains("password=") || lower.startsWith("basic ") || lower.startsWith("bearer ");
  }

  private String canonicalConfigureCommand(ConfigureAdapterCommand command) {
    return canonicalMap(
        Map.of(
            "tenantId", command.tenantId(),
            "adapterId", command.adapterId(),
            "investorId", command.investorId(),
            "baseUrlHash", urlHash(command.baseUrl()),
            "authCredentialRef", command.authCredentialRef(),
            "schemaVersion", command.schemaVersion(),
            "feedTypes", String.join(",", command.feedTypes()),
            "effectiveFrom", command.effectiveFrom().toString()));
  }

  private String checksum(String prefix, List<?> records) {
    return hash(prefix + ":" + records.stream().map(String::valueOf).sorted().reduce((left, right) -> left + ";" + right).orElse(""));
  }

  private String urlHash(String value) {
    return hash(value == null ? "" : value);
  }

  private String strategyKey(String investorId, String schemaVersion) {
    return investorId + ":" + schemaVersion;
  }

  private String adapterKey(String tenantId, String adapterId) {
    return tenantId + ":" + adapterId;
  }

  private String runKey(String tenantId, String runId) {
    return tenantId + ":" + runId;
  }

  private String deterministicId(String... parts) {
    return UUID.nameUUIDFromBytes(String.join(":", parts).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private boolean isUuid(String value) {
    try {
      UUID.fromString(value);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String canonicalMap(Map<String, String> map) {
    return map.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((left, right) -> left + ";" + right).orElse("");
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private InvestorFeedError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new InvestorFeedError(code, reason, message, List.of(), correlationId == null ? "" : correlationId, retryable);
  }

  private record IdempotencyEntry(String requestHash, InvestorAdapterResponse response) {}

  public enum AdapterStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ERROR,
    DECOMMISSIONED
  }

  public enum RunStatus {
    REQUESTED,
    FETCHING,
    VALIDATING,
    NORMALIZED,
    PUBLISHED,
    FAILED,
    DEAD_LETTERED
  }

  public interface InvestorApiClient {
    InvestorFeedPayload fetchCatalog(InvestorApiAdapter adapter, String cursor);

    InvestorFeedPayload fetchPriceSheet(InvestorApiAdapter adapter, String cursor);

    InvestorFeedPayload fetchEligibility(InvestorApiAdapter adapter, String cursor);

    void acknowledgeReceipt(InvestorApiAdapter adapter, InvestorFeedImportRun run);
  }

  public interface InvestorFeedNormalizer {
    List<InvestorFeedRecord> normalize(InvestorFeedImportRun run, List<Map<String, String>> records);
  }

  public interface InvestorHttpTransport {
    InvestorFeedPayload getJson(InvestorApiAdapter adapter, String feedType, String cursor);

    void postReceipt(InvestorApiAdapter adapter, InvestorFeedImportRun run);
  }

  public static final class GenericHttpJsonInvestorApiClient implements InvestorApiClient {
    private final InvestorHttpTransport transport;

    public GenericHttpJsonInvestorApiClient(InvestorHttpTransport transport) {
      this.transport = transport;
    }

    @Override
    public InvestorFeedPayload fetchCatalog(InvestorApiAdapter adapter, String cursor) {
      return transport.getJson(adapter, "CATALOG", cursor);
    }

    @Override
    public InvestorFeedPayload fetchPriceSheet(InvestorApiAdapter adapter, String cursor) {
      return transport.getJson(adapter, "PRICE_SHEET", cursor);
    }

    @Override
    public InvestorFeedPayload fetchEligibility(InvestorApiAdapter adapter, String cursor) {
      return transport.getJson(adapter, "ELIGIBILITY", cursor);
    }

    @Override
    public void acknowledgeReceipt(InvestorApiAdapter adapter, InvestorFeedImportRun run) {
      transport.postReceipt(adapter, run);
    }
  }

  public static final class FixtureInvestorApiClient implements InvestorApiClient {
    private final Map<String, InvestorFeedPayload> fixtures = new HashMap<>();
    private final List<String> acknowledgements = new ArrayList<>();

    public void addFixture(String feedType, InvestorFeedPayload payload) {
      fixtures.put(feedType, payload);
    }

    public List<String> acknowledgements() {
      return List.copyOf(acknowledgements);
    }

    @Override
    public InvestorFeedPayload fetchCatalog(InvestorApiAdapter adapter, String cursor) {
      return fixture("CATALOG");
    }

    @Override
    public InvestorFeedPayload fetchPriceSheet(InvestorApiAdapter adapter, String cursor) {
      return fixture("PRICE_SHEET");
    }

    @Override
    public InvestorFeedPayload fetchEligibility(InvestorApiAdapter adapter, String cursor) {
      return fixture("ELIGIBILITY");
    }

    @Override
    public void acknowledgeReceipt(InvestorApiAdapter adapter, InvestorFeedImportRun run) {
      acknowledgements.add(adapter.adapterId() + ":" + run.runId());
    }

    private InvestorFeedPayload fixture(String feedType) {
      InvestorFeedPayload payload = fixtures.get(feedType);
      if (payload == null) {
        throw new InvestorApiException("FIXTURE_NOT_FOUND", true);
      }
      return payload;
    }
  }

  public record ConfigureAdapterCommand(
      String tenantId,
      String adapterId,
      String investorId,
      String baseUrl,
      String authCredentialRef,
      String pollSchedule,
      List<String> feedTypes,
      String schemaVersion,
      int rateLimitPerMinute,
      boolean enabled,
      LocalDate effectiveFrom,
      String idempotencyKey,
      String actorId,
      String correlationId) {}

  public record SyncInvestorFeedCommand(String tenantId, String adapterId, String feedType, String cursor, String idempotencyKey, String actorId, String correlationId) {}

  public record InvestorCallbackCommand(String tenantId, String adapterId, String feedType, String cursor, String idempotencyKey, String actorId, String permission, String correlationId) {}

  public record InvestorFeedPayload(String schemaVersion, String feedType, List<Map<String, String>> records, String nextCursor, String checksumSource) {}

  public record InvestorApiAdapter(
      String tenantId,
      String adapterId,
      String investorId,
      String baseUrlHash,
      String credentialRef,
      AdapterStatus status,
      String schemaVersion,
      String pollSchedule,
      int rateLimitPerMinute,
      List<String> feedTypes,
      LocalDate effectiveFrom,
      int version,
      Instant createdAt,
      Instant updatedAt,
      String actorId,
      String correlationId) {}

  public record InvestorFeedImportRun(
      String tenantId,
      String runId,
      String adapterId,
      String feedType,
      RunStatus status,
      String cursor,
      String sourceChecksum,
      String normalizedChecksum,
      int sourceCount,
      int normalizedCount,
      String errorSummary,
      Instant startedAt,
      Instant completedAt,
      String actorId,
      String correlationId) {}

  public record InvestorFeedRecord(
      String tenantId,
      String runId,
      String recordId,
      String externalRecordId,
      String recordType,
      Map<String, String> normalizedJson,
      String validationStatus,
      List<String> reasonCodes) {}

  public record InvestorAdapterResponse(String id, String status, int version, Map<String, String> resultSummary, List<String> validationMessages, String auditRef, String correlationId) {}

  public record InvestorFeedError(String code, String reason, String message, List<String> fieldErrors, String correlationId, boolean retryable) {}

  public record InvestorAdapterResult(boolean valid, Optional<InvestorAdapterResponse> value, Optional<InvestorFeedError> error) {
    public static InvestorAdapterResult success(InvestorAdapterResponse value) {
      return new InvestorAdapterResult(true, Optional.of(value), Optional.empty());
    }

    public static InvestorAdapterResult failure(InvestorFeedError error) {
      return new InvestorAdapterResult(false, Optional.empty(), Optional.of(error));
    }
  }

  public record InvestorFeedOutboxEvent(
      String eventId,
      String eventType,
      int eventVersion,
      String tenantId,
      String eventKey,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String payloadHash,
      Instant occurredAt,
      Map<String, String> payload) {}

  public record InvestorFeedAuditRecord(
      String auditId,
      String tenantId,
      String adapterId,
      String actor,
      String action,
      String beforeHash,
      String afterHash,
      String correlationId,
      String replayHash,
      Instant occurredAt) {}

  public static final class InvestorApiException extends RuntimeException {
    private final String failureClass;
    private final boolean retryable;

    public InvestorApiException(String failureClass, boolean retryable) {
      super(failureClass);
      this.failureClass = failureClass;
      this.retryable = retryable;
    }

    public String failureClass() {
      return failureClass;
    }

    public boolean retryable() {
      return retryable;
    }
  }
}

package com.wcpe.integration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
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
import java.util.regex.Pattern;

public final class SftpFeedAdapterService {
  public static final String BASE_PATH = "/api/v1/tenants/{tenantId}/sftp-feed-adapters";
  public static final String WRITE_PERMISSION = "integrations.sftp-adapter.write";
  public static final String READ_PERMISSION = "integrations.sftp-adapter.read";
  public static final String FILE_DISCOVERED_EVENT_TYPE = "integration.sftp-file.discovered.v1";
  public static final String FILE_NORMALIZED_EVENT_TYPE = "integration.sftp-file.normalized.v1";
  public static final String FILE_FAILED_EVENT_TYPE = "integration.sftp-file.failed.v1";
  public static final String FILE_ARCHIVED_EVENT_TYPE = "integration.sftp-file.archived.v1";
  public static final String RECORD_NORMALIZED_EVENT_TYPE = "integration.investor-feed-record.normalized.v1";
  public static final String AUDIT_ACTION = "SFTP_FEED_ADAPTER_COMPLETED";

  private final Clock clock;
  private final SftpClient sftpClient;
  private final CredentialProvider credentialProvider;
  private final Map<String, SftpFeedAdapter> adapters = new HashMap<>();
  private final Map<String, SftpFeedFile> files = new HashMap<>();
  private final Map<String, SftpPollRun> runs = new HashMap<>();
  private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
  private final Map<String, FeedSchemaValidator> validators = new HashMap<>();
  private final Set<String> processedFileFingerprints = new HashSet<>();
  private final Set<String> activePollLocks = new HashSet<>();
  private final List<SftpFeedRecordStaging> stagingRecords = new ArrayList<>();
  private final List<SftpOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<SftpAuditRecord> auditRecords = new ArrayList<>();

  public SftpFeedAdapterService(SftpClient sftpClient, CredentialProvider credentialProvider) {
    this(Clock.systemUTC(), sftpClient, credentialProvider);
  }

  public SftpFeedAdapterService(Clock clock, SftpClient sftpClient, CredentialProvider credentialProvider) {
    this.clock = clock;
    this.sftpClient = sftpClient;
    this.credentialProvider = credentialProvider;
  }

  public void registerSchemaValidator(String feedType, String schemaVersion, FeedSchemaValidator validator) {
    validators.put(strategyKey(feedType, schemaVersion), validator);
  }

  public SftpAdapterResult configureAdapter(ConfigureSftpAdapterCommand command) {
    SftpFeedError validation = validateConfigureCommand(command);
    if (validation != null) {
      return SftpAdapterResult.failure(validation);
    }

    String requestHash = hash(canonicalConfigureCommand(command));
    String idempotencyKey = command.tenantId() + ":sftp-configure:" + command.idempotencyKey();
    SftpAdapterResult replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }

    Instant now = clock.instant();
    SftpFeedAdapter adapter =
        new SftpFeedAdapter(
            command.tenantId(),
            command.adapterId(),
            command.partnerId(),
            command.host(),
            command.port(),
            command.remotePath(),
            command.filePattern(),
            command.credentialRef(),
            command.knownHostFingerprint(),
            command.feedType(),
            command.schemaVersion(),
            command.archivePath(),
            command.pollSchedule(),
            command.enabled() ? AdapterStatus.ACTIVE : AdapterStatus.PAUSED,
            1,
            command.actorId(),
            command.correlationId(),
            now,
            now);
    adapters.put(adapterKey(adapter.tenantId(), adapter.adapterId()), adapter);
    writeAudit(command.actorId(), adapter, "configure");
    SftpAdapterResponse response =
        new SftpAdapterResponse(
            adapter.adapterId(),
            adapter.status().name(),
            adapter.version(),
            Map.of("feedType", adapter.feedType(), "schemaVersion", adapter.schemaVersion(), "partnerId", adapter.partnerId()),
            List.of(),
            deterministicId(adapter.tenantId(), adapter.adapterId(), "audit", "configure"),
            command.correlationId());
    idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
    return SftpAdapterResult.success(response);
  }

  public SftpAdapterResult pollAdapter(PollSftpAdapterCommand command) {
    SftpFeedError validation = validatePollCommand(command);
    if (validation != null) {
      return SftpAdapterResult.failure(validation);
    }
    String requestHash = hash(command.tenantId() + ":" + command.adapterId());
    String idempotencyKey = command.tenantId() + ":sftp-poll:" + command.idempotencyKey();
    SftpAdapterResult replay = replayOrConflict(idempotencyKey, requestHash, command.correlationId());
    if (replay != null) {
      return replay;
    }
    SftpFeedAdapter adapter = adapters.get(adapterKey(command.tenantId(), command.adapterId()));
    if (adapter == null) {
      return SftpAdapterResult.failure(error("404", "NOT_FOUND", "SFTP adapter was not found", command.correlationId(), false));
    }
    if (adapter.status() != AdapterStatus.ACTIVE) {
      return SftpAdapterResult.failure(error("422", "POLICY_NOT_SATISFIED", "SFTP adapter is not active", command.correlationId(), false));
    }
    FeedSchemaValidator validator = validators.get(strategyKey(adapter.feedType(), adapter.schemaVersion()));
    if (validator == null) {
      return SftpAdapterResult.failure(error("422", "POLICY_NOT_SATISFIED", "Feed schema validator is required", command.correlationId(), false));
    }

    String lockKey = adapterKey(adapter.tenantId(), adapter.adapterId());
    if (!activePollLocks.add(lockKey)) {
      return SftpAdapterResult.failure(error("409", "VERSION_CONFLICT", "SFTP adapter poll is already running", command.correlationId(), true));
    }

    String runId = deterministicId(command.tenantId(), command.adapterId(), command.idempotencyKey());
    SftpPollRun requested =
        new SftpPollRun(command.tenantId(), runId, command.adapterId(), RunStatus.REQUESTED, 0, 0, 0, "", clock.instant(), null, command.actorId(), command.correlationId());
    runs.put(runKey(requested.tenantId(), requested.runId()), requested);

    try {
      List<RemoteSftpFile> discovered = sftpClient.listFiles(adapter);
      int discoveredCount = 0;
      int normalizedCount = 0;
      int archivedCount = 0;
      List<String> validationMessages = new ArrayList<>();
      for (RemoteSftpFile remoteFile : discovered) {
        if (!isEligibleRemoteFile(adapter, remoteFile)) {
          continue;
        }
        discoveredCount++;
        writeFileEvent(FILE_DISCOVERED_EVENT_TYPE, adapter, requested, remoteFile, command.actorId(), command.idempotencyKey(), Map.of("fileName", remoteFile.fileName()));
        RemoteSftpFileContent content = sftpClient.download(adapter, remoteFile);
        String computedChecksum = hash(content.bytes());
        if (!isBlank(content.sidecarChecksum()) && !computedChecksum.equalsIgnoreCase(content.sidecarChecksum())) {
          validationMessages.add(remoteFile.fileName() + ":CHECKSUM_MISMATCH");
          writeFailedFile(adapter, requested, remoteFile, computedChecksum, "CHECKSUM_MISMATCH", command);
          continue;
        }
        String duplicateKey = adapter.tenantId() + ":" + adapter.adapterId() + ":" + computedChecksum;
        if (!processedFileFingerprints.add(duplicateKey)) {
          validationMessages.add(remoteFile.fileName() + ":DUPLICATE_FILE");
          continue;
        }

        ParseResult parsed = parseCsv(content.bytes());
        if (!parsed.validationMessages().isEmpty()) {
          validationMessages.addAll(prefix(remoteFile.fileName(), parsed.validationMessages()));
          writeFailedFile(adapter, requested, remoteFile, computedChecksum, String.join(",", parsed.validationMessages()), command);
          continue;
        }
        List<String> schemaMessages = validator.validate(remoteFile, parsed.rows());
        if (!schemaMessages.isEmpty()) {
          validationMessages.addAll(prefix(remoteFile.fileName(), schemaMessages));
          writeFailedFile(adapter, requested, remoteFile, computedChecksum, String.join(",", schemaMessages), command);
          continue;
        }

        String fileId = deterministicId(adapter.tenantId(), adapter.adapterId(), remoteFile.remotePath(), computedChecksum);
        SftpFeedFile normalizedFile =
            new SftpFeedFile(
                adapter.tenantId(),
                fileId,
                adapter.adapterId(),
                hash(remoteFile.remotePath()),
                remoteFile.fileName(),
                content.bytes().length,
                computedChecksum,
                FileStatus.NORMALIZED,
                requested.runId(),
                clock.instant(),
                clock.instant(),
                null,
                "");
        files.put(fileKey(normalizedFile.tenantId(), normalizedFile.fileId()), normalizedFile);
        for (int index = 0; index < parsed.rows().size(); index++) {
          Map<String, String> row = parsed.rows().get(index);
          String recordId = deterministicId(adapter.tenantId(), requested.runId(), remoteFile.fileName(), String.valueOf(index + 1));
          String externalRecordId = row.getOrDefault("externalRecordId", remoteFile.fileName() + ":" + (index + 1));
          SftpFeedRecordStaging record = new SftpFeedRecordStaging(adapter.tenantId(), fileId, requested.runId(), index + 1, externalRecordId, safeNormalizedRow(row), "VALID", List.of());
          stagingRecords.add(record);
          writeRecordEvent(adapter, requested, normalizedFile, record, command.actorId(), command.idempotencyKey());
        }
        normalizedCount += parsed.rows().size();
        writeFileEvent(FILE_NORMALIZED_EVENT_TYPE, adapter, requested, remoteFile, command.actorId(), command.idempotencyKey(), Map.of("fileId", fileId, "recordCount", String.valueOf(parsed.rows().size())));
        sftpClient.archive(adapter, remoteFile, adapter.archivePath());
        SftpFeedFile archivedFile = normalizedFile.withStatus(FileStatus.ARCHIVED, clock.instant(), "");
        files.put(fileKey(archivedFile.tenantId(), archivedFile.fileId()), archivedFile);
        archivedCount++;
        writeFileEvent(FILE_ARCHIVED_EVENT_TYPE, adapter, requested, remoteFile, command.actorId(), command.idempotencyKey(), Map.of("fileId", fileId, "archivePathHash", hash(adapter.archivePath())));
      }

      RunStatus status = validationMessages.isEmpty() ? RunStatus.ARCHIVED : RunStatus.FAILED;
      SftpPollRun completed =
          new SftpPollRun(
              requested.tenantId(),
              requested.runId(),
              requested.adapterId(),
              status,
              discoveredCount,
              normalizedCount,
              archivedCount,
              String.join(",", validationMessages),
              requested.startedAt(),
              clock.instant(),
              requested.actorId(),
              requested.correlationId());
      runs.put(runKey(completed.tenantId(), completed.runId()), completed);
      writeAudit(command.actorId(), adapter, status == RunStatus.ARCHIVED ? "poll-archived" : "poll-failed");
      SftpAdapterResponse response = response(completed, adapter, validationMessages);
      idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestHash, response));
      return SftpAdapterResult.success(response);
    } finally {
      activePollLocks.remove(lockKey);
    }
  }

  public SftpAdapterResult fetchRun(String tenantId, String adapterId, String runId, String correlationId) {
    if (!isUuid(tenantId) || isBlank(adapterId) || isBlank(runId)) {
      return SftpAdapterResult.failure(error("400", "VALIDATION_FAILED", "tenantId, adapterId, and runId are required", correlationId, false));
    }
    SftpFeedAdapter adapter = adapters.get(adapterKey(tenantId, adapterId));
    SftpPollRun run = runs.get(runKey(tenantId, runId));
    if (adapter == null || run == null || !run.adapterId().equals(adapterId)) {
      return SftpAdapterResult.failure(error("404", "NOT_FOUND", "SFTP poll run was not found", correlationId, false));
    }
    return SftpAdapterResult.success(response(run, adapter, run.errorSummary().isBlank() ? List.of() : List.of(run.errorSummary())));
  }

  public List<SftpFeedAdapter> adaptersForTenant(String tenantId) {
    return adapters.values().stream().filter(adapter -> adapter.tenantId().equals(tenantId)).sorted(Comparator.comparing(SftpFeedAdapter::adapterId)).toList();
  }

  public List<SftpFeedFile> filesForAdapter(String tenantId, String adapterId) {
    return files.values().stream().filter(file -> file.tenantId().equals(tenantId) && file.adapterId().equals(adapterId)).sorted(Comparator.comparing(SftpFeedFile::fileName)).toList();
  }

  public List<SftpPollRun> runsForTenant(String tenantId) {
    return runs.values().stream().filter(run -> run.tenantId().equals(tenantId)).sorted(Comparator.comparing(SftpPollRun::startedAt)).toList();
  }

  public List<SftpFeedRecordStaging> stagingRecordsForRun(String tenantId, String runId) {
    return stagingRecords.stream().filter(record -> record.tenantId().equals(tenantId) && record.runId().equals(runId)).toList();
  }

  public List<SftpOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  public List<SftpAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  private SftpFeedError validateConfigureCommand(ConfigureSftpAdapterCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    if (!isUuid(command.tenantId()) || isBlank(command.adapterId()) || isBlank(command.partnerId())) {
      return error("400", "VALIDATION_FAILED", "tenantId, adapterId, and partnerId are required", command.correlationId(), false);
    }
    if (isBlank(command.actorId())) {
      return error("401", "UNAUTHENTICATED", "actorId is required", command.correlationId(), false);
    }
    if (isBlank(command.idempotencyKey()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key and correlationId are required", command.correlationId(), false);
    }
    if (isBlank(command.credentialRef()) || looksLikeRawSecret(command.credentialRef()) || !credentialProvider.exists(command.tenantId(), command.credentialRef())) {
      return error("400", "VALIDATION_FAILED", "Tenant-scoped credentialRef is required and raw credentials are not accepted", command.correlationId(), false);
    }
    if (isBlank(command.host()) || command.port() <= 0 || command.port() > 65535) {
      return error("400", "VALIDATION_FAILED", "host and port are required", command.correlationId(), false);
    }
    if (isBlank(command.knownHostFingerprint()) || !command.knownHostFingerprint().equals(sftpClient.knownHostFingerprint(command.host(), command.port()))) {
      return error("422", "POLICY_NOT_SATISFIED", "Known-host fingerprint must match the tenant SFTP endpoint", command.correlationId(), false);
    }
    if (!isSafePath(command.remotePath()) || !isSafePath(command.archivePath()) || !isSafePattern(command.filePattern())) {
      return error("400", "VALIDATION_FAILED", "remotePath, archivePath, and filePattern must be safe", command.correlationId(), false);
    }
    if (isBlank(command.feedType()) || isBlank(command.schemaVersion()) || !validators.containsKey(strategyKey(command.feedType(), command.schemaVersion()))) {
      return error("422", "POLICY_NOT_SATISFIED", "Supported feed schema validator is required", command.correlationId(), false);
    }
    if (isBlank(command.pollSchedule())) {
      return error("400", "VALIDATION_FAILED", "pollSchedule is required", command.correlationId(), false);
    }
    return null;
  }

  private SftpFeedError validatePollCommand(PollSftpAdapterCommand command) {
    if (command == null) {
      return error("400", "VALIDATION_FAILED", "command is required", "", false);
    }
    if (!isUuid(command.tenantId()) || isBlank(command.adapterId())) {
      return error("400", "VALIDATION_FAILED", "tenantId and adapterId are required", command.correlationId(), false);
    }
    if (isBlank(command.actorId())) {
      return error("401", "UNAUTHENTICATED", "actorId is required", command.correlationId(), false);
    }
    if (isBlank(command.idempotencyKey()) || isBlank(command.correlationId())) {
      return error("400", "VALIDATION_FAILED", "Idempotency-Key and correlationId are required", command.correlationId(), false);
    }
    return null;
  }

  private void writeFailedFile(SftpFeedAdapter adapter, SftpPollRun run, RemoteSftpFile remoteFile, String checksum, String errorSummary, PollSftpAdapterCommand command) {
    String fileId = deterministicId(adapter.tenantId(), adapter.adapterId(), remoteFile.remotePath(), checksum);
    files.put(
        fileKey(adapter.tenantId(), fileId),
        new SftpFeedFile(
            adapter.tenantId(),
            fileId,
            adapter.adapterId(),
            hash(remoteFile.remotePath()),
            remoteFile.fileName(),
            remoteFile.sizeBytes(),
            checksum,
            FileStatus.FAILED,
            run.runId(),
            clock.instant(),
            null,
            clock.instant(),
            errorSummary));
    writeFileEvent(FILE_FAILED_EVENT_TYPE, adapter, run, remoteFile, command.actorId(), command.idempotencyKey(), Map.of("fileId", fileId, "failureClass", errorSummary));
  }

  private boolean isEligibleRemoteFile(SftpFeedAdapter adapter, RemoteSftpFile remoteFile) {
    return remoteFile != null
        && !remoteFile.directory()
        && !remoteFile.hidden()
        && isSafePath(remoteFile.remotePath())
        && globMatches(adapter.filePattern(), remoteFile.fileName());
  }

  private boolean globMatches(String pattern, String fileName) {
    String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q");
    return Pattern.compile("^" + regex + "$").matcher(fileName).matches();
  }

  private ParseResult parseCsv(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    String[] lines = text.strip().split("\\R");
    if (lines.length < 2) {
      return new ParseResult(List.of(), List.of("CSV_HEADER_AND_RECORD_REQUIRED"));
    }
    String[] header = splitCsvLine(lines[0]);
    if (header.length == 0 || Set.of(header).contains("")) {
      return new ParseResult(List.of(), List.of("CSV_HEADER_REQUIRED"));
    }
    List<Map<String, String>> rows = new ArrayList<>();
    List<String> validation = new ArrayList<>();
    for (int line = 1; line < lines.length; line++) {
      if (lines[line].isBlank()) {
        continue;
      }
      String[] values = splitCsvLine(lines[line]);
      if (values.length != header.length) {
        validation.add("CSV_COLUMN_COUNT_MISMATCH_ROW_" + (line + 1));
        continue;
      }
      Map<String, String> row = new HashMap<>();
      for (int column = 0; column < header.length; column++) {
        row.put(header[column], values[column]);
      }
      rows.add(row);
    }
    return new ParseResult(rows, validation);
  }

  private String[] splitCsvLine(String line) {
    return line.split(",", -1);
  }

  private Map<String, String> safeNormalizedRow(Map<String, String> row) {
    Map<String, String> normalized = new HashMap<>();
    for (Map.Entry<String, String> entry : row.entrySet()) {
      String key = entry.getKey();
      String lowerKey = key.toLowerCase();
      if (lowerKey.startsWith("raw") || lowerKey.contains("secret") || lowerKey.contains("password") || lowerKey.contains("token")) {
        continue;
      }
      normalized.put(key, entry.getValue());
    }
    return Map.copyOf(normalized);
  }

  private List<String> prefix(String fileName, List<String> messages) {
    return messages.stream().map(message -> fileName + ":" + message).toList();
  }

  private SftpAdapterResult replayOrConflict(String idempotencyKey, String requestHash, String correlationId) {
    IdempotencyEntry existing = idempotencyEntries.get(idempotencyKey);
    if (existing == null) {
      return null;
    }
    if (!existing.requestHash().equals(requestHash)) {
      return SftpAdapterResult.failure(error("409", "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different SFTP adapter body", correlationId, false));
    }
    return SftpAdapterResult.success(existing.response());
  }

  private SftpAdapterResponse response(SftpPollRun run, SftpFeedAdapter adapter, List<String> validationMessages) {
    return new SftpAdapterResponse(
        adapter.adapterId(),
        run.status().name(),
        adapter.version(),
        Map.of(
            "runId", run.runId(),
            "feedType", adapter.feedType(),
            "discoveredCount", String.valueOf(run.discoveredCount()),
            "normalizedCount", String.valueOf(run.normalizedCount()),
            "archivedCount", String.valueOf(run.archivedCount())),
        List.copyOf(validationMessages),
        deterministicId(run.tenantId(), run.runId(), "audit", "sftp"),
        run.correlationId());
  }

  private void writeFileEvent(String eventType, SftpFeedAdapter adapter, SftpPollRun run, RemoteSftpFile remoteFile, String actorId, String idempotencyKey, Map<String, String> payload) {
    Map<String, String> safePayload = new HashMap<>(payload);
    safePayload.put("adapterId", adapter.adapterId());
    safePayload.put("partnerId", adapter.partnerId());
    safePayload.put("runId", run.runId());
    safePayload.put("remotePathHash", hash(remoteFile.remotePath()));
    outboxEvents.add(
        new SftpOutboxEvent(
            deterministicId(adapter.tenantId(), run.runId(), eventType, String.valueOf(outboxEvents.size() + 1)),
            eventType,
            1,
            adapter.tenantId(),
            adapter.tenantId() + ":" + adapter.adapterId() + ":" + remoteFile.fileName(),
            actorId,
            run.correlationId(),
            run.correlationId(),
            idempotencyKey,
            hash(canonicalMap(safePayload)),
            clock.instant(),
            safePayload));
  }

  private void writeRecordEvent(SftpFeedAdapter adapter, SftpPollRun run, SftpFeedFile file, SftpFeedRecordStaging record, String actorId, String idempotencyKey) {
    Map<String, String> payload =
        Map.of(
            "adapterId", adapter.adapterId(),
            "fileId", file.fileId(),
            "runId", run.runId(),
            "rowNumber", String.valueOf(record.rowNumber()),
            "externalRecordId", record.externalRecordId(),
            "normalizedDataHash", hash(canonicalMap(record.normalizedJson())));
    outboxEvents.add(
        new SftpOutboxEvent(
            deterministicId(adapter.tenantId(), run.runId(), file.fileId(), String.valueOf(record.rowNumber())),
            RECORD_NORMALIZED_EVENT_TYPE,
            1,
            adapter.tenantId(),
            adapter.tenantId() + ":" + file.fileId(),
            actorId,
            run.correlationId(),
            run.correlationId(),
            idempotencyKey,
            hash(canonicalMap(payload)),
            clock.instant(),
            payload));
  }

  private void writeAudit(String actorId, SftpFeedAdapter adapter, String actionSuffix) {
    auditRecords.add(
        new SftpAuditRecord(
            deterministicId(adapter.tenantId(), adapter.adapterId(), "audit", actionSuffix, String.valueOf(auditRecords.size() + 1)),
            adapter.tenantId(),
            adapter.adapterId(),
            actorId,
            AUDIT_ACTION,
            hash(adapter.status() + ":" + adapter.schemaVersion() + ":" + actionSuffix),
            adapter.correlationId(),
            hash(adapter.adapterId() + ":" + adapter.feedType() + ":" + actionSuffix),
            clock.instant()));
  }

  private boolean isSafePath(String value) {
    return !isBlank(value) && !value.contains("..") && !value.contains("\\") && value.startsWith("/") && !value.contains("/.");
  }

  private boolean isSafePattern(String value) {
    return !isBlank(value) && !value.contains("..") && !value.contains("/") && !value.contains("\\") && !value.startsWith(".");
  }

  private boolean looksLikeRawSecret(String value) {
    String lower = value.toLowerCase();
    return lower.contains("secret=") || lower.contains("password=") || lower.startsWith("basic ") || lower.startsWith("bearer ") || lower.contains("-----begin");
  }

  private String canonicalConfigureCommand(ConfigureSftpAdapterCommand command) {
    Map<String, String> fields = new HashMap<>();
    fields.put("tenantId", command.tenantId());
    fields.put("adapterId", command.adapterId());
    fields.put("partnerId", command.partnerId());
    fields.put("host", command.host());
    fields.put("port", String.valueOf(command.port()));
    fields.put("remotePath", command.remotePath());
    fields.put("filePattern", command.filePattern());
    fields.put("credentialRef", command.credentialRef());
    fields.put("knownHostFingerprint", command.knownHostFingerprint());
    fields.put("feedType", command.feedType());
    fields.put("schemaVersion", command.schemaVersion());
    fields.put("archivePath", command.archivePath());
    return canonicalMap(fields);
  }

  private String strategyKey(String feedType, String schemaVersion) {
    return feedType + ":" + schemaVersion;
  }

  private String adapterKey(String tenantId, String adapterId) {
    return tenantId + ":" + adapterId;
  }

  private String runKey(String tenantId, String runId) {
    return tenantId + ":" + runId;
  }

  private String fileKey(String tenantId, String fileId) {
    return tenantId + ":" + fileId;
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

  private String hash(byte[] value) {
    return hash(new String(value, StandardCharsets.UTF_8));
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  private SftpFeedError error(String code, String reason, String message, String correlationId, boolean retryable) {
    return new SftpFeedError(code, reason, message, List.of(), correlationId == null ? "" : correlationId, retryable);
  }

  private record IdempotencyEntry(String requestHash, SftpAdapterResponse response) {}

  private record ParseResult(List<Map<String, String>> rows, List<String> validationMessages) {}

  public enum AdapterStatus {
    ACTIVE,
    PAUSED,
    ERROR,
    DECOMMISSIONED
  }

  public enum FileStatus {
    DISCOVERED,
    DOWNLOADING,
    VALIDATING,
    NORMALIZED,
    ARCHIVED,
    FAILED,
    DEAD_LETTERED
  }

  public enum RunStatus {
    REQUESTED,
    NORMALIZED,
    ARCHIVED,
    FAILED,
    DEAD_LETTERED
  }

  public interface CredentialProvider {
    boolean exists(String tenantId, String credentialRef);
  }

  public interface FeedSchemaValidator {
    List<String> validate(RemoteSftpFile file, List<Map<String, String>> rows);
  }

  public interface SftpClient {
    String knownHostFingerprint(String host, int port);

    List<RemoteSftpFile> listFiles(SftpFeedAdapter adapter);

    RemoteSftpFileContent download(SftpFeedAdapter adapter, RemoteSftpFile file);

    void archive(SftpFeedAdapter adapter, RemoteSftpFile file, String archivePath);
  }

  public static final class FixtureSftpClient implements SftpClient {
    private final Map<String, String> fingerprints = new HashMap<>();
    private final List<RemoteSftpFile> files = new ArrayList<>();
    private final Map<String, RemoteSftpFileContent> contents = new HashMap<>();
    private final List<String> archives = new ArrayList<>();

    public void addKnownHost(String host, int port, String fingerprint) {
      fingerprints.put(host + ":" + port, fingerprint);
    }

    public void addFile(RemoteSftpFile file, RemoteSftpFileContent content) {
      files.add(file);
      contents.put(file.remotePath(), content);
    }

    public List<String> archives() {
      return List.copyOf(archives);
    }

    @Override
    public String knownHostFingerprint(String host, int port) {
      return fingerprints.getOrDefault(host + ":" + port, "");
    }

    @Override
    public List<RemoteSftpFile> listFiles(SftpFeedAdapter adapter) {
      return files.stream().filter(file -> file.remotePath().startsWith(adapter.remotePath())).toList();
    }

    @Override
    public RemoteSftpFileContent download(SftpFeedAdapter adapter, RemoteSftpFile file) {
      RemoteSftpFileContent content = contents.get(file.remotePath());
      if (content == null) {
        throw new IllegalStateException("Fixture content missing for " + file.fileName());
      }
      return content;
    }

    @Override
    public void archive(SftpFeedAdapter adapter, RemoteSftpFile file, String archivePath) {
      archives.add(file.remotePath() + "->" + archivePath + "/" + file.fileName());
    }
  }

  public record ConfigureSftpAdapterCommand(
      String tenantId,
      String adapterId,
      String partnerId,
      String host,
      int port,
      String remotePath,
      String filePattern,
      String credentialRef,
      String knownHostFingerprint,
      String feedType,
      String schemaVersion,
      String archivePath,
      String pollSchedule,
      boolean enabled,
      String idempotencyKey,
      String actorId,
      String correlationId) {}

  public record PollSftpAdapterCommand(String tenantId, String adapterId, String idempotencyKey, String actorId, String correlationId) {}

  public record SftpFeedAdapter(
      String tenantId,
      String adapterId,
      String partnerId,
      String host,
      int port,
      String remotePath,
      String filePattern,
      String credentialRef,
      String knownHostFingerprint,
      String feedType,
      String schemaVersion,
      String archivePath,
      String pollSchedule,
      AdapterStatus status,
      int version,
      String actorId,
      String correlationId,
      Instant createdAt,
      Instant updatedAt) {}

  public record RemoteSftpFile(String remotePath, String fileName, long sizeBytes, boolean directory, boolean hidden, Instant modifiedAt) {}

  public record RemoteSftpFileContent(byte[] bytes, String sidecarChecksum) {}

  public record SftpFeedFile(
      String tenantId,
      String fileId,
      String adapterId,
      String remotePathHash,
      String fileName,
      long sizeBytes,
      String checksum,
      FileStatus status,
      String runId,
      Instant discoveredAt,
      Instant processedAt,
      Instant failedAt,
      String errorSummary) {
    SftpFeedFile withStatus(FileStatus nextStatus, Instant processedAt, String errorSummary) {
      return new SftpFeedFile(tenantId, fileId, adapterId, remotePathHash, fileName, sizeBytes, checksum, nextStatus, runId, discoveredAt, processedAt, failedAt, errorSummary);
    }
  }

  public record SftpPollRun(
      String tenantId,
      String runId,
      String adapterId,
      RunStatus status,
      int discoveredCount,
      int normalizedCount,
      int archivedCount,
      String errorSummary,
      Instant startedAt,
      Instant completedAt,
      String actorId,
      String correlationId) {}

  public record SftpFeedRecordStaging(String tenantId, String fileId, String runId, int rowNumber, String externalRecordId, Map<String, String> normalizedJson, String validationStatus, List<String> reasonCodes) {}

  public record SftpAdapterResponse(String id, String status, int version, Map<String, String> resultSummary, List<String> validationMessages, String auditRef, String correlationId) {}

  public record SftpFeedError(String code, String reason, String message, List<String> fieldErrors, String correlationId, boolean retryable) {}

  public record SftpAdapterResult(boolean valid, Optional<SftpAdapterResponse> value, Optional<SftpFeedError> error) {
    public static SftpAdapterResult success(SftpAdapterResponse value) {
      return new SftpAdapterResult(true, Optional.of(value), Optional.empty());
    }

    public static SftpAdapterResult failure(SftpFeedError error) {
      return new SftpAdapterResult(false, Optional.empty(), Optional.of(error));
    }
  }

  public record SftpOutboxEvent(
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

  public record SftpAuditRecord(String auditId, String tenantId, String adapterId, String actor, String action, String afterHash, String correlationId, String replayHash, Instant occurredAt) {}
}

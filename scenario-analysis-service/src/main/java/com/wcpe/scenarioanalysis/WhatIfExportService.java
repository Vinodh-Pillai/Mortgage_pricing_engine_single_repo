package com.wcpe.scenarioanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class WhatIfExportService {
  private static final Set<String> SOURCE_TYPES = Set.of("current-comparison", "selected-rows", "full-grid", "saved-analysis");
  private static final Set<String> FORMATS = Set.of("CSV", "JSON");
  private static final Set<String> RECIPIENT_TYPES = Set.of("internal", "borrower-facing draft");
  private static final Duration LINK_TTL = Duration.ofDays(7);
  static final String NON_BINDING_DISCLAIMER = "Non-binding what-if analysis for scenario comparison only; not a mortgage pricing commitment.";

  private final ExportRepository repository;
  private final ExportStorage storage;
  private final Clock clock;

  public WhatIfExportService() {
    throw FailClosedPersistence.notConfigured("what-if export store");
  }

  WhatIfExportService(ExportRepository repository, ExportStorage storage, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.storage = Objects.requireNonNull(storage, "storage is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public ExportResponse createExport(CreateExportCommand command) {
    CreateExportCommand valid = validate(command);
    String idempotencyKeyHash = sha256Hex(valid.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(valid));
    Optional<StoredExport> existing = repository.findByIdempotencyKeyHash(valid.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredExport stored = existing.get();
      if (!stored.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different export request");
      }
      return stored.response();
    }

    UUID exportId = UUID.randomUUID();
    Instant now = Instant.now(clock);
    byte[] content = render(valid, exportId, now);
    String contentSha256 = "sha256:" + sha256Hex(content);
    String storageUri = storage.store(valid.tenantId(), exportId, content, valid.format());
    Instant expiresAt = now.plus(LINK_TTL);
    String auditRef = auditRef("WHAT_IF_EXPORT_GENERATED", exportId, now);
    ExportEvent requestedEvent = event("whatif.export.requested.v1", valid, exportId, contentSha256, now);
    ExportEvent generatedEvent = event("whatif.export.generated.v1", valid, exportId, contentSha256, now);
    ExportResponse response = new ExportResponse(
        exportId,
        valid.sourceType(),
        valid.sourceId(),
        valid.format(),
        valid.recipientType(),
        valid.fileName(),
        "READY",
        storageUri,
        contentSha256,
        rowCount(valid),
        expiresAt,
        null,
        auditRef,
        "replay:what-if-export:" + contentSha256.substring("sha256:".length()),
        List.of(NON_BINDING_DISCLAIMER),
        List.of(requestedEvent.eventType(), generatedEvent.eventType()),
        valid.correlationId());
    repository.save(new StoredExport(
        valid.tenantId(),
        exportId,
        requestHash,
        idempotencyKeyHash,
        valid.actorId(),
        response,
        content,
        List.of(requestedEvent, generatedEvent),
        now));
    return response;
  }

  public ExportResponse getExport(String tenantId, UUID exportId) {
    return findExport(tenantId, exportId).response();
  }

  public ExportDownload download(String tenantId, UUID exportId) {
    StoredExport stored = findExport(tenantId, exportId);
    ExportResponse response = stored.response();
    if (response.revokedAt() != null) {
      throw new ExportRevokedException("export link was revoked");
    }
    if (!Instant.now(clock).isBefore(response.expiresAt())) {
      throw new ExportExpiredException("export link has expired");
    }
    byte[] content = storage.read(response.storageUri())
        .orElseThrow(() -> new DependencyUnavailableException("export content is unavailable"));
    return new ExportDownload(response.exportId(), response.fileName(), contentType(response.format()), content);
  }

  public ExportResponse revoke(String tenantId, UUID exportId, String actorId, String correlationId) {
    StoredExport stored = findExport(tenantId, exportId);
    ExportResponse current = stored.response();
    if (current.revokedAt() != null) {
      return current;
    }
    Instant now = Instant.now(clock);
    ExportResponse revoked = new ExportResponse(
        current.exportId(),
        current.sourceType(),
        current.sourceId(),
        current.format(),
        current.recipientType(),
        current.fileName(),
        "REVOKED",
        current.storageUri(),
        current.contentSha256(),
        current.rowCount(),
        current.expiresAt(),
        now,
        auditRef("WHAT_IF_EXPORT_REVOKED", exportId, now),
        current.replayRef(),
        current.validationMessages(),
        append(current.eventTypes(), "whatif.export.revoked.v1"),
        defaultText(correlationId, current.correlationId()));
    repository.update(stored, revoked, event("whatif.export.revoked.v1", tenantId, exportId, defaultText(actorId, stored.createdBy()), revoked.correlationId(), null, null, current.contentSha256(), now));
    return revoked;
  }

  private CreateExportCommand validate(CreateExportCommand command) {
    if (command == null) {
      throw new ValidationException("export request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceType = requireText(command.sourceType(), "sourceType is required").toLowerCase();
    if (!SOURCE_TYPES.contains(sourceType)) {
      throw new ValidationException("sourceType must be current-comparison, selected-rows, full-grid, or saved-analysis");
    }
    String sourceId = requireText(command.sourceId(), "sourceId is required");
    String format = requireText(command.format(), "format is required").toUpperCase();
    if (!FORMATS.contains(format)) {
      throw new PolicyNotSatisfiedException("PDF export requires an approved document template service; CSV and JSON are supported locally");
    }
    String recipientType = requireText(command.recipientType(), "recipientType is required").toLowerCase();
    if (!RECIPIENT_TYPES.contains(recipientType)) {
      throw new ValidationException("recipientType must be internal or borrower-facing draft");
    }
    if ("borrower-facing draft".equals(recipientType)) {
      throw new PolicyNotSatisfiedException("borrower-facing export requires active compliance template configuration");
    }
    List<String> rowIds = collapseText(command.rowIds());
    if ("selected-rows".equals(sourceType) && rowIds.isEmpty()) {
      throw new ValidationException("rowIds are required for selected-rows export scope");
    }
    String fileName = safeFileName(defaultText(command.fileName(), sourceType + '-' + sourceId + '.' + format.toLowerCase()));
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    return new CreateExportCommand(
        tenantId,
        sourceType,
        sourceId,
        rowIds,
        format,
        recipientType,
        command.includeLedger(),
        command.includeIneligible(),
        fileName,
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private StoredExport findExport(String tenantId, UUID exportId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (exportId == null) {
      throw new ValidationException("exportId is required");
    }
    return repository.findByExportId(normalizedTenantId, exportId)
        .orElseThrow(() -> new NotFoundException("what-if export was not found"));
  }

  private byte[] render(CreateExportCommand command, UUID exportId, Instant generatedAt) {
    String content = "CSV".equals(command.format()) ? renderCsv(command, exportId, generatedAt) : renderJson(command, exportId, generatedAt);
    return content.getBytes(StandardCharsets.UTF_8);
  }

  private String renderCsv(CreateExportCommand command, UUID exportId, Instant generatedAt) {
    List<String> rows = command.rowIds().isEmpty() ? List.of("source:" + command.sourceId()) : command.rowIds();
    StringBuilder csv = new StringBuilder();
    csv.append("export_id,source_type,source_id,row_id,format,recipient_type,include_ledger,include_ineligible,generated_at,disclaimer\r\n");
    for (String rowId : rows) {
      csv.append(csvCell(exportId.toString())).append(',')
          .append(csvCell(command.sourceType())).append(',')
          .append(csvCell(command.sourceId())).append(',')
          .append(csvCell(rowId)).append(',')
          .append(csvCell(command.format())).append(',')
          .append(csvCell(command.recipientType())).append(',')
          .append(command.includeLedger()).append(',')
          .append(command.includeIneligible()).append(',')
          .append(csvCell(generatedAt.toString())).append(',')
          .append(csvCell(NON_BINDING_DISCLAIMER)).append("\r\n");
    }
    return csv.toString();
  }

  private String renderJson(CreateExportCommand command, UUID exportId, Instant generatedAt) {
    String rowIds = command.rowIds().stream()
        .map(value -> "\"" + jsonEscape(value) + "\"")
        .reduce((left, right) -> left + "," + right)
        .orElse("");
    return "{"
        + "\"manifest\":{"
        + "\"schemaVersion\":\"what-if-export-v1\","
        + "\"exportId\":\"" + exportId + "\","
        + "\"sourceType\":\"" + jsonEscape(command.sourceType()) + "\","
        + "\"sourceId\":\"" + jsonEscape(command.sourceId()) + "\","
        + "\"format\":\"JSON\","
        + "\"recipientType\":\"" + jsonEscape(command.recipientType()) + "\","
        + "\"generatedAt\":\"" + generatedAt + "\"},"
        + "\"rows\":[" + rowIds + "],"
        + "\"audit\":{\"actorId\":\"" + jsonEscape(command.actorId()) + "\",\"correlationId\":\"" + jsonEscape(command.correlationId()) + "\"},"
        + "\"hashes\":{\"sourceRefs\":\"" + jsonEscape(command.sourceType() + ':' + command.sourceId() + ':' + command.rowIds()) + "\"},"
        + "\"disclaimer\":\"" + jsonEscape(NON_BINDING_DISCLAIMER) + "\"}"
        + System.lineSeparator();
  }

  static String csvCell(String value) {
    String safe = guardFormula(defaultText(value, ""));
    return "\"" + safe.replace("\"", "\"\"") + "\"";
  }

  static String guardFormula(String value) {
    if (value.isEmpty()) {
      return value;
    }
    char first = value.charAt(0);
    return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
  }

  private static int rowCount(CreateExportCommand command) {
    return command.rowIds().isEmpty() ? 1 : command.rowIds().size();
  }

  private static String canonicalRequest(CreateExportCommand command) {
    return command.tenantId() + '|' + command.sourceType() + '|' + command.sourceId() + '|' + command.rowIds() + '|'
        + command.format() + '|' + command.recipientType() + '|' + command.includeLedger() + '|'
        + command.includeIneligible() + '|' + command.fileName() + '|' + command.actorId();
  }

  private static String safeFileName(String fileName) {
    String cleaned = fileName.replace('\\', '-').replace('/', '-').trim();
    if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
      throw new ValidationException("fileName is invalid");
    }
    return cleaned;
  }

  private static String contentType(String format) {
    return "JSON".equals(format) ? "application/json" : "text/csv; charset=utf-8";
  }

  private static String auditRef(String action, UUID exportId, Instant occurredAt) {
    return "audit:" + action + ':' + exportId + ':' + occurredAt;
  }

  private static ExportEvent event(String eventType, CreateExportCommand command, UUID exportId, String contentSha256, Instant occurredAt) {
    return event(eventType, command.tenantId(), exportId, command.actorId(), command.correlationId(), command.causationId(), command.idempotencyKey(), contentSha256, occurredAt);
  }

  private static ExportEvent event(String eventType, String tenantId, UUID exportId, String actorId, String correlationId, String causationId, String idempotencyKey, String contentSha256, Instant occurredAt) {
    return new ExportEvent(UUID.randomUUID(), eventType, tenantId, exportId, actorId, correlationId, causationId, idempotencyKey, contentSha256, occurredAt);
  }

  private static List<String> append(List<String> values, String next) {
    List<String> appended = new ArrayList<>(values == null ? List.of() : values);
    appended.add(next);
    return List.copyOf(appended);
  }

  private static List<String> collapseText(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> collapsed = new LinkedHashSet<>();
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        collapsed.add(value.trim());
      }
    }
    return List.copyOf(collapsed);
  }

  private static String requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(message);
    }
    return value.trim();
  }

  private static String defaultText(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static String jsonEscape(String value) {
    return defaultText(value, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }

  private static String sha256Hex(String value) {
    return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(bytes);
      StringBuilder hex = new StringBuilder(hashed.length * 2);
      for (byte b : hashed) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  public record CreateExportCommand(
      String tenantId,
      String sourceType,
      String sourceId,
      List<String> rowIds,
      String format,
      String recipientType,
      boolean includeLedger,
      boolean includeIneligible,
      String fileName,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record ExportResponse(
      UUID exportId,
      String sourceType,
      String sourceId,
      String format,
      String recipientType,
      String fileName,
      String status,
      String storageUri,
      String contentSha256,
      int rowCount,
      Instant expiresAt,
      Instant revokedAt,
      String auditRef,
      String replayRef,
      List<String> validationMessages,
      List<String> eventTypes,
      String correlationId) {}

  public record ExportDownload(UUID exportId, String fileName, String contentType, byte[] content) {}

  public record ExportEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID exportId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String contentSha256,
      Instant occurredAt) {}

  public record StoredExport(
      String tenantId,
      UUID exportId,
      String requestHash,
      String idempotencyKeyHash,
      String createdBy,
      ExportResponse response,
      byte[] content,
      List<ExportEvent> events,
      Instant createdAt) {}

  public interface ExportRepository {
    Optional<StoredExport> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredExport> findByExportId(String tenantId, UUID exportId);

    void save(StoredExport export);

    void update(StoredExport current, ExportResponse response, ExportEvent event);
  }

  public interface ExportStorage {
    String store(String tenantId, UUID exportId, byte[] content, String format);

    Optional<byte[]> read(String storageUri);
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class PolicyNotSatisfiedException extends RuntimeException {
    public PolicyNotSatisfiedException(String message) {
      super(message);
    }
  }

  public static class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }

  public static class ExportExpiredException extends RuntimeException {
    public ExportExpiredException(String message) {
      super(message);
    }
  }

  public static class ExportRevokedException extends RuntimeException {
    public ExportRevokedException(String message) {
      super(message);
    }
  }

  public static class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException(String message) {
      super(message);
    }
  }
}

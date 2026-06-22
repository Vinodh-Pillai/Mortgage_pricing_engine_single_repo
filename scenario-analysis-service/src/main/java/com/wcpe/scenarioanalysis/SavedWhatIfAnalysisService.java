package com.wcpe.scenarioanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class SavedWhatIfAnalysisService {
  private static final Set<String> VALID_VISIBILITIES = Set.of("private", "team", "tenant-read-only");
  private static final Set<String> VALID_STATUSES = Set.of("SAVED", "ARCHIVED");
  private static final Set<String> VALID_SHARE_ROLES = Set.of("read", "write");
  private static final Pattern SSN_PATTERN = Pattern.compile("(?<!\\d)\\d{3}-\\d{2}-\\d{4}(?!\\d)");

  private final SavedAnalysisRepository repository;
  private final SelectionAvailabilityChecker selectionAvailabilityChecker;
  private final Clock clock;

  public SavedWhatIfAnalysisService() {
    throw FailClosedPersistence.notConfigured("saved what-if analysis store");
  }

  SavedWhatIfAnalysisService(SavedAnalysisRepository repository, Clock clock) {
    this(repository, new FailClosedSelectionAvailabilityChecker(), clock);
  }

  SavedWhatIfAnalysisService(SavedAnalysisRepository repository, SelectionAvailabilityChecker selectionAvailabilityChecker, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.selectionAvailabilityChecker = Objects.requireNonNull(selectionAvailabilityChecker, "selectionAvailabilityChecker is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public AnalysisResponse saveAnalysis(CreateAnalysisCommand command) {
    CreateAnalysisCommand validCommand = validateCreate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalCreateRequest(validCommand));
    Optional<StoredAnalysis> existing = repository.findByIdempotencyKeyHash(validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredAnalysis analysis = existing.get();
      if (!analysis.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different saved analysis request");
      }
      return analysis.response();
    }
    if (repository.activeNameExists(validCommand.tenantId(), validCommand.sourceQuoteId(), validCommand.actorId(), validCommand.name(), null)) {
      throw new NameConflictException("active saved analysis name already exists for this user and source quote");
    }

    UUID analysisId = UUID.randomUUID();
    Instant now = Instant.now(clock);
    String notes = defaultText(validCommand.notes(), "");
    String notesHash = "sha256:" + sha256Hex(notes);
    List<NoteHistoryEntry> noteHistory = List.of(noteHistoryEntry(1, notes, notesHash, validCommand.actorId(), now));
    String analysisHash = "sha256:" + sha256Hex(canonicalAnalysisHash(validCommand, notesHash));
    AnalysisResponse response = new AnalysisResponse(
        analysisId,
        "SAVED",
        1,
        validCommand.name(),
        validCommand.reasonCode(),
        validCommand.sourceQuoteId(),
        validCommand.sourceQuoteVersion(),
        validCommand.pricingConfigVersion(),
        validCommand.selectedVariantIds(),
        validCommand.selectedGridCellIds(),
        validCommand.tags(),
        validCommand.visibility(),
        validCommand.retentionCategory(),
        validCommand.linkToQuoteDecision(),
        false,
        analysisHash,
        notes,
        notesHash,
        noteHistory,
        auditRef("WHAT_IF_ANALYSIS_SAVED", analysisId, now),
        "replay:saved-what-if-analysis:" + analysisHash.substring("sha256:".length()),
        List.of("analysis saved; export packaging and external identity/team policy checks require owning dependencies"),
        List.of(),
        now,
        validCommand.correlationId());
    SavedAnalysisEvent event = event("whatif.analysis.saved.v1", validCommand.tenantId(), analysisId, validCommand.actorId(), validCommand.correlationId(), validCommand.causationId(), validCommand.idempotencyKey(), analysisHash, now);
    repository.save(new StoredAnalysis(
        validCommand.tenantId(),
        analysisId,
        requestHash,
        idempotencyKeyHash,
        validCommand.actorId(),
        response,
        notesHash,
        noteHistory,
        List.of(event),
        now));
    return response;
  }

  public AnalysisResponse getAnalysis(String tenantId, UUID analysisId, Integer currentSourceQuoteVersion, String currentPricingConfigVersion) {
    StoredAnalysis stored = findAnalysis(tenantId, analysisId);
    return withStaleBadge(stored.response(), currentSourceQuoteVersion, currentPricingConfigVersion);
  }

  public AnalysisSearchResponse searchAnalyses(String tenantId, String sourceQuoteId, String tag, String status) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    String normalizedStatus = status == null || status.isBlank() ? "SAVED" : status.trim().toUpperCase();
    if (!VALID_STATUSES.contains(normalizedStatus)) {
      throw new ValidationException("status must be SAVED or ARCHIVED");
    }
    List<AnalysisResponse> analyses = repository.findByTenant(normalizedTenantId).stream()
        .map(StoredAnalysis::response)
        .filter(response -> normalizedStatus.equals(response.status()))
        .filter(response -> sourceQuoteId == null || sourceQuoteId.isBlank() || sourceQuoteId.trim().equals(response.sourceQuoteId()))
        .filter(response -> tag == null || tag.isBlank() || response.tags().contains(tag.trim()))
        .toList();
    return new AnalysisSearchResponse(analyses, normalizedStatus);
  }

  public AnalysisResponse patchAnalysis(PatchAnalysisCommand command) {
    PatchAnalysisCommand validCommand = validatePatch(command);
    StoredAnalysis stored = findAnalysis(validCommand.tenantId(), validCommand.analysisId());
    AnalysisResponse current = stored.response();
    if (current.version() != validCommand.expectedVersion()) {
      throw new AnalysisVersionConflictException("If-Match version does not match current saved analysis version");
    }
    String nextName = defaultText(validCommand.name(), current.name());
    String nextStatus = defaultText(validCommand.status(), current.status()).toUpperCase();
    if (!VALID_STATUSES.contains(nextStatus)) {
      throw new ValidationException("status must be SAVED or ARCHIVED");
    }
    if (repository.activeNameExists(validCommand.tenantId(), current.sourceQuoteId(), stored.createdBy(), nextName, current.analysisId())) {
      throw new NameConflictException("active saved analysis name already exists for this user and source quote");
    }
    List<String> nextTags = validCommand.tags() == null ? current.tags() : collapseText(validCommand.tags());
    String nextVisibility = defaultText(validCommand.visibility(), current.visibility());
    requireVisibility(nextVisibility);
    int nextVersion = current.version() + 1;
    Instant now = Instant.now(clock);
    String nextNotes = validCommand.notes() == null ? current.notes() : defaultText(validCommand.notes(), "");
    String nextNotesHash = validCommand.notes() == null ? stored.notesHash() : notesHash(validCommand.notes());
    List<NoteHistoryEntry> nextNoteHistory = validCommand.notes() == null
        ? current.noteHistory()
        : appendNoteHistory(stored.noteHistory(), nextVersion, nextNotes, nextNotesHash, validCommand.actorId(), now);
    List<SharePermission> nextShares = validCommand.sharePermissions() == null ? current.sharePermissions() : validateShares(validCommand.sharePermissions());
    String nextHash = "sha256:" + sha256Hex(current.analysisId() + "|" + nextVersion + "|" + nextName + "|" + nextStatus + "|" + nextTags + "|" + nextVisibility + "|" + nextNotesHash + "|" + nextShares);
    AnalysisResponse updated = new AnalysisResponse(
        current.analysisId(),
        nextStatus,
        nextVersion,
        nextName,
        current.reasonCode(),
        current.sourceQuoteId(),
        current.sourceQuoteVersion(),
        current.pricingConfigVersion(),
        current.selectedVariantIds(),
        current.selectedGridCellIds(),
        nextTags,
        nextVisibility,
        current.retentionCategory(),
        current.linkedToQuoteDecision(),
        current.stale(),
        nextHash,
        nextNotes,
        nextNotesHash,
        nextNoteHistory,
        auditRef("WHAT_IF_ANALYSIS_" + nextStatus, current.analysisId(), now),
        "replay:saved-what-if-analysis:" + nextHash.substring("sha256:".length()),
        List.of("saved analysis updated; quote decision state remains owned by quote service"),
        nextShares,
        now,
        validCommand.correlationId());
    String eventType = "ARCHIVED".equals(nextStatus) ? "whatif.analysis.archived.v1" : "whatif.analysis.updated.v1";
    repository.update(stored, updated, nextNotesHash, nextNoteHistory, event(eventType, validCommand.tenantId(), current.analysisId(), validCommand.actorId(), validCommand.correlationId(), validCommand.causationId(), null, nextHash, now));
    return updated;
  }

  private StoredAnalysis findAnalysis(String tenantId, UUID analysisId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (analysisId == null) {
      throw new ValidationException("analysisId is required");
    }
    return repository.findByAnalysisId(normalizedTenantId, analysisId)
        .orElseThrow(() -> new NotFoundException("saved what-if analysis was not found"));
  }

  private CreateAnalysisCommand validateCreate(CreateAnalysisCommand command) {
    if (command == null) {
      throw new ValidationException("save analysis request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String name = requireText(command.name(), "name is required");
    String reasonCode = requireText(command.reasonCode(), "reasonCode is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    if (command.sourceQuoteVersion() == null || command.sourceQuoteVersion() < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    String pricingConfigVersion = requireText(command.pricingConfigVersion(), "pricingConfigVersion is required");
    List<String> variantIds = collapseText(command.selectedVariantIds());
    List<String> gridCellIds = collapseText(command.selectedGridCellIds());
    if (variantIds.isEmpty() && gridCellIds.isEmpty()) {
      throw new SelectionNotAvailableException("at least one selected variant or grid cell is required");
    }
    SelectionAvailabilityResult availability = selectionAvailabilityChecker.validateSelections(tenantId, sourceQuoteId, variantIds, gridCellIds);
    if (!availability.available()) {
      throw new SelectionNotAvailableException("selected variant or grid cell is missing or expired: " + availability.describe());
    }
    String visibility = requireVisibility(command.visibility());
    String retentionCategory = requireText(command.retentionCategory(), "retentionCategory is required");
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    String notes = defaultText(command.notes(), "");
    rejectSensitiveNotes(notes);
    return new CreateAnalysisCommand(
        tenantId,
        name,
        reasonCode,
        sourceQuoteId,
        command.sourceQuoteVersion(),
        pricingConfigVersion,
        variantIds,
        gridCellIds,
        notes,
        collapseText(command.tags()),
        visibility,
        retentionCategory,
        command.linkToQuoteDecision(),
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private PatchAnalysisCommand validatePatch(PatchAnalysisCommand command) {
    if (command == null) {
      throw new ValidationException("patch analysis request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    if (command.analysisId() == null) {
      throw new ValidationException("analysisId is required");
    }
    if (command.expectedVersion() == null || command.expectedVersion() < 1) {
      throw new ValidationException("If-Match version must be positive");
    }
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    if (command.notes() != null) {
      rejectSensitiveNotes(command.notes());
    }
    return new PatchAnalysisCommand(
        tenantId,
        command.analysisId(),
        command.expectedVersion(),
        command.name(),
        command.notes(),
        command.tags(),
        command.visibility(),
        command.status(),
        command.sharePermissions(),
        actorId,
        correlationId,
        causationId);
  }

  private static String requireVisibility(String visibility) {
    String normalized = requireText(visibility, "visibility is required").toLowerCase();
    if (!VALID_VISIBILITIES.contains(normalized)) {
      throw new ValidationException("visibility must be private, team, or tenant-read-only");
    }
    return normalized;
  }

  private static List<SharePermission> validateShares(List<SharePermission> shares) {
    if (shares == null || shares.isEmpty()) {
      return List.of();
    }
    List<SharePermission> validShares = new ArrayList<>();
    for (SharePermission share : shares) {
      if (share == null) {
        throw new ValidationException("share permission is required");
      }
      String targetType = requireText(share.targetType(), "share targetType is required").toLowerCase();
      if (!Set.of("user", "team").contains(targetType)) {
        throw new ShareTargetNotFoundException("share target was not found");
      }
      String targetId = requireText(share.targetId(), "share targetId is required");
      String role = requireText(share.role(), "share role is required").toLowerCase();
      if (!VALID_SHARE_ROLES.contains(role)) {
        throw new ValidationException("share role must be read or write");
      }
      validShares.add(new SharePermission(targetType, targetId, role));
    }
    return List.copyOf(validShares);
  }

  private static String notesHash(String notes) {
    String normalized = defaultText(notes, "");
    rejectSensitiveNotes(normalized);
    return "sha256:" + sha256Hex(normalized);
  }

  private static NoteHistoryEntry noteHistoryEntry(int version, String notes, String notesHash, String updatedBy, Instant updatedAt) {
    return new NoteHistoryEntry(
        version,
        notes,
        notesHash,
        updatedBy,
        updatedAt,
        auditRef("WHAT_IF_ANALYSIS_NOTES_UPDATED", UUID.nameUUIDFromBytes(notesHash.getBytes(StandardCharsets.UTF_8)), updatedAt));
  }

  private static List<NoteHistoryEntry> appendNoteHistory(List<NoteHistoryEntry> currentHistory, int version, String notes, String notesHash, String updatedBy, Instant updatedAt) {
    List<NoteHistoryEntry> history = new ArrayList<>(currentHistory == null ? List.of() : currentHistory);
    history.add(noteHistoryEntry(version, notes, notesHash, updatedBy, updatedAt));
    return List.copyOf(history);
  }

  private static void rejectSensitiveNotes(String notes) {
    if (SSN_PATTERN.matcher(notes).find()) {
      throw new ValidationException("notes contain a prohibited SSN pattern");
    }
  }

  private static AnalysisResponse withStaleBadge(AnalysisResponse response, Integer currentSourceQuoteVersion, String currentPricingConfigVersion) {
    boolean sourceQuoteStale = currentSourceQuoteVersion != null && !currentSourceQuoteVersion.equals(response.sourceQuoteVersion());
    boolean pricingConfigStale = currentPricingConfigVersion != null && !currentPricingConfigVersion.isBlank() && !currentPricingConfigVersion.trim().equals(response.pricingConfigVersion());
    if (!sourceQuoteStale && !pricingConfigStale) {
      return response;
    }
    return new AnalysisResponse(
        response.analysisId(),
        response.status(),
        response.version(),
        response.name(),
        response.reasonCode(),
        response.sourceQuoteId(),
        response.sourceQuoteVersion(),
        response.pricingConfigVersion(),
        response.selectedVariantIds(),
        response.selectedGridCellIds(),
        response.tags(),
        response.visibility(),
        response.retentionCategory(),
        response.linkedToQuoteDecision(),
        true,
        response.analysisHash(),
        response.notes(),
        response.notesHash(),
        response.noteHistory(),
        response.auditRef(),
        response.replayRef(),
        List.of("saved analysis is stale relative to supplied quote or pricing config version"),
        response.sharePermissions(),
        response.updatedAt(),
        response.correlationId());
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

  private static String canonicalCreateRequest(CreateAnalysisCommand command) {
    return command.tenantId() + '|' + command.name() + '|' + command.reasonCode() + '|' + command.sourceQuoteId() + '|'
        + command.sourceQuoteVersion() + '|' + command.pricingConfigVersion() + '|' + command.selectedVariantIds() + '|'
        + command.selectedGridCellIds() + '|' + command.notes() + '|' + command.tags() + '|' + command.visibility() + '|'
        + command.retentionCategory() + '|' + command.linkToQuoteDecision() + '|' + command.actorId();
  }

  private static String canonicalAnalysisHash(CreateAnalysisCommand command, String notesHash) {
    return command.tenantId() + '|' + command.sourceQuoteId() + '|' + command.sourceQuoteVersion() + '|'
        + command.pricingConfigVersion() + '|' + command.selectedVariantIds() + '|' + command.selectedGridCellIds() + '|'
        + command.tags() + '|' + command.visibility() + '|' + command.retentionCategory() + '|' + command.linkToQuoteDecision() + '|'
        + notesHash;
  }

  private static String auditRef(String action, UUID analysisId, Instant occurredAt) {
    return "audit:" + action + ':' + analysisId + ':' + occurredAt.toString();
  }

  private static SavedAnalysisEvent event(String eventType, String tenantId, UUID analysisId, String actorId, String correlationId, String causationId, String idempotencyKey, String analysisHash, Instant occurredAt) {
    return new SavedAnalysisEvent(UUID.randomUUID(), eventType, tenantId, analysisId, actorId, correlationId, causationId, idempotencyKey, analysisHash, occurredAt);
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
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

  public record CreateAnalysisCommand(
      String tenantId,
      String name,
      String reasonCode,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      String pricingConfigVersion,
      List<String> selectedVariantIds,
      List<String> selectedGridCellIds,
      String notes,
      List<String> tags,
      String visibility,
      String retentionCategory,
      boolean linkToQuoteDecision,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record PatchAnalysisCommand(
      String tenantId,
      UUID analysisId,
      Integer expectedVersion,
      String name,
      String notes,
      List<String> tags,
      String visibility,
      String status,
      List<SharePermission> sharePermissions,
      String actorId,
      String correlationId,
      String causationId) {}

  public record SharePermission(String targetType, String targetId, String role) {}

  public record NoteHistoryEntry(
      int version,
      String notes,
      String notesHash,
      String updatedBy,
      Instant updatedAt,
      String auditRef) {}

  public record AnalysisResponse(
      UUID analysisId,
      String status,
      int version,
      String name,
      String reasonCode,
      String sourceQuoteId,
      int sourceQuoteVersion,
      String pricingConfigVersion,
      List<String> selectedVariantIds,
      List<String> selectedGridCellIds,
      List<String> tags,
      String visibility,
      String retentionCategory,
      boolean linkedToQuoteDecision,
      boolean stale,
      String analysisHash,
      String notes,
      String notesHash,
      List<NoteHistoryEntry> noteHistory,
      String auditRef,
      String replayRef,
      List<String> validationMessages,
      List<SharePermission> sharePermissions,
      Instant updatedAt,
      String correlationId) {}

  public record AnalysisSearchResponse(List<AnalysisResponse> analyses, String statusFilter) {}

  public record StoredAnalysis(
      String tenantId,
      UUID analysisId,
      String requestHash,
      String idempotencyKeyHash,
      String createdBy,
      AnalysisResponse response,
      String notesHash,
      List<NoteHistoryEntry> noteHistory,
      List<SavedAnalysisEvent> events,
      Instant createdAt) {}

  public record SavedAnalysisEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID analysisId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String analysisHash,
      Instant occurredAt) {}

  public interface SavedAnalysisRepository {
    Optional<StoredAnalysis> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredAnalysis> findByAnalysisId(String tenantId, UUID analysisId);

    List<StoredAnalysis> findByTenant(String tenantId);

    boolean activeNameExists(String tenantId, String sourceQuoteId, String createdBy, String name, UUID excludingAnalysisId);

    void save(StoredAnalysis analysis);

    void update(StoredAnalysis current, AnalysisResponse response, String notesHash, List<NoteHistoryEntry> noteHistory, SavedAnalysisEvent event);
  }

  public interface SelectionAvailabilityChecker {
    SelectionAvailabilityResult validateSelections(String tenantId, String sourceQuoteId, List<String> variantIds, List<String> gridCellIds);
  }

  public record SelectionAvailabilityResult(
      List<String> missingVariantIds,
      List<String> expiredVariantIds,
      List<String> missingGridCellIds,
      List<String> expiredGridCellIds) {
    public boolean available() {
      return missingVariantIds.isEmpty() && expiredVariantIds.isEmpty() && missingGridCellIds.isEmpty() && expiredGridCellIds.isEmpty();
    }

    public String describe() {
      return "missingVariantIds=" + missingVariantIds
          + ", expiredVariantIds=" + expiredVariantIds
          + ", missingGridCellIds=" + missingGridCellIds
          + ", expiredGridCellIds=" + expiredGridCellIds;
    }
  }

  private static class FailClosedSelectionAvailabilityChecker implements SelectionAvailabilityChecker {
    @Override
    public SelectionAvailabilityResult validateSelections(String tenantId, String sourceQuoteId, List<String> variantIds, List<String> gridCellIds) {
      return new SelectionAvailabilityResult(
          List.copyOf(variantIds),
          List.of(),
          List.copyOf(gridCellIds),
          List.of());
    }
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
      super(message);
    }
  }

  public static class AnalysisVersionConflictException extends RuntimeException {
    public AnalysisVersionConflictException(String message) {
      super(message);
    }
  }

  public static class NameConflictException extends RuntimeException {
    public NameConflictException(String message) {
      super(message);
    }
  }

  public static class SelectionNotAvailableException extends RuntimeException {
    public SelectionNotAvailableException(String message) {
      super(message);
    }
  }

  public static class ShareTargetNotFoundException extends RuntimeException {
    public ShareTargetNotFoundException(String message) {
      super(message);
    }
  }

  public static class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
      super(message);
    }
  }
}

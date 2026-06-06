package com.wcpe.scenarioanalysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class BatchSensitivityGridService {
  private final BatchGridRepository repository;
  private final Clock clock;

  public BatchSensitivityGridService() {
    this(new InMemoryBatchGridRepository(), Clock.systemUTC());
  }

  BatchSensitivityGridService(BatchGridRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public BatchGridResponse createGrid(BatchGridCommand command) {
    BatchGridCommand validCommand = validate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(validCommand));
    Optional<StoredBatchGrid> existing = repository.findByIdempotencyKeyHash(validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredBatchGrid grid = existing.get();
      if (!grid.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different batch sensitivity grid request");
      }
      return grid.response();
    }

    int cellCount = cellCount(validCommand.axes());
    if (cellCount > validCommand.maxCells()) {
      throw new CellLimitExceededException("grid cell count " + cellCount + " exceeds requested maxCells " + validCommand.maxCells());
    }

    UUID gridId = UUID.randomUUID();
    Instant now = Instant.now(clock);
    List<BatchGridCell> cells = createQueuedCells(validCommand, gridId);
    BatchGridResponse response = new BatchGridResponse(
        gridId,
        "QUEUED",
        validCommand.gridName(),
        validCommand.sourceQuoteId(),
        validCommand.sourceQuoteVersion(),
        validCommand.axes(),
        cells,
        new BatchGridSummary(cells.size(), cells.size(), 0, 0, 0, 0),
        List.of("batch grid queued; pricing calls require an available tenant-governed pricing dependency"),
        "sha256:" + sha256Hex(canonicalResult(validCommand, cells)),
        validCommand.correlationId());
    repository.save(new StoredBatchGrid(
        validCommand.tenantId(),
        gridId,
        requestHash,
        idempotencyKeyHash,
        response,
        List.of(event("whatif.batch_grid.requested.v1", validCommand, gridId, null, response.resultHash(), now)),
        now));
    return response;
  }

  public BatchGridResponse getGrid(String tenantId, UUID gridId) {
    return findGrid(tenantId, gridId).response();
  }

  public BatchGridResponse runQueuedCells(String tenantId, UUID gridId) {
    StoredBatchGrid stored = findGrid(tenantId, gridId);
    BatchGridResponse current = stored.response();
    if ("CANCELLED".equals(current.status())) {
      return current;
    }
    List<BatchGridCell> priced = current.cells().stream()
        .map(cell -> {
          if (!"QUEUED".equals(cell.status()) && !"RUNNING".equals(cell.status())) {
            return cell;
          }
          String resultHash = "sha256:" + sha256Hex(current.gridId() + "|" + cell.xValue() + "|" + cell.yValue() + "|" + defaultText(cell.zValue(), ""));
          return new BatchGridCell(
              cell.cellId(),
              cell.xAxisType(),
              cell.xValue(),
              cell.yAxisType(),
              cell.yValue(),
              cell.zAxisType(),
              cell.zValue(),
              "FAILED",
              null,
              List.of("pricing_dependency_unavailable"),
              1,
              "PRICING_DEPENDENCY_UNAVAILABLE",
              resultHash);
        })
        .toList();
    BatchGridResponse updated = withCells(current, "FAILED", priced,
        List.of("pricing dependency unavailable; cells failed closed without invented pricing economics"));
    repository.update(stored, updated, event("whatif.batch_grid.failed.v1", stored, null, updated.resultHash(), Instant.now(clock)));
    return updated;
  }

  public BatchGridResponse pauseGrid(String tenantId, UUID gridId) {
    return transition(tenantId, gridId, "PAUSED", "whatif.batch_grid.paused.v1", "queued cells paused");
  }

  public BatchGridResponse resumeGrid(String tenantId, UUID gridId) {
    return transition(tenantId, gridId, "QUEUED", "whatif.batch_grid.resumed.v1", "paused cells returned to queued status");
  }

  public BatchGridResponse cancelGrid(String tenantId, UUID gridId) {
    StoredBatchGrid stored = findGrid(tenantId, gridId);
    List<BatchGridCell> cancelled = stored.response().cells().stream()
        .map(cell -> "PRICED".equals(cell.status()) ? cell : cell.withStatus("CANCELLED", "CANCELLED"))
        .toList();
    BatchGridResponse updated = withCells(stored.response(), "CANCELLED", cancelled, List.of("grid cancelled; late pricing results must be discarded"));
    repository.update(stored, updated, event("whatif.batch_grid.cancelled.v1", stored, null, updated.resultHash(), Instant.now(clock)));
    return updated;
  }

  public BatchGridResponse retryFailed(String tenantId, UUID gridId) {
    StoredBatchGrid stored = findGrid(tenantId, gridId);
    List<BatchGridCell> retried = stored.response().cells().stream()
        .map(cell -> "FAILED".equals(cell.status()) ? cell.withStatus("QUEUED", null) : cell)
        .toList();
    BatchGridResponse updated = withCells(stored.response(), "QUEUED", retried, List.of("failed cells requeued for pricing dependency retry"));
    repository.update(stored, updated, event("whatif.batch_grid.retry_failed.v1", stored, null, updated.resultHash(), Instant.now(clock)));
    return updated;
  }

  private BatchGridResponse transition(String tenantId, UUID gridId, String status, String eventType, String message) {
    StoredBatchGrid stored = findGrid(tenantId, gridId);
    List<BatchGridCell> cells = stored.response().cells().stream()
        .map(cell -> "PRICED".equals(cell.status()) || "FAILED".equals(cell.status()) ? cell : cell.withStatus(status, null))
        .toList();
    BatchGridResponse updated = withCells(stored.response(), status, cells, List.of(message));
    repository.update(stored, updated, event(eventType, stored, null, updated.resultHash(), Instant.now(clock)));
    return updated;
  }

  private StoredBatchGrid findGrid(String tenantId, UUID gridId) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    if (gridId == null) {
      throw new ValidationException("gridId is required");
    }
    return repository.findByGridId(normalizedTenantId, gridId)
        .orElseThrow(() -> new NotFoundException("batch sensitivity grid was not found"));
  }

  private BatchGridCommand validate(BatchGridCommand command) {
    if (command == null) {
      throw new ValidationException("batch sensitivity grid request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    if (command.sourceQuoteVersion() == null || command.sourceQuoteVersion() < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    String gridName = requireText(command.gridName(), "gridName is required");
    List<BatchGridAxis> axes = validateAxes(command.axes());
    if (command.maxCells() == null || command.maxCells() < 1) {
      throw new ValidationException("maxCells must be positive and tenant governed");
    }
    if (command.pricingAsOf() == null) {
      throw new ValidationException("pricingAsOf is required");
    }
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    return new BatchGridCommand(
        tenantId,
        sourceQuoteId,
        command.sourceQuoteVersion(),
        gridName,
        axes,
        command.includeIneligible(),
        command.maxCells(),
        command.pricingAsOf(),
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private static List<BatchGridAxis> validateAxes(List<BatchGridAxis> axes) {
    if (axes == null || axes.size() < 2 || axes.size() > 3) {
      throw new ValidationException("batch grid requires two axes and may include one optional lock-period axis");
    }
    Map<AxisType, BatchGridAxis> byType = new LinkedHashMap<>();
    List<BatchGridAxis> normalized = new ArrayList<>();
    for (BatchGridAxis axis : axes) {
      if (axis == null || axis.axisType() == null) {
        throw new ValidationException("axisType is required");
      }
      if (byType.containsKey(axis.axisType())) {
        throw new ValidationException("duplicate axis type " + axis.axisType() + " is not allowed");
      }
      List<String> values = collapseText(axis.values());
      if (values.isEmpty()) {
        throw new ValidationException("axis " + axis.axisType() + " requires at least one value");
      }
      BatchGridAxis normalizedAxis = new BatchGridAxis(axis.axisType(), values);
      byType.put(axis.axisType(), normalizedAxis);
      normalized.add(normalizedAxis);
    }
    return List.copyOf(normalized);
  }

  private static List<BatchGridCell> createQueuedCells(BatchGridCommand command, UUID gridId) {
    BatchGridAxis x = command.axes().get(0);
    BatchGridAxis y = command.axes().get(1);
    BatchGridAxis z = command.axes().size() == 3 ? command.axes().get(2) : null;
    List<String> zValues = z == null ? List.of("") : z.values();
    List<BatchGridCell> cells = new ArrayList<>();
    for (String xValue : x.values()) {
      for (String yValue : y.values()) {
        for (String zValue : zValues) {
          String hashInput = command.tenantId() + '|' + gridId + '|' + x.axisType() + ':' + xValue + '|' + y.axisType() + ':' + yValue + '|' + (z == null ? "" : z.axisType() + ":" + zValue);
          cells.add(new BatchGridCell(
              UUID.randomUUID(),
              x.axisType(),
              xValue,
              y.axisType(),
              yValue,
              z == null ? null : z.axisType(),
              z == null ? null : zValue,
              "QUEUED",
              canonicalOverrides(x, xValue, y, yValue, z, zValue),
              List.of(),
              0,
              null,
              "sha256:" + sha256Hex(hashInput)));
        }
      }
    }
    return List.copyOf(cells);
  }

  private static Map<String, String> canonicalOverrides(
      BatchGridAxis x,
      String xValue,
      BatchGridAxis y,
      String yValue,
      BatchGridAxis z,
      String zValue) {
    Map<AxisType, String> byType = new LinkedHashMap<>();
    byType.put(x.axisType(), xValue);
    byType.put(y.axisType(), yValue);
    if (z != null) {
      byType.put(z.axisType(), zValue);
    }
    Map<String, String> ordered = new LinkedHashMap<>();
    for (AxisType type : List.of(AxisType.LOCK_PERIOD, AxisType.LTV, AxisType.FICO)) {
      if (byType.containsKey(type)) {
        ordered.put(type.name(), byType.get(type));
      }
    }
    return Collections.unmodifiableMap(ordered);
  }

  private static BatchGridResponse withCells(
      BatchGridResponse current,
      String status,
      List<BatchGridCell> cells,
      List<String> messages) {
    BatchGridSummary summary = new BatchGridSummary(
        cells.size(),
        count(cells, "QUEUED"),
        count(cells, "PRICED"),
        count(cells, "INELIGIBLE"),
        count(cells, "FAILED"),
        count(cells, "CANCELLED"));
    String resultHash = "sha256:" + sha256Hex(current.gridId() + "|" + status + "|" + cells.stream().map(BatchGridCell::resultHash).toList());
    return new BatchGridResponse(
        current.gridId(),
        status,
        current.gridName(),
        current.sourceQuoteId(),
        current.sourceQuoteVersion(),
        current.axes(),
        List.copyOf(cells),
        summary,
        messages,
        resultHash,
        current.correlationId());
  }

  private static long count(List<BatchGridCell> cells, String status) {
    return cells.stream().filter(cell -> status.equals(cell.status())).count();
  }

  private static int cellCount(List<BatchGridAxis> axes) {
    int count = 1;
    for (BatchGridAxis axis : axes) {
      count *= axis.values().size();
    }
    return count;
  }

  private static ProductEvent event(String eventType, BatchGridCommand command, UUID gridId, UUID cellId, String resultHash, Instant occurredAt) {
    return new ProductEvent(UUID.randomUUID(), eventType, command.tenantId(), gridId, cellId, command.actorId(), command.correlationId(), command.causationId(), command.idempotencyKey(), resultHash, occurredAt);
  }

  private static ProductEvent event(String eventType, StoredBatchGrid grid, UUID cellId, String resultHash, Instant occurredAt) {
    ProductEvent prior = grid.events().get(0);
    return new ProductEvent(UUID.randomUUID(), eventType, grid.tenantId(), grid.gridId(), cellId, prior.actorId(), prior.correlationId(), prior.causationId(), prior.idempotencyKey(), resultHash, occurredAt);
  }

  private static String canonicalRequest(BatchGridCommand command) {
    return command.tenantId() + '|' + command.sourceQuoteId() + '|' + command.sourceQuoteVersion() + '|'
        + command.gridName() + '|' + command.axes() + '|' + command.includeIneligible() + '|'
        + command.maxCells() + '|' + command.pricingAsOf() + '|' + command.actorId();
  }

  private static String canonicalResult(BatchGridCommand command, List<BatchGridCell> cells) {
    return canonicalRequest(command) + '|' + cells.stream().map(cell -> cell.xValue() + ':' + cell.yValue() + ':' + defaultText(cell.zValue(), "") + ':' + cell.status()).toList();
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

  public enum AxisType {
    FICO,
    LTV,
    LOCK_PERIOD
  }

  public record BatchGridCommand(
      String tenantId,
      String sourceQuoteId,
      Integer sourceQuoteVersion,
      String gridName,
      List<BatchGridAxis> axes,
      boolean includeIneligible,
      Integer maxCells,
      Instant pricingAsOf,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record BatchGridAxis(AxisType axisType, List<String> values) {}

  public record BatchGridResponse(
      UUID gridId,
      String status,
      String gridName,
      String sourceQuoteId,
      int sourceQuoteVersion,
      List<BatchGridAxis> axes,
      List<BatchGridCell> cells,
      BatchGridSummary resultSummary,
      List<String> validationMessages,
      String resultHash,
      String correlationId) {}

  public record BatchGridCell(
      UUID cellId,
      AxisType xAxisType,
      String xValue,
      AxisType yAxisType,
      String yValue,
      AxisType zAxisType,
      String zValue,
      String status,
      Map<String, String> variantOverrides,
      List<String> ruleHits,
      int attemptCount,
      String errorCode,
      String resultHash) {
    BatchGridCell withStatus(String nextStatus, String errorCode) {
      return new BatchGridCell(cellId, xAxisType, xValue, yAxisType, yValue, zAxisType, zValue, nextStatus, variantOverrides, ruleHits, attemptCount, errorCode, resultHash);
    }
  }

  public record BatchGridSummary(long cellCount, long queuedCount, long pricedCount, long ineligibleCount, long failedCount, long cancelledCount) {}

  public record ProductEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID gridId,
      UUID cellId,
      String actorId,
      String correlationId,
      String causationId,
      String idempotencyKey,
      String resultHash,
      Instant occurredAt) {}

  public record StoredBatchGrid(
      String tenantId,
      UUID gridId,
      String requestHash,
      String idempotencyKeyHash,
      BatchGridResponse response,
      List<ProductEvent> events,
      Instant createdAt) {}

  interface BatchGridRepository {
    Optional<StoredBatchGrid> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    Optional<StoredBatchGrid> findByGridId(String tenantId, UUID gridId);

    void save(StoredBatchGrid grid);

    void update(StoredBatchGrid current, BatchGridResponse response, ProductEvent event);
  }

  static class InMemoryBatchGridRepository implements BatchGridRepository {
    private final Map<String, StoredBatchGrid> gridsByTenantAndIdempotency = new ConcurrentHashMap<>();
    private final Map<String, StoredBatchGrid> gridsByTenantAndGridId = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredBatchGrid> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash) {
      return Optional.ofNullable(gridsByTenantAndIdempotency.get(key(tenantId, idempotencyKeyHash)));
    }

    @Override
    public Optional<StoredBatchGrid> findByGridId(String tenantId, UUID gridId) {
      return Optional.ofNullable(gridsByTenantAndGridId.get(key(tenantId, gridId.toString())));
    }

    @Override
    public void save(StoredBatchGrid grid) {
      gridsByTenantAndIdempotency.put(key(grid.tenantId(), grid.idempotencyKeyHash()), grid);
      gridsByTenantAndGridId.put(key(grid.tenantId(), grid.gridId().toString()), grid);
    }

    @Override
    public void update(StoredBatchGrid current, BatchGridResponse response, ProductEvent event) {
      List<ProductEvent> events = new ArrayList<>(current.events());
      events.add(event);
      save(new StoredBatchGrid(current.tenantId(), current.gridId(), current.requestHash(), current.idempotencyKeyHash(), response, List.copyOf(events), current.createdAt()));
    }

    int size() {
      return gridsByTenantAndGridId.size();
    }
  }

  private static String key(String tenantId, String id) {
    return tenantId + ':' + id;
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class CellLimitExceededException extends RuntimeException {
    public CellLimitExceededException(String message) {
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
}

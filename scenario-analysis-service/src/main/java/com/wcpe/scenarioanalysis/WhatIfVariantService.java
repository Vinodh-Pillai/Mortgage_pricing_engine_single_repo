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
import org.springframework.stereotype.Service;

@Service
public class WhatIfVariantService {
  private static final Set<String> VALID_REASON_CODES = Set.of(
      "BORROWER_REQUEST",
      "COUNTER_OFFER",
      "FLOAT_DOWN_CHECK",
      "STRUCTURE_OPTIMIZATION");
  private static final Set<String> EDITABLE_FIELD_PATHS = Set.of(
      "fico",
      "loanAmount",
      "appraisedValue",
      "downPayment",
      "product",
      "lockPeriod",
      "occupancy",
      "propertyType",
      "state",
      "channel");

  private final WhatIfVariantRepository repository;
  private final Clock clock;

  public WhatIfVariantService() {
    throw FailClosedPersistence.notConfigured("what-if variant store");
  }

  public WhatIfVariantService(WhatIfVariantRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
  }

  public CreateVariantResponse createVariant(CreateVariantCommand command) {
    CreateVariantCommand validCommand = validate(command);
    String idempotencyKeyHash = sha256Hex(validCommand.idempotencyKey());
    String requestHash = sha256Hex(canonicalRequest(validCommand));

    Optional<StoredVariant> existing = repository.findByIdempotencyKeyHash(validCommand.tenantId(), idempotencyKeyHash);
    if (existing.isPresent()) {
      StoredVariant variant = existing.get();
      if (!variant.requestHash().equals(requestHash)) {
        throw new IdempotencyConflictException("idempotency key was already used with a different request");
      }
      return variant.response();
    }

    Instant now = Instant.now(clock);
    UUID variantId = UUID.randomUUID();
    String snapshotId = "quote-snapshot:" + validCommand.sourceQuoteId() + ":v" + validCommand.sourceQuoteVersion();
    List<String> changedFields = changedFields(validCommand.changes());
    String inputHash = "sha256:" + sha256Hex(canonicalInput(validCommand));

    CreateVariantResponse response = new CreateVariantResponse(
        variantId,
        "DRAFT",
        snapshotId,
        1,
        inputHash,
        changedFields,
        new VariantLinks(
            "/api/v1/tenants/%s/what-if/variants/%s/price".formatted(validCommand.tenantId(), variantId),
            "/api/v1/tenants/%s/what-if/variants/%s/compare".formatted(validCommand.tenantId(), variantId)),
        validCommand.correlationId());

    WhatIfVariantCreatedEvent event = new WhatIfVariantCreatedEvent(
        UUID.randomUUID(),
        "whatif.variant.created.v1",
        validCommand.tenantId(),
        variantId,
        validCommand.sourceQuoteId(),
        validCommand.sourceQuoteVersion(),
        snapshotId,
        changedFields,
        validCommand.actorId(),
        validCommand.correlationId(),
        validCommand.causationId(),
        inputHash,
        now);

    repository.save(new StoredVariant(
        validCommand.tenantId(),
        variantId,
        requestHash,
        idempotencyKeyHash,
        response,
        validCommand.changes(),
        event,
        now));
    return response;
  }

  Optional<StoredVariant> findVariantByIdempotencyKey(String tenantId, String idempotencyKey) {
    String normalizedTenantId = requireText(tenantId, "tenantId is required");
    String normalizedIdempotencyKey = requireText(idempotencyKey, "Idempotency-Key is required");
    return repository.findByIdempotencyKeyHash(normalizedTenantId, sha256Hex(normalizedIdempotencyKey));
  }

  private CreateVariantCommand validate(CreateVariantCommand command) {
    if (command == null) {
      throw new ValidationException("variant create request is required");
    }
    String tenantId = requireText(command.tenantId(), "tenantId is required");
    String sourceQuoteId = requireText(command.sourceQuoteId(), "sourceQuoteId is required");
    String variantName = requireText(command.variantName(), "variantName is required");
    String reasonCode = requireText(command.reasonCode(), "reasonCode is required");
    if (!VALID_REASON_CODES.contains(reasonCode)) {
      throw new ValidationException("reasonCode is not supported");
    }
    if (command.sourceQuoteVersion() == null || command.sourceQuoteVersion() < 1) {
      throw new ValidationException("sourceQuoteVersion must be positive");
    }
    Instant pricingAsOf = Objects.requireNonNull(command.pricingAsOf(), "pricingAsOf is required");
    String idempotencyKey = requireText(command.idempotencyKey(), "Idempotency-Key is required");
    String actorId = requireText(command.actorId(), "actorId is required");
    String correlationId = defaultText(command.correlationId(), UUID.randomUUID().toString());
    String causationId = defaultText(command.causationId(), correlationId);
    List<VariantChange> changes = validateChanges(command.changes());
    return new CreateVariantCommand(
        tenantId,
        sourceQuoteId,
        variantName,
        reasonCode,
        command.sourceQuoteVersion(),
        pricingAsOf,
        changes,
        idempotencyKey,
        actorId,
        correlationId,
        causationId);
  }

  private static List<VariantChange> validateChanges(List<VariantChange> changes) {
    if (changes == null || changes.isEmpty()) {
      throw new ValidationException("changes are required");
    }
    List<VariantChange> validChanges = new ArrayList<>();
    for (VariantChange change : changes) {
      if (change == null) {
        throw new ValidationException("change is required");
      }
      String fieldPath = requireText(change.fieldPath(), "fieldPath is required");
      if (!EDITABLE_FIELD_PATHS.contains(fieldPath)) {
        throw new UnsupportedFieldException("fieldPath is not editable: " + fieldPath);
      }
      String proposedValue = requireText(change.proposedValue(), "proposedValue is required");
      String valueType = requireText(change.valueType(), "valueType is required");
      validChanges.add(new VariantChange(fieldPath, change.previousValue(), proposedValue, valueType));
    }
    return List.copyOf(validChanges);
  }

  private static List<String> changedFields(List<VariantChange> changes) {
    LinkedHashSet<String> fields = new LinkedHashSet<>();
    for (VariantChange change : changes) {
      fields.add(change.fieldPath());
    }
    return List.copyOf(fields);
  }

  private static String canonicalRequest(CreateVariantCommand command) {
    return canonicalInput(command) + "|idempotencyActor=" + command.actorId();
  }

  private static String canonicalInput(CreateVariantCommand command) {
    StringBuilder builder = new StringBuilder();
    builder.append(command.tenantId()).append('|')
        .append(command.sourceQuoteId()).append('|')
        .append(command.sourceQuoteVersion()).append('|')
        .append(command.variantName()).append('|')
        .append(command.reasonCode()).append('|')
        .append(command.pricingAsOf());
    for (VariantChange change : command.changes()) {
      builder.append('|')
          .append(change.fieldPath()).append('=')
          .append(nullToEmpty(change.previousValue())).append("->")
          .append(change.proposedValue()).append(':')
          .append(change.valueType());
    }
    return builder.toString();
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

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  public record CreateVariantCommand(
      String tenantId,
      String sourceQuoteId,
      String variantName,
      String reasonCode,
      Integer sourceQuoteVersion,
      Instant pricingAsOf,
      List<VariantChange> changes,
      String idempotencyKey,
      String actorId,
      String correlationId,
      String causationId) {}

  public record VariantChange(
      String fieldPath,
      String previousValue,
      String proposedValue,
      String valueType) {}

  public record CreateVariantResponse(
      UUID variantId,
      String status,
      String sourceQuoteSnapshotId,
      int variantVersion,
      String inputHash,
      List<String> changedFields,
      VariantLinks links,
      String correlationId) {}

  public record VariantLinks(String price, String compare) {}

  public record StoredVariant(
      String tenantId,
      UUID variantId,
      String requestHash,
      String idempotencyKeyHash,
      CreateVariantResponse response,
      List<VariantChange> changes,
      WhatIfVariantCreatedEvent event,
      Instant createdAt) {}

  public record WhatIfVariantCreatedEvent(
      UUID eventId,
      String eventType,
      String tenantId,
      UUID variantId,
      String sourceQuoteId,
      int sourceQuoteVersion,
      String sourceQuoteSnapshotId,
      List<String> changedFields,
      String actorId,
      String correlationId,
      String causationId,
      String inputHash,
      Instant occurredAt) {}

  public interface WhatIfVariantRepository {
    Optional<StoredVariant> findByIdempotencyKeyHash(String tenantId, String idempotencyKeyHash);

    void save(StoredVariant variant);
  }

  public static class ValidationException extends RuntimeException {
    public ValidationException(String message) {
      super(message);
    }
  }

  public static class UnsupportedFieldException extends RuntimeException {
    public UnsupportedFieldException(String message) {
      super(message);
    }
  }

  public static class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
      super(message);
    }
  }
}

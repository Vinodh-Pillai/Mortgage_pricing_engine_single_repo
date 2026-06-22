package com.wcpe.mladvisory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TestModelVersionRepository implements ModelVersionRepository {
  private final Map<String, ModelVersion> versions = new HashMap<>();
  private final Map<String, ModelVersionIdempotencyRecord> idempotencyRecords = new HashMap<>();
  private final List<ModelVersionStatusHistory> statusHistory = new ArrayList<>();
  private final List<MlAdvisoryOutboxEvent> outboxEvents = new ArrayList<>();
  private final List<MlAdvisoryAuditRecord> auditRecords = new ArrayList<>();

  @Override
  public void save(ModelVersion version) {
    versions.put(versionKey(version.tenantId(), version.modelVersionId()), version);
  }

  @Override
  public Optional<ModelVersion> find(String tenantId, String modelVersionId) {
    return Optional.ofNullable(versions.get(versionKey(tenantId, modelVersionId)));
  }

  @Override
  public boolean existsByTenantModelAndSemanticVersion(String tenantId, String modelName, String semanticVersion) {
    String uniqueKey = versionUniqueKey(tenantId, modelName, semanticVersion);
    return versions.values().stream().anyMatch(version -> uniqueKey.equals(versionUniqueKey(version)));
  }

  @Override
  public List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType) {
    return versions.values().stream()
        .filter(version -> version.tenantId().equals(tenantId))
        .filter(version -> status == null || version.status() == status)
        .filter(version -> advisoryType == null || version.advisoryTypes().contains(advisoryType))
        .sorted(Comparator.comparing(ModelVersion::createdAt).reversed())
        .toList();
  }

  @Override
  public Optional<ModelVersionIdempotencyRecord> findIdempotency(String idempotencyKey) {
    return Optional.ofNullable(idempotencyRecords.get(idempotencyKey));
  }

  @Override
  public void saveIdempotency(ModelVersionIdempotencyRecord record) {
    idempotencyRecords.put(record.idempotencyKey(), record);
  }

  @Override
  public void saveStatusHistory(ModelVersionStatusHistory history) {
    statusHistory.add(history);
  }

  @Override
  public void saveOutboxEvent(MlAdvisoryOutboxEvent event) {
    outboxEvents.add(event);
  }

  @Override
  public void saveAuditRecord(MlAdvisoryAuditRecord record) {
    auditRecords.add(record);
  }

  @Override
  public List<MlAdvisoryOutboxEvent> outboxEvents() {
    return List.copyOf(outboxEvents);
  }

  @Override
  public List<MlAdvisoryAuditRecord> auditRecords() {
    return List.copyOf(auditRecords);
  }

  List<ModelVersionStatusHistory> statusHistory() {
    return List.copyOf(statusHistory);
  }

  private String versionKey(String tenantId, String modelVersionId) {
    return tenantId + ":" + modelVersionId;
  }

  private String versionUniqueKey(ModelVersion version) {
    return versionUniqueKey(version.tenantId(), version.modelName(), version.semanticVersion());
  }

  private String versionUniqueKey(String tenantId, String modelName, String semanticVersion) {
    return tenantId + ":" + normalized(modelName) + ":" + normalized(semanticVersion);
  }

  private String normalized(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}

package com.wcpe.mladvisory;

import java.util.List;
import java.util.Optional;

interface ModelVersionRepository {
  void save(ModelVersion version);

  Optional<ModelVersion> find(String tenantId, String modelVersionId);

  boolean existsByTenantModelAndSemanticVersion(String tenantId, String modelName, String semanticVersion);

  List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType);

  Optional<ModelVersionIdempotencyRecord> findIdempotency(String idempotencyKey);

  void saveIdempotency(ModelVersionIdempotencyRecord record);

  void saveStatusHistory(ModelVersionStatusHistory history);

  void saveOutboxEvent(MlAdvisoryOutboxEvent event);

  void saveAuditRecord(MlAdvisoryAuditRecord record);

  List<MlAdvisoryOutboxEvent> outboxEvents();

  List<MlAdvisoryAuditRecord> auditRecords();
}

record ModelVersionIdempotencyRecord(String idempotencyKey, String requestHash, ModelVersionResponse response) {}

record ModelVersionStatusHistory(
    String historyId,
    String modelVersionId,
    String tenantId,
    String beforeStatus,
    String afterStatus,
    String actorId,
    String reason,
    String governanceTicket,
    String correlationId,
    java.time.Instant changedAt) {}

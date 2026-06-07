package com.wcpe.mladvisory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;

final class JdbcModelVersionRepository implements ModelVersionRepository {
  private final Connection connection;
  private final DataSource dataSource;

  JdbcModelVersionRepository(Connection connection) {
    this.connection = connection;
    this.dataSource = null;
  }

  JdbcModelVersionRepository(DataSource dataSource) {
    this.connection = null;
    this.dataSource = dataSource;
  }

  @Override
  public void save(ModelVersion version) {
    withConnection(
        activeConnection -> {
      int updated;
      try (PreparedStatement statement =
          activeConnection.prepareStatement(
              "update ml_model_versions set status = ?, approved_by = ?, approved_at = ?, retired_at = ?, version = ?, lineage_json = ? where model_version_id = ?")) {
        statement.setString(1, version.status().name());
        statement.setString(2, version.approvedBy());
        statement.setTimestamp(3, version.approvedAt() == null ? null : Timestamp.from(version.approvedAt()));
        statement.setTimestamp(4, retiredAt(version.retiredAt()));
        statement.setInt(5, version.version());
        statement.setString(6, serializeMap(version.lineageRefs()));
        statement.setString(7, version.modelVersionId());
        updated = statement.executeUpdate();
      }
      if (updated == 0) {
        try (PreparedStatement statement =
            activeConnection.prepareStatement(
                "insert into ml_model_versions (model_version_id, tenant_id, model_name, semantic_version, advisory_types, allowed_use, status, artifact_uri, artifact_checksum, feature_schema_version, created_by, created_at, approved_by, approved_at, retired_at, version, lineage_json) values (?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
          statement.setString(1, version.modelVersionId());
          statement.setString(2, version.tenantId());
          statement.setString(3, version.modelName());
          statement.setString(4, version.semanticVersion());
          statement.setString(5, version.advisoryTypes().stream().map(Enum::name).collect(Collectors.joining(",")));
          statement.setString(6, version.allowedUse().name());
          statement.setString(7, version.status().name());
          statement.setString(8, version.artifactUri());
          statement.setString(9, version.artifactChecksum());
          statement.setString(10, version.featureSchemaVersion());
          statement.setString(11, version.createdBy());
          statement.setTimestamp(12, Timestamp.from(version.createdAt()));
          statement.setString(13, version.approvedBy());
          statement.setTimestamp(14, version.approvedAt() == null ? null : Timestamp.from(version.approvedAt()));
          statement.setTimestamp(15, retiredAt(version.retiredAt()));
          statement.setInt(16, version.version());
          statement.setString(17, serializeMap(version.lineageRefs()));
          statement.executeUpdate();
        }
      }
      try (PreparedStatement deleteEvidence = activeConnection.prepareStatement("delete from ml_model_governance_evidence where model_version_id = ?")) {
        deleteEvidence.setString(1, version.modelVersionId());
        deleteEvidence.executeUpdate();
      }
      for (ModelEvidence evidence : version.evidence()) {
        try (PreparedStatement evidenceStatement =
            activeConnection.prepareStatement(
                "insert into ml_model_governance_evidence (evidence_id, model_version_id, tenant_id, evidence_type, uri_or_payload, metric_json, review_status) values (?, ?, ?::uuid, ?, ?, ?, ?)")) {
          evidenceStatement.setString(1, version.modelVersionId() + ":" + evidence.evidenceType());
          evidenceStatement.setString(2, version.modelVersionId());
          evidenceStatement.setString(3, version.tenantId());
          evidenceStatement.setString(4, evidence.evidenceType());
          evidenceStatement.setString(5, evidence.uriOrPayload());
          evidenceStatement.setString(6, evidence.metrics().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(";")));
          evidenceStatement.setString(7, evidence.reviewStatus());
          evidenceStatement.executeUpdate();
        }
      }
      return null;
    });
  }

  @Override
  public Optional<ModelVersion> find(String tenantId, String modelVersionId) {
    return withConnection(activeConnection -> {
    try (PreparedStatement statement = activeConnection.prepareStatement("select * from ml_model_versions where tenant_id = ?::uuid and model_version_id = ?")) {
      statement.setString(1, tenantId);
      statement.setString(2, modelVersionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(readVersion(resultSet, readEvidence(activeConnection, modelVersionId)));
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to read model version governance state", ex);
    }
    });
  }

  @Override
  public boolean existsByTenantModelAndSemanticVersion(String tenantId, String modelName, String semanticVersion) {
    return withConnection(activeConnection -> {
    try (PreparedStatement statement =
        activeConnection.prepareStatement("select 1 from ml_model_versions where tenant_id = ?::uuid and lower(model_name) = lower(?) and lower(semantic_version) = lower(?)")) {
      statement.setString(1, tenantId);
      statement.setString(2, modelName);
      statement.setString(3, semanticVersion);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to check model version uniqueness", ex);
    }
    });
  }

  @Override
  public List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType) {
    return withConnection(activeConnection -> {
    try (PreparedStatement statement = activeConnection.prepareStatement("select * from ml_model_versions where tenant_id = ?::uuid order by created_at desc")) {
      statement.setString(1, tenantId);
      try (ResultSet resultSet = statement.executeQuery()) {
        java.util.ArrayList<ModelVersion> results = new java.util.ArrayList<>();
        while (resultSet.next()) {
          ModelVersion version = readVersion(resultSet, readEvidence(activeConnection, resultSet.getString("model_version_id")));
          if ((status == null || version.status() == status) && (advisoryType == null || version.advisoryTypes().contains(advisoryType))) {
            results.add(version);
          }
        }
        return List.copyOf(results);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to list model version governance state", ex);
    }
    });
  }

  @Override
  public Optional<ModelVersionIdempotencyRecord> findIdempotency(String idempotencyKey) {
    return withConnection(activeConnection -> {
      try (PreparedStatement statement = activeConnection.prepareStatement("select * from ml_model_idempotency where idempotency_key = ?")) {
        statement.setString(1, idempotencyKey);
        try (ResultSet resultSet = statement.executeQuery()) {
          if (!resultSet.next()) {
            return Optional.empty();
          }
          ModelVersionResponse response =
              new ModelVersionResponse(
                  resultSet.getString("model_version_id"),
                  resultSet.getString("tenant_id"),
                  resultSet.getString("model_name"),
                  resultSet.getString("semantic_version"),
                  ModelStatus.valueOf(resultSet.getString("status")),
                  AllowedUse.valueOf(resultSet.getString("allowed_use")),
                  resultSet.getInt("version"),
                  resultSet.getString("event_ref"),
                  resultSet.getString("audit_ref"),
                  resultSet.getString("cache_invalidation_ref"),
                  resultSet.getString("correlation_id"));
          return Optional.of(new ModelVersionIdempotencyRecord(idempotencyKey, resultSet.getString("request_hash"), response));
        }
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to read model version idempotency state", ex);
      }
    });
  }

  @Override
  public void saveIdempotency(ModelVersionIdempotencyRecord record) {
    withConnection(activeConnection -> {
      ModelVersionResponse response = record.response();
      try {
        int updated;
        try (PreparedStatement statement =
            activeConnection.prepareStatement(
                "update ml_model_idempotency set request_hash = ?, model_version_id = ?, tenant_id = ?::uuid, model_name = ?, semantic_version = ?, status = ?, allowed_use = ?, version = ?, event_ref = ?, audit_ref = ?, cache_invalidation_ref = ?, correlation_id = ? where idempotency_key = ?")) {
          statement.setString(1, record.requestHash());
          statement.setString(2, response.modelVersionId());
          statement.setString(3, response.tenantId());
          statement.setString(4, response.modelName());
          statement.setString(5, response.semanticVersion());
          statement.setString(6, response.status().name());
          statement.setString(7, response.allowedUse().name());
          statement.setInt(8, response.version());
          statement.setString(9, response.eventRef());
          statement.setString(10, response.auditRef());
          statement.setString(11, response.cacheInvalidationRef());
          statement.setString(12, response.correlationId());
          statement.setString(13, record.idempotencyKey());
          updated = statement.executeUpdate();
        }
        if (updated == 0) {
          try (PreparedStatement statement =
              activeConnection.prepareStatement(
                  "insert into ml_model_idempotency (idempotency_key, request_hash, model_version_id, tenant_id, model_name, semantic_version, status, allowed_use, version, event_ref, audit_ref, cache_invalidation_ref, correlation_id) values (?, ?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, record.idempotencyKey());
            statement.setString(2, record.requestHash());
            statement.setString(3, response.modelVersionId());
            statement.setString(4, response.tenantId());
            statement.setString(5, response.modelName());
            statement.setString(6, response.semanticVersion());
            statement.setString(7, response.status().name());
            statement.setString(8, response.allowedUse().name());
            statement.setInt(9, response.version());
            statement.setString(10, response.eventRef());
            statement.setString(11, response.auditRef());
            statement.setString(12, response.cacheInvalidationRef());
            statement.setString(13, response.correlationId());
            statement.executeUpdate();
          }
        }
        return null;
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to persist model version idempotency state", ex);
      }
    });
  }

  @Override
  public void saveStatusHistory(ModelVersionStatusHistory history) {
    withConnection(activeConnection -> {
      try (PreparedStatement statement =
          activeConnection.prepareStatement(
              "insert into ml_model_status_history (history_id, model_version_id, tenant_id, before_status, after_status, actor_id, reason, governance_ticket, correlation_id, changed_at) values (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?)")) {
        statement.setString(1, history.historyId());
        statement.setString(2, history.modelVersionId());
        statement.setString(3, history.tenantId());
        statement.setString(4, history.beforeStatus());
        statement.setString(5, history.afterStatus());
        statement.setString(6, history.actorId());
        statement.setString(7, history.reason());
        statement.setString(8, history.governanceTicket());
        statement.setString(9, history.correlationId());
        statement.setTimestamp(10, Timestamp.from(history.changedAt()));
        statement.executeUpdate();
        return null;
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to persist model version status history", ex);
      }
    });
  }

  @Override
  public void saveOutboxEvent(MlAdvisoryOutboxEvent event) {
    withConnection(activeConnection -> {
      try (PreparedStatement statement =
          activeConnection.prepareStatement(
              "insert into ml_model_outbox_events (event_id, event_type, tenant_id, aggregate_id, actor_id, correlation_id, idempotency_key, occurred_at, payload_json) values (?, ?, ?::uuid, ?, ?, ?, ?, ?, ?)")) {
        statement.setString(1, event.eventId());
        statement.setString(2, event.eventType());
        statement.setString(3, event.tenantId());
        statement.setString(4, event.aggregateId());
        statement.setString(5, event.actorId());
        statement.setString(6, event.correlationId());
        statement.setString(7, event.idempotencyKey());
        statement.setTimestamp(8, Timestamp.from(event.occurredAt()));
        statement.setString(9, serializeMap(event.payload()));
        statement.executeUpdate();
        return null;
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to persist model version outbox event", ex);
      }
    });
  }

  @Override
  public void saveAuditRecord(MlAdvisoryAuditRecord record) {
    withConnection(activeConnection -> {
      try (PreparedStatement statement =
          activeConnection.prepareStatement(
              "insert into ml_model_audit_records (audit_id, tenant_id, actor_id, action, before_summary, after_summary, correlation_id, recorded_at) values (?, ?::uuid, ?, ?, ?, ?, ?, ?)")) {
        statement.setString(1, record.auditId());
        statement.setString(2, record.tenantId());
        statement.setString(3, record.actorId());
        statement.setString(4, record.action());
        statement.setString(5, record.beforeSummary());
        statement.setString(6, record.afterSummary());
        statement.setString(7, record.correlationId());
        statement.setTimestamp(8, Timestamp.from(record.recordedAt()));
        statement.executeUpdate();
        return null;
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to persist model version audit record", ex);
      }
    });
  }

  @Override
  public List<MlAdvisoryOutboxEvent> outboxEvents() {
    return withConnection(activeConnection -> {
      try (PreparedStatement statement = activeConnection.prepareStatement("select * from ml_model_outbox_events order by occurred_at asc")) {
        try (ResultSet resultSet = statement.executeQuery()) {
          List<MlAdvisoryOutboxEvent> results = new ArrayList<>();
          while (resultSet.next()) {
            results.add(
                new MlAdvisoryOutboxEvent(
                    resultSet.getString("event_id"),
                    resultSet.getString("event_type"),
                    resultSet.getString("tenant_id"),
                    resultSet.getString("aggregate_id"),
                    resultSet.getString("actor_id"),
                    resultSet.getString("correlation_id"),
                    resultSet.getString("idempotency_key"),
                    instant(resultSet.getTimestamp("occurred_at")),
                    deserializeMap(resultSet.getString("payload_json"))));
          }
          return List.copyOf(results);
        }
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to read model version outbox events", ex);
      }
    });
  }

  @Override
  public List<MlAdvisoryAuditRecord> auditRecords() {
    return withConnection(activeConnection -> {
      try (PreparedStatement statement = activeConnection.prepareStatement("select * from ml_model_audit_records order by recorded_at asc")) {
        try (ResultSet resultSet = statement.executeQuery()) {
          List<MlAdvisoryAuditRecord> results = new ArrayList<>();
          while (resultSet.next()) {
            results.add(
                new MlAdvisoryAuditRecord(
                    resultSet.getString("audit_id"),
                    resultSet.getString("tenant_id"),
                    resultSet.getString("actor_id"),
                    resultSet.getString("action"),
                    resultSet.getString("before_summary"),
                    resultSet.getString("after_summary"),
                    resultSet.getString("correlation_id"),
                    instant(resultSet.getTimestamp("recorded_at"))));
          }
          return List.copyOf(results);
        }
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to read model version audit records", ex);
      }
    });
  }

  private ModelVersion readVersion(ResultSet resultSet, List<ModelEvidence> evidence) throws SQLException {
    Timestamp approvedAt = resultSet.getTimestamp("approved_at");
    return new ModelVersion(
        resultSet.getString("model_version_id"),
        resultSet.getString("tenant_id"),
        resultSet.getString("model_name"),
        resultSet.getString("semantic_version"),
        Arrays.stream(resultSet.getString("advisory_types").split(",")).filter(value -> !value.isBlank()).map(AdvisoryType::valueOf).toList(),
        AllowedUse.valueOf(resultSet.getString("allowed_use")),
        ModelStatus.valueOf(resultSet.getString("status")),
        resultSet.getString("artifact_uri"),
        resultSet.getString("artifact_checksum"),
        resultSet.getString("feature_schema_version"),
        resultSet.getString("created_by"),
        instant(resultSet.getTimestamp("created_at")),
        resultSet.getString("approved_by"),
        approvedAt == null ? null : approvedAt.toInstant(),
        resultSet.getString("retired_at"),
        evidence,
        Map.of(),
        resultSet.getInt("version"));
  }

  private List<ModelEvidence> readEvidence(Connection activeConnection, String modelVersionId) throws SQLException {
    try (PreparedStatement statement =
        activeConnection.prepareStatement("select evidence_type, uri_or_payload, metric_json, review_status from ml_model_governance_evidence where model_version_id = ?")) {
      statement.setString(1, modelVersionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        java.util.ArrayList<ModelEvidence> results = new java.util.ArrayList<>();
        while (resultSet.next()) {
          results.add(
              new ModelEvidence(
                  resultSet.getString("evidence_type"),
                  resultSet.getString("uri_or_payload"),
                  Map.of("serialized", resultSet.getString("metric_json")),
                  resultSet.getString("review_status")));
        }
        return List.copyOf(results);
      }
    }
  }

  private Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private Timestamp retiredAt(String retiredAt) {
    return retiredAt == null || retiredAt.isBlank() ? null : Timestamp.from(Instant.parse(retiredAt));
  }

  private <T> T withConnection(SqlWork<T> work) {
    if (connection != null) {
      try {
        return work.execute(connection);
      } catch (SQLException ex) {
        throw new IllegalStateException("Unable to access model version governance repository", ex);
      }
    }
    try (Connection activeConnection = dataSource.getConnection()) {
      return work.execute(activeConnection);
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to access model version governance repository", ex);
    }
  }

  private String serializeMap(Map<String, String> values) {
    return values.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(";"));
  }

  private Map<String, String> deserializeMap(String value) {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    Map<String, String> result = new HashMap<>();
    for (String part : value.split(";")) {
      int separator = part.indexOf('=');
      if (separator > 0) {
        result.put(part.substring(0, separator), part.substring(separator + 1));
      }
    }
    return Map.copyOf(result);
  }

  private interface SqlWork<T> {
    T execute(Connection connection) throws SQLException;
  }
}

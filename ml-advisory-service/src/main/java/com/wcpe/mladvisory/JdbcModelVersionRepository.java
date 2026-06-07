package com.wcpe.mladvisory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class JdbcModelVersionRepository implements ModelVersionRepository {
  private final Connection connection;

  JdbcModelVersionRepository(Connection connection) {
    this.connection = connection;
  }

  @Override
  public void save(ModelVersion version) {
    try {
      try (PreparedStatement statement =
          connection.prepareStatement(
              "insert into ml_model_versions (model_version_id, tenant_id, model_name, semantic_version, advisory_types, allowed_use, status, artifact_uri, artifact_checksum, feature_schema_version, created_by, created_at, approved_by, approved_at, retired_at, version, lineage_json) values (?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                  + "on conflict (model_version_id) do update set status = excluded.status, approved_by = excluded.approved_by, approved_at = excluded.approved_at, retired_at = excluded.retired_at, version = excluded.version, lineage_json = excluded.lineage_json")) {
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
        statement.setString(15, version.retiredAt());
        statement.setInt(16, version.version());
        statement.setString(17, version.lineageRefs().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().collect(Collectors.joining(";")));
        statement.executeUpdate();
      }
      try (PreparedStatement deleteEvidence = connection.prepareStatement("delete from ml_model_governance_evidence where model_version_id = ?")) {
        deleteEvidence.setString(1, version.modelVersionId());
        deleteEvidence.executeUpdate();
      }
      for (ModelEvidence evidence : version.evidence()) {
        try (PreparedStatement evidenceStatement =
            connection.prepareStatement(
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
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to persist model version governance state", ex);
    }
  }

  @Override
  public Optional<ModelVersion> find(String tenantId, String modelVersionId) {
    try (PreparedStatement statement = connection.prepareStatement("select * from ml_model_versions where tenant_id = ?::uuid and model_version_id = ?")) {
      statement.setString(1, tenantId);
      statement.setString(2, modelVersionId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(readVersion(resultSet, readEvidence(modelVersionId)));
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to read model version governance state", ex);
    }
  }

  @Override
  public boolean existsByTenantModelAndSemanticVersion(String tenantId, String modelName, String semanticVersion) {
    try (PreparedStatement statement =
        connection.prepareStatement("select 1 from ml_model_versions where tenant_id = ?::uuid and lower(model_name) = lower(?) and lower(semantic_version) = lower(?)")) {
      statement.setString(1, tenantId);
      statement.setString(2, modelName);
      statement.setString(3, semanticVersion);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to check model version uniqueness", ex);
    }
  }

  @Override
  public List<ModelVersion> list(String tenantId, ModelStatus status, AdvisoryType advisoryType) {
    try (PreparedStatement statement = connection.prepareStatement("select * from ml_model_versions where tenant_id = ?::uuid order by created_at desc")) {
      statement.setString(1, tenantId);
      try (ResultSet resultSet = statement.executeQuery()) {
        java.util.ArrayList<ModelVersion> results = new java.util.ArrayList<>();
        while (resultSet.next()) {
          ModelVersion version = readVersion(resultSet, readEvidence(resultSet.getString("model_version_id")));
          if ((status == null || version.status() == status) && (advisoryType == null || version.advisoryTypes().contains(advisoryType))) {
            results.add(version);
          }
        }
        return List.copyOf(results);
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("Unable to list model version governance state", ex);
    }
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

  private List<ModelEvidence> readEvidence(String modelVersionId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("select evidence_type, uri_or_payload, metric_json, review_status from ml_model_governance_evidence where model_version_id = ?")) {
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
}

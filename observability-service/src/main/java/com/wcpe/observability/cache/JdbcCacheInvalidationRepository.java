package com.wcpe.observability.cache;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcCacheInvalidationRepository implements CacheInvalidationRepository {
  private final DataSource dataSource;

  public JdbcCacheInvalidationRepository(DataSource dataSource) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
  }

  @Override
  public Optional<CacheInvalidationRequest> findByTenantIdAndIdempotencyKey(
      UUID tenantId,
      String idempotencyKey) {
    return findOne("tenant_id = ? and idempotency_key = ?", statement -> {
      statement.setObject(1, Objects.requireNonNull(tenantId, "tenantId is required"));
      statement.setString(2, SafeCacheText.requireSafeToken(idempotencyKey, "idempotencyKey", 160));
    });
  }

  @Override
  public Optional<CacheInvalidationRequest> findByTenantIdSourceEventAndNamespace(
      UUID tenantId,
      String sourceEventId,
      TenantCacheNamespace namespace) {
    return findOne("tenant_id = ? and source_event_id = ? and namespace = ?", statement -> {
      statement.setObject(1, Objects.requireNonNull(tenantId, "tenantId is required"));
      statement.setString(2, SafeCacheText.requireSafeToken(sourceEventId, "sourceEventId", 120));
      statement.setString(3, Objects.requireNonNull(namespace, "namespace is required").value());
    });
  }

  @Override
  public void save(CacheInvalidationRequest request) {
    Objects.requireNonNull(request, "request is required");
    String sql = """
        insert into cache_invalidation_request (
          id, tenant_id, namespace, scope_type, scope_ref, source_event_id, source_event_type,
          version_graph_jsonb, idempotency_key, status, attempt_count, last_error_code,
          requested_by, operator_reason, correlation_id, created_at, completed_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (id) do update set
          status = excluded.status,
          attempt_count = excluded.attempt_count,
          last_error_code = excluded.last_error_code,
          operator_reason = excluded.operator_reason,
          completed_at = excluded.completed_at
        """;
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, request.id());
      statement.setObject(2, request.tenantId());
      statement.setString(3, request.namespace().value());
      statement.setString(4, request.scopeType().name());
      statement.setString(5, request.scopeRef());
      statement.setString(6, request.sourceEventId());
      statement.setString(7, request.sourceEventType());
      statement.setString(8, "{\"digest\":\"" + request.versionGraphDigest() + "\"}");
      statement.setString(9, request.idempotencyKey());
      statement.setString(10, request.status().name());
      statement.setInt(11, request.attemptCount());
      statement.setString(12, request.lastErrorCode());
      statement.setString(13, request.requestedBy());
      statement.setString(14, request.operatorReason());
      statement.setString(15, request.correlationId());
      statement.setTimestamp(16, Timestamp.from(request.createdAt()));
      statement.setTimestamp(17, request.completedAt() == null ? null : Timestamp.from(request.completedAt()));
      statement.executeUpdate();
    } catch (SQLException ex) {
      throw new IllegalStateException("CACHE_INVALIDATION_PERSISTENCE_FAILED", ex);
    }
  }

  private Optional<CacheInvalidationRequest> findOne(String predicate, SqlBinder binder) {
    String sql = """
        select id, tenant_id, namespace, scope_type, scope_ref, source_event_id, source_event_type,
          version_graph_jsonb ->> 'digest' as version_graph_digest, idempotency_key, status,
          attempt_count, last_error_code, requested_by, operator_reason, correlation_id,
          created_at, completed_at
        from cache_invalidation_request
        where %s
        """.formatted(predicate);
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      binder.bind(statement);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          return Optional.empty();
        }
        return Optional.of(map(resultSet));
      }
    } catch (SQLException ex) {
      throw new IllegalStateException("CACHE_INVALIDATION_LOOKUP_FAILED", ex);
    }
  }

  private static CacheInvalidationRequest map(ResultSet resultSet) throws SQLException {
    Timestamp completedAt = resultSet.getTimestamp("completed_at");
    String versionGraphDigest = resultSet.getString("version_graph_digest");
    if (versionGraphDigest == null || versionGraphDigest.isBlank()) {
      versionGraphDigest = "persisted-version-graph-digest-unavailable";
    }
    return new CacheInvalidationRequest(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("tenant_id", UUID.class),
        new TenantCacheNamespace(resultSet.getString("namespace")),
        CacheInvalidationScopeType.valueOf(resultSet.getString("scope_type")),
        resultSet.getString("scope_ref"),
        resultSet.getString("source_event_id"),
        resultSet.getString("source_event_type"),
        versionGraphDigest,
        resultSet.getString("idempotency_key"),
        CacheInvalidationStatus.valueOf(resultSet.getString("status")),
        resultSet.getInt("attempt_count"),
        resultSet.getString("last_error_code"),
        resultSet.getString("requested_by"),
        resultSet.getString("operator_reason"),
        resultSet.getString("correlation_id"),
        resultSet.getTimestamp("created_at").toInstant(),
        completedAt == null ? null : completedAt.toInstant(),
        List.of("loaded from PostgreSQL cache_invalidation_request"));
  }

  @FunctionalInterface
  private interface SqlBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}

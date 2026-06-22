package com.wcpe.quote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public class JdbcQuoteJobRepository implements QuoteJobRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcQuoteJobRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<QuoteJob> findById(UUID tenantId, UUID jobId) {
        return findJob("tenant_id = ? and job_id = ?", statement -> {
            statement.setObject(1, tenantId);
            statement.setObject(2, jobId);
        });
    }

    @Override
    public Optional<QuoteJob> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
        return findJob("tenant_id = ? and idempotency_key = ?", statement -> {
            statement.setObject(1, tenantId);
            statement.setString(2, idempotencyKey);
        });
    }

    @Override
    public QuoteJob save(QuoteJob job) {
        String sql = "insert into quote_job (tenant_id, job_id, status, request_payload, request_hash, quote_id, "
            + "failure_code, failure_detail, progress, attempt_count, max_attempts, idempotency_key, created_by, "
            + "created_at, updated_at, expires_at, correlation_id, version) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "on conflict (tenant_id, job_id) do update set status = excluded.status, quote_id = excluded.quote_id, "
            + "failure_code = excluded.failure_code, failure_detail = excluded.failure_detail, progress = excluded.progress, "
            + "attempt_count = excluded.attempt_count, updated_at = excluded.updated_at, version = excluded.version";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, job.tenantId());
            statement.setObject(2, job.jobId());
            statement.setString(3, job.status().name());
            statement.setObject(4, JdbcJson.jsonb(objectMapper, job.requestPayload()));
            statement.setString(5, job.requestHash());
            statement.setObject(6, job.quoteId());
            statement.setString(7, job.failureCode());
            statement.setString(8, job.failureDetail());
            statement.setObject(9, JdbcJson.jsonb(objectMapper, job.progress()));
            statement.setInt(10, job.attemptCount());
            statement.setInt(11, job.maxAttempts());
            statement.setString(12, job.idempotencyKey());
            statement.setString(13, job.createdBy());
            statement.setTimestamp(14, Timestamp.from(job.createdAt()));
            statement.setTimestamp(15, Timestamp.from(job.updatedAt()));
            statement.setTimestamp(16, Timestamp.from(job.expiresAt()));
            statement.setString(17, job.correlationId());
            statement.setInt(18, job.version());
            statement.executeUpdate();
            return job;
        } catch (SQLException ex) {
            throw persistenceFailure("QUOTE_JOB_PERSISTENCE_SAVE_FAILED", ex);
        }
    }

    private Optional<QuoteJob> findJob(String predicate, SqlBinder binder) {
        String sql = "select tenant_id, job_id, status, request_payload, request_hash, quote_id, failure_code, "
            + "failure_detail, progress, attempt_count, max_attempts, idempotency_key, created_by, created_at, "
            + "updated_at, expires_at, correlation_id, version from quote_job where " + predicate;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapJob(resultSet));
            }
        } catch (SQLException ex) {
            throw persistenceFailure("QUOTE_JOB_PERSISTENCE_FIND_FAILED", ex);
        }
    }

    private QuoteJob mapJob(ResultSet resultSet) throws SQLException {
        return new QuoteJob(
            resultSet.getObject("tenant_id", UUID.class),
            resultSet.getObject("job_id", UUID.class),
            QuoteJobStatus.valueOf(resultSet.getString("status")),
            JdbcJson.read(objectMapper, resultSet.getString("request_payload"), STRING_MAP),
            resultSet.getString("request_hash"),
            resultSet.getObject("quote_id", UUID.class),
            resultSet.getString("failure_code"),
            resultSet.getString("failure_detail"),
            JdbcJson.read(objectMapper, resultSet.getString("progress"), STRING_MAP),
            resultSet.getInt("attempt_count"),
            resultSet.getInt("max_attempts"),
            resultSet.getString("idempotency_key"),
            resultSet.getString("created_by"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getTimestamp("expires_at").toInstant(),
            resultSet.getString("correlation_id"),
            resultSet.getInt("version")
        );
    }

    private static IllegalStateException persistenceFailure(String code, SQLException ex) {
        return new IllegalStateException(code + ": PostgreSQL quote job persistence failed closed instead of using in-memory source-of-truth", ex);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}

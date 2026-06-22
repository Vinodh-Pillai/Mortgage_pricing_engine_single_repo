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

public class JdbcQuoteSnapshotRepository implements QuoteSnapshotRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcQuoteSnapshotRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<QuoteSnapshot> findByQuoteId(UUID tenantId, UUID quoteId) {
        String sql = "select tenant_id, snapshot_id, quote_id, quote_version, manifest_version, canonical_request, "
            + "canonical_response, input_version_set, output_digest, replay_hash, evidence_refs, redaction_profile, "
            + "created_at, retention_until, audit_ref, correlation_id from quote_snapshot where tenant_id = ? and quote_id = ?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, tenantId);
            statement.setObject(2, quoteId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSnapshot(resultSet));
            }
        } catch (SQLException ex) {
            throw persistenceFailure("QUOTE_SNAPSHOT_PERSISTENCE_FIND_FAILED", ex);
        }
    }

    @Override
    public QuoteSnapshot saveNew(QuoteSnapshot snapshot) {
        String sql = "insert into quote_snapshot (tenant_id, snapshot_id, quote_id, quote_version, manifest_version, "
            + "canonical_request, canonical_response, input_version_set, output_digest, replay_hash, evidence_refs, "
            + "redaction_profile, created_at, retention_until, audit_ref, correlation_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, snapshot.tenantId());
            statement.setObject(2, snapshot.snapshotId());
            statement.setObject(3, snapshot.quoteId());
            statement.setInt(4, snapshot.quoteVersion());
            statement.setString(5, snapshot.manifestVersion());
            statement.setObject(6, JdbcJson.jsonb(objectMapper, snapshot.canonicalRequest()));
            statement.setObject(7, JdbcJson.jsonb(objectMapper, snapshot.canonicalResponse()));
            statement.setObject(8, JdbcJson.jsonb(objectMapper, snapshot.inputVersionSet()));
            statement.setString(9, snapshot.outputDigest());
            statement.setString(10, snapshot.replayHash());
            statement.setObject(11, JdbcJson.jsonb(objectMapper, snapshot.evidenceRefs()));
            statement.setString(12, snapshot.redactionProfile());
            statement.setTimestamp(13, Timestamp.from(snapshot.createdAt()));
            statement.setTimestamp(14, Timestamp.from(snapshot.retentionUntil()));
            statement.setString(15, snapshot.auditRef());
            statement.setString(16, snapshot.correlationId());
            statement.executeUpdate();
            return snapshot;
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) {
                throw new QuoteCreateException("QUOTE_SNAPSHOT_ALREADY_EXISTS", "Quote snapshot is immutable and already exists");
            }
            throw persistenceFailure("QUOTE_SNAPSHOT_PERSISTENCE_SAVE_FAILED", ex);
        }
    }

    private QuoteSnapshot mapSnapshot(ResultSet resultSet) throws SQLException {
        return new QuoteSnapshot(
            resultSet.getObject("tenant_id", UUID.class),
            resultSet.getObject("snapshot_id", UUID.class),
            resultSet.getObject("quote_id", UUID.class),
            resultSet.getInt("quote_version"),
            resultSet.getString("manifest_version"),
            JdbcJson.read(objectMapper, resultSet.getString("canonical_request"), STRING_MAP),
            JdbcJson.read(objectMapper, resultSet.getString("canonical_response"), STRING_MAP),
            JdbcJson.read(objectMapper, resultSet.getString("input_version_set"), STRING_MAP),
            resultSet.getString("output_digest"),
            resultSet.getString("replay_hash"),
            JdbcJson.read(objectMapper, resultSet.getString("evidence_refs"), STRING_MAP),
            resultSet.getString("redaction_profile"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("retention_until").toInstant(),
            resultSet.getString("audit_ref"),
            resultSet.getString("correlation_id")
        );
    }

    private static IllegalStateException persistenceFailure(String code, SQLException ex) {
        return new IllegalStateException(code + ": PostgreSQL quote snapshot persistence failed closed instead of using in-memory source-of-truth", ex);
    }
}

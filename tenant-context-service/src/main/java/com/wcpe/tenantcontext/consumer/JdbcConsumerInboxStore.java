package com.wcpe.tenantcontext.consumer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcConsumerInboxStore implements ConsumerInboxStore {
    private final JdbcTemplate jdbc;

    public JdbcConsumerInboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<ConsumerInboxRecord> find(String tenantId, String consumerName, UUID eventId) {
        return jdbc.query("SELECT * FROM tenant.consumer_inbox_record WHERE tenant_id = ? AND consumer_name = ? AND event_id = ?", this::map,
            tenantId, consumerName, eventId).stream().findFirst();
    }

    @Override
    public ConsumerInboxRecord save(ConsumerInboxRecord record) {
        jdbc.update("""
            INSERT INTO tenant.consumer_inbox_record (inbox_id, tenant_id, consumer_name, event_id, event_name, schema_ref, schema_version,
              payload_hash, status, attempt_count, first_seen_at, last_attempt_at, processed_at, result_payload_hash,
              correlation_id, causation_id, last_error_code, last_error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (inbox_id) DO UPDATE SET status = EXCLUDED.status, attempt_count = EXCLUDED.attempt_count,
              last_attempt_at = EXCLUDED.last_attempt_at, processed_at = EXCLUDED.processed_at,
              result_payload_hash = EXCLUDED.result_payload_hash, last_error_code = EXCLUDED.last_error_code,
              last_error_message = EXCLUDED.last_error_message
            """,
            record.inboxId(), record.tenantId(), record.consumerName(), record.eventId(), record.eventName(), record.schemaRef(),
            record.schemaVersion(), record.payloadHash(), record.status().name(), record.attemptCount(), ts(record.firstSeenAt()),
            ts(record.lastAttemptAt()), ts(record.processedAt()), record.resultHash(), record.correlationId(), record.causationId(),
            record.lastErrorCode(), record.lastErrorMessage());
        return record;
    }

    @Override
    public List<ConsumerInboxRecord> listByTenant(String tenantId) {
        return jdbc.query("SELECT * FROM tenant.consumer_inbox_record WHERE tenant_id = ? ORDER BY first_seen_at", this::map, tenantId);
    }

    private ConsumerInboxRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new ConsumerInboxRecord((UUID) rs.getObject("inbox_id"), rs.getString("tenant_id"), rs.getString("consumer_name"),
            (UUID) rs.getObject("event_id"), rs.getString("event_name"), rs.getString("schema_ref"), rs.getInt("schema_version"),
            rs.getString("payload_hash"), rs.getString("correlation_id"), rs.getString("causation_id"),
            ProcessingStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"), instant(rs.getTimestamp("first_seen_at")),
            instant(rs.getTimestamp("last_attempt_at")), instant(rs.getTimestamp("processed_at")), rs.getString("result_payload_hash"),
            rs.getString("last_error_code"), rs.getString("last_error_message"));
    }

    private static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}

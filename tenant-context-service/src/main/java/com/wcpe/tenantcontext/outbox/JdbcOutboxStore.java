package com.wcpe.tenantcontext.outbox;

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
public class JdbcOutboxStore implements OutboxStore {
    private final JdbcTemplate jdbc;

    public JdbcOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OutboxEvent> findByEventId(UUID eventId) {
        return queryOne("SELECT * FROM tenant.outbox_event WHERE event_id = ?", eventId);
    }

    @Override
    public Optional<OutboxEvent> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (tenantId == null || idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return queryOne("SELECT * FROM tenant.outbox_event WHERE tenant_id = ? AND idempotency_key = ?", tenantId, idempotencyKey);
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        jdbc.update("""
            INSERT INTO tenant.outbox_event (event_id, tenant_id, aggregate_type, aggregate_id, topic, partition_key, schema_ref, event_name,
              event_version, envelope_json, payload_hash, status, attempt_count, next_attempt_at, created_at, updated_at, published_at,
              actor_id, correlation_id, causation_id, idempotency_key, error_code, error_message, publisher_ref)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO UPDATE SET status = EXCLUDED.status, attempt_count = EXCLUDED.attempt_count,
              next_attempt_at = EXCLUDED.next_attempt_at, updated_at = EXCLUDED.updated_at, published_at = EXCLUDED.published_at,
              error_code = EXCLUDED.error_code, error_message = EXCLUDED.error_message, publisher_ref = EXCLUDED.publisher_ref
            """,
            event.eventId(), event.tenantId(), event.aggregateType(), event.aggregateId(), event.topic(), event.partitionKey(), event.schemaRef(),
            event.eventName(), event.eventVersion(), event.envelopeJson(), event.payloadHash(), event.status().name(), event.attemptCount(),
            ts(event.nextAttemptAt()), ts(event.createdAt()), ts(event.updatedAt()), ts(event.publishedAt()), event.actorId(), event.correlationId(),
            event.causationId(), event.idempotencyKey(), event.lastErrorCode(), event.lastErrorMessage(), event.quarantineReason());
        return event;
    }

    @Override
    public List<OutboxEvent> dueForPublish(String tenantId) {
        return jdbc.query("SELECT * FROM tenant.outbox_event WHERE tenant_id = ? AND status IN ('PENDING','RETRY_WAIT') ORDER BY created_at", this::map, tenantId);
    }

    @Override
    public List<OutboxEvent> listByTenant(String tenantId) {
        return jdbc.query("SELECT * FROM tenant.outbox_event WHERE tenant_id = ? ORDER BY created_at", this::map, tenantId);
    }

    private Optional<OutboxEvent> queryOne(String sql, Object... args) {
        return jdbc.query(sql, this::map, args).stream().findFirst();
    }

    private OutboxEvent map(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(rs.getString("tenant_id"), (UUID) rs.getObject("event_id"), rs.getString("aggregate_type"),
            rs.getString("aggregate_id"), rs.getString("topic"), rs.getString("partition_key"), rs.getString("schema_ref"),
            rs.getString("event_name"), rs.getInt("event_version"), rs.getString("envelope_json"), rs.getString("payload_hash"),
            OutboxStatus.valueOf(rs.getString("status")), rs.getInt("attempt_count"), instant(rs.getTimestamp("next_attempt_at")),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("published_at")),
            rs.getString("actor_id"), rs.getString("correlation_id"), rs.getString("causation_id"), rs.getString("idempotency_key"),
            List.of(), rs.getString("error_code"), rs.getString("error_message"), rs.getString("publisher_ref"));
    }

    private static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }
    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}

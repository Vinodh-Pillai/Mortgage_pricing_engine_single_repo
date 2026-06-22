package com.wcpe.tenantcontext.audit;

import com.wcpe.tenantcontext.event.DataClassification;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditLogStore implements AuditLogStore {
    private final JdbcTemplate jdbc;

    public JdbcAuditLogStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuditRecord> findByAuditId(UUID auditId) {
        return queryOne("SELECT * FROM tenant.audit_log_record WHERE audit_id = ?", auditId);
    }

    @Override
    public Optional<AuditRecord> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (tenantId == null || idempotencyKey == null || idempotencyKey.isBlank()) return Optional.empty();
        return queryOne("SELECT * FROM tenant.audit_log_record WHERE tenant_id = ? AND idempotency_key = ?", tenantId, idempotencyKey);
    }

    @Override
    public Optional<AuditRecord> latestForTenant(String tenantId) {
        return queryOne("SELECT * FROM tenant.audit_log_record WHERE tenant_id = ? ORDER BY occurred_at DESC, audit_id DESC LIMIT 1", tenantId);
    }

    @Override
    public AuditRecord append(AuditRecord record) {
        try {
            jdbc.update("""
                INSERT INTO tenant.audit_log_record (audit_id, tenant_id, occurred_at, actor_id, actor_type, action, entity_type, entity_id,
                  entity_version, outcome, correlation_id, causation_id, event_id, idempotency_key, before_ref, after_ref,
                  change_summary_json, data_classification, record_hash, previous_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.auditId(), record.tenantId(), ts(record.occurredAt()), record.actorId(), record.actorType(), record.action(),
                record.entityType(), record.entityId(), record.entityVersion(), record.outcome(), record.correlationId(), record.causationId(),
                record.eventId(), record.idempotencyKey(), record.beforeRef(), record.afterRef(), record.changeSummaryJson(),
                record.dataClassification().name(), record.recordHash(), record.previousHash());
            return record;
        } catch (DuplicateKeyException duplicate) {
            throw new AuditException("AUDIT_RECORD_IMMUTABLE", "audit record or idempotency key already exists");
        }
    }

    @Override
    public List<AuditRecord> listByTenant(String tenantId) {
        return jdbc.query("SELECT * FROM tenant.audit_log_record WHERE tenant_id = ? ORDER BY occurred_at, audit_id", this::map, tenantId);
    }

    private Optional<AuditRecord> queryOne(String sql, Object... args) {
        List<AuditRecord> rows = jdbc.query(sql, this::map, args);
        return rows.stream().findFirst();
    }

    private AuditRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new AuditRecord((UUID) rs.getObject("audit_id"), rs.getString("tenant_id"), instant(rs.getTimestamp("occurred_at")),
            rs.getString("actor_id"), rs.getString("actor_type"), rs.getString("action"), rs.getString("entity_type"),
            rs.getString("entity_id"), rs.getString("entity_version"), rs.getString("outcome"), rs.getString("correlation_id"),
            rs.getString("causation_id"), rs.getString("event_id"), rs.getString("idempotency_key"), rs.getString("before_ref"),
            rs.getString("after_ref"), rs.getString("change_summary_json"), DataClassification.valueOf(rs.getString("data_classification")),
            rs.getString("record_hash"), rs.getString("previous_hash"));
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

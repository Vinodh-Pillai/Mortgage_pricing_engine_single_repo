package com.wcpe.tenantcontext.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TestOnlyInMemoryAuditLogStore implements AuditLogStore {
    private final Map<UUID, AuditRecord> recordsById = new HashMap<>();
    private final Map<String, UUID> idempotencyByTenantAndKey = new HashMap<>();

    @Override
    public synchronized Optional<AuditRecord> findByAuditId(UUID auditId) {
        return Optional.ofNullable(recordsById.get(auditId));
    }

    @Override
    public synchronized Optional<AuditRecord> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (tenantId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        UUID auditId = idempotencyByTenantAndKey.get(tenantId + "|" + idempotencyKey);
        return auditId == null ? Optional.empty() : Optional.ofNullable(recordsById.get(auditId));
    }

    @Override
    public synchronized Optional<AuditRecord> latestForTenant(String tenantId) {
        return listByTenant(tenantId).stream().reduce((ignored, latest) -> latest);
    }

    @Override
    public synchronized AuditRecord append(AuditRecord record) {
        AuditRecord existing = recordsById.get(record.auditId());
        if (existing != null) {
            throw new AuditException("AUDIT_RECORD_IMMUTABLE", "audit record already exists; corrections must create a new audit record");
        }
        recordsById.put(record.auditId(), record);
        idempotencyByTenantAndKey.put(record.tenantId() + "|" + record.idempotencyKey(), record.auditId());
        return record;
    }

    @Override
    public synchronized List<AuditRecord> listByTenant(String tenantId) {
        return new ArrayList<>(recordsById.values()).stream()
            .filter(record -> record.tenantId().equals(tenantId))
            .sorted(Comparator.comparing(AuditRecord::occurredAt).thenComparing(AuditRecord::auditId))
            .toList();
    }
}

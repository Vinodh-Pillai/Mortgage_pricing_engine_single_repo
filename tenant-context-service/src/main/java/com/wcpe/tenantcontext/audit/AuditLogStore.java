package com.wcpe.tenantcontext.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditLogStore {
    Optional<AuditRecord> findByAuditId(UUID auditId);
    Optional<AuditRecord> findByIdempotencyKey(String tenantId, String idempotencyKey);
    Optional<AuditRecord> latestForTenant(String tenantId);
    AuditRecord append(AuditRecord record);
    List<AuditRecord> listByTenant(String tenantId);
}

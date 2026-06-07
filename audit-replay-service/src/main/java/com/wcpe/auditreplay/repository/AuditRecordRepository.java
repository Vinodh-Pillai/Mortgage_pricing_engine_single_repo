package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.AuditRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

    Optional<AuditRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<AuditRecord> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);

    Optional<AuditRecord> findTopByTenantIdOrderByOccurredAtDescCreatedAtDesc(UUID tenantId);
}

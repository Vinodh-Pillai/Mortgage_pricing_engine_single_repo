package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.RetentionPurgeRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionPurgeRunRepository extends JpaRepository<RetentionPurgeRun, UUID> {
    Optional<RetentionPurgeRun> findByTenantIdAndRunId(UUID tenantId, UUID runId);
    Optional<RetentionPurgeRun> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}

package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.LockReplayRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockReplayRunRepository extends JpaRepository<LockReplayRun, UUID> {

    Optional<LockReplayRun> findByTenantIdAndRunId(UUID tenantId, UUID runId);

    Optional<LockReplayRun> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}

package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.QuoteReplayRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteReplayRunRepository extends JpaRepository<QuoteReplayRun, UUID> {

    Optional<QuoteReplayRun> findByTenantIdAndRunId(UUID tenantId, UUID runId);

    Optional<QuoteReplayRun> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}

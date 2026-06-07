package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.QuoteReplayArtifact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteReplayArtifactRepository extends JpaRepository<QuoteReplayArtifact, UUID> {

    Optional<QuoteReplayArtifact> findByTenantIdAndRunId(UUID tenantId, UUID runId);
}

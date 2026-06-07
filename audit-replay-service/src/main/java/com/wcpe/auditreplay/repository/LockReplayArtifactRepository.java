package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.LockReplayArtifact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockReplayArtifactRepository extends JpaRepository<LockReplayArtifact, UUID> {

    Optional<LockReplayArtifact> findByTenantIdAndRunId(UUID tenantId, UUID runId);
}

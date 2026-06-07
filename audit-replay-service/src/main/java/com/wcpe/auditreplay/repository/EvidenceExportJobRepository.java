package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.EvidenceExportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceExportJobRepository extends JpaRepository<EvidenceExportJob, UUID> {

    Optional<EvidenceExportJob> findByTenantIdAndExportId(UUID tenantId, UUID exportId);

    Optional<EvidenceExportJob> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}

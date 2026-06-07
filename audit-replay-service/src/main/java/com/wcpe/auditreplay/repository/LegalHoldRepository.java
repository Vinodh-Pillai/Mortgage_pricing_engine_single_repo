package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.LegalHold;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalHoldRepository extends JpaRepository<LegalHold, UUID> {
    Optional<LegalHold> findByTenantIdAndHoldId(UUID tenantId, UUID holdId);
    Optional<LegalHold> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
}

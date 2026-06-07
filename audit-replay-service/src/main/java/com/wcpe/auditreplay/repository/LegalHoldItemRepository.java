package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.LegalHoldItem;
import com.wcpe.auditreplay.domain.LegalHoldStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalHoldItemRepository extends JpaRepository<LegalHoldItem, UUID> {
    List<LegalHoldItem> findAllByTenantIdAndHoldId(UUID tenantId, UUID holdId);
    long countByTenantIdAndHoldIdAndStatus(UUID tenantId, UUID holdId, LegalHoldStatus status);
    boolean existsByTenantIdAndAuditRecordIdAndStatus(UUID tenantId, UUID auditRecordId, LegalHoldStatus status);
}

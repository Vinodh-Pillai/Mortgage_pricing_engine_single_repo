package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.AuditRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID>, JpaSpecificationExecutor<AuditRecord> {

    Optional<AuditRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AuditRecord> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    Optional<AuditRecord> findByTenantIdAndRequestId(UUID tenantId, UUID requestId);

    Optional<AuditRecord> findTopByTenantIdAndSubjectTypeAndSubjectIdOrderByOccurredAtDescCreatedAtDesc(
            UUID tenantId, String subjectType, String subjectId);

    Optional<AuditRecord> findTopByTenantIdOrderByOccurredAtDescCreatedAtDesc(UUID tenantId);

    Optional<AuditRecord> findTopByTenantIdAndOccurredAtBeforeOrderByOccurredAtDescCreatedAtDescIdDesc(
            UUID tenantId, Instant occurredAt);

    List<AuditRecord> findAllByTenantIdAndOccurredAtBetweenOrderByOccurredAtAscCreatedAtAscIdAsc(
            UUID tenantId, Instant from, Instant to);

    List<AuditRecord> findAllByTenantIdAndRetentionUntilBeforeOrderByRetentionUntilAscCreatedAtAscIdAsc(
            UUID tenantId, LocalDate cutoff);
}

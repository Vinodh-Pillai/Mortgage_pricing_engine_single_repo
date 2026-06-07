package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.OutboxEvent;
import com.wcpe.auditreplay.domain.OutboxEventStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID>, OutboxEventRepositoryCustom {

    List<OutboxEvent> findByTenantIdAndStatusOrderByNextAttemptAtAsc(
            UUID tenantId, OutboxEventStatus status);

    List<OutboxEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<OutboxEvent> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<OutboxEvent> findByTenantIdAndEventKeyAndEventVersion(UUID tenantId, String eventKey, Integer eventVersion);

    List<OutboxEvent> findByTenantIdAndStatusOrderByNextAttemptAtAsc(
            UUID tenantId, OutboxEventStatus status, Pageable pageable);

    boolean existsByTenantIdAndEventKeyAndEventVersion(UUID tenantId, String eventKey, Integer eventVersion);
}

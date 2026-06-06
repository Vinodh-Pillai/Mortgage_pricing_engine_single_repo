package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.OutboxEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OutboxEventRepositoryCustomImpl implements OutboxEventRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public List<OutboxEvent> findPublishableWithSkipLocked(int limit) {
        return entityManager
                .createNativeQuery(
                        """
                        SELECT * FROM audit_outbox_events
                        WHERE status = 'PENDING'
                           OR (status = 'FAILED' AND next_attempt_at <= NOW())
                        ORDER BY next_attempt_at ASC
                        LIMIT :limit FOR UPDATE SKIP LOCKED
                        """,
                        OutboxEvent.class)
                .setParameter("limit", limit)
                .getResultList();
    }
}

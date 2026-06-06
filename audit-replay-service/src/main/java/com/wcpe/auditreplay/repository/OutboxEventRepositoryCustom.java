package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.OutboxEvent;
import java.util.List;

public interface OutboxEventRepositoryCustom {

    List<OutboxEvent> findPublishableWithSkipLocked(int limit);
}

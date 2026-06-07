package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.AuditSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditSnapshotRepository extends JpaRepository<AuditSnapshot, UUID> {
}

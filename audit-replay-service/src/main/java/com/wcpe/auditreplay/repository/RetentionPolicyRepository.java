package com.wcpe.auditreplay.repository;

import com.wcpe.auditreplay.domain.RetentionPolicy;
import com.wcpe.auditreplay.domain.RetentionPolicyStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID> {
    Optional<RetentionPolicy> findByTenantIdAndPolicyId(UUID tenantId, UUID policyId);
    Optional<RetentionPolicy> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);
    List<RetentionPolicy> findAllByTenantIdAndEvidenceTypeAndJurisdictionAndStatus(
            UUID tenantId, String evidenceType, String jurisdiction, RetentionPolicyStatus status);
}

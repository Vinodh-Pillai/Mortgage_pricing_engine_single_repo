package com.wcpe.auditreplay.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.domain.RetentionPolicy;
import com.wcpe.auditreplay.domain.RetentionPolicyStatus;
import com.wcpe.auditreplay.repository.AuditRecordRepository;
import com.wcpe.auditreplay.repository.LegalHoldItemRepository;
import com.wcpe.auditreplay.repository.LegalHoldRepository;
import com.wcpe.auditreplay.repository.RetentionPolicyRepository;
import com.wcpe.auditreplay.repository.RetentionPurgeRunRepository;
import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RetentionPolicyTest {

    @AfterEach
    void clearContext() {
        CorrelationContext.clear();
    }

    @Test
    void rejectsOverlappingActiveVersions() {
        UUID tenantId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        CorrelationContext.set(CorrelationId.of(correlationId.toString()), CausationId.ofNullable(null), RequestId.of("req-retention-policy"));
        RetentionPolicyRepository policies = mock(RetentionPolicyRepository.class);
        RetentionLegalHoldService service = new RetentionLegalHoldService(
                policies,
                mock(LegalHoldRepository.class),
                mock(LegalHoldItemRepository.class),
                mock(RetentionPurgeRunRepository.class),
                mock(AuditRecordRepository.class),
                mock(OutboxRecorder.class),
                new ObjectMapper().findAndRegisterModules());
        RetentionPolicy existing = RetentionPolicy.publish(
                UUID.randomUUID(), tenantId, "AUDIT_RECORD", "US-FED", null, null, 2555,
                LocalDate.parse("2026-01-01"), null, "creator", "approver", correlationId, "existing-idem");
        when(policies.findByTenantIdAndIdempotencyKey(tenantId, "new-idem")).thenReturn(Optional.empty());
        when(policies.findAllByTenantIdAndEvidenceTypeAndJurisdictionAndStatus(
                tenantId, "AUDIT_RECORD", "US-FED", RetentionPolicyStatus.PUBLISHED)).thenReturn(List.of(existing));

        assertThrows(RetentionLegalHoldService.PolicyNotSatisfiedException.class, () -> service.publishPolicy(
                new RetentionLegalHoldService.RetentionPolicyCommand(
                        tenantId, UUID.randomUUID(), "AUDIT_RECORD", "US-FED", null, null, 2555,
                        LocalDate.parse("2026-06-01"), LocalDate.parse("2027-06-01"),
                        "creator-2", "approver-2", correlationId, "new-idem")));
    }
}

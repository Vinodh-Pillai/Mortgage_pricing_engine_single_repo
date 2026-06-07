package com.wcpe.auditreplay.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LegalHoldTest {

    @Test
    void requiresCaseReasonAndApprover() {
        UUID tenantId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        byte[] scope = "{\"auditRecordIds\":[]}".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> LegalHold.apply(
                holdId, tenantId, "", scope, "regulator request", "requester", "approver", correlationId, "idem-1"));
        assertThrows(IllegalArgumentException.class, () -> LegalHold.apply(
                holdId, tenantId, "CASE-123", scope, "", "requester", "approver", correlationId, "idem-2"));
        assertThrows(IllegalArgumentException.class, () -> LegalHold.apply(
                holdId, tenantId, "CASE-123", scope, "regulator request", "same-user", "same-user", correlationId, "idem-3"));
    }
}

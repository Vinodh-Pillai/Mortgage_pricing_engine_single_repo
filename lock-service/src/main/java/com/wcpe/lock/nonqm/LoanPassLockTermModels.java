package com.wcpe.lock.nonqm;

import com.wcpe.lock.LockServiceException;

import java.time.Instant;
import java.util.Map;

public final class LoanPassLockTermModels {
    private LoanPassLockTermModels() {}

    public record LockTermOptionRef(
            String termRef,
            String tenantId,
            String productCode,
            String investorCode,
            String channelCode,
            int lockTermDays,
            String adjustmentRef,
            String floatDownRuleRef,
            Map<String, Object> lockTermPayload,
            String sourceSystem,
            String sourceProvenance,
            String sourcePayloadRef,
            boolean syntheticDevOnly,
            String status,
            Instant effectiveStart,
            Instant effectiveEnd) {
        public LockTermOptionRef {
            requireText(termRef, "termRef");
            requireText(tenantId, "tenantId");
            if (lockTermDays <= 0) throw new LockServiceException("VALIDATION_FAILED", "lockTermDays must be positive");
            requireText(sourceSystem, "sourceSystem");
            requireText(sourceProvenance, "sourceProvenance");
            if ("SYNTHETIC_DEV".equalsIgnoreCase(sourceSystem) && !syntheticDevOnly) {
                throw new LockServiceException("VALIDATION_FAILED", "synthetic dev source must be marked syntheticDevOnly");
            }
            if (effectiveStart != null && effectiveEnd != null && !effectiveEnd.isAfter(effectiveStart)) {
                throw new LockServiceException("VALIDATION_FAILED", "effectiveEnd must be after effectiveStart");
            }
            lockTermPayload = Map.copyOf(lockTermPayload == null ? Map.of() : lockTermPayload);
            status = status == null || status.isBlank() ? "DRAFT" : status.trim().toUpperCase();
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new LockServiceException("VALIDATION_FAILED", field + " is required");
        }
    }
}

package com.wcpe.quote;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record QuoteSnapshot(
    UUID tenantId,
    UUID snapshotId,
    UUID quoteId,
    int quoteVersion,
    String manifestVersion,
    Map<String, String> canonicalRequest,
    Map<String, String> canonicalResponse,
    Map<String, String> inputVersionSet,
    String outputDigest,
    String replayHash,
    Map<String, String> evidenceRefs,
    String redactionProfile,
    Instant createdAt,
    Instant retentionUntil,
    String auditRef,
    String correlationId
) {
    public QuoteSnapshot {
        canonicalRequest = stableMap(canonicalRequest);
        canonicalResponse = stableMap(canonicalResponse);
        inputVersionSet = stableMap(inputVersionSet);
        evidenceRefs = stableMap(evidenceRefs);
    }

    private static Map<String, String> stableMap(Map<String, String> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}

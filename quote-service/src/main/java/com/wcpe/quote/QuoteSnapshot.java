package com.wcpe.quote;

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
    String auditRef,
    String correlationId
) {
    public QuoteSnapshot {
        canonicalRequest = Map.copyOf(canonicalRequest == null ? Map.of() : canonicalRequest);
        canonicalResponse = Map.copyOf(canonicalResponse == null ? Map.of() : canonicalResponse);
        inputVersionSet = Map.copyOf(inputVersionSet == null ? Map.of() : inputVersionSet);
        evidenceRefs = Map.copyOf(evidenceRefs == null ? Map.of() : evidenceRefs);
    }
}

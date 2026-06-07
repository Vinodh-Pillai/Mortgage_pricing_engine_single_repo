package com.wcpe.quote;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record QuoteSnapshotExport(
    UUID tenantId,
    UUID quoteId,
    UUID snapshotId,
    String replayHash,
    String redactionProfile,
    boolean nonRedacted,
    Map<String, String> manifest,
    String auditRef,
    Instant exportedAt
) {
    public QuoteSnapshotExport {
        manifest = Collections.unmodifiableMap(new LinkedHashMap<>(manifest == null ? Map.of() : manifest));
    }
}

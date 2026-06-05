package com.wcpe.quote;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteComparisonExport(
    UUID tenantId,
    UUID exportId,
    UUID quoteId,
    String viewId,
    String viewVersion,
    List<UUID> rowOptionIds,
    String format,
    String redactionProfile,
    String storageRef,
    String createdBy,
    Instant createdAt,
    String idempotencyKey,
    String auditRef
) {
    public QuoteComparisonExport {
        rowOptionIds = List.copyOf(rowOptionIds == null ? List.of() : rowOptionIds);
    }
}

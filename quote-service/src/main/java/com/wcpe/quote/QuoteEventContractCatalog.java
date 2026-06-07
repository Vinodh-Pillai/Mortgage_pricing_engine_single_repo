package com.wcpe.quote;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuoteEventContractCatalog {
    private static final Set<String> REQUIRED_EVENT_TYPES = Set.of(
        "quote.created.v1",
        "quote.ready.v1",
        "quote.no_options.v1",
        "quote.failed.v1",
        "quote.expired.v1",
        "quote.stale_detected.v1",
        "best_execution.ranked.v1",
        "quote.snapshot_created.v1",
        "quote_option.selected.v1",
        "quote_replay.completed.v1"
    );

    private QuoteEventContractCatalog() {
    }

    public static Set<String> requiredEventTypes() {
        return REQUIRED_EVENT_TYPES;
    }

    public static Map<String, Object> schemaFor(String eventType) {
        if (!REQUIRED_EVENT_TYPES.contains(eventType)) {
            throw new QuoteCreateException("QUOTE_EVENT_SCHEMA_UNKNOWN", "Unsupported quote event type: " + eventType);
        }
        return Map.of(
            "eventType", eventType,
            "eventVersion", "1",
            "envelopeFields", List.of(
                "eventId", "eventType", "eventVersion", "tenantId", "aggregateType", "aggregateId",
                "occurredAt", "correlationId", "causationId", "idempotencyKey", "schemaRef", "producer", "piiClassification"
            ),
            "payloadFields", List.of("quoteId", "tenantId", "status", "version", "replayHash"),
            "redaction", "No borrower PII, compensation details, or hidden investor data without an approved contract boundary"
        );
    }
}

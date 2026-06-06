package com.wcpe.adjustment;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record JurisdictionSourceReference(
    String sourceId,
    String sourceType,
    String jurisdiction,
    Instant effectiveAsOf,
    String description
) {
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
        "STATE_LAW",
        "INVESTOR_GUIDE",
        "AGENCY_RULE",
        "INTERNAL_POLICY"
    );

    public JurisdictionSourceReference {
        sourceId = requireText(Objects.requireNonNull(sourceId, "sourceId is required"), "sourceId is required");
        sourceType = requireText(Objects.requireNonNull(sourceType, "sourceType is required"), "sourceType is required");
        Objects.requireNonNull(effectiveAsOf, "effectiveAsOf is required");
        if (!ALLOWED_SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("sourceType is not supported");
        }
        if (jurisdiction != null && !jurisdiction.isBlank()) {
            jurisdiction = requireText(jurisdiction, "jurisdiction is required when configured");
        } else {
            jurisdiction = null;
        }
        if ("STATE_LAW".equals(sourceType)) {
            jurisdiction = requireText(Objects.requireNonNull(jurisdiction,
                "jurisdiction is required when sourceType is STATE_LAW"),
                "jurisdiction is required when sourceType is STATE_LAW");
        }
        if (description != null && !description.isBlank()) {
            description = requireText(description, "description is required when configured");
        } else {
            description = null;
        }
    }

    private static String requireText(String value, String message) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}

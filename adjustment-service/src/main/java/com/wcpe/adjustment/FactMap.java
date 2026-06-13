package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical fact view for all supported adjustment-rule dimensions. */
public final class FactMap {
    public static final Set<String> SUPPORTED_DIMENSIONS = AdjustmentRuleBook.SUPPORTED_CONDITION_DIMENSIONS;

    private final Map<String, Object> facts;

    public FactMap(Map<String, ?> facts) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        if (facts != null) {
            facts.forEach((key, value) -> {
                if (key != null && SUPPORTED_DIMENSIONS.contains(key) && value != null) {
                    normalized.put(key, value);
                }
            });
        }
        this.facts = Map.copyOf(normalized);
    }

    public static FactMap from(AdjustmentCalculationRequest request) {
        Objects.requireNonNull(request, "request is required");
        return new FactMap(request.normalizedFacts());
    }

    public boolean supports(String dimension) {
        return SUPPORTED_DIMENSIONS.contains(dimension);
    }

    public boolean exists(String dimension) {
        Object value = facts.get(dimension);
        return value != null && !String.valueOf(value).isBlank();
    }

    public Object get(String dimension) {
        return facts.get(dimension);
    }

    public String getString(String dimension) {
        Object value = facts.get(dimension);
        return value == null ? null : String.valueOf(value);
    }

    public Optional<BigDecimal> getDecimal(String dimension) {
        Object value = facts.get(dimension);
        if (value == null || String.valueOf(value).isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(String.valueOf(value).trim()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public Map<String, Object> asMap() {
        return facts;
    }
}

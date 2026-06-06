package com.wcpe.adjustment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record JurisdictionKey(StateCode state, CountyFips countyFips, String municipality) {
    public JurisdictionKey {
        Objects.requireNonNull(state, "state is required");
        if (countyFips != null && countyFips.code() == null) {
            countyFips = null;
        }
        if (municipality != null) {
            municipality = requireText(Objects.requireNonNull(municipality, "municipality is required when configured"),
                "municipality is required when configured");
            if (countyFips == null) {
                throw new IllegalArgumentException("countyFips is required when municipality is configured");
            }
        }
    }

    public int specificityRank() {
        if (municipality != null) {
            return 2;
        }
        if (countyFips != null) {
            return 1;
        }
        return 0;
    }

    public boolean isMoreSpecificThan(JurisdictionKey other) {
        Objects.requireNonNull(other, "other is required");
        return specificityRank() > other.specificityRank() && covers(other);
    }

    public String selectorHash() {
        return hashOf(state.code(), countyFips == null ? null : countyFips.code(), municipality);
    }

    private boolean covers(JurisdictionKey other) {
        if (!state.equals(other.state)) {
            return false;
        }
        if (other.countyFips != null && !other.countyFips.equals(countyFips)) {
            return false;
        }
        return other.municipality == null || other.municipality.equals(municipality);
    }

    private static String requireText(String value, String message) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String hashOf(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

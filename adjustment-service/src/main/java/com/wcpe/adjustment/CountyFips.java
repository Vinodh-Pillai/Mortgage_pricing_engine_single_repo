package com.wcpe.adjustment;

import java.util.Objects;

public record CountyFips(String code) {
    public CountyFips {
        if (code != null) {
            code = requireText(Objects.requireNonNull(code, "code is required when configured"),
                "code is required when configured");
            if (!code.matches("\\d{5}")) {
                throw new IllegalArgumentException("code must be exactly 5 digits when configured");
            }
        }
    }

    public static CountyFips of(String code) {
        return new CountyFips(code == null || code.isBlank() ? null : code);
    }

    private static String requireText(String value, String message) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}

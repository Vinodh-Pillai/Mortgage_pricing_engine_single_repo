package com.wcpe.adjustment;

import java.util.Locale;
import java.util.Objects;

public record StateCode(String code) {
    public StateCode {
        code = canonical(code);
        if (!code.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("code must be exactly 2 uppercase letters");
        }
    }

    public static StateCode of(String code) {
        return new StateCode(code);
    }

    public static String canonical(String code) {
        return requireText(Objects.requireNonNull(code, "code is required"), "code is required")
            .toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}

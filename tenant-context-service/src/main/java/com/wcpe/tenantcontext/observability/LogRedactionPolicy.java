package com.wcpe.tenantcontext.observability;

import java.util.Set;

public class LogRedactionPolicy {
    private static final Set<String> EXACT_SENSITIVE_KEYS = Set.of(
        "authorization", "cookie", "setcookie", "jwt", "rawjwt", "password", "secret", "token", "accesstoken",
        "refreshtoken", "apikey", "credential", "ssn", "taxid", "borrowername", "customername", "email", "phone",
        "address"
    );
    public static final String REDACTED = "[REDACTED]";

    public String redact(String key, String value) {
        if (value == null) {
            return "";
        }
        if (isSensitiveKey(key) || looksLikeRawCredential(value)) {
            return REDACTED;
        }
        return value;
    }

    public boolean isSensitiveKey(String key) {
        String normalized = normalize(key);
        return EXACT_SENSITIVE_KEYS.contains(normalized)
            || normalized.contains("authorization")
            || normalized.contains("cookie")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("password")
            || normalized.contains("credential")
            || normalized.contains("borrower")
            || normalized.contains("customer")
            || normalized.contains("person")
            || normalized.contains("pii");
    }

    private boolean looksLikeRawCredential(String value) {
        String normalized = value.trim();
        return normalized.regionMatches(true, 0, "Bearer ", 0, 7)
            || normalized.regionMatches(true, 0, "Basic ", 0, 6)
            || normalized.split("\\.").length == 3 && normalized.length() > 80;
    }

    private String normalize(String key) {
        return key == null ? "" : key.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}

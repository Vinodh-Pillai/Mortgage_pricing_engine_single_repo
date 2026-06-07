package com.wcpe.tenantcontext.health;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class HealthRedactionPolicy {
    private static final String REDACTED = "[REDACTED]";

    public DependencyStatus redact(DependencyStatus status) {
        if (status == null) {
            throw new HealthValidationException("HEALTH_STATUS_REQUIRED", "dependency status is required");
        }
        Map<String, String> safeDetails = new LinkedHashMap<>();
        status.details().forEach((key, value) -> {
            if (isSensitive(key) || isSensitive(value)) {
                safeDetails.put(key, REDACTED);
            } else {
                safeDetails.put(key, value == null ? "" : value.trim());
            }
        });
        return new DependencyStatus(status.component(), status.status(), redactText(status.summary()), status.errorCode(), status.runbookUrl(), status.checkedAt(), safeDetails);
    }

    public String redactText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (isSensitive(lower)) {
            return REDACTED;
        }
        return value.trim();
    }

    private boolean isSensitive(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("secret")
            || lower.contains("password")
            || lower.contains("token")
            || lower.contains("credential")
            || lower.contains("connectionstring")
            || lower.contains("jdbc:")
            || lower.contains("redis://")
            || lower.contains("broker://");
    }
}

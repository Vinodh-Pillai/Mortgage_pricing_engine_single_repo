package com.wcpe.tenantcontext.audit;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuditRedactionPolicy {
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\\\\\"])*)\\\"");
    private static final Set<String> EXACT_SENSITIVE_KEYS = Set.of(
        "ssn", "taxid", "password", "passcode", "secret", "token", "accesstoken", "refreshtoken", "apikey",
        "authorization", "borrowername", "customername", "email", "phone", "address"
    );
    private static final String REDACTED = "[REDACTED]";

    public String redact(String changeSummaryJson) {
        if (changeSummaryJson == null || changeSummaryJson.isBlank()) {
            throw new AuditException("AUDIT_CONTEXT_REQUIRED", "changeSummaryJson is required");
        }
        Matcher matcher = JSON_STRING_FIELD.matcher(changeSummaryJson);
        StringBuilder redacted = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = sensitive(key) ? REDACTED : matcher.group(2);
            matcher.appendReplacement(redacted, Matcher.quoteReplacement("\"" + key + "\":\"" + value + "\""));
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }

    private boolean sensitive(String key) {
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return EXACT_SENSITIVE_KEYS.contains(normalized)
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("password")
            || normalized.contains("borrower")
            || normalized.contains("customer")
            || normalized.contains("person")
            || normalized.contains("pii");
    }
}

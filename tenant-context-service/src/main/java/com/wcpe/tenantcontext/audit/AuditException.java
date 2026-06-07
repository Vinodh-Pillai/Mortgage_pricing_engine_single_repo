package com.wcpe.tenantcontext.audit;

public class AuditException extends RuntimeException {
    private final String code;

    public AuditException(String code, String message) {
        super(message);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        this.code = code.trim();
    }

    public String code() {
        return code;
    }
}

package com.wcpe.tenantcontext.health;

public class HealthValidationException extends RuntimeException {
    private final String code;

    public HealthValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

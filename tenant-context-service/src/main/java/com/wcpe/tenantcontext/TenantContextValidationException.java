package com.wcpe.tenantcontext;

public class TenantContextValidationException extends RuntimeException {
    private final String code;

    public TenantContextValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

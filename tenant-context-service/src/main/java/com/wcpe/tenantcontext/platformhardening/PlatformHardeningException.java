package com.wcpe.tenantcontext.platformhardening;

public class PlatformHardeningException extends RuntimeException {
    private final String code;

    public PlatformHardeningException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

package com.wcpe.tenantcontext.ratelimit;

public class RateLimitPolicyException extends RuntimeException {
    private final String code;

    public RateLimitPolicyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

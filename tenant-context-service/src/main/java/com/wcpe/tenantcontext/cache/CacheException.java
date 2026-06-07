package com.wcpe.tenantcontext.cache;

public class CacheException extends RuntimeException {
    private final String code;

    public CacheException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

package com.wcpe.tenantcontext.consumer;

public class ConsumerInboxException extends RuntimeException {
    private final String code;

    public ConsumerInboxException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

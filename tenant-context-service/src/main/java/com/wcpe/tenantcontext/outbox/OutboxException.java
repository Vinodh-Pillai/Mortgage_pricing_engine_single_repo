package com.wcpe.tenantcontext.outbox;

public class OutboxException extends RuntimeException {
    private final String code;

    public OutboxException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

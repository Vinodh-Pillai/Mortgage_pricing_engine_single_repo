package com.wcpe.tenantcontext.event;

public class EventEnvelopeValidationException extends RuntimeException {
    private final String code;

    public EventEnvelopeValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

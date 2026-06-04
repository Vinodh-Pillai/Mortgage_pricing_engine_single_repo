package com.wcpe.quote;

public class QuoteCreateException extends RuntimeException {
    private final String code;

    public QuoteCreateException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

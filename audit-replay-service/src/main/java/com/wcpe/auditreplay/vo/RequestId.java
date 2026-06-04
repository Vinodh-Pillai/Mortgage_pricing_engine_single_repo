package com.wcpe.auditreplay.vo;

import java.util.Objects;

public final class RequestId {

    private final String value;

    private RequestId(String value) {
        this.value = value;
    }

    public static RequestId of(String id) {
        Objects.requireNonNull(id, "requestId must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        return new RequestId(id.trim());
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RequestId requestId = (RequestId) o;
        return value.equals(requestId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}

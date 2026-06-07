package com.wcpe.auditreplay.vo;

import java.util.Objects;
import java.util.UUID;

public final class CorrelationId {

    private final UUID value;

    private CorrelationId(UUID value) {
        this.value = value;
    }

    public static CorrelationId of(String id) {
        if (id == null) {
            throw new IllegalArgumentException("correlationId must not be null");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("correlationId is not a valid UUID: " + id, e);
        }
        return new CorrelationId(uuid);
    }

    public static CorrelationId generate() {
        return new CorrelationId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CorrelationId that = (CorrelationId) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}

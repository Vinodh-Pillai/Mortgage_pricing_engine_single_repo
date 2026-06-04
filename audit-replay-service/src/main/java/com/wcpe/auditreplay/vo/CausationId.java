package com.wcpe.auditreplay.vo;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class CausationId {

    private final UUID value;

    private CausationId(UUID value) {
        this.value = value;
    }

    public static CausationId of(UUID value) {
        return new CausationId(value);
    }

    public static CausationId ofNullable(UUID value) {
        return new CausationId(value);
    }

    public boolean isPresent() {
        return value != null;
    }

    public UUID get() {
        if (value == null) {
            throw new NoSuchElementException("CausationId is not present");
        }
        return value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CausationId that = (CausationId) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

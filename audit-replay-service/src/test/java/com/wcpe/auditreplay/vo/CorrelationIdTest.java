package com.wcpe.auditreplay.vo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdTest {

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CorrelationId.of(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> CorrelationId.of(""));
        assertThrows(IllegalArgumentException.class, () -> CorrelationId.of("   "));
    }

    @Test
    void rejectsNonUuid() {
        assertThrows(IllegalArgumentException.class, () -> CorrelationId.of("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> CorrelationId.of("123"));
    }

    @Test
    void acceptsValidUuid() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        CorrelationId id = CorrelationId.of(uuid);
        assertEquals(uuid, id.toString());
    }

    @Test
    void generateReturnsValidUuid() {
        CorrelationId id = CorrelationId.generate();
        assertDoesNotThrow(() -> java.util.UUID.fromString(id.toString()));
    }

    @Test
    void equalsAndHashCodeBasedOnValue() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        CorrelationId a = CorrelationId.of(uuid);
        CorrelationId b = CorrelationId.of(uuid);
        assertNotSame(a, b);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

package com.wcpe.auditreplay;

import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationContextTest {

    @AfterEach
    void tearDown() {
        CorrelationContext.clear();
    }

    @Test
    void requiresTenantAtPersistenceBoundary() {
        CorrelationContext.clear();
        assertThrows(IllegalStateException.class, CorrelationContext::checkPersistenceBoundary);
    }

    @Test
    void allowsPersistenceWhenCorrelationIdPresent() {
        CorrelationContext.set(
            CorrelationId.generate(),
            CausationId.ofNullable(null),
            RequestId.of("req-1")
        );
        assertDoesNotThrow(CorrelationContext::checkPersistenceBoundary);
    }

    @Test
    void requireCorrelationIdThrowsWhenMissing() {
        CorrelationContext.clear();
        assertThrows(IllegalStateException.class, CorrelationContext::requireCorrelationId);
    }

    @Test
    void requireCorrelationIdReturnsValueWhenSet() {
        CorrelationId expected = CorrelationId.generate();
        CorrelationContext.set(expected, CausationId.ofNullable(null), RequestId.of("req-1"));
        assertEquals(expected, CorrelationContext.requireCorrelationId());
    }

    @Test
    void getReturnsNullWhenCleared() {
        CorrelationContext.clear();
        assertNull(CorrelationContext.get());
    }

    @Test
    void getReturnsDataWhenSet() {
        CorrelationId corr = CorrelationId.generate();
        CausationId caus = CausationId.of(UUID.randomUUID());
        RequestId req = RequestId.of("req-2");
        CorrelationContext.set(corr, caus, req);

        CorrelationContext.Data data = CorrelationContext.get();
        assertNotNull(data);
        assertEquals(corr, data.correlationId());
        assertEquals(caus, data.causationId());
        assertEquals(req, data.requestId());
    }

    @Test
    void causationIdIsPresent() {
        CausationId present = CausationId.of(UUID.randomUUID());
        assertTrue(present.isPresent());

        CausationId absent = CausationId.ofNullable(null);
        assertFalse(absent.isPresent());
    }

    @Test
    void causationIdGetThrowsWhenEmpty() {
        CausationId absent = CausationId.ofNullable(null);
        assertThrows(NoSuchElementException.class, absent::get);
    }
}

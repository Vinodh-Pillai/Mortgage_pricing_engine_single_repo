package com.wcpe.auditreplay;

import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;

public final class CorrelationContext {

    private CorrelationContext() {}

    private static final ThreadLocal<Data> CURRENT = new ThreadLocal<>();

    public record Data(CorrelationId correlationId, CausationId causationId, RequestId requestId) {}

    public static void set(CorrelationId correlationId, CausationId causationId, RequestId requestId) {
        CURRENT.set(new Data(correlationId, causationId, requestId));
    }

    public static Data get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static CorrelationId requireCorrelationId() {
        Data data = CURRENT.get();
        if (data == null || data.correlationId() == null) {
            throw new IllegalStateException("CorrelationId is not set in the current thread");
        }
        return data.correlationId();
    }

    public static void checkPersistenceBoundary() {
        Data data = CURRENT.get();
        if (data == null || data.correlationId() == null) {
            throw new IllegalStateException("CorrelationId is missing — cannot cross persistence boundary");
        }
    }
}

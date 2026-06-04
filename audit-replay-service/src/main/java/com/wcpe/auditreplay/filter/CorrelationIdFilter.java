package com.wcpe.auditreplay.filter;

import com.wcpe.auditreplay.CorrelationContext;
import com.wcpe.auditreplay.vo.CausationId;
import com.wcpe.auditreplay.vo.CorrelationId;
import com.wcpe.auditreplay.vo.RequestId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CAUSATION_HEADER = "X-Causation-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().contains("/actuator")
                || request.getRequestURI().contains("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CorrelationId correlationId = resolveCorrelationId(request);
        CausationId causationId = resolveCausationId(request);
        RequestId requestId = resolveRequestId(request);

        response.setHeader(CORRELATION_HEADER, correlationId.toString());

        try {
            CorrelationContext.set(correlationId, causationId, requestId);
            MDC.put("correlation_id", correlationId.toString());
            MDC.put("request_id", requestId.toString());
            if (causationId.isPresent()) {
                MDC.put("causation_id", causationId.toString());
            }
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
            MDC.remove("correlation_id");
            MDC.remove("causation_id");
            MDC.remove("request_id");
        }
    }

    private CorrelationId resolveCorrelationId(HttpServletRequest request) {
        String value = request.getHeader(CORRELATION_HEADER);
        if (value != null && !value.isBlank()) {
            try {
                return CorrelationId.of(value);
            } catch (IllegalArgumentException ignored) {
                return CorrelationId.generate();
            }
        }
        return CorrelationId.generate();
    }

    private CausationId resolveCausationId(HttpServletRequest request) {
        String value = request.getHeader(CAUSATION_HEADER);
        if (value != null && !value.isBlank()) {
            try {
                return CausationId.of(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                return CausationId.ofNullable(null);
            }
        }
        return CausationId.ofNullable(null);
    }

    private RequestId resolveRequestId(HttpServletRequest request) {
        String value = request.getHeader(REQUEST_ID_HEADER);
        if (value != null && !value.isBlank()) {
            return RequestId.of(value);
        }
        return RequestId.of(UUID.randomUUID().toString());
    }
}

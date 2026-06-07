package com.wcpe.tenantcontext.event;

import java.util.List;
import java.util.Locale;

public class EventClassificationPolicy {
    private static final List<String> FORBIDDEN_PAYLOAD_MARKERS = List.of(
        "ssn",
        "socialsecuritynumber",
        "rawcreditdata",
        "creditreport",
        "fullloanapplication",
        "password",
        "secret",
        "token"
    );

    public void validatePayload(DataClassification classification, String payloadJson) {
        DataClassification.require(classification);
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", "payloadJson is required");
        }
        String normalized = payloadJson.replaceAll("[\\s_\\-]", "").toLowerCase(Locale.ROOT);
        for (String marker : FORBIDDEN_PAYLOAD_MARKERS) {
            if (normalized.contains(marker)) {
                throw new EventEnvelopeValidationException("PII_NOT_ALLOWED", "event payload contains a forbidden PII or secret marker");
            }
        }
    }
}

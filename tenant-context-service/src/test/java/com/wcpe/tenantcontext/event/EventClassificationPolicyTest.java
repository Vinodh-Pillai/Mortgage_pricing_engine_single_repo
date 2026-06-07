package com.wcpe.tenantcontext.event;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EventClassificationPolicyTest {
    private final EventClassificationPolicy policy = new EventClassificationPolicy();

    @Test
    void allowsSanitizedInternalFixturePayload() {
        assertThatCode(() -> policy.validatePayload(DataClassification.INTERNAL,
            "{\"tenantId\":\"tenant-alpha\",\"summary\":\"sanitized fixture\"}"))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsForbiddenPiiAndSecretMarkersForFixtures() {
        assertThatThrownBy(() -> policy.validatePayload(DataClassification.CONFIDENTIAL,
            "{\"tenantId\":\"tenant-alpha\",\"ssn\":\"redacted\"}"))
            .isInstanceOf(EventEnvelopeValidationException.class)
            .extracting(error -> ((EventEnvelopeValidationException) error).code())
            .isEqualTo("PII_NOT_ALLOWED");

        assertThatThrownBy(() -> policy.validatePayload(DataClassification.RESTRICTED,
            "{\"tenantId\":\"tenant-alpha\",\"apiToken\":\"redacted\"}"))
            .isInstanceOf(EventEnvelopeValidationException.class)
            .extracting(error -> ((EventEnvelopeValidationException) error).code())
            .isEqualTo("PII_NOT_ALLOWED");
    }
}

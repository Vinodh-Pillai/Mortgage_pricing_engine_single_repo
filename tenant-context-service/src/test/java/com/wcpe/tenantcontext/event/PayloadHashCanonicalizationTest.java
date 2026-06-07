package com.wcpe.tenantcontext.event;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PayloadHashCanonicalizationTest {
    @Test
    void canonicalizesObjectKeyOrderWhitespaceAndNestedStructuresBeforeHashing() {
        String first = "{ \"tenantId\" : \"tenant-alpha\", \"items\" : [ { \"b\" : 2, \"a\" : 1.0 } ], \"status\" : \"READY\" }";
        String second = "{\"status\":\"READY\",\"items\":[{\"a\":1,\"b\":2}],\"tenantId\":\"tenant-alpha\"}";

        assertThat(CanonicalPayloadHash.canonicalize(first))
            .isEqualTo("{\"items\":[{\"a\":1,\"b\":2}],\"status\":\"READY\",\"tenantId\":\"tenant-alpha\"}");
        assertThat(CanonicalPayloadHash.sha256(first)).isEqualTo(CanonicalPayloadHash.sha256(second));
    }

    @Test
    void rejectsMalformedPayloadInsteadOfHashingAmbiguousContent() {
        assertThatThrownBy(() -> CanonicalPayloadHash.sha256("{tenantId:tenant-alpha}"))
            .isInstanceOf(EventEnvelopeValidationException.class)
            .extracting(error -> ((EventEnvelopeValidationException) error).code())
            .isEqualTo("EVENT_ENVELOPE_VALIDATION_FAILED");
    }
}

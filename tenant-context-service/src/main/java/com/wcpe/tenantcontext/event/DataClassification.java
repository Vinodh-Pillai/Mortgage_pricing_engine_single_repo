package com.wcpe.tenantcontext.event;

public enum DataClassification {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED;

    static DataClassification require(DataClassification classification) {
        if (classification == null) {
            throw new EventEnvelopeValidationException("EVENT_CLASSIFICATION_REQUIRED", "dataClassification is required");
        }
        return classification;
    }
}

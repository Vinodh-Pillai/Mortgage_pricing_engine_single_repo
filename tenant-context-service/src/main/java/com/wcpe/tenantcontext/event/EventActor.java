package com.wcpe.tenantcontext.event;

public record EventActor(String actorId, String actorType, String service) {
    public EventActor {
        actorId = required(actorId, "actorId");
        actorType = required(actorType, "actorType").toUpperCase(java.util.Locale.ROOT);
        service = required(service, "service");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new EventEnvelopeValidationException("EVENT_ENVELOPE_VALIDATION_FAILED", fieldName + " is required");
        }
        return value.trim();
    }
}

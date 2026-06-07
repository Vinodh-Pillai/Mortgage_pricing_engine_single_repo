package com.wcpe.tenantcontext;

public record ActorRef(String actorId, String actorType) {
    public ActorRef {
        actorId = optional(actorId);
        actorType = optional(actorType);
    }

    static ActorRef empty() {
        return new ActorRef("", "");
    }

    boolean isComplete() {
        return !actorId.isBlank() && !actorType.isBlank();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}

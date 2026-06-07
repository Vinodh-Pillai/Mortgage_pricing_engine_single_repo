package com.wcpe.auditreplay.domain;

public interface EventContractRegistry {
    boolean supports(String eventType, Integer eventVersion);
}

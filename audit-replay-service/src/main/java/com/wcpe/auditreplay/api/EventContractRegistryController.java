package com.wcpe.auditreplay.api;

import com.wcpe.auditreplay.application.EventContractRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/event-contracts")
public class EventContractRegistryController {

    private final EventContractRegistryService registryService;

    public EventContractRegistryController(EventContractRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping("/envelopes/v1")
    public EventContractRegistryService.ContractMetadata envelopeV1() {
        return registryService.envelopeV1();
    }

    @GetMapping("/events/{eventType}/versions/{version}")
    public EventContractRegistryService.ContractMetadata eventVersion(@PathVariable String eventType, @PathVariable int version) {
        try {
            return registryService.eventVersion(eventType, version);
        } catch (EventContractRegistryService.UnknownEventContractException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}

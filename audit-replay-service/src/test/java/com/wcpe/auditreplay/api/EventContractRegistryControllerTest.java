package com.wcpe.auditreplay.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.auditreplay.application.EventContractRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EventContractRegistryControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new EventContractRegistryController(new EventContractRegistryService(new ObjectMapper().findAndRegisterModules()))).build();

    @Test
    void returnsEnvelopeSchemaMetadataAndFixtures() throws Exception {
        mockMvc.perform(get("/api/v1/event-contracts/envelopes/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("EventEnvelopeV1"))
                .andExpect(jsonPath("$.eventVersion").value(1))
                .andExpect(jsonPath("$.jsonSchema.required[0]").value("eventId"))
                .andExpect(jsonPath("$.fixtures[0].eventType").value("audit_record.created"));
    }

    @Test
    void returnsEventSchemaVersion() throws Exception {
        mockMvc.perform(get("/api/v1/event-contracts/events/{eventType}/versions/{version}", "audit_record.created", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaRef").value("audit-record-created-v1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}

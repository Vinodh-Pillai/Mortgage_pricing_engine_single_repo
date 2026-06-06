package com.wcpe.auditreplay.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorrelationFilterIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesAndReturnsMissingCorrelationId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        assertNotNull(correlationId);
        assertDoesNotThrow(() -> java.util.UUID.fromString(correlationId));
    }

    @Test
    void forwardsExistingCorrelationId() throws Exception {
        String existingId = "550e8400-e29b-41d4-a716-446655440000";
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-Id", existingId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", existingId))
                .andReturn();
    }

    @Test
    void generatesUuidForInvalidCorrelationId() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-Id", "not-a-valid-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String generatedId = result.getResponse().getHeader("X-Correlation-Id");
        assertNotEquals("not-a-valid-uuid", generatedId);
        assertDoesNotThrow(() -> java.util.UUID.fromString(generatedId));
    }
}

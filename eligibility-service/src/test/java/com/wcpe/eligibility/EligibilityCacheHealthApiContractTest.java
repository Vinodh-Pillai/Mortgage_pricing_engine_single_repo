package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.cache.EligibilityCacheHealth;
import com.wcpe.eligibility.cache.EligibilityCacheService;
import com.wcpe.eligibility.controller.EligibilityApiController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EligibilityCacheHealthApiContractTest {
    @Test
    void responseV1() throws Exception {
        EligibilityCacheService cacheService = mock(EligibilityCacheService.class);
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(cacheService.health(eq(tenantId), eq("CONVENTIONAL"), eq("CONVENTIONAL_PURCHASE")))
            .thenReturn(new EligibilityCacheHealth(
                tenantId,
                "DEGRADED",
                false,
                true,
                null,
                List.of(new EligibilityCacheHealth.TrackedNamespace("loan-limit", 86_400, null, null))
            ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EligibilityApiController(
            mock(),
            mock(JdbcTemplate.class),
            new ObjectMapper().findAndRegisterModules(),
            mock(),
            mock(),
            mock(),
            mock(),
            cacheService
        )).build();

        mockMvc.perform(get("/api/v1/tenants/11111111-1111-1111-1111-111111111111/eligibility/cache/health")
                .queryParam("productFamily", "CONVENTIONAL")
                .queryParam("quoteType", "CONVENTIONAL_PURCHASE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantId").value("11111111-1111-1111-1111-111111111111"))
            .andExpect(jsonPath("$.dbFallbackEnabled").value(true))
            .andExpect(jsonPath("$.trackedNamespaces[0].namespace").value("loan-limit"));
    }
}

package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.nonqm.NonQmEligibilityController;
import com.wcpe.eligibility.nonqm.NonQmEligibilityModels.*;
import com.wcpe.eligibility.nonqm.NonQmEligibilityService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static com.wcpe.eligibility.DscrFactCalculatorTest.bd;
import static com.wcpe.eligibility.DscrFactCalculatorTest.dscrRuleSet;
import static com.wcpe.eligibility.DscrFactCalculatorTest.product;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NonQmEligibilityApiContractTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluatesNonQmProductThroughPublicEndpoint() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NonQmEligibilityController(new NonQmEligibilityService())).build();
        NonQmEligibilityRequest request = new NonQmEligibilityRequest("DSCR_30YR", "INV-A", "BROKER", null,
            new ScenarioFacts(new RentSchedule(bd("2500.00")), new HousingExpense(bd("2000.00")), null, null, null),
            product("DSCR_30YR", "DSCR", dscrRuleSet()), null);

        mockMvc.perform(post("/api/v1/eligibility/non-qm/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCode").value("DSCR_30YR"))
            .andExpect(jsonPath("$.decision").value("ELIGIBLE"))
            .andExpect(jsonPath("$.eligible").value(true))
            .andExpect(jsonPath("$.calculatedFacts['nonQm.dscr.ratio']").exists())
            .andExpect(jsonPath("$.outcomes[0].ruleId").value("DSCR-MIN-001"))
            .andExpect(jsonPath("$.auditHash").isString());
    }
}

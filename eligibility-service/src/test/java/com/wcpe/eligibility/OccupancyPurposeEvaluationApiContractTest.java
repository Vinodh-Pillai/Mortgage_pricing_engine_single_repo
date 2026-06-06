package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties;
import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties.RuleRowConfig;
import com.wcpe.eligibility.config.OccupancyPurposeRuleProperties.RuleSetConfig;
import com.wcpe.eligibility.controller.EligibilityApiController;
import com.wcpe.eligibility.domain.models.OccupancyPurposeEvaluationRequest;
import com.wcpe.eligibility.domain.models.OccupancyPurposeFacts;
import com.wcpe.eligibility.domain.models.OccupancyPurposeProductCandidate;
import com.wcpe.eligibility.repository.OccupancyPurposeRuleRepository;
import com.wcpe.eligibility.service.OccupancyPurposeRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OccupancyPurposeEvaluationApiContractTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateV1() throws Exception {
        MockMvc mockMvc = mockMvc(ruleService(defaultProperties()));
        OccupancyPurposeEvaluationRequest request = request("PURCHASE", "INVESTMENT_PROPERTY");

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/occupancy-purpose", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evaluationId").isString())
            .andExpect(jsonPath("$.decision.ruleCode").value("CONF_OCCUPANCY_PURPOSE"))
            .andExpect(jsonPath("$.decision.eligibilityStatus").value("INELIGIBLE"))
            .andExpect(jsonPath("$.decision.reasonCode").value("OCCUPANCY_NOT_ALLOWED_FOR_PRODUCT_CHANNEL"))
            .andExpect(jsonPath("$.resultHash").isString());
    }

    @Test
    void missingOccupancyReturns422() throws Exception {
        MockMvc mockMvc = mockMvc(ruleService(defaultProperties()));
        OccupancyPurposeEvaluationRequest request = request("PURCHASE", null);

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/occupancy-purpose", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.decision.reasonCode").value("MISSING_OCCUPANCY"));
    }

    private MockMvc mockMvc(OccupancyPurposeRuleService occupancyPurposeRuleService) {
        return MockMvcBuilders.standaloneSetup(new EligibilityApiController(
            null, null, objectMapper, null, occupancyPurposeRuleService, null, null, null, null
        )).build();
    }

    private OccupancyPurposeRuleService ruleService(OccupancyPurposeRuleProperties properties) {
        return new OccupancyPurposeRuleService(properties, new OccupancyPurposeRuleRepository(null));
    }

    private OccupancyPurposeRuleProperties defaultProperties() {
        OccupancyPurposeRuleProperties props = new OccupancyPurposeRuleProperties();
        props.setEnabled(true);

        RuleSetConfig set = new RuleSetConfig();
        set.setRuleSetId(UUID.randomUUID().toString());
        set.setProductFamily("CONVENTIONAL");
        set.setProductCode("CONF30");
        set.setInvestorCode("FNMA");
        set.setChannel("RETAIL");
        set.setPrecedence(50);

        RuleRowConfig investmentDeny = new RuleRowConfig();
        investmentDeny.setRuleId(UUID.randomUUID().toString());
        investmentDeny.setLoanPurpose("PURCHASE");
        investmentDeny.setOccupancyType("INVESTMENT_PROPERTY");
        investmentDeny.setPropertyType("SINGLE_FAMILY");
        investmentDeny.setUnitsMin(1);
        investmentDeny.setUnitsMax(4);
        investmentDeny.setDecision("DENY");
        investmentDeny.setSeverity("HARD_STOP");
        investmentDeny.setReasonCode("OCCUPANCY_NOT_ALLOWED_FOR_PRODUCT_CHANNEL");
        investmentDeny.setMessageTemplate("Investment property purchase is not allowed for {{productCode}}/{{investorCode}}/{{channel}}.");
        investmentDeny.setPriority(10);

        set.setRows(List.of(investmentDeny));
        props.setRuleSets(List.of(set));
        return props;
    }

    private OccupancyPurposeEvaluationRequest request(String loanPurpose, String occupancyType) {
        return new OccupancyPurposeEvaluationRequest(
            UUID.fromString("cf6e0657-e55d-4485-8e7a-968cc8758fc0"),
            1,
            "2026-05-13",
            new OccupancyPurposeProductCandidate(UUID.fromString("11111111-1111-1111-1111-111111111111"), "CONF30", "FNMA", "RETAIL"),
            new OccupancyPurposeFacts(loanPurpose, occupancyType, 1, "SINGLE_FAMILY")
        );
    }
}

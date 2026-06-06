package com.wcpe.eligibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.eligibility.config.PropertyTypeRuleProperties;
import com.wcpe.eligibility.config.PropertyTypeRuleProperties.RuleRowConfig;
import com.wcpe.eligibility.config.PropertyTypeRuleProperties.RuleSetConfig;
import com.wcpe.eligibility.controller.EligibilityApiController;
import com.wcpe.eligibility.domain.models.PropertyTypeEvaluationRequest;
import com.wcpe.eligibility.domain.models.PropertyTypeFacts;
import com.wcpe.eligibility.domain.models.PropertyTypeProductCandidate;
import com.wcpe.eligibility.repository.PropertyTypeRuleRepository;
import com.wcpe.eligibility.service.PropertyTypeRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PropertyTypeEvaluationApiContractTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "CONF30";
    private static final String INVESTOR = "FNMA";
    private static final String CHANNEL = "RETAIL";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluateV1() throws Exception {
        MockMvc mockMvc = mockMvc(ruleService(defaultProperties()));
        PropertyTypeEvaluationRequest request = request("CONDO", 1, "UNKNOWN");

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/property-type", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evaluationId").isString())
            .andExpect(jsonPath("$.decision.ruleCode").value("CONF_PROPERTY_TYPE"))
            .andExpect(jsonPath("$.decision.eligibilityStatus").value("WARNING"))
            .andExpect(jsonPath("$.decision.severity").value("CONDITION"))
            .andExpect(jsonPath("$.decision.reasonCode").value("CONDO_PROJECT_REVIEW_REQUIRED"))
            .andExpect(jsonPath("$.decision.projectReviewRequirement").value("WARNING"))
            .andExpect(jsonPath("$.resultHash").isString());
    }

    @Test
    void missingPropertyTypeReturns422() throws Exception {
        MockMvc mockMvc = mockMvc(ruleService(defaultProperties()));
        PropertyTypeEvaluationRequest request = request(null, 1, "NONE");

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/property-type", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.decision.reasonCode").value("MISSING_PROPERTY_TYPE"));
    }

    @Test
    void invalidUnitsReturns422() throws Exception {
        MockMvc mockMvc = mockMvc(ruleService(defaultProperties()));
        PropertyTypeEvaluationRequest request = request("SINGLE_FAMILY", 5, "NONE");

        mockMvc.perform(post("/api/v1/tenants/{tenantId}/eligibility/evaluations/property-type", TENANT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.decision.reasonCode").value("INVALID_UNITS"));
    }

    private MockMvc mockMvc(PropertyTypeRuleService propertyTypeRuleService) {
        return MockMvcBuilders.standaloneSetup(new EligibilityApiController(
            null, null, objectMapper, null, null, propertyTypeRuleService, null, null, null
        )).build();
    }

    private PropertyTypeRuleService ruleService(PropertyTypeRuleProperties properties) {
        return new PropertyTypeRuleService(properties, new PropertyTypeRuleRepository(null));
    }

    private PropertyTypeRuleProperties defaultProperties() {
        PropertyTypeRuleProperties props = new PropertyTypeRuleProperties();
        props.setEnabled(true);

        RuleSetConfig set = new RuleSetConfig();
        set.setRuleSetId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        set.setProductFamily("CONVENTIONAL");
        set.setProductCode(PRODUCT);
        set.setInvestorCode(INVESTOR);
        set.setChannel(CHANNEL);
        set.setVersion(1);

        RuleRowConfig singleFamily = new RuleRowConfig();
        singleFamily.setRuleId("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        singleFamily.setPropertyType("SINGLE_FAMILY");
        singleFamily.setUnitsMin(1);
        singleFamily.setUnitsMax(1);
        singleFamily.setLoanPurpose("PURCHASE");
        singleFamily.setDecision("ALLOW");
        singleFamily.setSeverity("PASS");
        singleFamily.setReasonCode("SINGLE_FAMILY_ALLOWED");
        singleFamily.setMessageTemplate("Single family property type is allowed for {{productCode}}.");
        singleFamily.setPriority(10);

        RuleRowConfig condoWarning = new RuleRowConfig();
        condoWarning.setRuleId("cccccccc-cccc-cccc-cccc-cccccccccccc");
        condoWarning.setPropertyType("CONDO");
        condoWarning.setUnitsMin(1);
        condoWarning.setUnitsMax(1);
        condoWarning.setLoanPurpose("PURCHASE");
        condoWarning.setProjectReviewRequirement("WARNING");
        condoWarning.setDecision("CONDITION");
        condoWarning.setSeverity("CONDITION");
        condoWarning.setReasonCode("CONDO_PROJECT_REVIEW_REQUIRED");
        condoWarning.setMessageTemplate("Condo property type requires project review before final pricing.");
        condoWarning.setPriority(20);

        set.setRows(List.of(singleFamily, condoWarning));
        props.setRuleSets(List.of(set));
        return props;
    }

    private PropertyTypeEvaluationRequest request(String propertyType, int units, String projectReviewStatus) {
        return new PropertyTypeEvaluationRequest(
            UUID.fromString("cf6e0657-e55d-4485-8e7a-968cc8758fc0"),
            1,
            "2026-05-13",
            new PropertyTypeProductCandidate(UUID.fromString("11111111-1111-1111-1111-111111111111"), PRODUCT, INVESTOR, CHANNEL),
            new PropertyTypeFacts(propertyType, units, "PRIMARY_RESIDENCE", "PURCHASE", projectReviewStatus)
        );
    }
}

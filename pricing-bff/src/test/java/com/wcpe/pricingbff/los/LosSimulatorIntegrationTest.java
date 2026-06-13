package com.wcpe.pricingbff.los;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class LosSimulatorIntegrationTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void losSimulatorCanSubmitPricingAndReadOffers() throws Exception {
    MvcResult pricing = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .header("X-LOS-System", "BYTE")
            .header("X-LOS-Version", "24.1")
            .header("Authorization", "Bearer simulator-token")
            .header("X-Request-ID", "sim-req-001")
            .header("X-Correlation-ID", "sim-corr-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"requestId":"los-sim-001","tenantId":"tenant-los","loan":{"loanPurpose":"PURCHASE","loanAmount":450000,"loanType":"CONVENTIONAL","termMonths":360,"amortizationType":"FIXED","property":{"state":"TX","county":"TRAVIS","propertyType":"SINGLE_FAMILY","occupancy":"PRIMARY_RESIDENCE","units":1,"purchasePrice":500000,"appraisedValue":500000},"borrowers":[{"borrowerId":"b1","creditScore":745}]},"pricing":{"channel":"RETAIL","productFamily":"CONVENTIONAL","investorPreference":["FNMA"],"lockPeriodDays":30,"effectiveDate":"2026-06-12"}}
                """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.pricingRequestId").exists())
        .andReturn();

    JsonNode body = objectMapper.readTree(pricing.getResponse().getContentAsString());
    String pricingRequestId = body.path("pricingRequestId").asText();
    mockMvc.perform(get("/api/v1/los/pricing-requests/{id}/offers", pricingRequestId)
            .header("X-LOS-System", "BYTE")
            .header("Authorization", "Bearer simulator-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricingRequestId").value(pricingRequestId))
        .andExpect(jsonPath("$.offers").isArray());
  }
}

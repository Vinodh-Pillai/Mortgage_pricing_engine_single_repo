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
                {"requestId":"los-sim-001","tenantId":"tenant-los","quoteBorrowerInfo":{"borrowerLastName":"Rivera","loanNumber":"LN-SIM-001","numberOfBorrowers":1},"quoteAddressDTO":{"state":"TX","zip":"78701","countyName":"TRAVIS"},"requestedLoanAmount":450000,"purchasePrice":500000,"propertyValue":500000,"transactionType":"purchase","propertyInformationType":"single-family","occupancyType":"primary-residence","numberOfUnits":1,"incomeDocumentationType":"full-documentation","creditScore":745,"mortgageType":"conventional","amortizationType":"fixed","loanTermType":"30-year","desiredRateLockPeriod":30,"lockPeriodType":"30-day","channelType":"retail","creditApplicationFields":[{"fieldId":"field@base-loan-amount","value":{"type":"number","value":450000}},{"fieldId":"field@decision-credit-score","value":{"type":"number","value":745}}]}
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

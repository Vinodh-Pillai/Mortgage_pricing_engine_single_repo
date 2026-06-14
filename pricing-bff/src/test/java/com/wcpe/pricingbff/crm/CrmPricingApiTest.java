package com.wcpe.pricingbff.crm;

import static org.assertj.core.api.Assertions.assertThat;
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
class CrmPricingApiTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void minimalCrmFactsReturnQuickPricerGapsAndReplayRefs() throws Exception {
    mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/pricing-requests")
            .headers(Headers.crm("pricing:write"))
            .header("X-Request-ID", "crm-minimal-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "requestId": "crm-req-minimal",
                  "externalLeadId": "sf-lead-001",
                  "sourceRefs": { "salesforceOpportunityId": "opp-001" },
                  "leadFacts": {
                    "loanAmount": 650000,
                    "productFamily": "NON_QM",
                    "favoriteColor": "unsupported"
                  },
                  "unsupportedTopLevel": true
                }
                """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("NEEDS_FACTS"))
        .andExpect(jsonPath("$.missingFacts[0].field").exists())
        .andExpect(jsonPath("$.unsupportedFields[0].field").exists())
        .andExpect(jsonPath("$.replayRefs.scenarioRef").exists())
        .andExpect(jsonPath("$.sourceRefs.salesforceOpportunityId").value("opp-001"))
        .andExpect(jsonPath("$.quoteSummary.rateValuesIncluded").value(false));
  }

  @Test
  void fullCrmPayloadQueuesQuoteJobAndCanPromoteSaveShareAndDashboard() throws Exception {
    MvcResult created = mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/salesforce/pricing-requests")
            .headers(Headers.crm("pricing:write"))
            .header("X-Request-ID", "crm-full-001")
            .header("X-Correlation-ID", "corr-crm-full")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "requestId": "crm-req-full",
                  "externalLeadId": "sf-lead-full",
                  "leadFacts": {
                    "OB_LOAN_AMOUNT": 850000,
                    "OB_FICO": 720,
                    "OB_STATE": "CA",
                    "OB_LOAN_PURPOSE": "PURCHASE",
                    "OB_OCCUPANCY": "PRIMARY_RESIDENCE",
                    "OB_PRODUCT": "NON_QM",
                    "lockPeriodDays": 45,
                    "effectiveDate": "2026-06-13"
                  }
                }
                """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("QUOTE_JOB_QUEUED"))
        .andExpect(jsonPath("$.quoteJobId").isNotEmpty())
        .andExpect(jsonPath("$.missingFacts").isEmpty())
        .andReturn();

    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    String requestId = body.path("statusUrl").asText().substring(body.path("statusUrl").asText().lastIndexOf('/') + 1);

    mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/pricing-requests/{requestId}/continue", requestId)
            .headers(Headers.crm("pricing:write")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PROMOTED"));

    MvcResult saved = mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/pricing-requests/{requestId}/scenarios", requestId)
            .headers(Headers.crm("pricing:write")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SAVED"))
        .andReturn();

    String scenarioId = objectMapper.readTree(saved.getResponse().getContentAsString()).path("scenarioId").asText();
    mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/scenarios/{scenarioId}/share", scenarioId)
            .headers(Headers.crm("pricing:write")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SHARE_READY"));

    mockMvc.perform(get("/api/v1/tenants/tenant-crm/integrations/crm/dashboard")
            .headers(Headers.crm("pricing:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nonQmPricingSummary.productFamily").value("NON_QM"))
        .andExpect(jsonPath("$.pricingRequests").isArray());
  }

  @Test
  void webhookRegistrationAndPricingPushAreQueuedForCrm() throws Exception {
    mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/webhooks")
            .headers(Headers.crm("pricing:write"))
            .header("X-Request-ID", "crm-webhook-reg")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"url\":\"https://crm.example.test/hooks/pricing\",\"events\":[\"crm.pricing.updated\"],\"sourceSystem\":\"SALESFORCE\",\"secret\":\"not-recorded\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    MvcResult created = mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/pricing-requests")
            .headers(Headers.crm("pricing:write"))
            .header("X-Request-ID", "crm-push-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"requestId\":\"crm-push\",\"externalLeadId\":\"lead-push\",\"leadFacts\":{\"loanAmount\":500000,\"productFamily\":\"NON_QM\"}}"))
        .andExpect(status().isAccepted())
        .andReturn();

    String statusUrl = objectMapper.readTree(created.getResponse().getContentAsString()).path("statusUrl").asText();
    String requestId = statusUrl.substring(statusUrl.lastIndexOf('/') + 1);
    mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/pricing-requests/{requestId}/push", requestId)
            .headers(Headers.crm("pricing:write")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deliveries[0].status").value("QUEUED"));
  }

  @Test
  void crmAuthorizationRequiresTokenAndScope() throws Exception {
    mockMvc.perform(get("/api/v1/tenants/tenant-crm/integrations/crm/dashboard")
            .header("X-CRM-System", "SALESFORCE"))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/v1/tenants/tenant-crm/integrations/crm/pricing-requests")
            .header("X-CRM-System", "SALESFORCE")
            .header("Authorization", "Bearer token")
            .header("X-CRM-Scopes", "pricing:read")
            .header("X-Request-ID", "bad-scope")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void crmContractDocIsPublished() throws Exception {
    String spec = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/api-docs/crm-pricing-api.yaml"));
    assertThat(spec).contains("CrmPricingResponse:", "/pricing-requests/{requestId}/continue:", "/webhooks:", "SALESFORCE", "HUBSPOT");
  }

  private static final class Headers {
    static org.springframework.http.HttpHeaders crm(String scopes) {
      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.add("X-CRM-System", "SALESFORCE");
      headers.add("X-CRM-Scopes", scopes);
      headers.add("Authorization", "Bearer local-crm-test-token");
      headers.add("X-Correlation-ID", "corr-crm-test");
      return headers;
    }
  }
}

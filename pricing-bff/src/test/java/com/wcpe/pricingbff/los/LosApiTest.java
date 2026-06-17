package com.wcpe.pricingbff.los;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class LosApiTest {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired LosWebhookRegistry webhookRegistry;

  @Test
  void pricingRequestValidation() throws Exception {
    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "req-validation")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRICING_REQUEST_INVALID"));
  }

  @Test
  void idempotencyKeyPreventsDuplicate() throws Exception {
    MvcResult first = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "idem-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-001")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andReturn();

    MvcResult second = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "idem-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-001")))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
    JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
    assertThat(secondBody.path("pricingRequestId").asText()).isEqualTo(firstBody.path("pricingRequestId").asText());
    assertThat(secondBody.path("quoteJobId").asText()).isEqualTo(firstBody.path("quoteJobId").asText());
  }

  @Test
  void webhookDeliveryRetry() throws Exception {
    mockMvc.perform(post("/api/v1/los/webhooks")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-reg-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":\"tenant-los\",\"url\":\"https://los.example.test/webhooks\",\"events\":[\"pricing.completed\"],\"secret\":\"not-recorded\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-price-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-webhook")))
        .andExpect(status().isAccepted());

    assertThat(webhookRegistry.deliveries()).anyMatch(receipt -> receipt.eventType().equals("pricing.completed")
        && receipt.status().equals("QUEUED") && receipt.attemptCount() == 0);
  }

  @Test
  void mTLSAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/los/pricing-requests/missing")
            .header("X-LOS-System", "ENCOMPASS")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[] { new StubCertificate() }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NOT_FOUND"));
  }

  @Test
  void lockRequestValidation() throws Exception {
    mockMvc.perform(post("/api/v1/los/locks")
            .headers(Headers.auth())
            .header("X-Request-ID", "lock-invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOCK_REQUEST_INVALID"));
  }

  private String validPricingRequest(String requestId) {
    return """
        {
          "requestId": "%s",
          "tenantId": "tenant-los",
          "callbackUrl": "https://los.example.test/pricing/callback",
          "quoteBorrowerInfo": { "borrowerLastName": "Rivera", "loanNumber": "LN-001", "numberOfBorrowers": 1 },
          "quoteAddressDTO": { "street": "123 Main St", "city": "Austin", "state": "TX", "zip": "78701", "countyName": "TRAVIS" },
          "requestedLoanAmount": 450000,
          "purchasePrice": 500000,
          "propertyValue": 500000,
          "transactionType": "purchase",
          "propertyInformationType": "single-family",
          "occupancyType": "primary-residence",
          "numberOfUnits": 1,
          "incomeDocumentationType": "full-documentation",
          "totalMonthlyIncome": 12500,
          "totalLiabilityMonthlyPayment": 2500,
          "creditScore": 745,
          "mortgageType": "conventional",
          "amortizationType": "fixed",
          "loanTermType": "30-year",
          "desiredRateLockPeriod": 30,
          "lockPeriodType": "30-day",
          "channelType": "retail",
          "creditApplicationFields": [
            { "fieldId": "field@base-loan-amount", "value": { "type": "number", "value": 450000 } },
            { "fieldId": "field@loan-purpose", "value": { "type": "enum", "enumTypeId": "loan-purpose", "variantId": "purchase", "value": "purchase" } },
            { "fieldId": "field@decision-credit-score", "value": { "type": "number", "value": 745 } },
            { "fieldId": "field@desired-loan-term", "value": { "type": "duration", "value": "30-year" } }
          ]
        }
        """.formatted(requestId);
  }

  private static final class Headers {
    static org.springframework.http.HttpHeaders auth() {
      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.add("X-LOS-System", "ENCOMPASS");
      headers.add("X-LOS-Version", "24.1");
      headers.add("X-Correlation-ID", "corr-los-test");
      headers.add("Authorization", "Bearer local-test-token");
      return headers;
    }
  }

  private static final class StubCertificate extends X509Certificate {
    @Override public void checkValidity() { }
    @Override public void checkValidity(Date date) { }
    @Override public int getVersion() { return 3; }
    @Override public BigInteger getSerialNumber() { return BigInteger.ONE; }
    @Override public Principal getIssuerDN() { return () -> "CN=test"; }
    @Override public Principal getSubjectDN() { return () -> "CN=test"; }
    @Override public Date getNotBefore() { return Date.from(Instant.EPOCH); }
    @Override public Date getNotAfter() { return Date.from(Instant.now().plusSeconds(60)); }
    @Override public byte[] getTBSCertificate() { return new byte[0]; }
    @Override public byte[] getSignature() { return new byte[0]; }
    @Override public String getSigAlgName() { return "none"; }
    @Override public String getSigAlgOID() { return "0.0"; }
    @Override public byte[] getSigAlgParams() { return new byte[0]; }
    @Override public boolean[] getIssuerUniqueID() { return new boolean[0]; }
    @Override public boolean[] getSubjectUniqueID() { return new boolean[0]; }
    @Override public boolean[] getKeyUsage() { return new boolean[0]; }
    @Override public int getBasicConstraints() { return -1; }
    @Override public byte[] getEncoded() { return new byte[0]; }
    @Override public void verify(PublicKey key) { }
    @Override public void verify(PublicKey key, String sigProvider) { }
    @Override public String toString() { return "stub-certificate"; }
    @Override public PublicKey getPublicKey() { return null; }
    @Override public boolean hasUnsupportedCriticalExtension() { return false; }
    @Override public java.util.Set<String> getCriticalExtensionOIDs() { return java.util.Set.of(); }
    @Override public java.util.Set<String> getNonCriticalExtensionOIDs() { return java.util.Set.of(); }
    @Override public byte[] getExtensionValue(String oid) { return new byte[0]; }
  }
}

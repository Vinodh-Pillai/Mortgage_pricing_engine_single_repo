package com.wcpe.pricingbff.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PricingBffQuoteServiceLoanPassClientTest {
  @Test
  void failsClosedWhenQuoteServiceBaseUrlIsMissing() {
    PricingBffQuoteServiceLoanPassClient client = new PricingBffQuoteServiceLoanPassClient(RestClient.builder(), "", null, null);

    assertThatThrownBy(() -> client.executeSummary("tenant-a", "run-a", "trace-a"))
        .isInstanceOf(PricingBffQuoteServiceLoanPassClient.QuoteServiceUnavailableException.class)
        .hasMessageContaining("base URL is not configured");
  }

  @Test
  void consumesLoanPassSummaryAndProductResultShapesFromConfiguredQuoteService() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-summary"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {"totals":{"approved":1,"reviewRequired":0,"available":1,"rejected":0,"error":0},"products":[{"productId":"lp-product-001","productName":"LoanPass product","productCode":"LP-001","investorName":"Investor A","calculatedFields":[],"productFields":[],"status":{"type":"approved"},"versionNumber":"v1"}],"versionNumber":"v1","metadata":{"warnings":[]}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-product"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess("""
            {"productId":"lp-product-001","productName":"LoanPass product","productCode":"LP-001","investorName":"Investor A","isPricingEnabled":true,"productFields":[{"fieldId":"rule.maxLtv","value":{"type":"string","value":"rule-ref"}}],"calculatedFields":[{"fieldId":"rate.noteRate","value":{"type":"number","value":"6.5"}},{"fieldId":"stipulation.income","value":{"type":"string","value":"required"}},{"fieldId":"lockPeriod.30","value":{"type":"number","value":"30"}}],"status":{"type":"approved"},"versionNumber":"v1","metadata":{"source":"catalog-backed","warnings":[]}}
            """, MediaType.APPLICATION_JSON));

    PricingBffQuoteServiceLoanPassClient client = new PricingBffQuoteServiceLoanPassClient(builder, "https://quote-service.test", null, null);

    assertThat(client.executeSummary("tenant-a", "run-a", "trace-a").products()).hasSize(1);
    assertThat(client.executeProduct("tenant-a", "run-a", "lp-product-001", "trace-a").productFields()).hasSize(1);
    server.verify();
  }
}

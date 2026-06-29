package com.wcpe.pricingbff.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationField;
import com.wcpe.pricingbff.los.LosApiModels.CreditApplicationValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  @Test
  void postsRunFactsAndStableUuidTenantToQuoteService() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    String serviceTenant = PricingBffQuoteServiceLoanPassClient.DEFAULT_LOANHOUSE_TENANT_ID;
    List<CreditApplicationField> facts = List.of(
        new CreditApplicationField("field@base-loan-amount", new CreditApplicationValue("string", "loan-amount-ref", null, null)),
        new CreditApplicationField("field@state", new CreditApplicationValue("string", "TX", null, null)));
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-summary"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Tenant-ID", serviceTenant))
        .andExpect(jsonPath("$.tenantId").value(serviceTenant))
        .andExpect(jsonPath("$.pipelineRecordId").value("run-ui"))
        .andExpect(jsonPath("$.creditApplicationFields[0].fieldId").value("field@base-loan-amount"))
        .andExpect(jsonPath("$.creditApplicationFields[1].fieldId").value("field@state"))
        .andExpect(jsonPath("$.outputFieldsFilter.type").value("all"))
        .andExpect(jsonPath("$.outputFieldsFilter.version").value("current"))
        .andExpect(jsonPath("$.outputFieldsFilter.includeMetadata").value(true))
        .andRespond(withSuccess("{\"totals\":{\"approved\":0},\"products\":[]}", MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-product"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Tenant-ID", serviceTenant))
        .andExpect(jsonPath("$.tenantId").value(serviceTenant))
        .andExpect(jsonPath("$.productId").value("lp-product-001"))
        .andExpect(jsonPath("$.creditApplicationFields[0].fieldId").value("field@base-loan-amount"))
        .andExpect(jsonPath("$.outputFieldsFilter.type").value("all"))
        .andRespond(withSuccess("{\"productId\":\"lp-product-001\",\"status\":\"approved\"}", MediaType.APPLICATION_JSON));

    PricingBffQuoteServiceLoanPassClient client = new PricingBffQuoteServiceLoanPassClient(builder, "https://quote-service.test", null, null);

    client.executeSummary("ui-preview-tenant", "run-ui", "trace-ui", facts);
    client.executeProduct("ui-preview-tenant", "run-ui", "lp-product-001", "trace-ui", facts);

    server.verify();
  }

  @Test
  void normalizesDurableQuoteServiceCatalogResponseShapes() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-summary"))
        .andRespond(withSuccess("""
            {"success":true,"operation":"execute-summary","statusCounts":{"approved":1,"no_pricing":1},"products":[{"productId":"durable-product","productName":"Durable product","status":"approved","success":true,"rates":[{"noteRate":"rate-ref"}],"rules":[{"ruleId":"rule-ref"}],"stipulations":[{"stipulationId":"stip-ref"}],"calculations":[{"calculationId":"calc-ref"}],"versionMetadata":{"versionNumber":"catalog-v2"}}],"versionMetadata":{"versionNumber":"catalog-v2"}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-product"))
        .andRespond(withSuccess("""
            {"success":true,"operation":"execute-product","productId":"durable-product","productName":"Durable product","status":"approved","rates":[{"noteRate":"rate-ref"}],"rules":[{"ruleId":"rule-ref"}],"stipulations":[{"stipulationId":"stip-ref"}],"calculations":[{"calculationId":"calc-ref"}],"versionMetadata":{"versionNumber":"catalog-v2"}}
            """, MediaType.APPLICATION_JSON));

    PricingBffQuoteServiceLoanPassClient client = new PricingBffQuoteServiceLoanPassClient(builder, "https://quote-service.test", null, null);

    var summary = client.executeSummary("tenant-a", "run-a", "trace-a", List.of(new CreditApplicationField("field@state", new CreditApplicationValue("string", "TX", null, null))));
    var product = client.executeProduct("tenant-a", "run-a", "durable-product", "trace-a", List.of(new CreditApplicationField("field@state", new CreditApplicationValue("string", "TX", null, null))));

    assertThat(summary.totals().approved()).isEqualTo(1);
    assertThat(summary.totals().noPricing()).isEqualTo(1);
    assertThat(summary.products().get(0).status()).containsEntry("type", "approved");
    assertThat(summary.products().get(0).calculatedFields()).extracting(CreditApplicationField::fieldId)
        .contains("field@quote-service-rates", "field@quote-service-stipulations", "field@quote-service-calculations");
    assertThat(product.status()).containsEntry("type", "approved");
    assertThat(product.productFields()).extracting(CreditApplicationField::fieldId).contains("field@quote-service-rules");
    assertThat(product.calculatedFields()).extracting(CreditApplicationField::fieldId)
        .contains("field@quote-service-rates", "field@quote-service-stipulations", "field@quote-service-calculations");
    assertThat(product.metadata()).containsEntry("operation", "execute-product");
    server.verify();
  }

  @Test
  void adapterPersistsLaunchedQuickQuoteFactsForOffersAndDetailCalls() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    String serviceTenant = PricingBffQuoteServiceLoanPassClient.DEFAULT_LOANHOUSE_TENANT_ID;
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-summary"))
        .andExpect(header("X-Tenant-ID", serviceTenant))
        .andExpect(jsonPath("$.creditApplicationFields[0].fieldId").value("field@channel"))
        .andExpect(jsonPath("$.creditApplicationFields[2].fieldId").value("field@base-loan-amount"))
        .andExpect(jsonPath("$.creditApplicationFields[3].fieldId").value("field@state"))
        .andExpect(jsonPath("$.creditApplicationFields[4].fieldId").value("field@zip"))
        .andRespond(withSuccess("""
            {"success":true,"statusCounts":{"approved":1},"products":[{"productId":"100014","productName":"Expanded Prime Plus 30 Year Fixed","investorName":"OB","status":"approved","rates":[{"noteRatePercent":"7.37500","priceBps":"99.9340","lockPeriodDays":"60"}],"rules":[{"ruleId":"rule-ref"}],"stipulations":[{"stipulationId":"stip-ref"}],"calculations":{"monthlyPi":"2762.71","adjustedPrice":"99.934"},"sourceRefs":{"source_url":"https://lhposb2bbff1prod.loanhouse.us/api/v1/no-oauth/quickpricer/get-generic-quote-summary","product_id":"100014","source_index":"378","investor_name":"OB"},"versionMetadata":{"schemaVersion":"loanhouse-product-records-v1"}}],"versionMetadata":{"schemaVersion":"loanhouse-product-records-v1"}}
            """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://quote-service.test/api/v1/loanpass/execute-product"))
        .andExpect(header("X-Tenant-ID", serviceTenant))
        .andExpect(jsonPath("$.creditApplicationFields[2].fieldId").value("field@base-loan-amount"))
        .andExpect(jsonPath("$.creditApplicationFields[3].fieldId").value("field@state"))
        .andExpect(jsonPath("$.creditApplicationFields[4].fieldId").value("field@zip"))
        .andRespond(withSuccess("""
            {"success":true,"productId":"100014","productName":"Expanded Prime Plus 30 Year Fixed","investorName":"OB","status":"approved","rates":[{"noteRatePercent":"7.37500","priceBps":"99.9340","lockPeriodDays":"60"}],"rules":[{"ruleId":"rule-ref"}],"stipulations":[{"stipulationId":"stip-ref"}],"calculations":{"monthlyPi":"2762.71","adjustedPrice":"99.934"},"sourceRefs":{"source_url":"https://lhposb2bbff1prod.loanhouse.us/api/v1/no-oauth/quickpricer/get-generic-quote-summary","product_id":"100014","source_index":"378","investor_name":"OB"},"versionMetadata":{"schemaVersion":"loanhouse-product-records-v1"}}
            """, MediaType.APPLICATION_JSON));
    PricingBffUiFallbackAdapter adapter = new PricingBffUiFallbackAdapter(
        new PricingBffQuoteServiceLoanPassClient(builder, "https://quote-service.test", null, null));
    Map<String, Object> intake = new LinkedHashMap<>();
    intake.put("channel", "retail");
    intake.put("loanPurpose", "purchase");
    intake.put("baseLoanAmount", "loan-amount-ref");
    intake.put("state", "TX");
    intake.put("zip", "78701");
    intake.put("decisionCreditScore", "credit-score-ref");
    intake.put("documentationType", "full-documentation");
    intake.put("mortgageType", "conventional");
    intake.put("desiredLoanTerm", "30-year");
    intake.put("desiredAmortizationType", "fixed");
    intake.put("numberOfUnits", "1");

    PricingBffUiFallbackAdapter.QuoteRunLaunch launch = adapter.launchQuoteRun("ui-preview-tenant", "trace-launch", intake).getBody();
    PricingBffUiFallbackAdapter.OfferComparisonView offers = adapter.offerComparison("ui-preview-tenant", launch.runId(), "trace-offers");
    PricingBffUiFallbackAdapter.QuoteDetailView detail = adapter.quoteDetail("ui-preview-tenant", launch.runId(), "100014", "trace-detail");

    assertThat(launch.missingContractBlockers()).isEmpty();
    assertThat(launch.backendFactRefs()).doesNotContain("fact:borrowerLastName", "fact:loanNumber");
    assertThat(launch.backendFactRefs()).contains("fact:quoteAddressDTO.state", "fact:quoteAddressDTO.zip");
    assertThat(offers.offers()).hasSize(1);
    assertThat(offers.offers().get(0).sourceLabel()).isEqualTo("LoanHouse capture");
    assertThat(offers.offers().get(0).sourceRefs()).anyMatch(ref -> ref.contains("loanhouse"));
    assertThat(offers.offers().get(0).rate()).isEqualTo("7.37500");
    assertThat(offers.offers().get(0).price()).isEqualTo("99.934");
    assertThat(offers.offers().get(0).payment()).isEqualTo("2762.71");
    assertThat(offers.offers().get(0).lockPeriodDays()).isEqualTo("60");
    assertThat(offers.offers().get(0).rateRefs()).contains("field@quote-service-rates");
    assertThat(detail.summary().productRuleRefs()).contains("field@quote-service-rules");
    assertThat(detail.summary().stipulationRefs()).contains("field@quote-service-stipulations");
    server.verify();
  }
}

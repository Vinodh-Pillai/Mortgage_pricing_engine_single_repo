package com.wcpe.pricingbff.los;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricingbff.los.LosFeatureFlagService.LoanPassTenantFlags;
import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
  @Autowired LosPricingService losPricingService;
  @Autowired LosFeatureFlagService featureFlagService;

  @BeforeEach
  void configureLoanPassTenantFlags() {
    featureFlagService.clear();
    featureFlagService.configure(flags("tenant-los", true, true, true, 2));
  }

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
  void pricingRequestWithoutScenarioReferenceCreatesQuoteJobWithoutPipelineScenarioContext() {
    CapturingQuoteClient quoteClient = new CapturingQuoteClient();
    LosFeatureFlagService flagService = new LosFeatureFlagService();
    flagService.configure(flags("tenant-los", true, true, true, 2));
    LosPricingService service = new LosPricingService(new ObjectMapper(), new LosScenarioAdapter(), quoteClient,
        new LosLockServiceClient(), new LosWebhookRegistry(new ObjectMapper(), false), new LosIdempotencyStore(), flagService);

    LosApiModels.LosPricingResponse response = service.createPricingRequest(sampleRequest("los-no-scenario", null, null),
        "idem-no-scenario", "corr-no-scenario");

    assertThat(response.status()).isEqualTo("ACCEPTED");
    assertThat(quoteClient.lastRequest.scenarioId()).isNull();
    assertThat(quoteClient.lastRequest.scenarioVersion()).isZero();
    assertThat(quoteClient.lastRequest.clientContext())
        .containsEntry("pipelineScenarioLinked", "false")
        .containsEntry("requestSnapshotRef", "los-request-snapshot:tenant-los:los-no-scenario")
        .containsEntry("mappingConfigRef", "los-mapping-config:tenant-los");
  }

  @Test
  void pricingRequestWithScenarioReferencePassesNonMutatingReferenceOnly() {
    CapturingQuoteClient quoteClient = new CapturingQuoteClient();
    LosFeatureFlagService flagService = new LosFeatureFlagService();
    flagService.configure(flags("tenant-los", true, true, true, 2));
    LosPricingService service = new LosPricingService(new ObjectMapper(), new LosScenarioAdapter(), quoteClient,
        new LosLockServiceClient(), new LosWebhookRegistry(new ObjectMapper(), false), new LosIdempotencyStore(), flagService);

    service.createPricingRequest(sampleRequest("los-with-scenario", "scenario-los-123", 3),
        "idem-with-scenario", "corr-with-scenario");

    assertThat(quoteClient.lastRequest.scenarioId()).isEqualTo("scenario-los-123");
    assertThat(quoteClient.lastRequest.scenarioVersion()).isEqualTo(3);
    assertThat(quoteClient.lastRequest.clientContext())
        .containsEntry("pipelineScenarioLinked", "true")
        .containsEntry("scenarioRef", "scenario-los-123")
        .containsEntry("scenarioVersion", "3");
  }

  @Test
  void loanPassIdempotencyHeaderIsAccepted() throws Exception {
    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("Idempotency-Key", "loanpass-idem-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-idempotency-key")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.quoteJobId").isNotEmpty())
        .andExpect(jsonPath("$.statusUrl").isNotEmpty());
  }

  @Test
  void numericEnumValuesFailClosedWithoutConfiguredMapping() throws Exception {
    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("Idempotency-Key", "numeric-enum-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-numeric-enum").replace("\"propertyInformationType\": \"single-family\"", "\"propertyInformationType\": \"101\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOANPASS_ENUM_MAPPING_REQUIRED"))
        .andExpect(jsonPath("$.message").value("propertyInformationType uses a numeric LoanPass code without configured mapping metadata"));
  }

  @Test
  void disabledTenantLoanPassCompatibilityRejectsLosEndpointBeforeMutation() throws Exception {
    featureFlagService.configure(flags("tenant-los", false, true, false, 3));

    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("Idempotency-Key", "compat-disabled-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-compat-disabled")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LOS_COMPATIBILITY_DISABLED"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("tenant-feature-flags:tenant-los:v3")));

    assertThat(losPricingService.getPricingRequest("los-req-compat-disabled").status()).isEqualTo("NOT_FOUND");
  }

  @Test
  void strictMappingCanBeVersionedOffByTenantConfig() throws Exception {
    featureFlagService.configure(flags("tenant-los", true, false, true, 4));

    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("Idempotency-Key", "strict-disabled-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-strict-disabled").replace("\"propertyInformationType\": \"single-family\"", "\"propertyInformationType\": \"101\"")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void productSpecificFieldsFailClosedWithoutTenantAuthorizationMetadata() throws Exception {
    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("Idempotency-Key", "product-auth-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-product-auth").replace("\"callbackUrl\": \"https://los.example.test/pricing/callback\",", "\"callbackUrl\": \"https://los.example.test/pricing/callback\",\n          \"productId\": \"prod-unauthorized\",")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("LOANPASS_PRODUCT_AUTHORIZATION_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Tenant product, selectedProgramId, and priceGroup authorization metadata is not configured; pricing was not started"));
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

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-price-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-webhook")))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    losPricingService.completePricingRequest(body.path("pricingRequestId").asText(), body.path("quoteJobId").asText(),
        List.of("product-result-ref"), List.of(), "corr-los-test");

    assertThat(webhookRegistry.deliveries()).anyMatch(receipt -> receipt.eventType().equals("pricing.completed")
        && receipt.status().equals("QUEUED") && receipt.attemptCount() == 0);
  }

  @Test
  void initialAcceptedPricingRequestDoesNotDispatchCompletedCallback() throws Exception {
    mockMvc.perform(post("/api/v1/los/webhooks")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-reg-initial-accepted")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":\"tenant-los\",\"url\":\"https://los.example.test/webhooks-initial\",\"events\":[\"pricing.completed\"],\"signingCredentialRef\":\"tenant-signing-ref-initial\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-initial-accepted-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-webhook-initial-accepted")))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    assertThat(webhookRegistry.deliveries().stream()
        .filter(receipt -> receipt.eventType().equals("pricing.completed"))
        .filter(receipt -> receipt.idempotencyKey().contains(body.path("quoteJobId").asText()))
        .filter(receipt -> receipt.idempotencyKey().contains("ACCEPTED"))
        .toList()).isEmpty();
  }

  @Test
  void asyncQuoteCompletionWebhookIsSignedAndIdempotent() throws Exception {
    mockMvc.perform(post("/api/v1/los/webhooks")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-reg-signed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":\"tenant-los\",\"url\":\"https://los.example.test/webhooks\",\"events\":[\"pricing.completed\"],\"signingCredentialRef\":\"tenant-signing-ref\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.secret").doesNotExist())
        .andExpect(jsonPath("$.signingCredentialRef").doesNotExist());

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-signed-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-webhook-signed")))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    losPricingService.completePricingRequest(body.path("pricingRequestId").asText(), body.path("quoteJobId").asText(),
        List.of("product-result-ref"), List.of(), "corr-los-test");

    var matching = webhookRegistry.deliveries().stream()
        .filter(receipt -> receipt.eventType().equals("pricing.completed"))
        .filter(receipt -> receipt.idempotencyKey().contains("COMPLETED"))
        .filter(receipt -> receipt.idempotencyKey().contains(body.path("quoteJobId").asText()))
        .toList();
    assertThat(matching).anySatisfy(receipt -> {
      assertThat(receipt.status()).isEqualTo("QUEUED");
      assertThat(receipt.signatureHeader()).startsWith("sha256=");
      assertThat(receipt.auditRef()).startsWith("webhook-delivery:");
    });
  }

  @Test
  void webhookDeliveryBlocksWhenSigningConfigMissing() throws Exception {
    mockMvc.perform(post("/api/v1/los/webhooks")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-reg-missing-signing")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":\"tenant-los\",\"url\":\"https://los.example.test/webhooks-missing\",\"events\":[\"pricing.completed\"]}"))
        .andExpect(status().isCreated());

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-missing-signing-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-webhook-missing-signing")))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    losPricingService.completePricingRequest(body.path("pricingRequestId").asText(), body.path("quoteJobId").asText(),
        List.of(), List.of(), "corr-los-test");

    assertThat(webhookRegistry.deliveries().stream()
        .filter(receipt -> receipt.eventType().equals("pricing.completed"))
        .filter(receipt -> "BLOCKED".equals(receipt.status()))
        .filter(receipt -> receipt.idempotencyKey().contains(body.path("quoteJobId").asText()))
        .toList())
        .anySatisfy(receipt -> assertThat(receipt.lastError()).contains("signing credential"));
  }

  @Test
  void callbackDeliveryDisabledRecordsAuditableDisabledReceiptWithoutExternalAttempt() throws Exception {
    featureFlagService.configure(flags("tenant-los", true, true, false, 5));
    mockMvc.perform(post("/api/v1/los/webhooks")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-reg-callback-disabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":\"tenant-los\",\"url\":\"https://los.example.test/webhooks-disabled\",\"events\":[\"pricing.completed\"],\"signingCredentialRef\":\"tenant-signing-ref\"}"))
        .andExpect(status().isCreated());

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "callback-disabled-001")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-callback-disabled")))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    var receipts = losPricingService.completePricingRequest(body.path("pricingRequestId").asText(), body.path("quoteJobId").asText(),
        List.of("product-result-ref"), List.of(), "corr-los-test");

    assertThat(receipts).anySatisfy(receipt -> {
      assertThat(receipt.status()).isEqualTo("DISABLED");
      assertThat(receipt.attemptCount()).isZero();
      assertThat(receipt.auditRef()).contains("tenant-feature-flags:audit:tenant-los:v5");
      assertThat(receipt.lastError()).contains("callback delivery disabled");
    });
  }

  @Test
  void lockCallbacksHonorTenantCallbackDeliveryDisabledFlag() throws Exception {
    featureFlagService.configure(flags("tenant-los", true, true, false, 6));
    mockMvc.perform(post("/api/v1/los/webhooks")
            .headers(Headers.auth())
            .header("X-Request-ID", "webhook-reg-lock-callback-disabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"tenantId\":\"tenant-los\",\"url\":\"https://los.example.test/webhooks-lock-disabled\",\"events\":[\"lock.confirmed\",\"lock.extended\"],\"signingCredentialRef\":\"tenant-signing-ref\"}"))
        .andExpect(status().isCreated());

    MvcResult accepted = mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(Headers.auth())
            .header("X-Request-ID", "lock-callback-disabled-pricing")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-lock-callback-disabled")))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode body = objectMapper.readTree(accepted.getResponse().getContentAsString());
    MvcResult lockResult = mockMvc.perform(post("/api/v1/los/locks")
            .headers(Headers.auth())
            .header("X-Request-ID", "lock-callback-disabled-confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "pricingRequestId": "%s",
                  "offerId": "offer-lock-callback-disabled",
                  "lockPeriodDays": 30,
                  "requestedBy": "loan-officer"
                }
                """.formatted(body.path("pricingRequestId").asText())))
        .andExpect(status().isAccepted())
        .andReturn();

    JsonNode lockBody = objectMapper.readTree(lockResult.getResponse().getContentAsString());
    String lockId = lockBody.path("lockId").asText();
    assertThat(webhookRegistry.deliveries().stream()
        .filter(receipt -> receipt.eventType().equals("lock.confirmed"))
        .filter(receipt -> receipt.idempotencyKey().contains(body.path("pricingRequestId").asText()))
        .toList())
        .anySatisfy(receipt -> {
          assertThat(receipt.status()).isEqualTo("DISABLED");
          assertThat(receipt.attemptCount()).isZero();
          assertThat(receipt.auditRef()).contains("tenant-feature-flags:audit:tenant-los:v6");
          assertThat(receipt.lastError()).contains("callback delivery disabled");
        });

    mockMvc.perform(post("/api/v1/los/locks/{id}/extend", lockId)
            .headers(Headers.auth())
            .header("X-Request-ID", "lock-callback-disabled-extend")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"extendByDays\":7,\"requestedBy\":\"loan-officer\",\"reason\":\"borrower requested extension\"}"))
        .andExpect(status().isOk());

    assertThat(webhookRegistry.deliveries().stream()
        .filter(receipt -> receipt.eventType().equals("lock.extended"))
        .filter(receipt -> receipt.idempotencyKey().contains(body.path("pricingRequestId").asText()))
        .toList())
        .anySatisfy(receipt -> {
          assertThat(receipt.status()).isEqualTo("DISABLED");
          assertThat(receipt.attemptCount()).isZero();
          assertThat(receipt.auditRef()).contains("tenant-feature-flags:audit:tenant-los:v6");
          assertThat(receipt.lastError()).contains("callback delivery disabled");
        });
  }

  @Test
  void mTLSAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/los/pricing-requests/missing")
            .header("X-LOS-System", "ENCOMPASS")
            .header("X-LOS-Scopes", "los:pricing-request:read")
            .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[] { new StubCertificate() }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NOT_FOUND"));
  }

  @Test
  void endpointScopeMatrixDocumentsRepresentativeLosPermissions() {
    assertThat(LosAuthFilter.requiredScopesFor("POST", "/api/v1/los/pricing-requests"))
        .containsExactly("los:pricing-request:write");
    assertThat(LosAuthFilter.requiredScopesFor("GET", "/api/v1/los/products/search"))
        .containsExactly("los:product-catalog:read");
    assertThat(LosAuthFilter.requiredScopesFor("POST", "/api/v1/los/product-eligibility"))
        .containsExactly("los:product-eligibility:write");
    assertThat(LosAuthFilter.configuredScopeMatrix()).anySatisfy(entry ->
        assertThat(entry).contains("POST ^/api/v1/los/webhooks$ -> los:webhook:write"));
  }

  @Test
  void missingRequiredScopeRejectsBeforePricingMutation() throws Exception {
    org.springframework.http.HttpHeaders headers = Headers.auth();
    headers.set("X-LOS-Scopes", "los:pricing-request:read");

    mockMvc.perform(post("/api/v1/los/pricing-requests")
            .headers(headers)
            .header("X-Request-ID", "missing-write-scope")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPricingRequest("los-req-missing-write-scope")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LOS_SCOPE_REQUIRED"))
        .andExpect(jsonPath("$.message").value("los:pricing-request:write scope is required for POST /api/v1/los/pricing-requests"));

    assertThat(losPricingService.getPricingRequest("los-req-missing-write-scope").status()).isEqualTo("NOT_FOUND");
  }

  @Test
  void missingScopeDefinitionFailsClosedBeforeUnknownLosEndpoint() throws Exception {
    mockMvc.perform(get("/api/v1/los/unmapped-management-endpoint")
            .headers(Headers.auth()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LOS_SCOPE_DEFINITION_REQUIRED"));
  }

  @Test
  void serviceAccountScopesAreCheckedSeparatelyFromInteractiveScopes() throws Exception {
    mockMvc.perform(get("/api/v1/los/pricing-requests/service-status-001")
            .header("X-LOS-System", "ENCOMPASS")
            .header("Authorization", "Bearer service-token")
            .header("X-LOS-Service-Account", "true")
            .header("X-LOS-Scopes", "los:pricing-request:read"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("LOS_SERVICE_SCOPE_REQUIRED"));

    mockMvc.perform(get("/api/v1/los/pricing-requests/service-status-001")
            .header("X-LOS-System", "ENCOMPASS")
            .header("Authorization", "Bearer service-token")
            .header("X-LOS-Service-Account", "true")
            .header("X-LOS-Service-Scopes", "los:pricing-request:read"))
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

  @Test
  void productCatalogFailsClosedWhenMetadataUnavailable() throws Exception {
    mockMvc.perform(get("/api/v1/los/products")
            .headers(Headers.auth())
            .header("X-Tenant-ID", "tenant-los")
            .param("channel", "retail")
            .param("page", "0")
            .param("pageSize", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products.length()").value(0))
        .andExpect(jsonPath("$.count").value(0))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.pageSize").value(10))
        .andExpect(jsonPath("$.authorizationStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.blockedReason").value("CATALOG_METADATA_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.metadata.source").value("fail-closed"))
        .andExpect(jsonPath("$.metadata.requestedFilters.channel").value("retail"));
  }

  @Test
  void productCatalogValidatesPagination() throws Exception {
    mockMvc.perform(get("/api/v1/los/products")
            .headers(Headers.auth())
            .header("X-Tenant-ID", "tenant-los")
            .param("page", "-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRODUCT_CATALOG_PAGINATION_INVALID"));
  }

  @Test
  void productSearchFailsClosedWithAppliedFiltersAndBoundedTextQuery() throws Exception {
    mockMvc.perform(get("/api/v1/los/products/search")
            .headers(Headers.auth())
            .header("X-Tenant-ID", "tenant-los")
            .param("productFamily", "CONVENTIONAL")
            .param("channel", "RETAIL")
            .param("investor", "FNMA")
            .param("loanPurpose", "PURCHASE")
            .param("propertyType", "SINGLE_FAMILY")
            .param("occupancy", "PRIMARY_RESIDENCE")
            .param("term", "360")
            .param("amortization", "FIXED")
            .param("effectiveDate", "2026-03-01")
            .param("q", "30 year fixed")
            .param("page", "1")
            .param("pageSize", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products.length()").value(0))
        .andExpect(jsonPath("$.authorizationStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.blockedReason").value("CATALOG_METADATA_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.metadata.endpoint").value("/api/v1/los/products/search"))
        .andExpect(jsonPath("$.metadata.searchScope").value("catalog-metadata-only"))
        .andExpect(jsonPath("$.metadata.appliedFilters.productFamily").value("CONVENTIONAL"))
        .andExpect(jsonPath("$.metadata.appliedFilters.query").value("30 year fixed"))
        .andExpect(jsonPath("$.metadata.appliedFilters.effectiveDate").value("2026-03-01"));
  }

  @Test
  void productSearchRejectsUnsupportedFilterConsistently() throws Exception {
    mockMvc.perform(get("/api/v1/los/products/search")
            .headers(Headers.auth())
            .header("X-Tenant-ID", "tenant-los")
            .param("loanPassInternalRulePayload", "restricted"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRODUCT_SEARCH_FILTER_UNSUPPORTED"));
  }

  @Test
  void productSearchValidatesEffectiveDate() throws Exception {
    mockMvc.perform(get("/api/v1/los/products/search")
            .headers(Headers.auth())
            .header("X-Tenant-ID", "tenant-los")
            .param("effectiveDate", "03/01/2026"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRODUCT_SEARCH_FILTER_INVALID"));
  }

  @Test
  void productDetailFailsClosedWithIncompleteMappingStatus() throws Exception {
    mockMvc.perform(get("/api/v1/los/products/lp-prod-001")
            .headers(Headers.auth())
            .header("X-Tenant-ID", "tenant-los"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.productId").value("lp-prod-001"))
        .andExpect(jsonPath("$.authorizationStatus").value("BLOCKED"))
        .andExpect(jsonPath("$.blockedReason").value("CATALOG_METADATA_NOT_CONFIGURED"))
        .andExpect(jsonPath("$.requiredFields.length()").value(0))
        .andExpect(jsonPath("$.conditionalFields.length()").value(0))
        .andExpect(jsonPath("$.mappingMetadataStatus").value("INCOMPLETE"))
        .andExpect(jsonPath("$.quoteCompatibility.status").value("BLOCKED"))
        .andExpect(jsonPath("$.metadata.endpoint").value("/api/v1/los/products/{productId}"));
  }

  @Test
  void productDetailDoesNotRevealInternalsWithoutTenantContext() throws Exception {
    mockMvc.perform(get("/api/v1/los/products/restricted-product")
            .headers(Headers.auth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRODUCT_DETAIL_NOT_FOUND"));
  }

  @Test
  void productEligibilityFailsClosedForExplicitProductsWhenAuthorizationConfigUnavailable() throws Exception {
    mockMvc.perform(post("/api/v1/los/product-eligibility")
            .headers(Headers.auth())
            .header("X-Request-ID", "product-eligibility-auth-unavailable")
            .header("X-Tenant-ID", "tenant-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "clientId": "los-client-1",
                  "productIds": ["restricted-product"],
                  "channel": "retail",
                  "investor": "FNMA",
                  "creditApplicationFields": [
                    { "fieldId": "field@base-loan-amount", "value": { "type": "number", "value": 450000 } }
                  ]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.results[0].productId").value("restricted-product"))
        .andExpect(jsonPath("$.results[0].eligibility").value("ineligible"))
        .andExpect(jsonPath("$.results[0].reasonCodes[0]").value("TENANT_PRODUCT_AUTHORIZATION_UNAVAILABLE"))
        .andExpect(jsonPath("$.results[0].ruleConfigRefs[0].source").value("catalog-service"))
        .andExpect(jsonPath("$.metadata.authorizationMetadataStatus").value("UNAVAILABLE"));
  }

  @Test
  void productEligibilityReportsMissingMappedFieldsWithRequestPath() throws Exception {
    mockMvc.perform(post("/api/v1/los/product-eligibility")
            .headers(Headers.auth())
            .header("X-Request-ID", "product-eligibility-missing-fields")
            .header("X-Tenant-ID", "tenant-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productIds": ["lp-prod-001"],
                  "productFamily": "CONVENTIONAL",
                  "channel": "retail"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INCOMPLETE"))
        .andExpect(jsonPath("$.results[0].eligibility").value("requires_more_information"))
        .andExpect(jsonPath("$.results[0].fieldMessages[0].requestPath").value("$.creditApplicationFields"))
        .andExpect(jsonPath("$.results[0].fieldMessages[0].reasonCode").value("LOANPASS_MAPPED_FIELDS_REQUIRED"));
  }

  @Test
  void productEligibilityReportsUnmappedEnumWithRequestPathAndFieldId() throws Exception {
    mockMvc.perform(post("/api/v1/los/product-eligibility")
            .headers(Headers.auth())
            .header("X-Request-ID", "product-eligibility-numeric-enum")
            .header("X-Tenant-ID", "tenant-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productIds": ["lp-prod-001"],
                  "creditApplicationFields": [
                    { "fieldId": "field@loan-purpose", "value": { "type": "enum", "enumTypeId": "loan-purpose", "variantId": "101", "value": "101" } }
                  ]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INCOMPLETE"))
        .andExpect(jsonPath("$.results[0].eligibility").value("requires_more_information"))
        .andExpect(jsonPath("$.results[0].fieldMessages[0].requestPath").value("$.creditApplicationFields[0].value"))
        .andExpect(jsonPath("$.results[0].fieldMessages[0].fieldId").value("field@loan-purpose"))
        .andExpect(jsonPath("$.results[0].fieldMessages[0].reasonCode").value("LOANPASS_ENUM_MAPPING_REQUIRED"));
  }

  @Test
  void productEligibilityAcceptsProductFamilyOnlyFilter() throws Exception {
    mockMvc.perform(post("/api/v1/los/product-eligibility")
            .headers(Headers.auth())
            .header("X-Request-ID", "product-eligibility-family-only")
            .header("X-Tenant-ID", "tenant-los")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "productFamily": "CONVENTIONAL",
                  "channel": "retail",
                  "creditApplicationFields": [
                    { "fieldId": "field@base-loan-amount", "value": { "type": "number", "value": 450000 } }
                  ]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BLOCKED"))
        .andExpect(jsonPath("$.results.length()").value(0))
        .andExpect(jsonPath("$.metadata.requestedContext.productFamily").value("CONVENTIONAL"))
        .andExpect(jsonPath("$.metadata.requestedContext.productIds.length()").value(0));
  }

  @Test
  void productEligibilityRequiresTenantMapping() throws Exception {
    mockMvc.perform(post("/api/v1/los/product-eligibility")
            .headers(Headers.auth())
            .header("X-Request-ID", "product-eligibility-missing-tenant")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"productIds\":[\"lp-prod-001\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_TENANT_MAPPING"))
        .andExpect(jsonPath("$.message").value("tenantId is required at $.tenantId or X-Tenant-ID before product eligibility can be evaluated"));
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

  private LosApiModels.LosPricingRequest sampleRequest(String requestId, String scenarioId, Integer scenarioVersion) {
    return new LosApiModels.LosPricingRequest(requestId, "tenant-los", "https://los.example.test/pricing/callback",
        null, null, null, scenarioId, scenarioVersion,
        new LosApiModels.LoanPassBorrowerInfo("Rivera", "LN-001", 1),
        new LosApiModels.LoanPassAddress(null, null, "TX", "78701", null, null, null, null),
        new java.math.BigDecimal("450000"), null, null, "purchase", "single-family", "primary-residence", 1,
        "full-documentation", null, null, null, null, 745, "conventional", "fixed", "30-year", 30, "30-day",
        "retail", List.of(new LosApiModels.CreditApplicationField("field@base-loan-amount",
            new LosApiModels.CreditApplicationValue("number", 450000, null, null))));
  }

  private static LoanPassTenantFlags flags(String tenantId, boolean compatibilityEnabled, boolean strictMappingEnabled,
      boolean callbackDeliveryEnabled, int version) {
    return new LoanPassTenantFlags(tenantId, compatibilityEnabled, strictMappingEnabled, callbackDeliveryEnabled,
        "tenant-feature-flags:" + tenantId + ":v" + version,
        "tenant-feature-flags:audit:" + tenantId + ":v" + version,
        version, Instant.parse("2026-06-18T08:00:00Z"));
  }

  private static class CapturingQuoteClient extends LosQuoteServiceClient {
    private LosApiModels.QuoteServiceRequest lastRequest;

    CapturingQuoteClient() {
      super(org.springframework.web.client.RestClient.builder(), "");
    }

    @Override
    LosApiModels.QuoteServiceResponse submitQuoteJob(LosApiModels.QuoteServiceRequest request) {
      this.lastRequest = request;
      return new LosApiModels.QuoteServiceResponse("job-" + request.requestId(), "QUEUED",
          "/api/v1/los/quote-requests/job-" + request.requestId(), request.correlationId());
    }
  }

  private static final class Headers {
    static org.springframework.http.HttpHeaders auth() {
      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.add("X-LOS-System", "ENCOMPASS");
      headers.add("X-LOS-Version", "24.1");
      headers.add("X-Correlation-ID", "corr-los-test");
      headers.add("Authorization", "Bearer local-test-token");
      headers.add("X-LOS-Scopes", String.join(" ",
          "los:pricing-request:write",
          "los:pricing-request:read",
          "los:product-catalog:read",
          "los:product-eligibility:write",
          "los:lock:write",
          "los:lock:read",
          "los:webhook:write"));
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

package com.wcpe.catalog;

import com.wcpe.catalog.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OverlayResolutionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("catalog")
            .withUsername("catalog_app")
            .withPassword("catalog_app");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TestRestTemplate restTemplate;

    UUID tenantId;
    String productCode = "CONV30";

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        seedProduct();
    }

    @Test
    void happyPath_singleOverlay_mergesAttributes() {
        createOverlay("interestRate", "3.5", LocalDate.now(), null);
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, "FNMA", "RETAIL", null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().appliedOverlays()).hasSize(1);
        assertThat(resp.getBody().resolvedAttributes()).containsKey("interestRate");
        assertThat(resp.getBody().resolvedAttributes().get("interestRate")).isEqualTo("3.5");
    }

    @Test
    void noOverlays_returnsBaseAttributesOnly() {
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().appliedOverlays()).isEmpty();
        assertThat(resp.getBody().baseAttributes()).containsKey("product_code");
        assertThat(resp.getBody().baseAttributes().get("product_code")).isEqualTo(productCode);
    }

    @Test
    void expiredOverlay_isIgnored() {
        LocalDate past = LocalDate.now().minusDays(100);
        createOverlay("interestRate", "3.5", past, past.plusDays(1));
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().appliedOverlays()).isEmpty();
    }

    @Test
    void multipleActiveOverlays_appliedInPrecedenceOrder() {
        createOverlay("interestRate", "3.5", LocalDate.now().minusDays(30), null);
        createOverlay("interestRate", "3.99", LocalDate.now().minusDays(1), null);
        createOverlay("lenderFee", "250", LocalDate.now(), null);
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().appliedOverlays()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(resp.getBody().resolvedAttributes().get("interestRate")).isEqualTo("3.99");
    }

    @Test
    void futureDatedOverlay_notAppliedYet() {
        LocalDate future = LocalDate.now().plusDays(30);
        createOverlay("interestRate", "3.5", future, null);
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().appliedOverlays()).isEmpty();
    }

    @Test
    void overlayFieldOverwritesBaseField() {
        createOverlay("product_name", "Modified Product Name", LocalDate.now(), null);
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().baseAttributes().get("product_name")).isNotEqualTo("Modified Product Name");
        assertThat(resp.getBody().resolvedAttributes().get("product_name")).isEqualTo("Modified Product Name");
    }

    @Test
    void overlayAddsNewFieldNotInBase() {
        createOverlay("customRateCap", "5.0", LocalDate.now(), null);
        OverlayResolveRequest req = new OverlayResolveRequest(productCode, null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().baseAttributes()).doesNotContainKey("customRateCap");
        assertThat(resp.getBody().resolvedAttributes()).containsKey("customRateCap");
        assertThat(resp.getBody().resolvedAttributes().get("customRateCap")).isEqualTo("5.0");
    }

    @Test
    void invalidOverlayProduct_returnsEmptyResult() {
        OverlayResolveRequest req = new OverlayResolveRequest("NONEXISTENT_PRODUCT", null, null, null);
        HttpHeaders headers = requestHeaders();

        ResponseEntity<OverlayResolveResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays/resolve",
                new HttpEntity<>(req, headers), OverlayResolveResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().baseAttributes()).isEmpty();
        assertThat(resp.getBody().appliedOverlays()).isEmpty();
    }

    void seedProduct() {
        HttpHeaders wHeaders = new HttpHeaders();
        wHeaders.set("X-Roles", "CATALOG_WRITER");
        wHeaders.set("X-Actor-Id", "actor-1");
        wHeaders.set("Idempotency-Key", "prod-" + UUID.randomUUID());
        wHeaders.setContentType(MediaType.APPLICATION_JSON);
        ProductRequest pbody = new ProductRequest(productCode, "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/conventional-products/drafts",
                new HttpEntity<>(pbody, wHeaders), CatalogResponse.class);
    }

    void createOverlay(String attribute, String overrideValue, LocalDate effectiveDate, LocalDate expiryDate) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "CATALOG_ADMIN");
        headers.set("X-Actor-Id", "actor-overlay");
        headers.setContentType(MediaType.APPLICATION_JSON);
        OverlayCreateRequest req = new OverlayCreateRequest(productCode, attribute, overrideValue,
                effectiveDate, expiryDate, "test reason", "overlay-" + UUID.randomUUID());
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/products/overlays",
                new HttpEntity<>(req, headers), OverlayCreateResponse.class);
    }

    HttpHeaders requestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", "CATALOG_READER");
        headers.set("X-Actor-Id", "actor-1");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

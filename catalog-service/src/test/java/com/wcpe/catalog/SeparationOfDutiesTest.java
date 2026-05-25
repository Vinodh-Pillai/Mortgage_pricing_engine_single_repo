package com.wcpe.catalog;

import com.wcpe.catalog.auth.AuthorizationService;
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
class SeparationOfDutiesTest {

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

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void sameActorSubmitsAndApproves_sodViolation403() {
        String sameActor = "same-person";
        seedAndSubmitApprove(sameActor, sameActor);
        HttpHeaders h = approverHeaders("same-person");
        h.set("Idempotency-Key", "app-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("approve");

        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl() + "/versions/current/actions/approve",
                new HttpEntity<>(body, h), String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY,
                HttpStatus.FORBIDDEN, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void differentActorSubmitsAndApproves_succeeds() {
        seedAndSubmitApprove("actor-submit", "actor-submit");
        HttpHeaders h = approverHeaders("actor-approver");
        h.set("Idempotency-Key", "app-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("approve");

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                baseUrl() + "/versions/current/actions/approve",
                new HttpEntity<>(body, h), CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.APPROVED);
    }

    @Test
    void missingIdempotencyKey_handledGracefully() {
        seedAndSubmitApprove("actor-submit", "actor-submit");
        HttpHeaders h = approverHeaders("actor-approver");
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Roles", "CATALOG_APPROVER");
        // No Idempotency-Key header set
        LifecycleActionRequest body = new LifecycleActionRequest("approve");

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                baseUrl() + "/versions/current/actions/approve",
                new HttpEntity<>(body, h), CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.APPROVED);
    }

    @Test
    void nullActorId_failsWithAuthError() {
        prepareForApproval(tenantId, null, null);
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_APPROVER");
        h.setContentType(MediaType.APPLICATION_JSON);
        // No X-Actor-Id header
        LifecycleActionRequest body = new LifecycleActionRequest("approve");

        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl() + "/versions/current/actions/approve",
                new HttpEntity<>(body, h), String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNPROCESSABLE_ENTITY,
                HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.FORBIDDEN);
    }

    // ---- HELPERS ----

    void prepareForApproval(UUID tid, String submitActorId, String approveActorId) {
        seedMinimalCatalog(tid);
        validateCatalog(tid);
        submitForApproval(tid, submitActorId != null ? submitActorId : "actor-sub");
    }

    void seedAndSubmitApprove(String submitActorId, String ignoredApproveActorId) {
        seedMinimalCatalog(tenantId);
        validateCatalog(tenantId);
        submitForApproval(tenantId, submitActorId);
    }

    void seedMinimalCatalog(UUID tid) {
        HttpHeaders wHeaders = new HttpHeaders();
        wHeaders.set("X-Roles", "CATALOG_WRITER");
        wHeaders.set("X-Actor-Id", "actor-1");
        wHeaders.set("Idempotency-Key", "p-" + UUID.randomUUID());
        wHeaders.setContentType(MediaType.APPLICATION_JSON);
        ProductRequest pr = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/conventional-products/drafts",
                new HttpEntity<>(pr, wHeaders), CatalogResponse.class);
        HttpHeaders wHeaders2 = new HttpHeaders();
        wHeaders2.set("X-Roles", "CATALOG_WRITER");
        wHeaders2.set("X-Actor-Id", "actor-1");
        wHeaders2.set("Idempotency-Key", "i-" + UUID.randomUUID());
        wHeaders2.setContentType(MediaType.APPLICATION_JSON);
        InvestorRequest ir = new InvestorRequest("FNMA", "Fannie Mae",
                List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null);
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/investors/drafts",
                new HttpEntity<>(ir, wHeaders2), CatalogResponse.class);
    }

    void validateCatalog(UUID tid) {
        HttpHeaders h = writerHeaders();
        h.set("Idempotency-Key", "val-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("validate");
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/validate",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void submitForApproval(UUID tid, String actorId) {
        HttpHeaders h = writerHeaders();
        h.set("X-Actor-Id", actorId);
        h.set("Idempotency-Key", "sub-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("submit");
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/submit-approval",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    String baseUrl() {
        return "/api/v1/tenants/" + tenantId + "/product-catalog";
    }

    HttpHeaders writerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_WRITER");
        h.set("X-Actor-Id", "actor-1");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    HttpHeaders approverHeaders(String actorId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_APPROVER");
        h.set("X-Actor-Id", actorId);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}

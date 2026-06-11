package com.wcpe.catalog.domain;

import com.wcpe.catalog.auth.AuthorizationService;
import com.wcpe.catalog.auth.CatalogRoles;
import com.wcpe.catalog.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RBACAuthorizationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("catalog")
            .withUsername("catalog_app")
            .withPassword("catalog_app");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        DockerClientFactory.instance().client();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    AuthorizationService authorizationService;

    @Autowired
    TestRestTemplate restTemplate;

    UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        seedCatalogData();
    }

    @Test
    void test01_postProduct_requires_CATALOG_WRITER() {
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-1");
        headers.set("Idempotency-Key", "prod-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ProductRequest body = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/conventional-products/drafts",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().products()).hasSize(1);
    }

    @Test
    void test01b_postProduct_REJECTED_without_CATALOG_WRITER() {
        String roles = "CATALOG_APPROVER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-1");
        headers.set("Idempotency-Key", "prod-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ProductRequest body = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/conventional-products/drafts",
                entity, String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test02_postInvestor_requires_CATALOG_WRITER() {
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-1");
        headers.set("Idempotency-Key", "inv-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        InvestorRequest body = new InvestorRequest("FNMA", "Fannie Mae",
                List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null);
        HttpEntity<InvestorRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/investors/drafts",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void test02b_postInvestor_REJECTED_without_CATALOG_WRITER() {
        String roles = "CATALOG_APPROVER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-1");
        headers.set("Idempotency-Key", "inv-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        InvestorRequest body = new InvestorRequest("FNMA2", "Fannie Mae2",
                List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null);
        HttpEntity<InvestorRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/investors/drafts",
                entity, String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test03_approve_requires_CATALOG_APPROVER() {
        submitAndApprove(tenantId, "CATALOG_APPROVER", "actor-a");
    }

    @Test
    void test03a_approve_allows_CATALOG_MANAGER() {
        submitAndApprove(tenantId, "CATALOG_MANAGER", "actor-manager-approve");
    }

    @Test
    void test03b_approve_REJECTED_for_write_Role() {
        prepareForApproval(tenantId);
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "other-actor");
        headers.set("Idempotency-Key", "approve-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        LifecycleActionRequest body = new LifecycleActionRequest("test approve");
        HttpEntity<LifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/versions/current/actions/approve",
                entity, String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test04_publish_requires_CATALOG_PUBLISHER() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        bringToApproved(tid);
        String roles = "CATALOG_PUBLISHER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-pub");
        headers.set("Idempotency-Key", "pub-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        PublishCatalogRequest body = new PublishCatalogRequest("initial", LocalDate.now());
        HttpEntity<PublishCatalogRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/publish",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.PUBLISHED);
    }

    @Test
    void test04a_publish_allows_CATALOG_MANAGER() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        bringToApproved(tid);
        HttpHeaders headers = headers("CATALOG_MANAGER");
        headers.set("X-Actor-Id", "actor-manager-publish");
        headers.set("Idempotency-Key", "pub-" + UUID.randomUUID());

        PublishCatalogRequest body = new PublishCatalogRequest("manager publish", LocalDate.now());
        HttpEntity<PublishCatalogRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/publish",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.PUBLISHED);
    }

    @Test
    void test04b_publish_REJECTED_for_writer() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        bringToApproved(tid);
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-w");
        headers.set("Idempotency-Key", "pub-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        PublishCatalogRequest body = new PublishCatalogRequest("initial", LocalDate.now());
        HttpEntity<PublishCatalogRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/publish",
                entity, String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test05_rollback_requires_CATALOG_ADMIN() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        String roles = "CATALOG_ADMIN";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "admin");
        headers.set("Idempotency-Key", "rb-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        VersionedLifecycleActionRequest body = new VersionedLifecycleActionRequest("rollback test", null);
        HttpEntity<VersionedLifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/rollback",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.ROLLED_BACK);
    }

    @Test
    void test05b_rollback_REJECTED_for_non_admin() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-w");
        headers.set("Idempotency-Key", "rb-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        VersionedLifecycleActionRequest body = new VersionedLifecycleActionRequest("rollback test", null);
        HttpEntity<VersionedLifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/rollback",
                entity, String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test06_getProducts_requires_CATALOG_READER_or_auth() {
        String roles = "CATALOG_READER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/products",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void test06b_getProducts_REJECTED_for_NONE() {
        String roles = "NONE";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/products",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test07_reject_requires_CATALOG_APPROVER() {
        prepareForApproval(tenantId);
        String roles = "CATALOG_APPROVER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-reject");
        headers.set("Idempotency-Key", "reject-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        RejectCatalogRequest body = new RejectCatalogRequest("not good enough");
        HttpEntity<RejectCatalogRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/versions/current/actions/reject",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.REJECTED);
    }

    @Test
    void test08_draft_requires_CATALOG_WRITER() {
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-1");
        headers.set("Idempotency-Key", "draft-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        LifecycleActionRequest body = new LifecycleActionRequest("new draft");
        HttpEntity<LifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/versions/current/actions/draft",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void test09_submitApproval_requires_CATALOG_WRITER() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        validateCatalog(tid);
        String roles = "CATALOG_WRITER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-1");
        headers.set("Idempotency-Key", "sub-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        LifecycleActionRequest body = new LifecycleActionRequest("submitting for approval");
        HttpEntity<LifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/submit-approval",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void test09a_write_flow_allows_CATALOG_MANAGER() {
        HttpHeaders headers = headers("CATALOG_MANAGER");
        headers.set("X-Actor-Id", "actor-manager-write");
        headers.set("Idempotency-Key", "prod-" + UUID.randomUUID());

        ProductRequest body = new ProductRequest("CONV30M", "Conventional 30yr Manager", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + UUID.randomUUID() + "/product-catalog/conventional-products/drafts",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().products()).hasSize(1);
    }

    @Test
    void test10_getVersions_requires_CATALOG_READER() {
        String roles = "CATALOG_READER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/versions",
                HttpMethod.GET, new HttpEntity<>(headers), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void test10b_getVersions_REJECTED_without_READ_CATALOG() {
        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/versions",
                HttpMethod.GET, new HttpEntity<>(headers("NONE")), String.class);

        assertThat(resp.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test10c_snapshot_requires_READ_CATALOG() {
        UUID tid = UUID.randomUUID();
        ProductConfigSnapshot snapshot = publishedSnapshot(tid);

        ResponseEntity<ProductConfigSnapshot> allowed = restTemplate.exchange(
                "/api/v1/tenants/" + tid + "/product-catalog/config-snapshots/" + snapshot.snapshotId(),
                HttpMethod.GET, new HttpEntity<>(headers("CATALOG_READER")), ProductConfigSnapshot.class);
        ResponseEntity<String> rejected = restTemplate.exchange(
                "/api/v1/tenants/" + tid + "/product-catalog/config-snapshots/" + snapshot.snapshotId(),
                HttpMethod.GET, new HttpEntity<>(headers("NONE")), String.class);

        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test10d_events_requires_READ_CATALOG() {
        ResponseEntity<List> allowed = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/events",
                HttpMethod.GET, new HttpEntity<>(headers("CATALOG_READER")), List.class);
        ResponseEntity<String> rejected = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/events",
                HttpMethod.GET, new HttpEntity<>(headers("NONE")), String.class);

        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test10e_audit_requires_READ_CATALOG() {
        ResponseEntity<List> allowed = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/audit",
                HttpMethod.GET, new HttpEntity<>(headers("CATALOG_READER")), List.class);
        ResponseEntity<String> rejected = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/product-catalog/audit",
                HttpMethod.GET, new HttpEntity<>(headers("NONE")), String.class);

        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void test11_suspend_requires_CATALOG_PUBLISHER() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        bringToPublished(tid);
        String roles = "CATALOG_PUBLISHER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-sus");
        headers.set("Idempotency-Key", "sus-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        LifecycleActionRequest body = new LifecycleActionRequest("suspend");
        HttpEntity<LifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/suspend",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.SUSPENDED);
    }

    @Test
    void test12_retire_requires_CATALOG_PUBLISHER() {
        UUID tid = UUID.randomUUID();
        seedMinimalCatalog(tid);
        bringToPublished(tid);
        String roles = "CATALOG_PUBLISHER";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.set("X-Actor-Id", "actor-ret");
        headers.set("Idempotency-Key", "ret-" + UUID.randomUUID());
        headers.setContentType(MediaType.APPLICATION_JSON);

        LifecycleActionRequest body = new LifecycleActionRequest("retire");
        HttpEntity<LifecycleActionRequest> entity = new HttpEntity<>(body, headers);

        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/retire",
                entity, CatalogResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CatalogStatus.RETIRED);
    }

    // ---- HELPERS ----

    HttpHeaders headers(String roles) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Roles", roles);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    ProductConfigSnapshot publishedSnapshot(UUID tid) {
        seedMinimalCatalog(tid);
        bringToPublished(tid);
        HttpHeaders headers = headers("CATALOG_READER");
        headers.set("X-Actor-Id", "actor-reader");
        headers.set("Idempotency-Key", "snap-" + UUID.randomUUID());
        ResolveCatalogRequest request = new ResolveCatalogRequest(LocalDate.now(), "RETAIL", "TX",
                "CONVENTIONAL", "FNMA", null, null, null, null, null);
        ResponseEntity<ProductConfigSnapshot> response = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/config-snapshots/resolve",
                new HttpEntity<>(request, headers), ProductConfigSnapshot.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    void seedCatalogData() {
        HttpHeaders wHeaders = new HttpHeaders();
        wHeaders.set("X-Roles", "CATALOG_WRITER");
        wHeaders.set("X-Actor-Id", "actor-1");
        wHeaders.set("Idempotency-Key", "prod-" + UUID.randomUUID());
        wHeaders.setContentType(MediaType.APPLICATION_JSON);
        ProductRequest body = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tenantId + "/product-catalog/conventional-products/drafts",
                new HttpEntity<>(body, wHeaders), CatalogResponse.class);
    }

    void seedMinimalCatalog(UUID tid) {
        HttpHeaders wHeaders = new HttpHeaders();
        wHeaders.set("X-Roles", "CATALOG_WRITER");
        wHeaders.set("X-Actor-Id", "actor-1");
        wHeaders.set("Idempotency-Key", "p-" + UUID.randomUUID());
        wHeaders.setContentType(MediaType.APPLICATION_JSON);
        ProductRequest pbody = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/conventional-products/drafts",
                new HttpEntity<>(pbody, wHeaders), CatalogResponse.class);
        HttpHeaders wHeaders2 = new HttpHeaders();
        wHeaders2.set("X-Roles", "CATALOG_WRITER");
        wHeaders2.set("X-Actor-Id", "actor-1");
        wHeaders2.set("Idempotency-Key", "i-" + UUID.randomUUID());
        wHeaders2.setContentType(MediaType.APPLICATION_JSON);
        InvestorRequest ibody = new InvestorRequest("FNMA", "Fannie Mae",
                List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null);
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/investors/drafts",
                new HttpEntity<>(ibody, wHeaders2), CatalogResponse.class);
    }

    void validateCatalog(UUID tid) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_WRITER");
        h.set("X-Actor-Id", "actor-1");
        h.set("Idempotency-Key", "val-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        LifecycleActionRequest body = new LifecycleActionRequest("validate");
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/validate",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void submitForApproval(UUID tid, String submitActorId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_WRITER");
        h.set("X-Actor-Id", submitActorId);
        h.set("Idempotency-Key", "sub-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        LifecycleActionRequest body = new LifecycleActionRequest("submit");
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/submit-approval",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void prepareForApproval(UUID tid) {
        seedMinimalCatalog(tid);
        validateCatalog(tid);
        submitForApproval(tid, "actor-1");
    }

    void submitAndApprove(UUID tid, String roles, String approverActorId) {
        seedMinimalCatalog(tid);
        validateCatalog(tid);
        submitForApproval(tid, "actor-submit");
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", roles);
        h.set("X-Actor-Id", approverActorId);
        h.set("Idempotency-Key", "app-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        LifecycleActionRequest body = new LifecycleActionRequest("approve");
        ResponseEntity<CatalogResponse> resp = restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/approve",
                new HttpEntity<>(body, h), CatalogResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    void bringToApproved(UUID tid) {
        validateCatalog(tid);
        submitForApproval(tid, "actor-submit");
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_APPROVER");
        h.set("X-Actor-Id", "actor-app");
        h.set("Idempotency-Key", "app-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        LifecycleActionRequest body = new LifecycleActionRequest("approve");
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/approve",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void bringToPublished(UUID tid) {
        bringToApproved(tid);
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_PUBLISHER");
        h.set("X-Actor-Id", "actor-pub");
        h.set("Idempotency-Key", "pub-" + UUID.randomUUID());
        h.setContentType(MediaType.APPLICATION_JSON);
        PublishCatalogRequest body = new PublishCatalogRequest("publish", LocalDate.now());
        restTemplate.postForEntity(
                "/api/v1/tenants/" + tid + "/product-catalog/versions/current/actions/publish",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }
}

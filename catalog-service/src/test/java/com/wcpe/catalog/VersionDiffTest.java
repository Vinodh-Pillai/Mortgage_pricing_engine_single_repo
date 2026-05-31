package com.wcpe.catalog.domain;

import com.wcpe.catalog.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VersionDiffTest {

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

    @Autowired
    JdbcTemplate jdbc;

    UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void added_artifactExistsInV2NotV1() {
        tenantId = UUID.randomUUID();
        publishV1();
        int v1 = currentVersion();
        addProductInNewVersion("NEWPROD", "New Product");
        publishV2();
        int v2 = currentVersion();

        DiffResponse diff = getDiff(v1, v2);

        assertThat(diff).isNotNull();
        Optional<VersionDiff> added = diff.diffs().stream()
                .filter(d -> "ADDED".equals(d.diffType())).findAny();
        assertThat(added).isPresent();
        assertThat(diff.diffCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void modified_attributeChangedBetweenVersions() {
        tenantId = UUID.randomUUID();
        publishV1();
        int v1 = currentVersion();
        modifyProductInNewVersion("CONV30", "Modified Name");
        publishV2();
        int v2 = currentVersion();

        DiffResponse diff = getDiff(v1, v2);

        assertThat(diff).isNotNull();
        Optional<VersionDiff> modified = diff.diffs().stream()
                .filter(d -> "MODIFIED".equals(d.diffType())).findAny();
        assertThat(modified).isPresent();
        assertThat(modified.get().oldValue()).isNotEqualTo(modified.get().newValue());
    }

    @Test
    void removed_artifactRetiredBetweenVersions() {
        tenantId = UUID.randomUUID();
        publishV1();
        int v1 = currentVersion();
        retireProductInNewVersion("CONV30");
        publishV2();
        int v2 = currentVersion();

        DiffResponse diff = getDiff(v1, v2);

        assertThat(diff).isNotNull();
        Optional<VersionDiff> removed = diff.diffs().stream()
                .filter(d -> "REMOVED".equals(d.diffType())).findAny();
        assertThat(removed).isPresent();
    }

    @Test
    void sameVersion_diffIsEmpty() {
        tenantId = UUID.randomUUID();
        publishV1();
        int v = currentVersion();

        DiffResponse diff = getDiff(v, v);

        assertThat(diff).isNotNull();
        assertThat(diff.diffCount()).isZero();
        assertThat(diff.diffs()).isEmpty();
    }

    @Test
    void adjacentVersions_diff() {
        tenantId = UUID.randomUUID();
        publishV1();
        int v1 = currentVersion();
        addProductInNewVersion("ADJPROD", "Adjacent Product");
        publishV2();
        int v2 = currentVersion();

        assertThat(v2).isEqualTo(v1 + 1);

        DiffResponse diff = getDiff(v1, v2);
        assertThat(diff).isNotNull();
        assertThat(diff.versionAMeta().get("version")).isEqualTo(v1);
        assertThat(diff.versionBMeta().get("version")).isEqualTo(v2);
    }

    @Test
    void nonAdjacentVersions_skipV2_compareV1VsV3() {
        tenantId = UUID.randomUUID();
        publishV1();
        int v1 = currentVersion();
        addProductInNewVersion("V2PROD", "Version 2 Product");
        publishV2();
        int v2 = currentVersion();
        addProductInNewVersion("V3PROD", "Version 3 Product");
        publishV2();
        int v3 = currentVersion();

        assertThat(v3).isGreaterThan(v1 + 1);

        DiffResponse diff = getDiff(v1, v3);
        assertThat(diff).isNotNull();
        assertThat(diff.diffCount()).isGreaterThanOrEqualTo(1);
    }

    // ---- HELPERS ----

    void seedMinimalData() {
        HttpHeaders h = writerHeaders();
        h.set("Idempotency-Key", "prod-seed-" + UUID.randomUUID());
        ProductRequest body = new ProductRequest("CONV30", "Conventional 30yr", "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        restTemplate.postForEntity(baseUrl() + "/conventional-products/drafts",
                new HttpEntity<>(body, h), CatalogResponse.class);
        HttpHeaders h2 = writerHeaders();
        h2.set("Idempotency-Key", "inv-seed-" + UUID.randomUUID());
        InvestorRequest ibody = new InvestorRequest("FNMA", "Fannie Mae",
                List.of("RETAIL"), List.of("CONV30"), LocalDate.now(), null);
        restTemplate.postForEntity(baseUrl() + "/investors/drafts",
                new HttpEntity<>(ibody, h2), CatalogResponse.class);
    }

    void validate() {
        HttpHeaders h = writerHeaders();
        h.set("Idempotency-Key", "val-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("validate");
        restTemplate.postForEntity(baseUrl() + "/versions/current/actions/validate",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void submitForApproval() {
        HttpHeaders h = writerHeaders();
        h.set("Idempotency-Key", "sub-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("submit");
        restTemplate.postForEntity(baseUrl() + "/versions/current/actions/submit-approval",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void approve() {
        HttpHeaders h = approverHeaders();
        h.set("Idempotency-Key", "app-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("approve");
        restTemplate.postForEntity(baseUrl() + "/versions/current/actions/approve",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void publish() {
        HttpHeaders h = publisherHeaders();
        h.set("Idempotency-Key", "pub-" + UUID.randomUUID());
        PublishCatalogRequest body = new PublishCatalogRequest("publish", LocalDate.now());
        restTemplate.postForEntity(baseUrl() + "/versions/current/actions/publish",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void publishV1() {
        seedMinimalData();
        validate();
        submitForApproval();
        approve();
        publish();
    }

    void addProductInNewVersion(String code, String name) {
        startNewDraft();
        HttpHeaders h = writerHeaders();
        h.set("Idempotency-Key", "prod-new-" + UUID.randomUUID());
        ProductRequest body = new ProductRequest(code, name, "CONVENTIONAL",
                List.of("RETAIL"), List.of("TX"), LocalDate.now(), null);
        restTemplate.postForEntity(baseUrl() + "/conventional-products/drafts",
                new HttpEntity<>(body, h), CatalogResponse.class);
    }

    void modifyProductInNewVersion(String code, String newName) {
        startNewDraft();
        String sql = "update catalog.product_definition set product_name = ? where tenant_id = ? and product_code = ?";
        jdbc.update(sql, newName, tenantId, code);
    }

    void retireProductInNewVersion(String code) {
        startNewDraft();
        String sql = "delete from catalog.product_definition where tenant_id = ? and product_code = ?";
        jdbc.update(sql, tenantId, code);
    }

    void publishV2() {
        validate();
        submitForApproval();
        approve();
        publish();
    }

    void startNewDraft() {
        HttpHeaders h = writerHeaders();
        h.set("Idempotency-Key", "draft-" + UUID.randomUUID());
        LifecycleActionRequest body = new LifecycleActionRequest("new draft");
        try {
            restTemplate.postForEntity(baseUrl() + "/versions/current/actions/draft",
                    new HttpEntity<>(body, h), CatalogResponse.class);
        } catch (Exception e) {
            // If draft fails (not in a rollbackable state), create fresh tables manually
        }
    }

    int currentVersion() {
        String sql = "select version from catalog.product_catalog where tenant_id = ? order by updated_at desc limit 1";
        return jdbc.queryForObject(sql, Integer.class, tenantId);
    }

    DiffResponse getDiff(int v1, int v2) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_READER");
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<DiffResponse> resp = restTemplate.exchange(
                "/api/v1/tenants/" + tenantId + "/catalog/diff?version_a=" + v1 + "&version_b=" + v2,
                HttpMethod.GET, new HttpEntity<>(h), DiffResponse.class);
        return resp.getBody();
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

    HttpHeaders approverHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_APPROVER");
        h.set("X-Actor-Id", "actor-approver");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    HttpHeaders publisherHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Roles", "CATALOG_PUBLISHER");
        h.set("X-Actor-Id", "actor-publisher");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}

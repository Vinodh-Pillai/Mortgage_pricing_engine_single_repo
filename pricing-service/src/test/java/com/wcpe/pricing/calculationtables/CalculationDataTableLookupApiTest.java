package com.wcpe.pricing.calculationtables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationDataTableLookupRepository;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationLookupReference;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CalculationLookupValidationRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.CreateLookupRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.EditLookupOptionsRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.InMemoryCalculationDataTableLookupRepository;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupCreateResponse;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupHeaders;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupOptionDraft;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupPublishResponse;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupReferenceValidationStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupRuntimeStatus;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupValueRequest;
import com.wcpe.pricing.calculationtables.CalculationDataTableLookupApi.LookupVersionStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculationDataTableLookupApiTest {
    private static final String TENANT = "tenant-a";
    private static final String TABLE_ID = "county-adjustment-table";

    private CalculationDataTableLookupRepository repository;
    private CalculationDataTableLookupApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCalculationDataTableLookupRepository();
        api = new CalculationDataTableLookupApi(repository);
    }

    @Test
    void createLookupStoresIdentityKeysOptionsAndTenantScope() {
        LookupCreateResponse response = api.createLookup(TENANT, writeHeaders(), lookupCreateRequest("factor-a"));

        assertEquals(TABLE_ID, response.tableId());
        assertEquals(1, response.versionNumber());
        assertEquals(LookupVersionStatus.DRAFT, response.status());
        assertEquals(List.of("state", "county"), response.keyFields());
        assertEquals("tenant-a", response.tenantScope());
        assertEquals(1, response.optionCount());
        assertTrue(repository.findVersion(TENANT, response.versionId()).orElseThrow().options()
                .containsValue(new CalculationDataTableLookupApi.LookupOption(
                        Map.of("state", "IL", "county", "COOK"), "factor-a", "COOK County option")));
    }

    @Test
    void editedLookupOptionsPublishAsAuditableNewVersion() {
        LookupCreateResponse created = api.createLookup(TENANT, writeHeaders(), lookupCreateRequest("factor-a"));
        LookupPublishResponse firstPublish = api.publishLookupOptions(TENANT, publishHeaders(), created.versionId());

        var edited = api.editLookupOptions(TENANT, writeHeaders(), TABLE_ID,
                new EditLookupOptionsRequest(List.of(option("IL", "COOK", "factor-b")), "caller-supplied update"));
        LookupPublishResponse secondPublish = api.publishLookupOptions(TENANT, publishHeaders(), edited.versionId());

        assertEquals(1, firstPublish.versionNumber());
        assertEquals(2, secondPublish.versionNumber());
        assertEquals(LookupVersionStatus.PUBLISHED, secondPublish.status());
        assertEquals(List.of("pricing.calculation-lookup-created.v1", "pricing.calculation-lookup-published.v1",
                "pricing.calculation-lookup-options-edited.v1", "pricing.calculation-lookup-published.v1"),
                repository.events().stream().map(CalculationDataTableLookupApi.LookupAuditEvent::eventType).toList());
    }

    @Test
    void validationFailsWhenCalculationReferenceMissesPublishedTableOrRequiredKeys() {
        LookupCreateResponse created = api.createLookup(TENANT, writeHeaders(), lookupCreateRequest("factor-a"));
        api.publishLookupOptions(TENANT, publishHeaders(), created.versionId());

        var result = api.validateCalculationReferences(TENANT, readHeaders(), new CalculationLookupValidationRequest(
                "calc@manual-factor",
                List.of(
                        new CalculationLookupReference(TABLE_ID, Set.of("state")),
                        new CalculationLookupReference("missing-table", Set.of("state", "county")))));

        assertEquals(LookupReferenceValidationStatus.INVALID, result.status());
        assertEquals(List.of("MISSING_REQUIRED_KEYS:county-adjustment-table:county", "TABLE_NOT_FOUND:missing-table"),
                result.errors());
    }

    @Test
    void missingLookupValueReturnsBlockedMissingDataWithoutInventedValue() {
        LookupCreateResponse created = api.createLookup(TENANT, writeHeaders(), lookupCreateRequest("factor-a"));
        api.publishLookupOptions(TENANT, publishHeaders(), created.versionId());

        var result = api.lookupValue(TENANT, readHeaders(),
                new LookupValueRequest(TABLE_ID, Map.of("state", "IL", "county", "DUPAGE")));

        assertEquals(LookupRuntimeStatus.BLOCKED_MISSING_DATA, result.status());
        assertEquals("LOOKUP_VALUE_MISSING", result.missingReason());
        assertNull(result.value());
    }

    @Test
    void publishedLookupReturnsCallerSuppliedOptionOnlyOnExactKeyMatch() {
        LookupCreateResponse created = api.createLookup(TENANT, writeHeaders(), lookupCreateRequest("factor-a"));
        api.publishLookupOptions(TENANT, publishHeaders(), created.versionId());

        var result = api.lookupValue(TENANT, readHeaders(),
                new LookupValueRequest(TABLE_ID, Map.of("state", "IL", "county", "COOK")));

        assertEquals(LookupRuntimeStatus.FOUND, result.status());
        assertEquals("factor-a", result.value());
    }

    private static CreateLookupRequest lookupCreateRequest(String value) {
        return new CreateLookupRequest(TABLE_ID, "County adjustment lookup", List.of("state", "county"),
                List.of(option("IL", "COOK", value)), TENANT, "caller-managed lookup table");
    }

    private static LookupOptionDraft option(String state, String county, String value) {
        return new LookupOptionDraft(Map.of("state", state, "county", county), value, county + " County option");
    }

    private static LookupHeaders writeHeaders() {
        return new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_ADMIN_WRITE_PERMISSION),
                "pricing-admin-1", "corr-lookup-write");
    }

    private static LookupHeaders publishHeaders() {
        return new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_ADMIN_PUBLISH_PERMISSION),
                "pricing-publisher-1", "corr-lookup-publish");
    }

    private static LookupHeaders readHeaders() {
        return new LookupHeaders(Set.of(CalculationDataTableLookupApi.LOOKUP_READ_PERMISSION),
                "calculation-runner", "corr-lookup-read");
    }
}

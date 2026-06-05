package com.wcpe.pricing.baserate;

import com.wcpe.pricing.baserate.BaseRateSelectionApi.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PII-05-S03 Base Pricing Grid Management")
class BasePricingGridManagementTest {
    private static final String TENANT = "tenant-a";
    private static final String PRODUCT = "CONVENTIONAL";
    private static final String INVESTOR = "SYNTH-INVESTOR";
    private static final String CHANNEL = "RETAIL";
    private static final Instant EFFECTIVE_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EFFECTIVE_TO = Instant.parse("2027-01-01T00:00:00Z");

    private InMemoryBaseRateSelectionRepository repository;
    private BaseRateSelectionApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBaseRateSelectionRepository();
        api = new BaseRateSelectionApi(repository);
    }

    @Test
    @DisplayName("GridImportValidator_rejectsDuplicateRowKey")
    void GridImportValidator_rejectsDuplicateRowKey() {
        BaseGridImportRequest duplicateRows = validImportRequest(List.of(
                row(30, "6.12500", "100.00000"),
                row(30, "6.12500", "100.00000")));

        BaseRateSelectionValidationException ex = assertThrows(BaseRateSelectionValidationException.class,
                () -> api.importBaseGrid(TENANT, importHeaders(), duplicateRows));

        assertEquals("GRID_DUPLICATE_ROW", ex.getMessage());
    }

    @Test
    @DisplayName("GridImportValidator_rejectsInvalidScale")
    void GridImportValidator_rejectsInvalidScale() {
        BaseGridImportRequest invalidScale = validImportRequest(List.of(row(30, "6.125001", "100.00000")));

        BaseRateSelectionValidationException ex = assertThrows(BaseRateSelectionValidationException.class,
                () -> api.importBaseGrid(TENANT, importHeaders(), invalidScale));

        assertEquals("GRID_SCALE_INVALID", ex.getMessage());
    }

    @Test
    @DisplayName("BaseGridLookupService_returnsSinglePublishedRow")
    void BaseGridLookupService_returnsSinglePublishedRow() {
        BaseGridImportResponse imported = api.importBaseGrid(TENANT, importHeaders(),
                validImportRequest(List.of(row(30, "6.12500", "100.00000"))));

        BaseGridValidationResult validation = api.validateBaseGrid(TENANT, importHeaders(), imported.gridVersionId());
        assertEquals(GridImportStatus.VALIDATED, validation.status());

        BaseGridPublishResponse published = api.publishBaseGrid(TENANT, publishHeaders(), imported.gridVersionId());
        assertEquals(GridVersionStatus.PUBLISHED, published.status());

        BaseGridLookupResponse lookup = api.lookupBaseGridRow(TENANT, readHeaders(), new BaseGridLookupRequest(
                PRODUCT, INVESTOR, CHANNEL, 30, new BigDecimal("6.12500"),
                Instant.parse("2026-06-01T00:00:00Z"), Map.of()));

        assertEquals(imported.gridVersionId(), lookup.gridVersionId());
        assertEquals(new BigDecimal("100.00000"), lookup.basePrice());
        assertEquals("default", lookup.bucketKeyHash());
        assertTrue(repository.wasGridCacheInvalidated(TENANT, imported.gridVersionId()));
        assertTrue(repository.gridEvents().stream().anyMatch(event -> "pricing.base-grid-published.v1".equals(event.eventType())));
    }

    @Test
    @DisplayName("BaseGridPublication_preventsOverlappingPublishedWindows")
    void BaseGridPublication_preventsOverlappingPublishedWindows() {
        UUID publishedVersion = importValidatePublish(List.of(row(30, "6.12500", "100.00000")));
        assertNotNull(publishedVersion);

        BaseGridImportResponse second = api.importBaseGrid(TENANT, importHeaders(),
                validImportRequest(List.of(row(30, "6.25000", "100.50000"))));
        api.validateBaseGrid(TENANT, importHeaders(), second.gridVersionId());

        BaseRateSelectionConflictException ex = assertThrows(BaseRateSelectionConflictException.class,
                () -> api.publishBaseGrid(TENANT, publishHeaders(), second.gridVersionId()));

        assertEquals("GRID_EFFECTIVE_WINDOW_OVERLAP", ex.getMessage());
    }

    @Test
    @DisplayName("BaseGridCache_invalidatesOnPublish")
    void BaseGridCache_invalidatesOnPublish() {
        UUID versionId = importValidatePublish(List.of(row(30, "6.12500", "100.00000")));

        assertTrue(repository.wasGridCacheInvalidated(TENANT, versionId));
    }

    private UUID importValidatePublish(List<BaseGridRowDraft> rows) {
        BaseGridImportResponse imported = api.importBaseGrid(TENANT, importHeaders(), validImportRequest(rows));
        api.validateBaseGrid(TENANT, importHeaders(), imported.gridVersionId());
        api.publishBaseGrid(TENANT, publishHeaders(), imported.gridVersionId());
        return imported.gridVersionId();
    }

    private static BaseGridImportRequest validImportRequest(List<BaseGridRowDraft> rows) {
        return new BaseGridImportRequest(PRODUCT, INVESTOR, CHANNEL, EFFECTIVE_FROM, EFFECTIVE_TO,
                "CSV", "synthetic-digest-1", true, Set.of(30, 45, 60), Set.of("purpose"), rows);
    }

    private static BaseGridRowDraft row(int lockPeriodDays, String noteRate, String basePrice) {
        return new BaseGridRowDraft(lockPeriodDays, new BigDecimal(noteRate), new BigDecimal(basePrice), Map.of());
    }

    private static BaseRateSelectionHeaders importHeaders() {
        return new BaseRateSelectionHeaders(Set.of(BaseRateSelectionApi.GRID_IMPORT_PERMISSION),
                "importer-1", "corr-1", "idem-import-1");
    }

    private static BaseRateSelectionHeaders publishHeaders() {
        return new BaseRateSelectionHeaders(Set.of(BaseRateSelectionApi.GRID_PUBLISH_PERMISSION),
                "publisher-1", "corr-1", "idem-publish-1");
    }

    private static BaseRateSelectionHeaders readHeaders() {
        return new BaseRateSelectionHeaders(Set.of(BaseRateSelectionApi.GRID_READ_PERMISSION),
                "reader-1", "corr-1", null);
    }
}

package com.wcpe.adjustment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wcpe.adjustment.FeeCatalogVersion.CatalogStatus;
import com.wcpe.adjustment.FeeCatalogVersion.EffectiveWindow;
import com.wcpe.adjustment.FeeCatalogVersion.FeeCatalogEvent;
import com.wcpe.adjustment.FeeCatalogVersion.FeeCatalogRequestContext;
import com.wcpe.adjustment.FeeCatalogVersion.FeeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeeCatalogLifecycleTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000066");
    private static final UUID CATALOG_ID = UUID.fromString("20000000-0000-0000-0000-000000000066");
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void publishRequiresSeparationOfDutiesAndCreatesEventAuditMetadata() {
        FeeCatalogVersion draft = draftCatalog(List.of(FeeCatalogTestFixtures.fixedAmountFee("CONFIGURED_FEE_A")));

        assertThat(draft.validateDefinitions()).isEmpty();
        assertThat(draft.contentHash()).hasSize(64);
        assertThatThrownBy(() -> draft.publish("requester-1", Instant.parse("2026-01-02T00:00:00Z"), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requester cannot publish");

        FeeCatalogVersion published = draft.publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());
        FeeCatalogEvent event = published.event(
            "FeeCatalogPublished",
            "approver-1",
            "correlation-1",
            "causation-1",
            "idem-1",
            UUID.fromString("40000000-0000-0000-0000-000000000066"),
            Instant.parse("2026-01-02T00:00:01Z"),
            "urn:wcpe:fee-catalog-snapshot:10000000-0000-0000-0000-000000000066:20000000-0000-0000-0000-000000000066:1"
        );

        assertThat(published.status()).isEqualTo(CatalogStatus.PUBLISHED);
        assertThat(published.approvedBy()).isEqualTo("approver-1");
        assertThat(event.topic()).isEqualTo("pricing.fees.catalog.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.schemaVersion()).isEqualTo("pricing.fees.catalog.v1");
        assertThat(event.sourceService()).isEqualTo("adjustment-service");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-01-02T00:00:01Z"));
        assertThat(event.effectiveWindow()).isEqualTo(published.effectiveWindow());
        assertThat(event.snapshotUri()).startsWith("urn:wcpe:fee-catalog-snapshot:");
        assertThat(event.snapshotHash()).isEqualTo(published.contentHash());
        assertThat(event.feeCodeListHash()).hasSize(64);
        assertThat(event.feeCodes()).containsExactly("CONFIGURED_FEE_A");
        assertThat(published.audit("FEE_CATALOG_PUBLISHED", "approver-1", "correlation-1", draft.contentHash()).afterHash())
            .isEqualTo(published.contentHash());
    }

    @Test
    void publishedCatalogsAreImmutableAndCannotOverlapForTenant() {
        FeeCatalogVersion draft = draftCatalog(List.of(FeeCatalogTestFixtures.fixedAmountFee("CONFIGURED_FEE_A")));
        FeeCatalogVersion published = draft.publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());

        assertThatThrownBy(() -> published.replaceDraftDefinitions(List.of(FeeCatalogTestFixtures.waivedFee("CONFIGURED_FEE_B"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("immutable");
        assertThatThrownBy(() -> draft.publish("approver-2", Instant.parse("2026-01-03T00:00:00Z"), List.of(published)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overlaps existing catalog");
    }

    @Test
    void rollbackTransitionsPublishedCatalogAndProducesRollbackEventContract() {
        FeeCatalogVersion published = draftCatalog(List.of(FeeCatalogTestFixtures.fixedAmountFee("CONFIGURED_FEE_A")))
            .publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());

        FeeCatalogVersion rolledBack = published.rollback("approver-1", Instant.parse("2026-01-03T00:00:00Z"));
        FeeCatalogEvent event = rolledBack.event(
            "FeeCatalogRolledBack",
            "approver-1",
            "correlation-rollback-1",
            "causation-rollback-1",
            "idem-rollback-1",
            UUID.fromString("50000000-0000-0000-0000-000000000066"),
            Instant.parse("2026-01-03T00:00:01Z"),
            "urn:wcpe:fee-catalog-snapshot:10000000-0000-0000-0000-000000000066:20000000-0000-0000-0000-000000000066:1-rolled-back"
        );

        assertThat(rolledBack.status()).isEqualTo(CatalogStatus.ROLLED_BACK);
        assertThat(event.eventType()).isEqualTo("FeeCatalogRolledBack");
        assertThat(event.status()).isEqualTo("ROLLED_BACK");
        assertThat(event.key()).isEqualTo(TENANT_ID + ":" + CATALOG_ID + ":1");
        assertThat(event.feeCodes()).containsExactly("CONFIGURED_FEE_A");
        assertThatThrownBy(() -> FeeCatalogLifecycleTest.draftCatalog(List.of(FeeCatalogTestFixtures.fixedAmountFee("CONFIGURED_FEE_A")))
            .rollback("approver-1", Instant.parse("2026-01-03T00:00:00Z")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("only published or suspended fee catalogs can be rolled back");
    }

    @Test
    void resolvesPublishedCatalogAndMatchingFeeDefinitionForTenantContext() {
        FeeDefinition matching = FeeCatalogTestFixtures.fixedAmountFee("CONFIGURED_FEE_A");
        FeeCatalogVersion published = draftCatalog(List.of(matching))
            .publish("approver-1", Instant.parse("2026-01-02T00:00:00Z"), List.of());
        FeeCatalogRequestContext context = FeeCatalogTestFixtures.context(TENANT_ID);

        FeeCatalogVersion resolvedCatalog = FeeCatalogVersion.resolvePublished(TENANT_ID, context, List.of(published));

        assertThat(resolvedCatalog.catalogVersionId()).isEqualTo(CATALOG_ID);
        assertThat(resolvedCatalog.resolve("CONFIGURED_FEE_A", context).feeDefinitionId()).isEqualTo(matching.feeDefinitionId());
        assertThatThrownBy(() -> resolvedCatalog.resolve("CONFIGURED_FEE_A", FeeCatalogTestFixtures.context(UUID.randomUUID())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenant mismatch");
    }

    static FeeCatalogVersion draftCatalog(List<FeeDefinition> definitions) {
        return new FeeCatalogVersion(
            TENANT_ID,
            CATALOG_ID,
            1,
            CatalogStatus.DRAFT,
            new EffectiveWindow(START, null),
            definitions,
            "requester-1",
            null,
            null,
            null
        );
    }
}

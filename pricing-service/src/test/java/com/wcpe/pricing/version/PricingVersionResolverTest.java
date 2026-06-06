package com.wcpe.pricing.version;

import com.wcpe.pricing.version.PricingVersionResolver.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PII-05-S07 As-Of Pricing Version Resolution")
class PricingVersionResolverTest {
    private static final String TENANT = "tenant-a";
    private static final String PRODUCT = "CONVENTIONAL";
    private static final String INVESTOR = "SYNTH-INVESTOR";
    private static final String CHANNEL = "RETAIL";
    private static final Instant JAN_1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant APR_1 = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant JUL_1 = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant OCT_1 = Instant.parse("2026-10-01T00:00:00Z");

    private InMemoryVersionGraphRepository repository;
    private PricingVersionResolver resolver;

    @BeforeEach
    void setUp() {
        repository = new InMemoryVersionGraphRepository();
        resolver = new PricingVersionResolver(repository);
    }

    @Test
    @DisplayName("PricingVersionResolver_selectsEffectiveOpenEndedVersion")
    void PricingVersionResolver_selectsEffectiveOpenEndedVersion() {
        ArtifactVersion version = addVersion(ArtifactType.GRID, JAN_1, null, "PUBLISHED");

        VersionGraphResult result = resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(),
                request(APR_1, ArtifactType.GRID));

        assertEquals(APR_1, result.asOf());
        assertEquals(1, result.versionRefs().size());
        assertEquals(version.id(), result.versionRefs().get(0).versionId());
        assertEquals("GRID", result.versionRefs().get(0).artifactType());
        assertTrue(repository.findEventsByTenant(TENANT).contains("pricing.version-graph-resolved.v1"));
        assertEquals(1, repository.audits().size());
    }

    @Test
    @DisplayName("PricingVersionResolver_rejectsOverlap")
    void PricingVersionResolver_rejectsOverlap() {
        addVersion(ArtifactType.GRID, JAN_1, OCT_1, "PUBLISHED");
        addVersion(ArtifactType.GRID, APR_1, null, "PUBLISHED");

        VersionGraphConflictException ex = assertThrows(VersionGraphConflictException.class,
                () -> resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(), request(JUL_1, ArtifactType.GRID)));

        assertEquals("VERSION_AMBIGUOUS: multiple overlapping GRID versions", ex.getMessage());
    }

    @Test
    @DisplayName("PricingVersionResolver_blocksFutureAsOfWithoutPermission")
    void PricingVersionResolver_blocksFutureAsOfWithoutPermission() {
        addVersion(ArtifactType.GRID, JAN_1, null, "PUBLISHED");
        Instant futureAsOf = Instant.now().plusSeconds(3600);

        VersionGraphAccessDeniedException ex = assertThrows(VersionGraphAccessDeniedException.class,
                () -> resolver.resolveVersionGraph(TENANT, resolveHeaders(), request(futureAsOf, ArtifactType.GRID)));

        assertEquals("AS_OF_FUTURE_FORBIDDEN", ex.getMessage());
    }

    @Test
    @DisplayName("PricingVersionResolver_resolvesMultipleArtifactTypes")
    void PricingVersionResolver_resolvesMultipleArtifactTypes() {
        ArtifactVersion grid = addVersion(ArtifactType.GRID, JAN_1, null, "PUBLISHED");
        ArtifactVersion rounding = addVersion(ArtifactType.ROUNDING, JAN_1, null, "PUBLISHED");

        VersionGraphResult result = resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(),
                request(APR_1, ArtifactType.GRID, ArtifactType.ROUNDING));

        assertEquals(2, result.versionRefs().size());
        assertTrue(result.versionRefs().stream().anyMatch(ref -> ref.versionId().equals(grid.id())));
        assertTrue(result.versionRefs().stream().anyMatch(ref -> ref.versionId().equals(rounding.id())));
        assertTrue(result.versionRefs().stream().anyMatch(ref -> "GRID".equals(ref.artifactType())));
        assertTrue(result.versionRefs().stream().anyMatch(ref -> "ROUNDING".equals(ref.artifactType())));
    }

    @Test
    @DisplayName("PricingVersionResolver_selectsLatestWhenOpenEndedOverlap")
    void PricingVersionResolver_selectsLatestWhenOpenEndedOverlap() {
        addVersion(ArtifactType.GRID, JAN_1, APR_1, "PUBLISHED");
        ArtifactVersion latest = addVersion(ArtifactType.GRID, APR_1, null, "PUBLISHED");

        VersionGraphResult result = resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(),
                request(JUL_1, ArtifactType.GRID));

        assertEquals(1, result.versionRefs().size());
        assertEquals(latest.id(), result.versionRefs().get(0).versionId());
    }

    @Test
    @DisplayName("PricingVersionResolver_skipsSuspendedVersion")
    void PricingVersionResolver_skipsSuspendedVersion() {
        addVersion(ArtifactType.GRID, JAN_1, null, "SUSPENDED");
        ArtifactVersion published = addVersion(ArtifactType.GRID, JAN_1, null, "PUBLISHED");

        VersionGraphResult result = resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(),
                request(APR_1, ArtifactType.GRID));

        assertEquals(1, result.versionRefs().size());
        assertEquals(published.id(), result.versionRefs().get(0).versionId());
    }

    @Test
    @DisplayName("PricingVersionResolver_graphHashDeterministic")
    void PricingVersionResolver_graphHashDeterministic() {
        addVersion(ArtifactType.GRID, JAN_1, null, "PUBLISHED");
        addVersion(ArtifactType.ROUNDING, JAN_1, null, "PUBLISHED");

        VersionGraphResult first = resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(),
                request(APR_1, ArtifactType.GRID, ArtifactType.ROUNDING));
        VersionGraphResult second = resolver.resolveVersionGraph(TENANT, resolveHeadersWithReplay(),
                request(APR_1, ArtifactType.ROUNDING, ArtifactType.GRID));

        assertEquals(first.graphHash(), second.graphHash());
    }

    private ArtifactVersion addVersion(ArtifactType type, Instant from, Instant to, String status) {
        ArtifactVersion version = new ArtifactVersion(UUID.randomUUID(), TENANT, PRODUCT, INVESTOR, CHANNEL, type,
                repository.findArtifactVersions(TENANT, type, PRODUCT, INVESTOR, CHANNEL).size() + 1,
                VersionArtifactStatus.valueOf(status), from, to, type.name() + "-hash-" + UUID.randomUUID());
        repository.addArtifactVersion(version);
        return version;
    }

    private static VersionGraphHeaders resolveHeaders() {
        return new VersionGraphHeaders(Set.of(PricingVersionResolver.VERSION_GRAPH_RESOLVE_PERMISSION),
                "resolver-1", "corr-1", "idem-resolve-1");
    }

    private static VersionGraphHeaders resolveHeadersWithReplay() {
        return new VersionGraphHeaders(Set.of(PricingVersionResolver.VERSION_GRAPH_RESOLVE_PERMISSION,
                PricingVersionResolver.REPLAY_HISTORICAL_PERMISSION), "resolver-1", "corr-1", "idem-resolve-1");
    }

    private static ResolveVersionGraphRequest request(Instant asOf, ArtifactType... types) {
        return new ResolveVersionGraphRequest(PRODUCT, INVESTOR, CHANNEL, asOf, "scenario-hash-1", List.of(types),
                List.of());
    }
}

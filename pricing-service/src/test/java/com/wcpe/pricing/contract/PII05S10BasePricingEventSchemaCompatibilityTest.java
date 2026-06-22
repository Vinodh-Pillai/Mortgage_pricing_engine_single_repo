package com.wcpe.pricing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcpe.pricing.baserate.BaseRateSelectionApi;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseGridEvent;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BasePricingGridRow;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BasePricingGridVersion;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionConflictException;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionHeaders;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionRequest;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionResponse;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.GridVersionStatus;
import com.wcpe.pricing.baserate.InMemoryBaseRateSelectionRepository;
import com.wcpe.pricing.finalprice.FinalPriceApi;
import com.wcpe.pricing.finalprice.FinalPriceApi.AdjustmentRule;
import com.wcpe.pricing.finalprice.FinalPriceApi.CapFloorRule;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceHeaders;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceRequest;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceResponse;
import com.wcpe.pricing.finalprice.InMemoryFinalPriceRepository;
import com.wcpe.pricing.finalprice.FinalPriceApi.PricingConfigurationSnapshot;
import com.wcpe.pricing.finalprice.FinalPriceApi.ScenarioFacts;
import com.wcpe.pricing.finalprice.FinalPriceApi.SelectedBaseRate;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.CreateRoundingPolicyRequest;
import com.wcpe.pricing.rounding.api.InMemoryRoundingPolicyRepository;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingHeaders;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyVersion;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingRule;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingSampleFixture;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingUnit;
import com.wcpe.pricing.version.PricingVersionResolver;
import com.wcpe.pricing.version.PricingVersionResolver.ArtifactType;
import com.wcpe.pricing.version.PricingVersionResolver.ArtifactVersion;
import com.wcpe.pricing.version.InMemoryVersionGraphRepository;
import com.wcpe.pricing.version.PricingVersionResolver.ResolveVersionGraphRequest;
import com.wcpe.pricing.version.PricingVersionResolver.VersionGraphHeaders;
import com.wcpe.pricing.version.PricingVersionResolver.VersionGraphResult;
import com.wcpe.pricing.version.VersionArtifactStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PII-05-S10 executable contract slice for implemented base-pricing surfaces.
 * All fixture values are synthetic and labeled; no production prices, rates, or investor assumptions are encoded.
 */
@DisplayName("PII-05-S10 base pricing event and golden contracts")
class PII05S10BasePricingEventSchemaCompatibilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = "tenant-pii05-s10";
    private static final String PRODUCT = "SYNTH_CONVENTIONAL";
    private static final String INVESTOR = "SYNTH_INVESTOR";
    private static final String CHANNEL = "SYNTH_RETAIL";
    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");
    private static final UUID SELECTION_ID = UUID.fromString("55555555-5555-5555-5555-555555555510");

    @Test
    void PII05S10_baseRateSelectionEventAndIdempotencyContract() {
        InMemoryBaseRateSelectionRepository repository = new InMemoryBaseRateSelectionRepository();
        BaseRateSelectionApi api = new BaseRateSelectionApi(repository);
        UUID gridId = addPublishedGridVersion(repository);
        repository.addGridRow(new BasePricingGridRow(UUID.randomUUID(), TENANT, gridId, 30,
                new BigDecimal("6.12500"), new BigDecimal("100.00000"), Map.of("fixture", "PII-05-S10"),
                "row-hash-pii05-s10", AS_OF));

        BaseRateSelectionResponse first = api.selectRate(TENANT, baseRateHeaders("idem-pii05-s10"), baseRateRequest(30));
        BaseRateSelectionResponse replay = api.selectRate(TENANT, baseRateHeaders("idem-pii05-s10"), baseRateRequest(30));

        assertEquals(first.resultHash(), replay.resultHash(), "same idempotency key and payload must replay the same result");
        assertEquals(new BigDecimal("6.12500"), first.selectedNoteRate());
        assertEquals(new BigDecimal("100.00000"), first.selectedBasePrice());
        assertEquals(5, first.selectedNoteRate().scale());
        assertEquals(5, first.selectedBasePrice().scale());

        BaseGridEvent event = repository.gridEvents().get(0);
        assertEquals("pricing.base-rate-selected.v1", event.eventType());
        assertEquals(TENANT, event.tenantId());
        assertEquals("actor-pii05-s10", event.actorId());
        assertEquals("corr-pii05-s10", event.correlationId());
        assertEquals("idem-pii05-s10", event.idempotencyKey());
        assertEquals(first.resultHash(), event.payload().get("resultHash"));
        assertEquals("scenario-hash-pii05-s10", event.payload().get("scenarioHash"));
        assertEquals("30", event.payload().get("lockPeriodDays"));
        assertEquals(1, repository.audits().size(), "selection must persist audit evidence with the outbox event");

        assertThrows(BaseRateSelectionConflictException.class,
                () -> api.selectRate(TENANT, baseRateHeaders("idem-pii05-s10"), baseRateRequest(45)),
                "same idempotency key with a different request must fail closed");
    }

    @Test
    void PII05S10_finalPriceMatchesGoldenAndPublishesContractEvent() throws Exception {
        JsonNode golden = readGolden();
        InMemoryFinalPriceRepository repository = new InMemoryFinalPriceRepository();
        RoundingPolicyApi roundingPolicyApi = new RoundingPolicyApi(new InMemoryRoundingPolicyRepository());
        RoundingPolicyVersion roundingPolicy = publishRoundingPolicy(roundingPolicyApi);
        PricingConfigurationSnapshot configuration = pricingConfiguration(roundingPolicy.id().toString());
        SelectedBaseRate selection = new SelectedBaseRate(TENANT, SELECTION_ID,
                new BigDecimal(golden.path("selected_note_rate").asText()),
                new BigDecimal(golden.path("base_price").asText()),
                golden.path("lock_period_days").asInt(), "grid-version-PII-05-S10", "selection-hash-pii05-s10");
        ScenarioFacts facts = new ScenarioFacts(TENANT, "scenario-pii05-s10", "scenario-hash-pii05-s10",
                Map.of("fixture_label", golden.path("fixture_label").asText()));

        FinalPriceApi api = new FinalPriceApi(
                repository,
                (tenantId, selectionId) -> TENANT.equals(tenantId) && SELECTION_ID.equals(selectionId)
                        ? Optional.of(selection) : Optional.empty(),
                (tenantId, scenarioId, scenarioHash) -> TENANT.equals(tenantId)
                        && facts.scenarioId().equals(scenarioId) && facts.scenarioHash().equals(scenarioHash)
                        ? Optional.of(facts) : Optional.empty(),
                (tenantId, versionRefs, asOf) -> TENANT.equals(tenantId)
                        && versionRefs.stream().anyMatch(configuration.versionRefs()::contains)
                        ? Optional.of(configuration) : Optional.empty(),
                roundingPolicyApi);

        FinalPriceResponse response = api.calculate(TENANT, finalPriceHeaders(), finalPriceRequest(false));

        assertEquals(golden.path("rounded_final_price").asText(), response.roundedFinalPrice().toPlainString());
        assertEquals(5, response.roundedFinalPrice().scale());
        assertEquals(8, response.subtotal().scale());
        assertFalse(response.ledger().stream().anyMatch(entry -> String.valueOf(entry.outputValue()).contains("E")),
                "ledger must use canonical BigDecimal decimal strings rather than scientific notation");
        assertEquals("pricing.final-price-calculated.v1", repository.events().get(0).eventType());
        assertEquals(TENANT + ":" + response.finalPriceId(), repository.events().get(0).eventKey());
        assertTrue(response.versionGraph().refs().contains("adjustment-version-PII-05-S10"));
        assertTrue(response.versionGraph().refs().contains("cap-floor-version-PII-05-S10"));
        assertEquals("FINAL_PRICE_CALCULATION_COMPLETED", repository.audits().get(0).action());
        assertNotNull(response.resultHash());
        assertEquals(64, response.resultHash().length());
    }

    @Test
    void PII05S10_versionGraphResolvedEventUsesTenantScopedEnvelope() {
        InMemoryVersionGraphRepository repository = new InMemoryVersionGraphRepository();
        repository.addArtifactVersion(artifact(ArtifactType.GRID, "11111111-1111-1111-1111-000000000510", "grid-hash-pii05-s10"));
        repository.addArtifactVersion(artifact(ArtifactType.ROUNDING, "22222222-2222-2222-2222-000000000510", "rounding-hash-pii05-s10"));
        PricingVersionResolver resolver = new PricingVersionResolver(repository);

        VersionGraphResult result = resolver.resolveVersionGraph(TENANT,
                new VersionGraphHeaders(Set.of(PricingVersionResolver.VERSION_GRAPH_RESOLVE_PERMISSION),
                        "actor-pii05-s10", "corr-pii05-s10", "idem-version-pii05-s10"),
                new ResolveVersionGraphRequest(PRODUCT, INVESTOR, CHANNEL, AS_OF, "scenario-hash-pii05-s10",
                        List.of(ArtifactType.GRID, ArtifactType.ROUNDING), List.of()));

        assertEquals(2, result.versionRefs().size());
        assertEquals("pricing.version-graph-resolved.v1", repository.events().get(0).eventType());
        assertEquals(TENANT, repository.events().get(0).tenantId());
        assertEquals(result.graphHash(), repository.events().get(0).payload().get("graph_hash"));
        assertFalse(repository.events().get(0).payload().toString().contains("borrower"));
        assertEquals(1, repository.audits().size());
    }

    @Test
    void PII05S10_openApiDocumentsImplementedContractErrorSurfaces() throws Exception {
        String contract = java.nio.file.Files.readString(java.nio.file.Paths.get("contracts", "pricing-contract.yml"));

        assertTrue(contract.contains("/api/v1/tenants/{tenantId}/pricing/base-rate-selections"));
        assertTrue(contract.contains("/api/v1/tenants/{tenantId}/pricing/final-prices"));
        assertTrue(contract.contains("Idempotency-Key"));
        assertTrue(contract.contains("\"409\": { description: \"VERSION_CONFLICT, IDEMPOTENCY_CONFLICT"));
        assertTrue(contract.contains("\"422\": { description: \"BASE_RATE_SELECTION_REQUIRED"));
        assertTrue(contract.contains("\"503\": { description: \"DEPENDENCY_UNAVAILABLE\" }"));
    }

    private static BaseRateSelectionHeaders baseRateHeaders(String idempotencyKey) {
        return new BaseRateSelectionHeaders(Set.of(BaseRateSelectionApi.BASE_RATE_WRITE_PERMISSION),
                "actor-pii05-s10", "corr-pii05-s10", idempotencyKey);
    }

    private static BaseRateSelectionRequest baseRateRequest(int lockPeriodDays) {
        return new BaseRateSelectionRequest("scenario-pii05-s10", "scenario-hash-pii05-s10",
                PRODUCT, INVESTOR, CHANNEL, lockPeriodDays, AS_OF, new BigDecimal("6.12500"), "policy-pii05-s10");
    }

    private static UUID addPublishedGridVersion(InMemoryBaseRateSelectionRepository repository) {
        UUID gridId = UUID.randomUUID();
        repository.addGridVersion(new BasePricingGridVersion(gridId, TENANT, PRODUCT, INVESTOR, CHANNEL, 1,
                GridVersionStatus.PUBLISHED, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"), "source-digest-pii05-s10", "approver-pii05-s10",
                AS_OF, AS_OF, AS_OF));
        return gridId;
    }

    private static FinalPriceHeaders finalPriceHeaders() {
        return new FinalPriceHeaders(Set.of(FinalPriceApi.FINAL_PRICE_WRITE_PERMISSION),
                "actor-pii05-s10", "corr-pii05-s10", "idem-final-pii05-s10");
    }

    private static FinalPriceRequest finalPriceRequest(boolean dryRun) {
        return new FinalPriceRequest(SELECTION_ID, "scenario-pii05-s10", "scenario-hash-pii05-s10",
                List.of("adjustment-version-PII-05-S10"), AS_OF, "quote-request-pii05-s10", dryRun);
    }

    private static PricingConfigurationSnapshot pricingConfiguration(String roundingPolicyVersionRef) {
        return new PricingConfigurationSnapshot(TENANT,
                List.of("adjustment-version-PII-05-S10", "cap-floor-version-PII-05-S10"),
                PRODUCT, INVESTOR, CHANNEL, "BASE", "FINAL_PRICE",
                List.of(new AdjustmentRule("adj-synth-pii05-s10", "adjustment-version-PII-05-S10", null, null,
                        null, new BigDecimal("0.12500000"), 10, "SYNTHETIC_CONTRACT_ADJUSTMENT")),
                List.of(new CapFloorRule("cap-floor-version-PII-05-S10", new BigDecimal("95.00000000"),
                        new BigDecimal("105.00000000"), "SYNTHETIC_BOUNDS_OK")));
    }

    private static RoundingPolicyVersion publishRoundingPolicy(RoundingPolicyApi api) {
        CreateRoundingPolicyRequest request = new CreateRoundingPolicyRequest(
                TENANT, "BASE", PRODUCT, INVESTOR, CHANNEL, LocalDate.parse("2026-01-01"),
                LocalDate.parse("2027-01-01"), 1,
                List.of(new RoundingRule("rounding-policy-PII-05-S10", "FINAL_PRICE", RoundingUnit.PRICE, 5,
                        RoundingMode.HALF_UP, new BigDecimal("0.12500"), 0, "ROUND_FINAL_PRICE")),
                List.of(new RoundingSampleFixture("synthetic-pii05-s10", "FINAL_PRICE",
                        new BigDecimal("100.12500"), new BigDecimal("100.12500"))));
        RoundingPolicyVersion draft = api.createDraft(TENANT, roundingHeaders(RoundingPolicyApi.ROUNDING_WRITE_PERMISSION,
                "creator-pii05-s10", "idem-round-draft-pii05-s10"), request);
        api.validatePolicy(TENANT, draft.id(), roundingHeaders(RoundingPolicyApi.ROUNDING_WRITE_PERMISSION,
                "creator-pii05-s10", "idem-round-validate-pii05-s10"));
        return api.publish(TENANT, draft.id(), roundingHeaders(RoundingPolicyApi.ROUNDING_APPROVE_PERMISSION,
                "approver-pii05-s10", "idem-round-publish-pii05-s10"));
    }

    private static RoundingHeaders roundingHeaders(String permission, String actorId, String idempotencyKey) {
        return new RoundingHeaders(Set.of(permission, RoundingPolicyApi.ROUNDING_PUBLISH_PERMISSION),
                actorId, "corr-round-pii05-s10", idempotencyKey);
    }

    private static ArtifactVersion artifact(ArtifactType type, String id, String hash) {
        return new ArtifactVersion(UUID.fromString(id), TENANT, PRODUCT, INVESTOR, CHANNEL, type, 1,
                VersionArtifactStatus.PUBLISHED, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"), hash);
    }

    private static JsonNode readGolden() throws Exception {
        try (InputStream input = PII05S10BasePricingEventSchemaCompatibilityTest.class
                .getResourceAsStream("/golden/base-pricing/pii05-s10-contract-golden.json")) {
            assertNotNull(input, "golden fixture must be packaged as a test resource");
            return MAPPER.readTree(input);
        }
    }
}

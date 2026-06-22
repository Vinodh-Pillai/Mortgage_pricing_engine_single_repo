package com.wcpe.pricing.parrate;

import com.wcpe.pricing.parrate.ParRateIdentificationApi.CandidateEvaluation;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParCandidateRate;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParComparator;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParPolicyStatus;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParPolicyVersion;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParRateErrorCode;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParRateHeaders;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParRateIdentificationException;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParRateIdentificationRequest;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParRateIdentificationResponse;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.ParTieBreaker;
import com.wcpe.pricing.parrate.ParRateIdentificationApi.PriceBasis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParRateIdentificationApiTest {
    private static final String TENANT = "tenant-a";
    private static final String PRODUCT = "CONVENTIONAL";
    private static final String INVESTOR = "FANNIE";
    private static final String CHANNEL = "RETAIL";
    private static final UUID GRID_VERSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    private InMemoryParRateIdentificationRepository repository;
    private ParRateIdentificationApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryParRateIdentificationRepository();
        repository.addPolicy(policy("par-policy-v1", new BigDecimal("100.00000"), ParComparator.NEAREST_TO_TARGET,
                ParTieBreaker.LOWEST_NOTE_RATE, PriceBasis.BASE));
        api = new ParRateIdentificationApi(repository);
    }

    @Test
    void ParRateIdentificationService_usesConfiguredComparator() {
        ParRateIdentificationResponse response = api.identify(TENANT, writeHeaders(), request(List.of(
                candidate("6.00000", 30, "99.75000", null, "row-low"),
                candidate("6.12500", 30, "100.12500", null, "row-par"),
                candidate("6.25000", 30, "100.37500", null, "row-high"))));

        assertEquals(new BigDecimal("6.12500"), response.parNoteRate());
        assertEquals(new BigDecimal("100.12500"), response.parPrice());
        assertEquals("par-policy-v1", response.parPolicyVersionId());
        assertEquals("pricing.par-rate-identified.v1", repository.events().get(0).eventType());
        assertEquals("PAR_RATE_IDENTIFICATION_COMPLETED", repository.audits().get(0).action());
        assertTrue(response.cacheKey().startsWith("pricing:par-rate:tenant-a:scenario-hash-1:"));
        assertTrue(response.candidateEvaluations().stream().anyMatch(CandidateEvaluation::parCandidate));
    }

    @Test
    void ParRateIdentificationService_rejectsUnresolvedTie() {
        repository = new InMemoryParRateIdentificationRepository();
        repository.addPolicy(policy("par-policy-v1", new BigDecimal("100.00000"), ParComparator.NEAREST_TO_TARGET,
                null, PriceBasis.BASE));
        api = new ParRateIdentificationApi(repository);

        ParRateIdentificationException exception = assertThrows(ParRateIdentificationException.class, () ->
                api.identify(TENANT, writeHeaders(), request(List.of(
                        candidate("6.00000", 30, "99.87500", null, "row-a"),
                        candidate("6.12500", 30, "100.12500", null, "row-b")))));

        assertEquals(ParRateErrorCode.PAR_TIE_UNRESOLVED, exception.code());
    }

    @Test
    void ParRateIdentificationService_scopesByLockPeriod() {
        ParRateIdentificationResponse response = api.identify(TENANT, writeHeaders(), request(List.of(
                candidate("6.00000", 45, "100.00000", null, "wrong-lock"),
                candidate("6.25000", 30, "100.25000", null, "right-lock"))));

        assertEquals(new BigDecimal("6.25000"), response.parNoteRate());
        assertEquals(1, response.candidateEvaluations().size());
        assertEquals("right-lock", response.candidateEvaluations().get(0).rowRef());
    }

    @Test
    void ParPolicyRepository_resolvesPublishedAsOfVersion() {
        ParRateIdentificationResponse response = api.identify(TENANT, writeHeaders(), request(List.of(
                candidate("6.12500", 30, "100.00000", null, "row-par"))));

        assertNotNull(repository.findById(response.parIdentificationId()).orElseThrow());
        assertEquals(response.resultHash(), repository.findById(response.parIdentificationId()).orElseThrow().resultHash());
    }

    @Test
    void ParIdentificationResult_persistsLedger() {
        ParRateIdentificationResponse response = api.identify(TENANT, writeHeaders(), request(List.of(
                candidate("6.12500", 30, "100.00000", null, "row-par"))));

        ParRateIdentificationResponse stored = repository.findById(response.parIdentificationId()).orElseThrow().response();
        assertEquals(response.ledger(), stored.ledger());
        assertFalse(stored.ledger().isEmpty());
    }

    @Test
    void ParPolicyCache_invalidatesOnPublish() {
        api.handlePolicyPublished(TENANT, PRODUCT + ":" + INVESTOR + ":" + CHANNEL, "par-policy-v1");

        assertTrue(repository.wasPolicyCacheInvalidated(
                "pricing:par-policy:tenant-a:CONVENTIONAL:FANNIE:RETAIL:par-policy-v1"));
    }

    @Test
    void parPolicyMissingReturnsMachineReadableError() {
        repository = new InMemoryParRateIdentificationRepository();
        api = new ParRateIdentificationApi(repository);

        ParRateIdentificationException exception = assertThrows(ParRateIdentificationException.class, () ->
                api.identify(TENANT, writeHeaders(), request(List.of(
                        candidate("6.12500", 30, "100.00000", null, "row-par")))));

        assertEquals(ParRateErrorCode.PAR_POLICY_MISSING, exception.code());
    }

    @Test
    void requestedRateMustBeInGridSlice() {
        ParRateIdentificationRequest request = new ParRateIdentificationRequest("scenario-hash-1", GRID_VERSION_ID,
                PRODUCT, INVESTOR, CHANNEL, 30,
                List.of(candidate("6.12500", 30, "100.00000", null, "row-par")),
                new BigDecimal("6.50000"), "par-policy-v1", AS_OF);

        ParRateIdentificationException exception = assertThrows(ParRateIdentificationException.class,
                () -> api.identify(TENANT, writeHeaders(), request));

        assertEquals(ParRateErrorCode.REQUESTED_RATE_NOT_IN_GRID, exception.code());
    }

    private static ParPolicyVersion policy(String id, BigDecimal target, ParComparator comparator,
            ParTieBreaker tieBreaker, PriceBasis priceBasis) {
        return new ParPolicyVersion(id, TENANT, PRODUCT, INVESTOR, CHANNEL, ParPolicyStatus.PUBLISHED,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z"), target,
                comparator, tieBreaker, priceBasis, "rounding-policy-v1");
    }

    private static ParRateIdentificationRequest request(List<ParCandidateRate> candidates) {
        return new ParRateIdentificationRequest("scenario-hash-1", GRID_VERSION_ID, PRODUCT, INVESTOR, CHANNEL,
                30, candidates, null, "par-policy-v1", AS_OF);
    }

    private static ParCandidateRate candidate(String noteRate, int lockPeriodDays, String basePrice,
            String finalPrice, String rowRef) {
        return new ParCandidateRate(new BigDecimal(noteRate), lockPeriodDays, new BigDecimal(basePrice),
                finalPrice == null ? null : new BigDecimal(finalPrice), rowRef);
    }

    private static ParRateHeaders writeHeaders() {
        return new ParRateHeaders(Set.of(ParRateIdentificationApi.PAR_RATE_WRITE_PERMISSION), "actor-1", "corr-1",
                "idem-1");
    }
}

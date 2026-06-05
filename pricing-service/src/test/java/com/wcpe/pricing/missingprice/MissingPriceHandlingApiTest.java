package com.wcpe.pricing.missingprice;

import com.wcpe.pricing.baserate.BaseRateSelectionApi;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionNoteRateUnavailableException;
import com.wcpe.pricing.baserate.BaseRateSelectionApi.CandidateRate;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingPriceHandlingApiTest {
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String PRODUCT = "CONVENTIONAL";
    private static final String INVESTOR = "FANNIE";
    private static final String CHANNEL = "RETAIL";
    private static final Instant AS_OF = Instant.parse("2026-06-01T00:00:00Z");

    private InMemoryMissingPriceRepository repository;
    private MissingPriceHandlingApi api;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMissingPriceRepository();
        api = new MissingPriceHandlingApi(repository);
    }

    @Test
    void MissingPricePolicy_rejectsSilentFallback() {
        assertThrows(BaseRateSelectionNoteRateUnavailableException.class, () ->
                invokeBaseRateSelectionWithoutRequestedRate());

        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-silent"),
                request(MissingPriceLookupStatus.MISSING_NOTE_RATE));

        assertEquals(MissingPriceIncidentStatus.OPEN, response.status());
        assertEquals("PRICE_ROW_MISSING_NOTE_RATE", response.error().errorCode());
        assertEquals(422, response.error().httpStatus());
        assertNotNull(repository.findIncident(TENANT_A, response.id()).orElseThrow().diagnostic());
    }

    @Test
    void MissingPricePolicy_classifiesMissingLockPeriod() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-lock"),
                request(MissingPriceLookupStatus.MISSING_LOCK_PERIOD));

        assertEquals("PRICE_ROW_MISSING_LOCK_PERIOD", response.error().errorCode());
        assertTrue(response.error().remediation().contains("lock period"));
        assertEquals("pricing.missing-price-detected.v1", repository.events().get(0).eventType());
        assertEquals("MISSING_PRICE_DETECTED", repository.audits().get(0).action());
    }

    @Test
    void MissingPricePolicy_classifiesAmbiguousLookup() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-ambiguous"),
                request(MissingPriceLookupStatus.AMBIGUOUS_ROWS));

        assertEquals("PRICE_LOOKUP_AMBIGUOUS", response.error().errorCode());
        assertEquals(409, response.error().httpStatus());
        assertTrue(response.error().message().contains("multiple pricing rows"));
    }

    @Test
    void MissingPriceIncident_persistsRedactedDiagnostics() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-diagnostics"),
                request(MissingPriceLookupStatus.NO_ACTIVE_GRID));

        MissingPriceIncident incident = repository.findIncident(TENANT_A, response.id()).orElseThrow();

        assertEquals(PRODUCT, incident.productCode());
        assertEquals("scenario-hash-1", incident.scenarioHash());
        assertEquals(Map.of("sourceSystem", "quote-api", "clientContext", "redacted"),
                incident.diagnostic().redactedContext());
        assertFalse(incident.diagnostic().authorizedRange().isPresent(),
                "quote users must not receive grid range diagnostics");
    }

    @Test
    void MissingPriceNegativeCache_invalidatesOnGridPublish() {
        api.detectMissingPrice(TENANT_A, writeHeaders("idem-cache"),
                request(MissingPriceLookupStatus.MISSING_BUCKET));

        assertTrue(repository.hasNegativeCacheEntry(TENANT_A, "grid-v1"));

        api.handleGridPublished(TENANT_A, "grid-v1");

        assertFalse(repository.hasNegativeCacheEntry(TENANT_A, "grid-v1"));
    }

    @Test
    void MissingPriceTenantIsolationTest() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-tenant"),
                request(MissingPriceLookupStatus.NO_ACTIVE_GRID));

        MissingPriceException exception = assertThrows(MissingPriceException.class, () ->
                api.getIncident(TENANT_B, response.id(), readHeaders()));

        assertEquals(MissingPriceErrorCode.NOT_FOUND, exception.code());
        assertEquals(404, exception.httpStatus());
    }

    @Test
    void missing_price_grid_missing_422() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-contract"),
                request(MissingPriceLookupStatus.NO_ACTIVE_GRID));

        assertNotNull(response.id());
        assertEquals(MissingPriceIncidentStatus.OPEN, response.status());
        assertEquals("PRICE_GRID_MISSING", response.error().errorCode());
        assertNotNull(response.error().diagnosticsRef());
        assertEquals("corr-1", response.correlationId());
    }

    @Test
    void missing_price_row_missing_lock_422() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-lock-contract"),
                request(MissingPriceLookupStatus.MISSING_LOCK_PERIOD));

        assertEquals("PRICE_ROW_MISSING_LOCK_PERIOD", response.error().errorCode());
        assertEquals(422, response.error().httpStatus());
    }

    @Test
    void missing_price_incident_get_200() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-get"),
                request(MissingPriceLookupStatus.MISSING_NOTE_RATE));

        MissingPriceIncident incident = api.getIncident(TENANT_A, response.id(), readHeaders());

        assertEquals(response.id(), incident.id());
        assertEquals(MissingPriceReason.PRICE_ROW_MISSING_NOTE_RATE, incident.reasonCode());
        assertEquals(TENANT_A, incident.tenantId());
    }

    @Test
    void retryRecordsResolutionAndAudit() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, writeHeaders("idem-retry-create"),
                request(MissingPriceLookupStatus.MISSING_NOTE_RATE));

        MissingPriceRetryResponse retry = api.retry(TENANT_A, response.id(), retryHeaders(),
                new MissingPriceRetryRequest(MissingPriceLookupStatus.EXACT_MATCH, "base-rate-selection:ok"));

        assertEquals(MissingPriceIncidentStatus.RESOLVED, retry.resultStatus());
        assertEquals(MissingPriceIncidentStatus.RESOLVED,
                repository.findIncident(TENANT_A, response.id()).orElseThrow().status());
        assertEquals(1, repository.findRetries(TENANT_A, response.id()).size());
        assertTrue(repository.audits().stream().anyMatch(audit -> "MISSING_PRICE_RETRY_RECORDED".equals(audit.action())));
    }

    @Test
    void idempotencyReplayAndConflict() {
        MissingPriceHandlingResponse first = api.detectMissingPrice(TENANT_A, writeHeaders("idem-replay"),
                request(MissingPriceLookupStatus.NO_ACTIVE_GRID));
        MissingPriceHandlingResponse replay = api.detectMissingPrice(TENANT_A, writeHeaders("idem-replay"),
                request(MissingPriceLookupStatus.NO_ACTIVE_GRID));

        assertEquals(first.id(), replay.id());

        MissingPriceException exception = assertThrows(MissingPriceException.class, () ->
                api.detectMissingPrice(TENANT_A, writeHeaders("idem-replay"),
                        request(MissingPriceLookupStatus.MISSING_BUCKET)));

        assertEquals(MissingPriceErrorCode.IDEMPOTENCY_CONFLICT, exception.code());
    }

    @Test
    void authorizedDiagnosticsIncludeSameTenantRangeOnly() {
        MissingPriceHandlingResponse response = api.detectMissingPrice(TENANT_A, diagnosticHeaders("idem-range"),
                request(MissingPriceLookupStatus.MISSING_NOTE_RATE));

        LookupDiagnostic diagnostic = repository.findIncident(TENANT_A, response.id()).orElseThrow().diagnostic();

        assertTrue(diagnostic.authorizedRange().isPresent());
        assertEquals(new BigDecimal("5.50000"), diagnostic.authorizedRange().orElseThrow().minNoteRate());
    }

    private static void invokeBaseRateSelectionWithoutRequestedRate() {
        List<CandidateRate> candidates = List.of(new CandidateRate(new BigDecimal("6.12500"),
                new BigDecimal("100.00000"), 1, "GRID_MATCH"));
        try {
            java.lang.reflect.Method method = BaseRateSelectionApi.class.getDeclaredMethod("applySelectionPolicy",
                    List.class, BigDecimal.class, String.class);
            method.setAccessible(true);
            method.invoke(null, candidates, null, "policy-v1");
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(ex);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static MissingPriceLookupRequest request(MissingPriceLookupStatus status) {
        return new MissingPriceLookupRequest("scenario-hash-1", PRODUCT, INVESTOR, CHANNEL, 30,
                new BigDecimal("6.12500"), AS_OF, "grid-v1", "ltv=synthetic", status,
                new AvailablePriceRange(new BigDecimal("5.50000"), new BigDecimal("7.50000"),
                        new BigDecimal("95.00000"), new BigDecimal("102.00000"), true),
                Map.of("sourceSystem", "quote-api", "clientContext", "redacted"));
    }

    private static MissingPriceHeaders writeHeaders(String idempotencyKey) {
        return new MissingPriceHeaders(Set.of(MissingPriceHandlingApi.MISSING_PRICE_WRITE_PERMISSION), "actor-1",
                "corr-1", idempotencyKey);
    }

    private static MissingPriceHeaders retryHeaders() {
        return new MissingPriceHeaders(Set.of(MissingPriceHandlingApi.MISSING_PRICE_RETRY_PERMISSION), "actor-1",
                "corr-1", "idem-retry");
    }

    private static MissingPriceHeaders readHeaders() {
        return new MissingPriceHeaders(Set.of(MissingPriceHandlingApi.MISSING_PRICE_READ_PERMISSION), "actor-1",
                "corr-1", null);
    }

    private static MissingPriceHeaders diagnosticHeaders(String idempotencyKey) {
        return new MissingPriceHeaders(Set.of(MissingPriceHandlingApi.MISSING_PRICE_WRITE_PERMISSION,
                MissingPriceHandlingApi.MISSING_PRICE_READ_PERMISSION), "actor-1", "corr-1", idempotencyKey);
    }
}

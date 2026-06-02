package com.wcpe.pricing.quote;

import com.wcpe.pricing.quote.api.QuoteApi;
import com.wcpe.pricing.quote.api.QuoteApi.CatalogCandidate;
import com.wcpe.pricing.quote.api.QuoteApi.DurableQuoteRepository;
import com.wcpe.pricing.quote.api.QuoteApi.EligibilityEvaluation;
import com.wcpe.pricing.quote.api.QuoteApi.QuoteAccessDeniedException;
import com.wcpe.pricing.quote.api.QuoteApi.QuoteCreateRequest;
import com.wcpe.pricing.quote.api.QuoteApi.QuoteHeaders;
import com.wcpe.pricing.quote.api.QuoteApi.QuotePersistenceException;
import com.wcpe.pricing.quote.api.QuoteApi.QuoteReason;
import com.wcpe.pricing.quote.api.QuoteApi.QuoteResponse;
import com.wcpe.pricing.quote.api.QuoteApi.QuoteValidationException;
import com.wcpe.pricing.quote.api.QuoteApi.ScenarioReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuoteApiTest {
    @TempDir
    private Path quoteStorageDirectory;

    private CountingScenarioAdapter scenarioAdapter;
    private CountingCatalogAdapter catalogAdapter;
    private CountingEligibilityAdapter eligibilityAdapter;
    private DurableQuoteRepository repository;
    private QuoteApi api;

    @BeforeEach
    void setUp() {
        scenarioAdapter = new CountingScenarioAdapter();
        catalogAdapter = new CountingCatalogAdapter();
        eligibilityAdapter = new CountingEligibilityAdapter();
        repository = new DurableQuoteRepository(quoteStorageDirectory);
        api = new QuoteApi(scenarioAdapter, catalogAdapter, eligibilityAdapter, repository);
    }

    @Test
    void authorizedConventionalPurchaseCreatesEligibilityOnlyQuoteShell() {
        QuoteResponse response = api.createQuote("tenant-a", authorizedHeaders("corr-001"), validRequest("tenant-a"));

        assertTrue(response.quoteId().startsWith("quote-"));
        assertEquals("scenario-tenant-a", response.scenarioId());
        assertEquals(1, response.scenarioVersion());
        assertEquals("ELIGIBILITY_ONLY", response.quoteStatus());
        assertEquals("audit-corr-001", response.auditReference());
        assertEquals("corr-001", response.correlationId());
        assertEquals(1, scenarioAdapter.calls.get());
        assertEquals(1, catalogAdapter.calls.get());
        assertEquals(2, eligibilityAdapter.calls.get());
        assertTrue(repository.findById(response.quoteId()).isPresent());
    }

    @Test
    void activeCandidatesPreserveExplicitCatalogDisplayOrder() {
        QuoteResponse response = api.createQuote("tenant-a", authorizedHeaders("corr-002"), validRequest("tenant-a"));

        assertEquals(2, response.options().size());
        assertEquals(List.of("PRODUCT-A", "PRODUCT-Z"), response.options().stream()
                .map(QuoteApi.QuoteOption::productCode)
                .toList());
        assertEquals("PRODUCT-A", response.options().get(0).productCode());
        assertEquals("ELIGIBLE", response.options().get(0).eligibilityStatus());
        assertEquals(10, response.options().get(0).displayOrder());
        assertEquals("PRODUCT-Z", response.options().get(1).productCode());
        assertEquals("INELIGIBLE", response.options().get(1).eligibilityStatus());
        assertEquals(20, response.options().get(1).displayOrder());
        assertEquals(List.of(10, 20), response.options().stream()
                .map(QuoteApi.QuoteOption::displayOrder)
                .toList());
        assertTrue(response.options().get(0).displayOrder() < response.options().get(1).displayOrder());
    }

    @Test
    void failedEligibilityOptionIncludesReasonEvidence() {
        QuoteResponse response = api.createQuote("tenant-a", authorizedHeaders("corr-003"), validRequest("tenant-a"));

        QuoteReason reason = response.options().get(1).reason();
        assertNotNull(reason);
        assertEquals("REQ001_FICO_BAND_UNSUPPORTED", reason.code());
        assertEquals("FAIL", reason.severity());
        assertEquals("Representative FICO band is outside synthetic candidate policy", reason.text());
        assertEquals("Review borrower credit facts", reason.remediationHint());
        assertEquals("SYNTH_FICO_LOW", reason.actualValue());
        assertEquals("SYNTH_FICO_STANDARD", reason.requiredValue());
    }

    @Test
    void missingLoanOfficerRoleFailsBeforeScenarioEligibilityOrQuoteWrites() throws IOException {
        assertThrows(QuoteAccessDeniedException.class,
                () -> api.createQuote("tenant-a", QuoteHeaders.of("CATALOG_READER", "actor-1", "corr-004", "idem-004"), validRequest("tenant-a")));

        assertNoAdapterOrRepositoryWrites();
    }

    @Test
    void crossTenantGetIsDeniedWithoutReturningQuoteData() {
        QuoteResponse response = api.createQuote("tenant-a", authorizedHeaders("corr-005"), validRequest("tenant-a"));

        assertThrows(QuoteAccessDeniedException.class,
                () -> api.getQuote("tenant-b", response.quoteId(), authorizedHeaders("corr-006")));
    }

    @Test
    void invalidScenarioFailsClosedBeforeScenarioEligibilityOrQuoteWrites() throws IOException {
        QuoteCreateRequest invalid = new QuoteCreateRequest(
                "tenant-a", "borrower-1", "SYNTH_FICO_STANDARD", 500000, 400000,
                "TX", "75001", "SINGLE_FAMILY", 1, "PRIMARY", "FHA", "PURCHASE", "RETAIL", 30);

        assertThrows(QuoteValidationException.class,
                () -> api.createQuote("tenant-a", authorizedHeaders("corr-007"), invalid));

        assertNoAdapterOrRepositoryWrites();
    }

    @Test
    void authorizedTenantCanRetrievePersistedQuoteWithMatchingCorrelationAndAuditEvidence() {
        QuoteResponse created = api.createQuote("tenant-a", authorizedHeaders("corr-008"), validRequest("tenant-a"));

        QuoteResponse retrieved = api.getQuote("tenant-a", created.quoteId(), authorizedHeaders("corr-009"));

        assertEquals(created.quoteId(), retrieved.quoteId());
        assertEquals(created.correlationId(), retrieved.correlationId());
        assertEquals(created.auditReference(), retrieved.auditReference());
        assertEquals(created.options(), retrieved.options());
    }

    @Test
    void freshRepositoryInstanceRetrievesDurablyPersistedQuote() {
        QuoteResponse created = api.createQuote("tenant-a", authorizedHeaders("corr-010"), validRequest("tenant-a"));
        QuoteApi restartedApi = new QuoteApi(
                scenarioAdapter,
                catalogAdapter,
                eligibilityAdapter,
                new DurableQuoteRepository(quoteStorageDirectory));

        QuoteResponse retrieved = restartedApi.getQuote("tenant-a", created.quoteId(), authorizedHeaders("corr-011"));

        assertEquals(created.quoteId(), retrieved.quoteId());
        assertEquals("tenant-a", retrieved.tenantId());
        assertEquals("scenario-tenant-a", retrieved.scenarioId());
        assertEquals(1, retrieved.scenarioVersion());
        assertEquals("audit-corr-010", retrieved.auditReference());
        assertEquals("corr-010", retrieved.correlationId());
        assertEquals(created.options(), retrieved.options());
        assertEquals("REQ001_FICO_BAND_UNSUPPORTED", retrieved.options().get(1).reason().code());
        assertEquals("SYNTH_FICO_STANDARD", retrieved.options().get(1).reason().requiredValue());
    }

    @Test
    void durableRepositoryRejectsUnsafeQuoteIds() {
        assertTrue(repository.findById("../quote-escape").isEmpty());
    }

    @Test
    void durableRepositoryRejectsUnsafeQuoteIdsOnSaveWithoutWritingOutsideStorage() throws IOException {
        String escapeName = "quote-escape-" + quoteStorageDirectory.getFileName();
        QuoteResponse unsafe = new QuoteResponse(
                "../" + escapeName,
                "tenant-a",
                "scenario-tenant-a",
                1,
                "ELIGIBILITY_ONLY",
                "audit-corr-012",
                "corr-012",
                List.of());
        Path outsideStoragePath = quoteStorageDirectory.getParent().resolve(escapeName + ".quote");

        assertThrows(QuotePersistenceException.class, () -> repository.save(unsafe));

        assertFalse(Files.exists(outsideStoragePath));
        try (var files = Files.list(quoteStorageDirectory)) {
            assertEquals(0, files.count());
        }
    }

    private void assertNoAdapterOrRepositoryWrites() throws IOException {
        assertEquals(0, scenarioAdapter.calls.get());
        assertEquals(0, catalogAdapter.calls.get());
        assertEquals(0, eligibilityAdapter.calls.get());
        assertTrue(repository.findById("quote-never-created").isEmpty());
        if (Files.exists(quoteStorageDirectory)) {
            try (var files = Files.list(quoteStorageDirectory)) {
                assertEquals(0, files.count());
            }
        }
    }

    private static QuoteHeaders authorizedHeaders(String correlationId) {
        return QuoteHeaders.of("PRICING_LOAN_OFFICER", "actor-1", correlationId, "idem-" + correlationId);
    }

    private static QuoteCreateRequest validRequest(String tenantId) {
        return new QuoteCreateRequest(
                tenantId, "borrower-1", "SYNTH_FICO_STANDARD", 500000, 400000,
                "TX", "75001", "SINGLE_FAMILY", 1, "PRIMARY", "CONVENTIONAL", "PURCHASE", "RETAIL", 30);
    }

    private static final class CountingScenarioAdapter implements QuoteApi.ScenarioAdapter {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ScenarioReference createScenario(String tenantId, QuoteCreateRequest request) {
            calls.incrementAndGet();
            return new ScenarioReference("scenario-" + tenantId, 1);
        }
    }

    private static final class CountingCatalogAdapter implements QuoteApi.CatalogCandidateAdapter {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public List<CatalogCandidate> activeConventionalCandidates(String tenantId, String channel) {
            calls.incrementAndGet();
            return List.of(
                    new CatalogCandidate("PRODUCT-Z", "INVESTOR-B", channel, 20),
                    new CatalogCandidate("PRODUCT-A", "INVESTOR-A", channel, 10));
        }
    }

    private static final class CountingEligibilityAdapter implements QuoteApi.EligibilityEvaluationAdapter {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public EligibilityEvaluation evaluate(String tenantId, ScenarioReference scenario, CatalogCandidate candidate) {
            calls.incrementAndGet();
            if ("PRODUCT-A".equals(candidate.productCode())) {
                return EligibilityEvaluation.eligibleResult();
            }
            return EligibilityEvaluation.ineligible(new QuoteReason(
                    "REQ001_FICO_BAND_UNSUPPORTED",
                    "FAIL",
                    "Representative FICO band is outside synthetic candidate policy",
                    "Review borrower credit facts",
                    "SYNTH_FICO_LOW",
                    "SYNTH_FICO_STANDARD"));
        }
    }
}

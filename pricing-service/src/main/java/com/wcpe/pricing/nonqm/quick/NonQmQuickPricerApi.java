package com.wcpe.pricing.nonqm.quick;

import com.wcpe.pricing.nonqm.NonQmPricingApi;
import com.wcpe.pricing.nonqm.NonQmPricingApi.EligibilityDecision;
import com.wcpe.pricing.nonqm.NonQmPricingApi.EligibilityStatus;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmBatchPriceResult;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmBlocker;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmMarginPolicy;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPriceResult;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPricingHeaders;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmPricingRequest;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmProductType;
import com.wcpe.pricing.nonqm.NonQmPricingApi.NonQmRateSheet;
import com.wcpe.pricing.nonqm.NonQmPricingApi.StaticNonQmRateSheetResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fast-path Non-QM quick pricer that reuses the configured Non-QM pricing core.
 *
 * <p>The quick path intentionally does not invent bands, rates, thresholds, or investor rules. It accepts configured tier
 * facts directly or through an injected {@link QuickTierResolver}. Missing configured tier facts return a preliminary
 * no-offer result with visible assumptions/blockers rather than deriving hidden defaults.</p>
 */
public final class NonQmQuickPricerApi {
    public static final String QUICK_PRICE_PERMISSION = NonQmPricingApi.NON_QM_PRICE_PERMISSION;
    public static final String PRELIMINARY_STATUS = "PRELIMINARY";
    public static final String NO_OFFER_STATUS = "NO_OFFER";
    public static final String CONVERTED_STATUS = "CONVERTED";
    public static final Duration QUICK_QUOTE_BUDGET = Duration.ofMillis(200);

    private final QuickCandidateProvider candidateProvider;
    private final QuickEligibilityAdapter eligibilityAdapter;
    private final QuickTierResolver tierResolver;
    private final QuickQuoteRepository repository;
    private final ScenarioDraftClient scenarioDraftClient;
    private final Clock clock;
    private final Map<QuickCacheKey, List<NonQmPriceResult>> priceCache = new ConcurrentHashMap<>();

    public NonQmQuickPricerApi() {
        this(new StaticQuickCandidateProvider(List.of()), new AlwaysReferableEligibilityAdapter(), new RequestSuppliedTierResolver(),
                new FailClosedQuickQuoteRepository(), new FailClosedScenarioDraftClient(), Clock.systemUTC());
    }

    public NonQmQuickPricerApi(QuickCandidateProvider candidateProvider, QuickEligibilityAdapter eligibilityAdapter,
            QuickTierResolver tierResolver, QuickQuoteRepository repository, ScenarioDraftClient scenarioDraftClient, Clock clock) {
        this.candidateProvider = Objects.requireNonNull(candidateProvider);
        this.eligibilityAdapter = Objects.requireNonNull(eligibilityAdapter);
        this.tierResolver = Objects.requireNonNull(tierResolver);
        this.repository = Objects.requireNonNull(repository);
        this.scenarioDraftClient = Objects.requireNonNull(scenarioDraftClient);
        this.clock = Objects.requireNonNull(clock);
    }

    public QuickQuoteResult quote(String tenantId, NonQmQuickQuoteHeaders headers, NonQmQuickQuoteRequest request) {
        Instant started = clock.instant();
        requireTenant(tenantId);
        requireAuthorized(headers);
        validateRequest(tenantId, request);

        QuickFactBundle facts = tierResolver.resolve(request);
        List<QuickQuoteAssumption> assumptions = mergeAssumptions(facts.assumptions(), templateFor(request.productType()).assumptions());
        List<QuickQuoteBlocker> factBlockers = new ArrayList<>(facts.blockers());
        List<QuickPriceCandidate> candidates = candidateProvider.findQuickPriceCandidates(tenantId, request.channelCode(), request.productType());
        if (candidates.isEmpty()) {
            factBlockers.add(new QuickQuoteBlocker("NO_QUICK_PRICE_CANDIDATES",
                    "no quick-price-capable Non-QM products are configured for tenant/channel/type",
                    "catalog-service.quick-candidates", "publish quick-price-capable products"));
        }

        QuickCacheKey cacheKey = QuickCacheKey.from(tenantId, request, facts, candidates);
        boolean cacheHit = false;
        List<NonQmPriceResult> priceResults = List.of();
        if (factBlockers.isEmpty()) {
            List<NonQmPriceResult> cached = priceCache.get(cacheKey);
            if (cached == null) {
                priceResults = priceCandidates(tenantId, headers, request, facts, candidates);
                priceCache.put(cacheKey, priceResults);
            } else {
                priceResults = cached;
                cacheHit = true;
            }
        }

        List<QuickQuoteOffer> offers = priceResults.stream()
                .filter(result -> "PRICED".equals(result.status()))
                .map(QuickQuoteOffer::from)
                .sorted(Comparator.comparing(QuickQuoteOffer::rate).thenComparing(QuickQuoteOffer::price, Comparator.reverseOrder()))
                .toList();
        List<QuickQuoteBlocker> blockers = new ArrayList<>(factBlockers);
        priceResults.stream()
                .filter(result -> !"PRICED".equals(result.status()))
                .flatMap(result -> result.blockers().stream())
                .map(QuickQuoteBlocker::from)
                .forEach(blockers::add);

        long latencyMillis = Math.max(0, Duration.between(started, clock.instant()).toMillis());
        String quoteId = "quick-" + UUID.nameUUIDFromBytes((tenantId + ":" + headers.correlationId() + ":" + started).getBytes(StandardCharsets.UTF_8));
        QuickQuoteResult result = new QuickQuoteResult(quoteId, tenantId, request.channelCode(), request.productType(),
                offers.isEmpty() ? NO_OFFER_STATUS : PRELIMINARY_STATUS, true, cacheHit ? "HIT" : "MISS", latencyMillis,
                latencyMillis < QUICK_QUOTE_BUDGET.toMillis(), List.copyOf(offers), List.copyOf(blockers), assumptions,
                request, null, "audit-quick-" + headers.correlationId(), headers.correlationId(), started);
        repository.save(result);
        return result;
    }

    public QuickQuoteResult getQuote(String tenantId, String quickQuoteId, NonQmQuickQuoteHeaders headers) {
        requireTenant(tenantId);
        requireText(quickQuoteId, "quick_quote_id is required");
        requireAuthorized(headers);
        QuickQuoteResult result = repository.findById(quickQuoteId)
                .orElseThrow(() -> new QuickQuoteNotFoundException("quick quote not found"));
        if (!tenantId.equals(result.tenantId())) {
            throw new QuickQuoteAccessDeniedException("quick quote tenant does not match request tenant");
        }
        return result;
    }

    public ScenarioReference continueToFullScenario(String tenantId, String quickQuoteId, NonQmQuickQuoteHeaders headers) {
        QuickQuoteResult result = getQuote(tenantId, quickQuoteId, headers);
        ScenarioReference scenario = scenarioDraftClient.createFromQuickQuote(result);
        repository.save(result.withConvertedScenario(scenario.scenarioId()));
        return scenario;
    }

    public QuickPricerUiConfiguration uiConfiguration() {
        return new QuickPricerUiConfiguration("/pricing/non-qm/quick", List.of(
                productForm("DSCR", true, "propertyType", "loanAmount", "estimatedValue", "dscrRatio", "fico", "state"),
                productForm("BANK_STATEMENT", true, "loanAmount", "estimatedValue", "statementMonths", "statementType",
                        "qualifyingMonthlyIncome", "fico", "state"),
                productForm("ASSET_DEPLETION", true, "loanAmount", "estimatedValue", "assetType", "assetIncomeMethod",
                        "seasoningBand", "fico", "state"),
                productForm("FIX_FLIP", false, "loanAmount", "purchasePrice", "arv", "rehabBudget", "term", "fico", "state", "exitStrategy")),
                List.of("rate", "price", "investor", "product", "eligibilityStatus", "topConditions", "assumptions", "continueFullApplication"));
    }

    private List<NonQmPriceResult> priceCandidates(String tenantId, NonQmQuickQuoteHeaders headers, NonQmQuickQuoteRequest request,
            QuickFactBundle facts, List<QuickPriceCandidate> candidates) {
        NonQmPricingApi pricingApi = new NonQmPricingApi(new StaticNonQmRateSheetResolver(
                candidates.stream().map(QuickPriceCandidate::rateSheet).toList()));
        List<NonQmPricingRequest> pricingRequests = candidates.stream()
                .map(candidate -> new NonQmPricingRequest(tenantId, scenarioId(request, candidate), request.productType(),
                        candidate.investorCode(), request.channelCode(), clock.instant(), facts.tierFacts(), facts.numericFacts(),
                        eligibilityAdapter.evaluate(request, candidate, facts), request.marginPolicies()))
                .toList();
        NonQmBatchPriceResult batch = pricingApi.priceBatch(tenantId,
                new NonQmPricingHeaders(headers.permissions(), headers.actorId(), headers.correlationId()), pricingRequests);
        return batch.results();
    }

    private static String scenarioId(NonQmQuickQuoteRequest request, QuickPriceCandidate candidate) {
        return "quick-scenario-" + UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.productType() + ":"
                + request.channelCode() + ":" + candidate.investorCode() + ":" + request.loanAmount() + ":" + request.fico()
                + ":" + request.state()).getBytes(StandardCharsets.UTF_8));
    }

    private static ProductTemplate templateFor(NonQmProductType type) {
        return ProductTemplate.TEMPLATES.getOrDefault(type, ProductTemplate.EMPTY);
    }

    private static List<QuickQuoteAssumption> mergeAssumptions(List<QuickQuoteAssumption> supplied, List<QuickQuoteAssumption> template) {
        Map<String, QuickQuoteAssumption> merged = new LinkedHashMap<>();
        for (QuickQuoteAssumption assumption : supplied == null ? List.<QuickQuoteAssumption>of() : supplied) {
            merged.put(assumption.code(), assumption);
        }
        for (QuickQuoteAssumption assumption : template) {
            merged.putIfAbsent(assumption.code(), assumption);
        }
        return List.copyOf(merged.values());
    }

    private static QuickPricerProductForm productForm(String productType, boolean pricingEnabled, String... fields) {
        return new QuickPricerProductForm(productType, pricingEnabled, List.of(fields));
    }

    private static void validateRequest(String tenantId, NonQmQuickQuoteRequest request) {
        if (request == null) {
            throw new QuickQuoteValidationException("request is required");
        }
        requireText(request.tenantId(), "tenant_id is required");
        if (!tenantId.equals(request.tenantId())) {
            throw new QuickQuoteAccessDeniedException("request tenant does not match path tenant");
        }
        requireText(request.channelCode(), "channel_code is required");
        if (request.productType() == null) {
            throw new QuickQuoteValidationException("product_type is required");
        }
        if (request.loanAmount() == null || request.loanAmount().signum() <= 0) {
            throw new QuickQuoteValidationException("loan_amount must be positive");
        }
        if (request.fico() == null || request.fico() <= 0) {
            throw new QuickQuoteValidationException("fico is required");
        }
        requireText(request.state(), "state is required");
    }

    private static void requireAuthorized(NonQmQuickQuoteHeaders headers) {
        if (headers == null) {
            throw new QuickQuoteAccessDeniedException("headers are required");
        }
        requireText(headers.actorId(), "X-Actor-Id is required");
        requireText(headers.correlationId(), "X-Correlation-Id is required");
        if (!headers.permissions().contains(QUICK_PRICE_PERMISSION)) {
            throw new QuickQuoteAccessDeniedException(QUICK_PRICE_PERMISSION + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new QuickQuoteValidationException(message);
        }
    }

    public record NonQmQuickQuoteHeaders(Set<String> permissions, String actorId, String correlationId) {
        public NonQmQuickQuoteHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }

        public static NonQmQuickQuoteHeaders of(String permissionsHeader, String actorId, String correlationId) {
            Set<String> parsed = new HashSet<>();
            if (permissionsHeader != null && !permissionsHeader.isBlank()) {
                for (String permission : permissionsHeader.split(",")) {
                    if (!permission.isBlank()) {
                        parsed.add(permission.trim());
                    }
                }
            }
            return new NonQmQuickQuoteHeaders(parsed, actorId, correlationId);
        }
    }

    public record NonQmQuickQuoteRequest(String tenantId, String channelCode, NonQmProductType productType,
            BigDecimal loanAmount, Integer fico, String state, Map<String, String> tierFacts,
            Map<String, BigDecimal> numericFacts, Map<String, NonQmMarginPolicy> marginPolicies) {
        public NonQmQuickQuoteRequest {
            tierFacts = tierFacts == null ? Map.of() : Map.copyOf(tierFacts);
            numericFacts = numericFacts == null ? Map.of() : Map.copyOf(numericFacts);
            marginPolicies = marginPolicies == null ? Map.of() : Map.copyOf(marginPolicies);
        }
    }

    public record QuickFactBundle(Map<String, String> tierFacts, Map<String, BigDecimal> numericFacts,
            List<QuickQuoteAssumption> assumptions, List<QuickQuoteBlocker> blockers) {
        public QuickFactBundle {
            tierFacts = tierFacts == null ? Map.of() : Map.copyOf(tierFacts);
            numericFacts = numericFacts == null ? Map.of() : Map.copyOf(numericFacts);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    public record QuickQuoteAssumption(String code, String displayText, String source, boolean mustConfirmBeforeLock) {}

    public record QuickQuoteBlocker(String code, String message, String sourceRef, String remediationHint) {
        static QuickQuoteBlocker from(NonQmBlocker blocker) {
            return new QuickQuoteBlocker(blocker.code(), blocker.message(), blocker.sourceRef(), blocker.remediationHint());
        }
    }

    public record QuickPriceCandidate(String investorCode, NonQmRateSheet rateSheet) {
        public QuickPriceCandidate {
            requireText(investorCode, "investor_code is required");
            rateSheet = Objects.requireNonNull(rateSheet, "rate sheet is required");
        }
    }

    public record QuickQuoteOffer(String priceId, String investorCode, String productCode, BigDecimal rate, BigDecimal price,
            String eligibilityStatus, List<String> topConditions, List<String> versionRefs, String resultHash) {
        public QuickQuoteOffer {
            topConditions = topConditions == null ? List.of() : List.copyOf(topConditions);
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }

        static QuickQuoteOffer from(NonQmPriceResult result) {
            return new QuickQuoteOffer(result.priceId().toString(), result.investorCode(), result.investorProductCode(),
                    result.finalNoteRate(), result.finalPrice(), result.status(), List.of("PRELIMINARY_QUOTE"), result.versionRefs(),
                    result.resultHash());
        }
    }

    public record QuickQuoteResult(String quickQuoteId, String tenantId, String channelCode, NonQmProductType productType,
            String status, boolean preliminary, String cacheStatus, long latencyMillis, boolean latencyBudgetMet,
            List<QuickQuoteOffer> offers, List<QuickQuoteBlocker> blockers, List<QuickQuoteAssumption> assumptions,
            NonQmQuickQuoteRequest request, String convertedScenarioId, String auditReference, String correlationId,
            Instant createdAt) {
        public QuickQuoteResult {
            offers = offers == null ? List.of() : List.copyOf(offers);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        }

        QuickQuoteResult withConvertedScenario(String scenarioId) {
            return new QuickQuoteResult(quickQuoteId, tenantId, channelCode, productType, CONVERTED_STATUS, preliminary,
                    cacheStatus, latencyMillis, latencyBudgetMet, offers, blockers, assumptions, request, scenarioId,
                    auditReference, correlationId, createdAt);
        }
    }

    public record ScenarioReference(String scenarioId, String sourceQuickQuoteId, List<QuickQuoteAssumption> confirmationTasks) {
        public ScenarioReference {
            confirmationTasks = confirmationTasks == null ? List.of() : List.copyOf(confirmationTasks);
        }
    }

    public record QuickPricerUiConfiguration(String route, List<QuickPricerProductForm> forms, List<String> resultCardFields) {
        public QuickPricerUiConfiguration {
            forms = forms == null ? List.of() : List.copyOf(forms);
            resultCardFields = resultCardFields == null ? List.of() : List.copyOf(resultCardFields);
        }
    }

    public record QuickPricerProductForm(String productType, boolean pricingEnabled, List<String> fields) {
        public QuickPricerProductForm {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public interface QuickCandidateProvider {
        List<QuickPriceCandidate> findQuickPriceCandidates(String tenantId, String channelCode, NonQmProductType productType);
    }

    public static final class StaticQuickCandidateProvider implements QuickCandidateProvider {
        private final List<NonQmRateSheet> rateSheets;

        public StaticQuickCandidateProvider(List<NonQmRateSheet> rateSheets) {
            this.rateSheets = rateSheets == null ? List.of() : List.copyOf(rateSheets);
        }

        @Override
        public List<QuickPriceCandidate> findQuickPriceCandidates(String tenantId, String channelCode, NonQmProductType productType) {
            return rateSheets.stream()
                    .filter(sheet -> sheet.status() == NonQmPricingApi.RateSheetStatus.PUBLISHED)
                    .filter(sheet -> sheet.channelCode().equalsIgnoreCase(channelCode))
                    .filter(sheet -> sheet.productType() == productType)
                    .map(sheet -> new QuickPriceCandidate(sheet.investorCode(), sheet))
                    .toList();
        }
    }

    public interface QuickEligibilityAdapter {
        EligibilityDecision evaluate(NonQmQuickQuoteRequest request, QuickPriceCandidate candidate, QuickFactBundle facts);
    }

    public static final class AlwaysReferableEligibilityAdapter implements QuickEligibilityAdapter {
        @Override
        public EligibilityDecision evaluate(NonQmQuickQuoteRequest request, QuickPriceCandidate candidate, QuickFactBundle facts) {
            return new EligibilityDecision(EligibilityStatus.REFER,
                    "quick-pricer:referable:" + request.productType(), "QUICK_PRICE_PRELIMINARY_REFER");
        }
    }

    public interface QuickTierResolver {
        QuickFactBundle resolve(NonQmQuickQuoteRequest request);
    }

    public static final class RequestSuppliedTierResolver implements QuickTierResolver {
        @Override
        public QuickFactBundle resolve(NonQmQuickQuoteRequest request) {
            ProductTemplate template = templateFor(request.productType());
            List<String> missing = template.requiredTierFacts().stream()
                    .filter(fact -> request.tierFacts().get(fact) == null || request.tierFacts().get(fact).isBlank())
                    .toList();
            List<QuickQuoteBlocker> blockers = missing.isEmpty() ? List.of()
                    : List.of(new QuickQuoteBlocker("QUICK_TIER_FACT_MISSING",
                            "configured quick pricing tier facts are required before Non-QM core pricing",
                            String.join(",", missing), "provide configured tier mappings from catalog/eligibility configuration"));
            List<QuickQuoteAssumption> assumptions = missing.stream()
                    .map(fact -> new QuickQuoteAssumption("CONFIRM_" + fact.toUpperCase(),
                            "Confirm configured quick pricing fact: " + fact, "REQUIRED_FOR_FULL_INTAKE", true))
                    .toList();
            return new QuickFactBundle(request.tierFacts(), request.numericFacts(), assumptions, blockers);
        }
    }

    public interface QuickQuoteRepository {
        void save(QuickQuoteResult result);
        Optional<QuickQuoteResult> findById(String quickQuoteId);
    }

    public static final class FailClosedQuickQuoteRepository implements QuickQuoteRepository {
        private static final String MESSAGE = "Non-QM quick quote durable schema is not configured; refusing in-memory source-of-truth fallback";

        @Override
        public void save(QuickQuoteResult result) {
            throw new QuickQuoteValidationException(MESSAGE);
        }

        @Override
        public Optional<QuickQuoteResult> findById(String quickQuoteId) {
            throw new QuickQuoteValidationException(MESSAGE);
        }
    }

    public interface ScenarioDraftClient {
        ScenarioReference createFromQuickQuote(QuickQuoteResult result);
    }

    public static final class FailClosedScenarioDraftClient implements ScenarioDraftClient {
        @Override
        public ScenarioReference createFromQuickQuote(QuickQuoteResult result) {
            throw new QuickQuoteValidationException(
                    "Non-QM quick quote scenario-draft datasource/schema contract is not configured; refusing in-memory fallback");
        }
    }

    private record QuickCacheKey(String tenantId, String channelCode, NonQmProductType productType, BigDecimal loanAmount,
            Integer fico, String state, Map<String, String> tierFacts, Map<String, BigDecimal> numericFacts,
            List<String> candidateRefs) {
        private QuickCacheKey {
            tierFacts = tierFacts == null ? Map.of() : Map.copyOf(tierFacts);
            numericFacts = numericFacts == null ? Map.of() : Map.copyOf(numericFacts);
            candidateRefs = candidateRefs == null ? List.of() : List.copyOf(candidateRefs);
        }

        static QuickCacheKey from(String tenantId, NonQmQuickQuoteRequest request, QuickFactBundle facts,
                List<QuickPriceCandidate> candidates) {
            return new QuickCacheKey(tenantId, request.channelCode(), request.productType(), request.loanAmount(), request.fico(),
                    request.state(), facts.tierFacts(), facts.numericFacts(), candidates.stream()
                    .map(candidate -> candidate.rateSheet().rateSheetId() + ":" + candidate.rateSheet().version())
                    .sorted().toList());
        }
    }

    private record ProductTemplate(List<String> requiredTierFacts, List<QuickQuoteAssumption> assumptions) {
        private static final ProductTemplate EMPTY = new ProductTemplate(List.of(), List.of());
        private static final Map<NonQmProductType, ProductTemplate> TEMPLATES = templates();

        private static Map<NonQmProductType, ProductTemplate> templates() {
            EnumMap<NonQmProductType, ProductTemplate> templates = new EnumMap<>(NonQmProductType.class);
            templates.put(NonQmProductType.DSCR, new ProductTemplate(List.of("dscrTier", "ficoBand", "ltvBand"),
                    assumptions("DSCR", "lease/rent evidence", "property cash flow", "prepayment structure")));
            templates.put(NonQmProductType.BANK_STATEMENT, new ProductTemplate(
                    List.of("statementType", "statementMonths", "ficoBand", "ltvBand"),
                    assumptions("BANK_STATEMENT", "bank statement documentation", "expense factor", "income trend support")));
            templates.put(NonQmProductType.ASSET_DEPLETION, new ProductTemplate(
                    List.of("assetType", "assetIncomeMethod", "seasoningBand", "ficoBand", "ltvBand"),
                    assumptions("ASSET_DEPLETION", "asset source documentation", "asset seasoning", "full reserve analysis")));
            return Map.copyOf(templates);
        }

        private static List<QuickQuoteAssumption> assumptions(String prefix, String... items) {
            return List.of(items).stream()
                    .map(item -> new QuickQuoteAssumption(prefix + "_" + item.toUpperCase().replace(' ', '_').replace('/', '_'),
                            "Confirm " + item + " before lock", "REQUIRED_FOR_FULL_INTAKE", true))
                    .collect(Collectors.toUnmodifiableList());
        }
    }

    public static class QuickQuoteValidationException extends RuntimeException {
        public QuickQuoteValidationException(String message) { super(message); }
    }

    public static class QuickQuoteAccessDeniedException extends RuntimeException {
        public QuickQuoteAccessDeniedException(String message) { super(message); }
    }

    public static class QuickQuoteNotFoundException extends RuntimeException {
        public QuickQuoteNotFoundException(String message) { super(message); }
    }
}

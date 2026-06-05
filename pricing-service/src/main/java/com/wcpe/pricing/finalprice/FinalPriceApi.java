package com.wcpe.pricing.finalprice;

import com.wcpe.pricing.rounding.api.RoundingPolicyApi;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.ResolveRoundingPolicyRequest;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundedValue;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingHeaders;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyConflictException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyNotSatisfiedException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyResolution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FinalPriceApi {
    public static final String FINAL_PRICE_WRITE_PERMISSION = "pricing.quote.calculate";
    public static final String FINAL_PRICE_READ_PERMISSION = "pricing.final-price.read";

    private static final int INTERMEDIATE_SCALE = 8;
    private static final int PERSISTED_PRICE_SCALE = 5;

    private final FinalPriceRepository repository;
    private final BaseRateSelectionPort selectionPort;
    private final ScenarioFactsPort scenarioFactsPort;
    private final PricingConfigurationPort configurationPort;
    private final RoundingPolicyApi roundingPolicyApi;

    public FinalPriceApi(
            FinalPriceRepository repository,
            BaseRateSelectionPort selectionPort,
            ScenarioFactsPort scenarioFactsPort,
            PricingConfigurationPort configurationPort,
            RoundingPolicyApi roundingPolicyApi) {
        this.repository = Objects.requireNonNull(repository);
        this.selectionPort = Objects.requireNonNull(selectionPort);
        this.scenarioFactsPort = Objects.requireNonNull(scenarioFactsPort);
        this.configurationPort = Objects.requireNonNull(configurationPort);
        this.roundingPolicyApi = Objects.requireNonNull(roundingPolicyApi);
    }

    public FinalPriceResponse calculate(String tenantId, FinalPriceHeaders headers, FinalPriceRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, FINAL_PRICE_WRITE_PERMISSION);
        validateRequest(request);

        String requestHash = stableHash("request", tenantId, request.selectionId(), request.scenarioId(),
                request.scenarioHash(), request.pricingConfigVersionRefs(), request.asOf(), request.quoteRequestId(), request.dryRun());
        Optional<FinalPriceResult> prior = repository.findByIdempotencyKey(tenantId, headers.idempotencyKey());
        if (prior.isPresent()) {
            if (!prior.get().requestHash().equals(requestHash)) {
                throw new FinalPriceException(FinalPriceErrorCode.IDEMPOTENCY_CONFLICT,
                        "idempotency key was already used for a different final price request");
            }
            return prior.get().response();
        }

        SelectedBaseRate selection = selectionPort.findSelection(tenantId, request.selectionId())
                .orElseThrow(() -> new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED,
                        "base rate selection is required"));
        if (!tenantId.equals(selection.tenantId())) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED,
                    "base rate selection tenant does not match request tenant");
        }

        ScenarioFacts facts = scenarioFactsPort.findFacts(tenantId, request.scenarioId(), request.scenarioHash())
                .orElseThrow(() -> new FinalPriceException(FinalPriceErrorCode.SCENARIO_FACT_MISSING,
                        "scenario facts are required for deterministic adjustment eligibility"));
        PricingConfigurationSnapshot configuration = configurationPort.findSnapshot(tenantId,
                        request.pricingConfigVersionRefs(), request.asOf())
                .orElseThrow(() -> new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFIG_MISSING,
                        "published pricing adjustment configuration is required"));

        List<FinalPriceLedgerEntry> ledger = new ArrayList<>();
        BigDecimal basePrice = priceScale(selection.basePrice());
        BigDecimal subtotal = basePrice.setScale(INTERMEDIATE_SCALE, RoundingMode.UNNECESSARY);
        ledger.add(new FinalPriceLedgerEntry(1, "BASE_PRICE", basePrice, "START", subtotal,
                selection.gridVersionRef(), "BASE_RATE_SELECTED", null));

        List<AdjustmentResult> adjustments = applyAdjustments(configuration, facts, selection.lockPeriodDays(), ledger, subtotal);
        if (!adjustments.isEmpty()) {
            subtotal = adjustments.get(adjustments.size() - 1).outputValue();
        }

        List<CapFloorResult> capFloorResults = applyCapsFloors(configuration, ledger, subtotal);
        if (!capFloorResults.isEmpty()) {
            subtotal = capFloorResults.get(capFloorResults.size() - 1).outputValue();
        }

        RoundingPolicyResolution rounding = resolveRounding(tenantId, headers, configuration, request.asOf(), subtotal);
        RoundedValue roundedValue = rounding.roundedValue();
        if (roundedValue == null) {
            throw new FinalPriceException(FinalPriceErrorCode.ROUNDING_POLICY_MISSING,
                    "rounding policy did not return a rounded final price");
        }
        BigDecimal roundedFinalPrice = priceScale(roundedValue.outputValue());
        ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "ROUND_FINAL_PRICE", subtotal,
                "ROUND", roundedFinalPrice, rounding.policyVersionId() + ":" + rounding.ruleId(),
                roundedValue.reasonCode(), roundedValue.roundingMode()));

        VersionGraph versionGraph = configuration.versionGraph(selection.gridVersionRef(), rounding.policyVersionId());
        String resultHash = stableHash("final-price", tenantId, request.selectionId(), request.scenarioHash(),
                roundedFinalPrice, versionGraph.hash(), adjustments, capFloorResults, ledger);
        String cacheKey = "pricing:final-price:%s:%s:%s:%s".formatted(
                tenantId, request.scenarioHash(), request.selectionId(), versionGraph.hash());
        FinalPriceResponse response = new FinalPriceResponse(
                UUID.nameUUIDFromBytes((tenantId + ":" + resultHash).getBytes(StandardCharsets.UTF_8)),
                selection.selectedNoteRate(),
                selection.lockPeriodDays(),
                basePrice,
                List.copyOf(adjustments),
                subtotal.setScale(INTERMEDIATE_SCALE, RoundingMode.UNNECESSARY),
                List.copyOf(capFloorResults),
                roundedFinalPrice,
                List.copyOf(ledger),
                versionGraph,
                resultHash,
                cacheKey);

        if (!request.dryRun()) {
            repository.save(new FinalPriceResult(
                    response.finalPriceId(), tenantId, request.selectionId(), request.scenarioHash(), requestHash,
                    headers.idempotencyKey(), response, headers.actorId(), headers.correlationId(), Instant.now()));
            repository.saveEvent(new FinalPriceEvent(
                    "pricing.final-price-calculated.v1", tenantId + ":" + response.finalPriceId(), tenantId,
                    response.finalPriceId(), response.roundedFinalPrice(), response.selectedNoteRate(),
                    response.lockPeriodDays(), response.versionGraph().refs(), response.resultHash(), headers.correlationId()));
            repository.saveAudit(new FinalPriceAudit(
                    "FINAL_PRICE_CALCULATION_COMPLETED", tenantId, response.finalPriceId(), headers.actorId(),
                    headers.correlationId(), response.versionGraph().refs(), response.resultHash()));
        }
        return response;
    }

    public FinalPriceResponse get(String tenantId, UUID finalPriceId, FinalPriceHeaders headers) {
        requireTenant(tenantId);
        requirePermission(headers, FINAL_PRICE_READ_PERMISSION);
        requireNonNull(finalPriceId, "final_price_id is required");
        FinalPriceResult result = repository.findById(finalPriceId)
                .orElseThrow(() -> new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED,
                        "final price result was not found"));
        if (!tenantId.equals(result.tenantId())) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED,
                    "final price tenant does not match request tenant");
        }
        return result.response();
    }

    private List<AdjustmentResult> applyAdjustments(
            PricingConfigurationSnapshot configuration,
            ScenarioFacts facts,
            int lockPeriodDays,
            List<FinalPriceLedgerEntry> ledger,
            BigDecimal startingSubtotal) {
        List<AdjustmentRule> applicable = configuration.adjustmentRules().stream()
                .filter(rule -> rule.appliesTo(facts, lockPeriodDays))
                .sorted(Comparator.comparing(AdjustmentRule::precedence).thenComparing(AdjustmentRule::ruleId))
                .toList();

        Set<Integer> precedences = new HashSet<>();
        for (AdjustmentRule rule : applicable) {
            if (!precedences.add(rule.precedence())) {
                throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFLICT,
                        "multiple applicable adjustment rules share precedence " + rule.precedence());
            }
        }

        BigDecimal running = startingSubtotal;
        List<AdjustmentResult> results = new ArrayList<>();
        for (AdjustmentRule rule : applicable) {
            BigDecimal input = running;
            running = running.add(intermediateScale(rule.amount())).setScale(INTERMEDIATE_SCALE, RoundingMode.UNNECESSARY);
            AdjustmentResult result = new AdjustmentResult(rule.ruleId(), rule.versionRef(), rule.reasonCode(),
                    intermediateScale(rule.amount()), input, running);
            results.add(result);
            ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "ADJUSTMENT", input, "ADD", running,
                    rule.versionRef() + ":" + rule.ruleId(), rule.reasonCode(), null));
        }
        return results;
    }

    private List<CapFloorResult> applyCapsFloors(
            PricingConfigurationSnapshot configuration,
            List<FinalPriceLedgerEntry> ledger,
            BigDecimal subtotal) {
        List<CapFloorResult> results = new ArrayList<>();
        BigDecimal running = subtotal;
        for (CapFloorRule rule : configuration.capFloorRules()) {
            if (rule.minPrice() != null && running.compareTo(rule.minPrice()) < 0) {
                throw new FinalPriceException(FinalPriceErrorCode.CAP_FLOOR_BLOCKED, rule.reasonCode());
            }
            if (rule.maxPrice() != null && running.compareTo(rule.maxPrice()) > 0) {
                throw new FinalPriceException(FinalPriceErrorCode.CAP_FLOOR_BLOCKED, rule.reasonCode());
            }
            CapFloorResult result = new CapFloorResult(rule.versionRef(), rule.reasonCode(), subtotal, running, false);
            results.add(result);
            ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "CAP_FLOOR_CHECK", running, "CHECK", running,
                    rule.versionRef(), rule.reasonCode(), null));
        }
        return results;
    }

    private RoundingPolicyResolution resolveRounding(
            String tenantId,
            FinalPriceHeaders headers,
            PricingConfigurationSnapshot configuration,
            Instant asOf,
            BigDecimal subtotal) {
        try {
            return roundingPolicyApi.resolve(tenantId, new ResolveRoundingPolicyRequest(
                    configuration.roundingScope(),
                    configuration.productCode(),
                    configuration.investorCode(),
                    configuration.channelCode(),
                    configuration.roundingOutputContext(),
                    LocalDate.ofInstant(asOf, ZoneOffset.UTC),
                    subtotal),
                    new RoundingHeaders(Set.of(RoundingPolicyApi.ROUNDING_READ_PERMISSION),
                            headers.actorId(), headers.correlationId(), headers.idempotencyKey()));
        } catch (RoundingPolicyNotSatisfiedException ex) {
            throw new FinalPriceException(FinalPriceErrorCode.ROUNDING_POLICY_MISSING, ex.getMessage());
        } catch (RoundingPolicyConflictException ex) {
            throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFLICT, ex.getMessage());
        }
    }

    private static void validateRequest(FinalPriceRequest request) {
        if (request == null) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED, "request is required");
        }
        requireNonNull(request.selectionId(), "selection_id is required");
        requireText(request.scenarioId(), "scenario_id is required");
        requireText(request.scenarioHash(), "scenario_hash is required");
        if (request.pricingConfigVersionRefs().isEmpty()) {
            throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFIG_MISSING,
                    "pricing_config_version_refs are required");
        }
        requireNonNull(request.asOf(), "as_of is required");
    }

    private static void requirePermission(FinalPriceHeaders headers, String permission) {
        if (headers == null) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED, "headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        requireText(headers.idempotencyKey(), "idempotency_key is required");
        if (!headers.permissions().contains(permission)) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED,
                    permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED, message);
        }
    }

    private static void requireNonNull(Object value, String message) {
        if (value == null) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED, message);
        }
    }

    private static BigDecimal priceScale(BigDecimal value) {
        if (value == null) {
            throw new FinalPriceException(FinalPriceErrorCode.BASE_RATE_SELECTION_REQUIRED, "price value is required");
        }
        return value.setScale(PERSISTED_PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal intermediateScale(BigDecimal value) {
        if (value == null) {
            throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFIG_MISSING, "amount is required");
        }
        return value.setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    private static String stableHash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public enum FinalPriceErrorCode {
        BASE_RATE_SELECTION_REQUIRED,
        ADJUSTMENT_CONFIG_MISSING,
        ADJUSTMENT_CONFLICT,
        CAP_FLOOR_BLOCKED,
        ROUNDING_POLICY_MISSING,
        SCENARIO_FACT_MISSING,
        IDEMPOTENCY_CONFLICT
    }

    public record FinalPriceHeaders(Set<String> permissions, String actorId, String correlationId, String idempotencyKey) {
        public FinalPriceHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record FinalPriceRequest(
            UUID selectionId,
            String scenarioId,
            String scenarioHash,
            List<String> pricingConfigVersionRefs,
            Instant asOf,
            String quoteRequestId,
            boolean dryRun) {
        public FinalPriceRequest {
            pricingConfigVersionRefs = pricingConfigVersionRefs == null ? List.of() : List.copyOf(pricingConfigVersionRefs);
        }
    }

    public record FinalPriceResponse(
            UUID finalPriceId,
            BigDecimal selectedNoteRate,
            int lockPeriodDays,
            BigDecimal basePrice,
            List<AdjustmentResult> adjustments,
            BigDecimal subtotal,
            List<CapFloorResult> capFloorResults,
            BigDecimal roundedFinalPrice,
            List<FinalPriceLedgerEntry> ledger,
            VersionGraph versionGraph,
            String resultHash,
            String cacheKey) {
        public FinalPriceResponse {
            selectedNoteRate = selectedNoteRate == null ? null : selectedNoteRate.setScale(PERSISTED_PRICE_SCALE, RoundingMode.HALF_UP);
            basePrice = priceScale(basePrice);
            subtotal = intermediateScale(subtotal);
            roundedFinalPrice = priceScale(roundedFinalPrice);
            adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
            capFloorResults = capFloorResults == null ? List.of() : List.copyOf(capFloorResults);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
        }
    }

    public record AdjustmentResult(
            String ruleId,
            String versionRef,
            String reasonCode,
            BigDecimal amount,
            BigDecimal inputValue,
            BigDecimal outputValue) {
        public AdjustmentResult {
            amount = intermediateScale(amount);
            inputValue = intermediateScale(inputValue);
            outputValue = intermediateScale(outputValue);
        }
    }

    public record CapFloorResult(
            String versionRef,
            String reasonCode,
            BigDecimal inputValue,
            BigDecimal outputValue,
            boolean adjusted) {
        public CapFloorResult {
            inputValue = intermediateScale(inputValue);
            outputValue = intermediateScale(outputValue);
        }
    }

    public record FinalPriceLedgerEntry(
            int ordinal,
            String step,
            BigDecimal inputValue,
            String operation,
            BigDecimal outputValue,
            String configRef,
            String reasonCode,
            RoundingMode roundingMode) {
        public FinalPriceLedgerEntry {
            inputValue = inputValue == null ? null : intermediateScale(inputValue);
            outputValue = outputValue == null ? null : intermediateScale(outputValue);
        }
    }

    public record VersionGraph(List<String> refs, String hash) {
        public VersionGraph {
            refs = refs == null ? List.of() : List.copyOf(refs.stream().sorted().toList());
            hash = hash == null || hash.isBlank() ? stableHash(refs) : hash;
        }
    }

    public record SelectedBaseRate(
            String tenantId,
            UUID selectionId,
            BigDecimal selectedNoteRate,
            BigDecimal basePrice,
            int lockPeriodDays,
            String gridVersionRef,
            String resultHash) {
        public SelectedBaseRate {
            selectedNoteRate = selectedNoteRate == null ? null : selectedNoteRate.setScale(PERSISTED_PRICE_SCALE, RoundingMode.HALF_UP);
            basePrice = priceScale(basePrice);
        }
    }

    public record ScenarioFacts(String tenantId, String scenarioId, String scenarioHash, Map<String, String> facts) {
        public ScenarioFacts {
            facts = facts == null ? Map.of() : Map.copyOf(facts);
        }

        String value(String factKey) {
            return facts.get(factKey);
        }
    }

    public record AdjustmentRule(
            String ruleId,
            String versionRef,
            String requiredFactKey,
            String requiredFactValue,
            Integer lockPeriodDays,
            BigDecimal amount,
            int precedence,
            String reasonCode) {
        public AdjustmentRule {
            requireText(ruleId, "rule_id is required");
            requireText(versionRef, "version_ref is required");
            amount = intermediateScale(amount);
            requireText(reasonCode, "reason_code is required");
        }

        boolean appliesTo(ScenarioFacts facts, int selectedLockPeriodDays) {
            if (lockPeriodDays != null && lockPeriodDays != selectedLockPeriodDays) {
                return false;
            }
            if (requiredFactKey == null || requiredFactKey.isBlank()) {
                return true;
            }
            return Objects.equals(requiredFactValue, facts.value(requiredFactKey));
        }
    }

    public record CapFloorRule(String versionRef, BigDecimal minPrice, BigDecimal maxPrice, String reasonCode) {
        public CapFloorRule {
            requireText(versionRef, "cap_floor version_ref is required");
            minPrice = minPrice == null ? null : intermediateScale(minPrice);
            maxPrice = maxPrice == null ? null : intermediateScale(maxPrice);
            requireText(reasonCode, "reason_code is required");
        }
    }

    public record PricingConfigurationSnapshot(
            String tenantId,
            List<String> versionRefs,
            String productCode,
            String investorCode,
            String channelCode,
            String roundingScope,
            String roundingOutputContext,
            List<AdjustmentRule> adjustmentRules,
            List<CapFloorRule> capFloorRules) {
        public PricingConfigurationSnapshot {
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
            adjustmentRules = adjustmentRules == null ? List.of() : List.copyOf(adjustmentRules);
            capFloorRules = capFloorRules == null ? List.of() : List.copyOf(capFloorRules);
            roundingScope = roundingScope == null || roundingScope.isBlank() ? "BASE" : roundingScope;
            roundingOutputContext = roundingOutputContext == null || roundingOutputContext.isBlank()
                    ? "FINAL_PRICE" : roundingOutputContext;
        }

        VersionGraph versionGraph(String gridVersionRef, String roundingPolicyVersionRef) {
            List<String> refs = new ArrayList<>(versionRefs);
            refs.add(gridVersionRef);
            refs.add(roundingPolicyVersionRef);
            return new VersionGraph(refs, stableHash(refs));
        }
    }

    public record FinalPriceResult(
            UUID id,
            String tenantId,
            UUID selectionId,
            String scenarioHash,
            String requestHash,
            String idempotencyKey,
            FinalPriceResponse response,
            String actorId,
            String correlationId,
            Instant createdAt) {
    }

    public record FinalPriceEvent(
            String eventType,
            String eventKey,
            String tenantId,
            UUID finalPriceId,
            BigDecimal finalPrice,
            BigDecimal selectedNoteRate,
            int lockPeriodDays,
            List<String> versionRefs,
            String resultHash,
            String correlationId) {
        public FinalPriceEvent {
            finalPrice = priceScale(finalPrice);
            selectedNoteRate = selectedNoteRate == null ? null : selectedNoteRate.setScale(PERSISTED_PRICE_SCALE, RoundingMode.HALF_UP);
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }
    }

    public record FinalPriceAudit(
            String action,
            String tenantId,
            UUID finalPriceId,
            String actorId,
            String correlationId,
            List<String> versionRefs,
            String resultHash) {
        public FinalPriceAudit {
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
        }
    }

    public interface BaseRateSelectionPort {
        Optional<SelectedBaseRate> findSelection(String tenantId, UUID selectionId);
    }

    public interface ScenarioFactsPort {
        Optional<ScenarioFacts> findFacts(String tenantId, String scenarioId, String scenarioHash);
    }

    public interface PricingConfigurationPort {
        Optional<PricingConfigurationSnapshot> findSnapshot(String tenantId, List<String> versionRefs, Instant asOf);
    }

    public interface FinalPriceRepository {
        void save(FinalPriceResult result);

        Optional<FinalPriceResult> findById(UUID finalPriceId);

        Optional<FinalPriceResult> findByIdempotencyKey(String tenantId, String idempotencyKey);

        void saveEvent(FinalPriceEvent event);

        void saveAudit(FinalPriceAudit audit);
    }

    public static final class InMemoryFinalPriceRepository implements FinalPriceRepository {
        private final Map<UUID, FinalPriceResult> results = new ConcurrentHashMap<>();
        private final Map<String, UUID> idempotencyIndex = new ConcurrentHashMap<>();
        private final List<FinalPriceEvent> events = new ArrayList<>();
        private final List<FinalPriceAudit> audits = new ArrayList<>();

        @Override
        public void save(FinalPriceResult result) {
            results.put(result.id(), result);
            idempotencyIndex.put(result.tenantId() + ":" + result.idempotencyKey(), result.id());
        }

        @Override
        public Optional<FinalPriceResult> findById(UUID finalPriceId) {
            return Optional.ofNullable(results.get(finalPriceId));
        }

        @Override
        public Optional<FinalPriceResult> findByIdempotencyKey(String tenantId, String idempotencyKey) {
            UUID resultId = idempotencyIndex.get(tenantId + ":" + idempotencyKey);
            return resultId == null ? Optional.empty() : findById(resultId);
        }

        @Override
        public void saveEvent(FinalPriceEvent event) {
            events.add(event);
        }

        @Override
        public void saveAudit(FinalPriceAudit audit) {
            audits.add(audit);
        }

        public List<FinalPriceEvent> events() {
            return List.copyOf(events);
        }

        public List<FinalPriceAudit> audits() {
            return List.copyOf(audits);
        }
    }

    public static class FinalPriceException extends RuntimeException {
        private final FinalPriceErrorCode code;

        public FinalPriceException(FinalPriceErrorCode code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code);
        }

        public FinalPriceErrorCode code() {
            return code;
        }
    }
}

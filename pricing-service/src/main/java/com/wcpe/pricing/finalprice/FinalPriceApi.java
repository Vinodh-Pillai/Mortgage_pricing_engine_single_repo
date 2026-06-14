package com.wcpe.pricing.finalprice;

import com.wcpe.pricing.rounding.api.RoundingPolicyApi;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.ResolveRoundingPolicyRequest;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundedValue;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingHeaders;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyConflictException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyNotSatisfiedException;
import com.wcpe.pricing.rounding.api.RoundingPolicyApi.RoundingPolicyResolution;
import com.wcpe.pricing.mi.MiPricingApi;
import com.wcpe.pricing.mi.MiPricingApi.MiPriceOption;
import com.wcpe.pricing.mi.MiPricingApi.MiPriceRequest;
import com.wcpe.pricing.mi.MiPricingApi.MiPriceResponse;
import com.wcpe.pricing.mi.MiPricingApi.MiPricingHeaders;
import com.wcpe.pricing.mi.MiPricingApi.MiProgram;
import com.wcpe.pricing.mi.MiPricingApi.MiRateCard;
import com.wcpe.pricing.government.GovernmentPricingApi;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentFeeLineItem;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPriceOption;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPriceRequest;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPriceResponse;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentPricingHeaders;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductConfiguration;
import com.wcpe.pricing.government.GovernmentPricingApi.GovernmentProductType;

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
import java.util.LinkedHashMap;
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
    private final AdjustmentCalculationPort adjustmentCalculationPort;
    private final MiPricingApi miPricingApi;
    private final GovernmentPricingApi governmentPricingApi;

    public FinalPriceApi(
            FinalPriceRepository repository,
            BaseRateSelectionPort selectionPort,
            ScenarioFactsPort scenarioFactsPort,
            PricingConfigurationPort configurationPort,
            RoundingPolicyApi roundingPolicyApi) {
        this(repository, selectionPort, scenarioFactsPort, configurationPort, roundingPolicyApi,
                new ConfigurationAdjustmentCalculationPort(), new MiPricingApi(), new GovernmentPricingApi());
    }

    public FinalPriceApi(
            FinalPriceRepository repository,
            BaseRateSelectionPort selectionPort,
            ScenarioFactsPort scenarioFactsPort,
            PricingConfigurationPort configurationPort,
            RoundingPolicyApi roundingPolicyApi,
            AdjustmentCalculationPort adjustmentCalculationPort) {
        this(repository, selectionPort, scenarioFactsPort, configurationPort, roundingPolicyApi,
                adjustmentCalculationPort, new MiPricingApi(), new GovernmentPricingApi());
    }

    public FinalPriceApi(
            FinalPriceRepository repository,
            BaseRateSelectionPort selectionPort,
            ScenarioFactsPort scenarioFactsPort,
            PricingConfigurationPort configurationPort,
            RoundingPolicyApi roundingPolicyApi,
            AdjustmentCalculationPort adjustmentCalculationPort,
            MiPricingApi miPricingApi) {
        this(repository, selectionPort, scenarioFactsPort, configurationPort, roundingPolicyApi,
                adjustmentCalculationPort, miPricingApi, new GovernmentPricingApi());
    }

    public FinalPriceApi(
            FinalPriceRepository repository,
            BaseRateSelectionPort selectionPort,
            ScenarioFactsPort scenarioFactsPort,
            PricingConfigurationPort configurationPort,
            RoundingPolicyApi roundingPolicyApi,
            AdjustmentCalculationPort adjustmentCalculationPort,
            MiPricingApi miPricingApi,
            GovernmentPricingApi governmentPricingApi) {
        this.repository = Objects.requireNonNull(repository);
        this.selectionPort = Objects.requireNonNull(selectionPort);
        this.scenarioFactsPort = Objects.requireNonNull(scenarioFactsPort);
        this.configurationPort = Objects.requireNonNull(configurationPort);
        this.roundingPolicyApi = Objects.requireNonNull(roundingPolicyApi);
        this.adjustmentCalculationPort = Objects.requireNonNull(adjustmentCalculationPort);
        this.miPricingApi = Objects.requireNonNull(miPricingApi);
        this.governmentPricingApi = Objects.requireNonNull(governmentPricingApi);
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

        AdjustmentCalculationRequest adjustmentRequest = buildAdjustmentCalculationRequest(tenantId, request, selection, facts, configuration);
        AdjustmentCalculationResult adjustmentCalculation = adjustmentCalculationPort instanceof ConfigurationAdjustmentCalculationPort adapter
                ? adapter.calculate(adjustmentRequest, configuration, facts, selection.lockPeriodDays())
                : adjustmentCalculationPort.calculate(adjustmentRequest);
        if (adjustmentCalculation == null) {
            throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFIG_MISSING,
                    "adjustment-service did not return a calculation result");
        }
        if (adjustmentCalculation.blocked()) {
            throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFIG_MISSING,
                    "adjustment-service blocked calculation: " + adjustmentCalculation.resultHash());
        }
        List<AdjustmentResult> adjustments = applyAdjustmentLines(adjustmentCalculation, ledger, subtotal);
        if (!adjustments.isEmpty()) {
            subtotal = adjustments.get(adjustments.size() - 1).outputValue();
        }

        MortgageInsuranceApplication mortgageInsuranceApplication = applyMortgageInsurance(
                tenantId, headers, configuration, facts, ledger, subtotal);
        subtotal = mortgageInsuranceApplication.subtotal();
        List<MiPriceOption> mortgageInsurance = mortgageInsuranceApplication.options();

        GovernmentPricingApplication governmentPricingApplication = applyGovernmentPricing(
                tenantId, headers, configuration, facts, ledger, subtotal);
        subtotal = governmentPricingApplication.subtotal();
        List<GovernmentPriceOption> governmentPricing = governmentPricingApplication.options();

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

        List<String> insuranceAndGovernmentRefs = new ArrayList<>(mortgageInsurance.stream().map(MiPriceOption::versionRef).toList());
        insuranceAndGovernmentRefs.addAll(governmentPricing.stream().map(GovernmentPriceOption::versionRef).toList());
        VersionGraph versionGraph = configuration.versionGraph(selection.gridVersionRef(), rounding.policyVersionId(),
                insuranceAndGovernmentRefs);
        String resultHash = stableHash("final-price", tenantId, request.selectionId(), request.scenarioHash(),
                roundedFinalPrice, versionGraph.hash(), adjustmentCalculation.resultHash(), adjustments, mortgageInsurance,
                governmentPricing, capFloorResults, ledger);
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
                List.copyOf(mortgageInsurance),
                List.copyOf(governmentPricing),
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

    private static AdjustmentCalculationRequest buildAdjustmentCalculationRequest(
            String tenantId,
            FinalPriceRequest request,
            SelectedBaseRate selection,
            ScenarioFacts facts,
            PricingConfigurationSnapshot configuration) {
        Map<String, Object> loanFacts = new LinkedHashMap<>(facts.facts());
        loanFacts.putIfAbsent("lockPeriodDays", selection.lockPeriodDays());
        BasePriceDecisionStub baseDecision = new BasePriceDecisionStub(
                request.scenarioId(), selection.selectionId().toString(), String.valueOf(selection.selectedNoteRate()),
                selection.basePrice().doubleValue(), "PRICE_POINTS", "pricing-service");
        RuleBookSelector selector = new RuleBookSelector(configuration.productCode(), configuration.investorCode(), configuration.channelCode());
        return new AdjustmentCalculationRequest(
                baseDecision,
                facts.facts(),
                List.of(),
                String.join(",", request.pricingConfigVersionRefs()),
                tenantUuid(tenantId),
                selector,
                request.asOf(),
                loanFacts);
    }

    private static List<AdjustmentResult> applyAdjustmentLines(
            AdjustmentCalculationResult calculation,
            List<FinalPriceLedgerEntry> ledger,
            BigDecimal startingSubtotal) {
        BigDecimal running = startingSubtotal;
        List<AdjustmentResult> results = new ArrayList<>();
        for (AdjustmentLine line : calculation.adjustments()) {
            BigDecimal input = running;
            BigDecimal amount = intermediateScale(BigDecimal.valueOf(line.amount()));
            running = running.add(amount).setScale(INTERMEDIATE_SCALE, RoundingMode.UNNECESSARY);
            String ruleId = firstNonBlank(line.ruleId(), line.factorKey(), line.sourceRef());
            String versionRef = firstNonBlank(line.sourceRef(), line.source(), calculation.referenceDataVersion());
            String reasonCode = firstNonBlank(line.reason(), line.factorKey(), "ADJUSTMENT_LINE");
            AdjustmentResult result = new AdjustmentResult(ruleId, versionRef, reasonCode, amount, input, running,
                    firstNonBlank(line.outputType(), "POINTS_DELTA"), line.warnings());
            results.add(result);
            ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "ADJUSTMENT", input,
                    amount.signum() < 0 ? "SUB" : "ADD", running,
                    versionRef + ":" + ruleId, reasonCode, null));
        }
        return results;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static UUID tenantUuid(String tenantId) {
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(tenantId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static List<AdjustmentLine> configuredAdjustmentLines(
            PricingConfigurationSnapshot configuration,
            ScenarioFacts facts,
            int lockPeriodDays) {
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

        List<AdjustmentLine> results = new ArrayList<>();
        for (AdjustmentRule rule : applicable) {
            results.add(new AdjustmentLine(rule.ruleId(), rule.amount().doubleValue(), rule.reasonCode(),
                    rule.versionRef(), "POINTS_DELTA", rule.ruleId(), rule.versionRef(), true,
                    rule.reasonCode(), rule.versionRef() + ":" + rule.ruleId(), List.of()));
        }
        return results;
    }

    private List<CapFloorResult> applyCapsFloors(
            PricingConfigurationSnapshot configuration,
            List<FinalPriceLedgerEntry> ledger,
            BigDecimal subtotal) {
        if (configuration.priceBoundaryPolicyRequired() && configuration.capFloorRules().isEmpty()) {
            throw new FinalPriceException(FinalPriceErrorCode.PRICE_BOUNDARY_POLICY_MISSING,
                    "published price boundary policy is required");
        }
        List<CapFloorResult> results = new ArrayList<>();
        BigDecimal running = subtotal;
        for (CapFloorRule rule : configuration.capFloorRules().stream()
                .sorted(Comparator.comparing(CapFloorRule::precedence).thenComparing(CapFloorRule::versionRef))
                .toList()) {
            BigDecimal input = running;
            boolean belowFloor = rule.minPrice() != null && running.compareTo(rule.minPrice()) < 0;
            boolean aboveCap = rule.maxPrice() != null && running.compareTo(rule.maxPrice()) > 0;
            if (belowFloor || aboveCap) {
                running = switch (rule.action()) {
                    case ADJUST -> belowFloor ? rule.minPrice() : rule.maxPrice();
                    case BLOCK -> throw new FinalPriceException(FinalPriceErrorCode.CAP_FLOOR_BLOCKED, rule.reasonCode());
                    case WARN -> throw new FinalPriceException(FinalPriceErrorCode.PRICE_BOUNDARY_POLICY_NOT_SATISFIED,
                            "WARN action is not enabled by pricing-service configuration");
                };
            }
            boolean adjusted = input.compareTo(running) != 0;
            CapFloorResult result = new CapFloorResult(rule.versionRef(), rule.reasonCode(), input, running,
                    adjusted, rule.action());
            results.add(result);
            ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "CAP_FLOOR_CHECK", input,
                    adjusted ? "ADJUST" : "CHECK", running,
                    rule.versionRef(), rule.reasonCode(), null));
        }
        return results;
    }

    private MortgageInsuranceApplication applyMortgageInsurance(
            String tenantId,
            FinalPriceHeaders headers,
            PricingConfigurationSnapshot configuration,
            ScenarioFacts facts,
            List<FinalPriceLedgerEntry> ledger,
            BigDecimal subtotal) {
        if (configuration.miRateCards().isEmpty() && configuration.miPrograms().isEmpty()
                && !configuration.mortgageInsuranceRequired()) {
            return new MortgageInsuranceApplication(List.of(), subtotal);
        }
        String loanType = firstFact(facts, "loanType", "loan_type");
        BigDecimal ltv = decimalFact(facts, "ltv", "loanToValue", "loan_to_value");
        Integer fico = integerFact(facts, "fico", "representativeFico", "representative_fico");
        BigDecimal loanAmount = decimalFact(facts, "loanAmount", "loan_amount");
        Integer coveragePercent = integerFact(facts, "miCoveragePercent", "coveragePercent", "coverage_percent");
        if (loanType == null || ltv == null || fico == null || loanAmount == null || coveragePercent == null) {
            if (configuration.mortgageInsuranceRequired()) {
                throw new FinalPriceException(FinalPriceErrorCode.MI_PRICING_BLOCKED,
                        "mortgage insurance pricing facts are required when MI is configured as required");
            }
            return new MortgageInsuranceApplication(List.of(), subtotal);
        }
        MiPriceResponse response = miPricingApi.price(tenantId,
                new MiPricingHeaders(Set.of(MiPricingApi.MI_PRICE_PERMISSION), headers.actorId(), headers.correlationId()),
                new MiPriceRequest(loanType, ltv, fico, loanAmount, coveragePercent,
                        configuration.miPrograms(), configuration.miRateCards()));
        if (!response.blockers().isEmpty()) {
            throw new FinalPriceException(FinalPriceErrorCode.MI_PRICING_BLOCKED,
                    response.blockers().get(0).code() + ": " + response.blockers().get(0).message());
        }
        MiPriceOption selected = response.selectedOption();
        if (selected == null) {
            return new MortgageInsuranceApplication(List.of(), subtotal);
        }
        BigDecimal input = subtotal;
        BigDecimal output = subtotal.add(selected.priceAdjustment()).setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
        String operation = selected.priceAdjustment().signum() == 0 ? "INCLUDE" : selected.priceAdjustment().signum() < 0 ? "SUB" : "ADD";
        ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "MORTGAGE_INSURANCE", input, operation, output,
                selected.versionRef() + ":" + selected.sourceRef(), "MI_" + selected.carrier() + "_" + selected.premiumType(), null));
        return new MortgageInsuranceApplication(List.of(selected), output);
    }

    private GovernmentPricingApplication applyGovernmentPricing(
            String tenantId,
            FinalPriceHeaders headers,
            PricingConfigurationSnapshot configuration,
            ScenarioFacts facts,
            List<FinalPriceLedgerEntry> ledger,
            BigDecimal subtotal) {
        GovernmentProductType productType = governmentProductType(facts, configuration);
        if (productType == null && configuration.governmentProductConfigurations().isEmpty()) {
            return new GovernmentPricingApplication(List.of(), subtotal);
        }
        if (productType == null) {
            return new GovernmentPricingApplication(List.of(), subtotal);
        }
        if (configuration.governmentProductConfigurations().isEmpty()) {
            throw new FinalPriceException(FinalPriceErrorCode.GOVERNMENT_CONFIG_MISSING,
                    "government fee, loan-limit, and eligibility configuration is required for " + productType);
        }

        BigDecimal loanAmount = decimalFact(facts, "loanAmount", "loan_amount");
        String countyFips = firstFact(facts, "countyFips", "county_fips");
        String state = firstFact(facts, "propertyState", "state");
        if (loanAmount == null || countyFips == null || state == null) {
            throw new FinalPriceException(FinalPriceErrorCode.GOVERNMENT_CONFIG_MISSING,
                    "government pricing requires loanAmount, countyFips, and state facts");
        }

        GovernmentPriceResponse response = governmentPricingApi.price(tenantId,
                new GovernmentPricingHeaders(Set.of(GovernmentPricingApi.GOVERNMENT_PRICE_PERMISSION),
                        headers.actorId(), headers.correlationId()),
                new GovernmentPriceRequest(productType, loanAmount, countyFips, state,
                        decimalFact(facts, "householdIncome", "household_income"),
                        firstFact(facts, "propertyEligibilityRef", "property_eligibility_ref"),
                        booleanFact(facts, "vaFundingFeeExempt", "va_funding_fee_exempt"),
                        booleanFact(facts, "vaFirstUse", "va_first_use"),
                        decimalFactOrZero(facts, "downPaymentPercent", "down_payment_percent"),
                        decimalFactOrZero(facts, "vaEntitlementUsed", "va_entitlement_used"),
                        configuration.governmentProductConfigurations()));
        if (!response.blockers().isEmpty()) {
            throw new FinalPriceException(FinalPriceErrorCode.GOVERNMENT_CONFIG_MISSING,
                    response.blockers().get(0).code() + ": " + response.blockers().get(0).message());
        }
        GovernmentPriceOption selected = response.selectedOption();
        if (selected == null) {
            return new GovernmentPricingApplication(List.of(), subtotal);
        }
        for (GovernmentFeeLineItem lineItem : selected.lineItems()) {
            ledger.add(new FinalPriceLedgerEntry(ledger.size() + 1, "GOVERNMENT_FEE", subtotal,
                    "INCLUDE", subtotal, lineItem.versionRef() + ":" + lineItem.sourceRef(),
                    lineItem.feeType(), null));
        }
        return new GovernmentPricingApplication(List.of(selected), subtotal);
    }

    private static GovernmentProductType governmentProductType(ScenarioFacts facts, PricingConfigurationSnapshot configuration) {
        String raw = firstNonBlank(firstFact(facts, "loanType", "loan_type", "productType", "product_type"),
                configuration.productCode());
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "FHA", "FHA_FIXED", "FHA_ARM" -> GovernmentProductType.FHA;
            case "VA", "VA_FIXED", "VA_ARM" -> GovernmentProductType.VA;
            case "USDA", "USDA_RURAL", "USDA_FIXED" -> GovernmentProductType.USDA;
            default -> null;
        };
    }

    private static String firstFact(ScenarioFacts facts, String... keys) {
        for (String key : keys) {
            String value = facts.value(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimalFact(ScenarioFacts facts, String... keys) {
        String value = firstFact(facts, keys);
        return value == null ? null : new BigDecimal(value);
    }

    private static Integer integerFact(ScenarioFacts facts, String... keys) {
        String value = firstFact(facts, keys);
        return value == null ? null : Integer.valueOf(value);
    }

    private static BigDecimal decimalFactOrZero(ScenarioFacts facts, String... keys) {
        BigDecimal value = decimalFact(facts, keys);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean booleanFact(ScenarioFacts facts, String... keys) {
        String value = firstFact(facts, keys);
        return value != null && Boolean.parseBoolean(value);
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
        PRICE_BOUNDARY_POLICY_MISSING,
        PRICE_BOUNDARY_POLICY_NOT_SATISFIED,
        PRICE_BOUNDARY_CONFLICT,
        ROUNDING_POLICY_MISSING,
        SCENARIO_FACT_MISSING,
        IDEMPOTENCY_CONFLICT,
        MI_PRICING_BLOCKED,
        GOVERNMENT_CONFIG_MISSING
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
            List<MiPriceOption> mortgageInsurance,
            List<GovernmentPriceOption> governmentPricing,
            BigDecimal roundedFinalPrice,
            List<FinalPriceLedgerEntry> ledger,
            VersionGraph versionGraph,
            String resultHash,
            String cacheKey) {
        public FinalPriceResponse(UUID finalPriceId, BigDecimal selectedNoteRate, int lockPeriodDays,
                BigDecimal basePrice, List<AdjustmentResult> adjustments, BigDecimal subtotal,
                List<CapFloorResult> capFloorResults, BigDecimal roundedFinalPrice,
                List<FinalPriceLedgerEntry> ledger, VersionGraph versionGraph, String resultHash, String cacheKey) {
            this(finalPriceId, selectedNoteRate, lockPeriodDays, basePrice, adjustments, subtotal,
                    capFloorResults, List.of(), List.of(), roundedFinalPrice, ledger, versionGraph, resultHash, cacheKey);
        }

        public FinalPriceResponse(UUID finalPriceId, BigDecimal selectedNoteRate, int lockPeriodDays,
                BigDecimal basePrice, List<AdjustmentResult> adjustments, BigDecimal subtotal,
                List<CapFloorResult> capFloorResults, List<MiPriceOption> mortgageInsurance,
                BigDecimal roundedFinalPrice, List<FinalPriceLedgerEntry> ledger, VersionGraph versionGraph,
                String resultHash, String cacheKey) {
            this(finalPriceId, selectedNoteRate, lockPeriodDays, basePrice, adjustments, subtotal,
                    capFloorResults, mortgageInsurance, List.of(), roundedFinalPrice, ledger, versionGraph, resultHash, cacheKey);
        }

        public FinalPriceResponse {
            selectedNoteRate = selectedNoteRate == null ? null : selectedNoteRate.setScale(PERSISTED_PRICE_SCALE, RoundingMode.HALF_UP);
            basePrice = priceScale(basePrice);
            subtotal = intermediateScale(subtotal);
            roundedFinalPrice = priceScale(roundedFinalPrice);
            adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
            capFloorResults = capFloorResults == null ? List.of() : List.copyOf(capFloorResults);
            mortgageInsurance = mortgageInsurance == null ? List.of() : List.copyOf(mortgageInsurance);
            governmentPricing = governmentPricing == null ? List.of() : List.copyOf(governmentPricing);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
        }
    }

    private record MortgageInsuranceApplication(List<MiPriceOption> options, BigDecimal subtotal) {
        private MortgageInsuranceApplication {
            options = options == null ? List.of() : List.copyOf(options);
            subtotal = intermediateScale(subtotal);
        }
    }

    private record GovernmentPricingApplication(List<GovernmentPriceOption> options, BigDecimal subtotal) {
        private GovernmentPricingApplication {
            options = options == null ? List.of() : List.copyOf(options);
            subtotal = intermediateScale(subtotal);
        }
    }

    public record AdjustmentResult(
            String ruleId,
            String versionRef,
            String reasonCode,
            BigDecimal amount,
            BigDecimal inputValue,
            BigDecimal outputValue,
            String outputType,
            List<String> conditions) {
        public AdjustmentResult(String ruleId, String versionRef, String reasonCode, BigDecimal amount,
                BigDecimal inputValue, BigDecimal outputValue) {
            this(ruleId, versionRef, reasonCode, amount, inputValue, outputValue, "POINTS_DELTA", List.of());
        }

        public AdjustmentResult {
            amount = intermediateScale(amount);
            inputValue = intermediateScale(inputValue);
            outputValue = intermediateScale(outputValue);
            outputType = outputType == null || outputType.isBlank() ? "POINTS_DELTA" : outputType;
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
        }
    }

    public record BasePriceDecisionStub(
            String scenarioId,
            String basePriceId,
            String baseRateBasis,
            double basePriceAmount,
            String currency,
            String source) {
    }

    public record RuleBookSelector(String productFamily, String investor, String channel) {
    }

    public record AdjustmentFactor(String factorKey, double amount, String reason) {
    }

    public record AdjustmentCalculationRequest(
            BasePriceDecisionStub basePriceDecision,
            Map<String, String> loanAttributes,
            List<AdjustmentFactor> adjustmentFactors,
            String referenceDataVersion,
            UUID tenantId,
            RuleBookSelector selector,
            Instant quoteDate,
            Map<String, Object> loanFacts) {
        public AdjustmentCalculationRequest {
            loanAttributes = loanAttributes == null ? Map.of() : Map.copyOf(loanAttributes);
            adjustmentFactors = adjustmentFactors == null ? List.of() : List.copyOf(adjustmentFactors);
            loanFacts = loanFacts == null ? Map.of() : Map.copyOf(loanFacts);
        }
    }

    public record AdjustmentLine(
            String factorKey,
            double amount,
            String reason,
            String source,
            String outputType,
            String ruleId,
            String sourceRef,
            boolean applied,
            String label,
            String auditRef,
            List<String> warnings) {
        public AdjustmentLine {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record AdjustmentCalculationResult(
            String scenarioId,
            String basePriceId,
            List<AdjustmentLine> adjustments,
            double totalAdjustment,
            String referenceDataVersion,
            String calculationMode,
            List<String> auditRefs,
            String resultHash,
            Map<String, Object> totalsByType,
            boolean blocked) {
        public AdjustmentCalculationResult {
            adjustments = adjustments == null ? List.of() : List.copyOf(adjustments);
            auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
            totalsByType = totalsByType == null ? Map.of() : Map.copyOf(totalsByType);
        }
    }

    public interface AdjustmentCalculationPort {
        AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request);
    }

    private static final class ConfigurationAdjustmentCalculationPort implements AdjustmentCalculationPort {
        @Override
        public AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request) {
            throw new FinalPriceException(FinalPriceErrorCode.ADJUSTMENT_CONFIG_MISSING,
                    "configuration-backed adjustment adapter requires FinalPriceApi context");
        }

        AdjustmentCalculationResult calculate(AdjustmentCalculationRequest request,
                PricingConfigurationSnapshot configuration, ScenarioFacts facts, int lockPeriodDays) {
            List<AdjustmentLine> lines = configuredAdjustmentLines(configuration, facts, lockPeriodDays);
            BigDecimal total = lines.stream()
                    .map(line -> BigDecimal.valueOf(line.amount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
            return new AdjustmentCalculationResult(
                    request.basePriceDecision().scenarioId(),
                    request.basePriceDecision().basePriceId(),
                    lines,
                    total.doubleValue(),
                    request.referenceDataVersion(),
                    "configuration-adapter",
                    lines.stream().map(AdjustmentLine::auditRef).toList(),
                    stableHash("configuration-adjustments", request.referenceDataVersion(), lines),
                    Map.of("POINTS_DELTA", total),
                    false);
        }
    }

    public record CapFloorResult(
            String versionRef,
            String reasonCode,
            BigDecimal inputValue,
            BigDecimal outputValue,
            boolean adjusted,
            CapFloorAction action) {
        public CapFloorResult {
            inputValue = intermediateScale(inputValue);
            outputValue = intermediateScale(outputValue);
            action = action == null ? CapFloorAction.BLOCK : action;
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

    public enum CapFloorAction {
        ADJUST,
        BLOCK,
        WARN
    }

    public record CapFloorRule(
            String versionRef,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            CapFloorAction action,
            int precedence,
            String reasonCode) {
        public CapFloorRule(String versionRef, BigDecimal minPrice, BigDecimal maxPrice, String reasonCode) {
            this(versionRef, minPrice, maxPrice, CapFloorAction.BLOCK, 0, reasonCode);
        }

        public CapFloorRule {
            requireText(versionRef, "cap_floor version_ref is required");
            minPrice = minPrice == null ? null : intermediateScale(minPrice);
            maxPrice = maxPrice == null ? null : intermediateScale(maxPrice);
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                throw new FinalPriceException(FinalPriceErrorCode.PRICE_BOUNDARY_CONFLICT,
                        "price boundary floor cannot be greater than cap");
            }
            action = action == null ? CapFloorAction.BLOCK : action;
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
            List<CapFloorRule> capFloorRules,
            List<MiRateCard> miRateCards,
            List<MiProgram> miPrograms,
            List<GovernmentProductConfiguration> governmentProductConfigurations,
            boolean mortgageInsuranceRequired,
            boolean priceBoundaryPolicyRequired) {
        public PricingConfigurationSnapshot(
                String tenantId,
                List<String> versionRefs,
                String productCode,
                String investorCode,
                String channelCode,
                String roundingScope,
                String roundingOutputContext,
                List<AdjustmentRule> adjustmentRules,
                List<CapFloorRule> capFloorRules) {
            this(tenantId, versionRefs, productCode, investorCode, channelCode, roundingScope, roundingOutputContext,
                    adjustmentRules, capFloorRules, List.of(), List.of(), List.of(), false, false);
        }

        public PricingConfigurationSnapshot(
                String tenantId,
                List<String> versionRefs,
                String productCode,
                String investorCode,
                String channelCode,
                String roundingScope,
                String roundingOutputContext,
                List<AdjustmentRule> adjustmentRules,
                List<CapFloorRule> capFloorRules,
                boolean priceBoundaryPolicyRequired) {
            this(tenantId, versionRefs, productCode, investorCode, channelCode, roundingScope, roundingOutputContext,
                    adjustmentRules, capFloorRules, List.of(), List.of(), List.of(), false, priceBoundaryPolicyRequired);
        }

        public PricingConfigurationSnapshot(
                String tenantId,
                List<String> versionRefs,
                String productCode,
                String investorCode,
                String channelCode,
                String roundingScope,
                String roundingOutputContext,
                List<AdjustmentRule> adjustmentRules,
                List<CapFloorRule> capFloorRules,
                List<MiRateCard> miRateCards,
                List<MiProgram> miPrograms,
                boolean mortgageInsuranceRequired) {
            this(tenantId, versionRefs, productCode, investorCode, channelCode, roundingScope, roundingOutputContext,
                    adjustmentRules, capFloorRules, miRateCards, miPrograms, List.of(), mortgageInsuranceRequired, false);
        }

        public PricingConfigurationSnapshot(
                String tenantId,
                List<String> versionRefs,
                String productCode,
                String investorCode,
                String channelCode,
                String roundingScope,
                String roundingOutputContext,
                List<AdjustmentRule> adjustmentRules,
                List<CapFloorRule> capFloorRules,
                List<MiRateCard> miRateCards,
                List<MiProgram> miPrograms,
                List<GovernmentProductConfiguration> governmentProductConfigurations,
                boolean mortgageInsuranceRequired) {
            this(tenantId, versionRefs, productCode, investorCode, channelCode, roundingScope, roundingOutputContext,
                    adjustmentRules, capFloorRules, miRateCards, miPrograms, governmentProductConfigurations,
                    mortgageInsuranceRequired, false);
        }

        public PricingConfigurationSnapshot {
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
            adjustmentRules = adjustmentRules == null ? List.of() : List.copyOf(adjustmentRules);
            capFloorRules = capFloorRules == null ? List.of() : List.copyOf(capFloorRules);
            miRateCards = miRateCards == null ? List.of() : List.copyOf(miRateCards);
            miPrograms = miPrograms == null ? List.of() : List.copyOf(miPrograms);
            governmentProductConfigurations = governmentProductConfigurations == null ? List.of() : List.copyOf(governmentProductConfigurations);
            roundingScope = roundingScope == null || roundingScope.isBlank() ? "BASE" : roundingScope;
            roundingOutputContext = roundingOutputContext == null || roundingOutputContext.isBlank()
                    ? "FINAL_PRICE" : roundingOutputContext;
        }

        VersionGraph versionGraph(String gridVersionRef, String roundingPolicyVersionRef) {
            return versionGraph(gridVersionRef, roundingPolicyVersionRef, List.of());
        }

        VersionGraph versionGraph(String gridVersionRef, String roundingPolicyVersionRef, List<String> extraRefs) {
            List<String> refs = new ArrayList<>(versionRefs);
            refs.add(gridVersionRef);
            refs.add(roundingPolicyVersionRef);
            if (extraRefs != null) {
                refs.addAll(extraRefs);
            }
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

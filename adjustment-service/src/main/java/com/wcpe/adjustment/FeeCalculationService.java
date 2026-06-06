package com.wcpe.adjustment;

import com.wcpe.adjustment.FeeCatalogVersion.CalculationMethod;
import com.wcpe.adjustment.FeeCatalogVersion.CatalogStatus;
import com.wcpe.adjustment.FeeCatalogVersion.FeeCatalogRequestContext;
import com.wcpe.adjustment.FeeCatalogVersion.FeeDefinition;
import com.wcpe.adjustment.FeeCatalogVersion.FeePrecisionPolicy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Deterministic fee calculator for PII-06-S07.
 *
 * <p>The calculator consumes tenant-scoped, effective-dated fee catalog configuration
 * and caller-supplied config values. It does not embed mortgage pricing fee values,
 * thresholds, rates, investor policy, or compliance constants.</p>
 */
public final class FeeCalculationService {
    public static final String EVENT_TOPIC = "pricing.quote.fees.v1";
    public static final String EVENT_TYPE = "QuoteFeesCalculated.v1";
    public static final String SOURCE_SERVICE = "adjustment-service";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    public FeeCalculationResult calculate(FeeCalculationRequest request) {
        Objects.requireNonNull(request, "fee calculation request is required");
        request.validate();
        FeePrecisionPolicy precision = request.precisionPolicy();
        MathContext mathContext = new MathContext(precision.precision());
        List<FeeLine> lines = new ArrayList<>();

        List<FeeDefinition> definitions = request.catalog().definitions().stream()
            .filter(FeeDefinition::enabled)
            .filter(definition -> definition.applicability().matches(request.catalogContext()))
            .sorted(Comparator.comparing(FeeDefinition::feeCode))
            .toList();

        for (int index = 0; index < definitions.size(); index++) {
            FeeDefinition definition = definitions.get(index);
            FormulaResult formula = evaluateFormula(definition, request, mathContext);
            CapFloorResult capFloor = applyCapFloor(definition, formula.rawAmount(), request, precision);
            BigDecimal rounded = precision.normalizeMoney(capFloor.adjustedAmount());
            assertCreditAllowed(definition, rounded, request);
            lines.add(FeeLine.from(definition, formula, capFloor, rounded, index + 1, request.catalog().contentHash()));
        }

        BigDecimal borrowerPaid = sumByPayer(lines, "BORROWER");
        BigDecimal lenderPaid = sumByPayer(lines, "LENDER");
        BigDecimal thirdParty = lines.stream()
            .filter(line -> !"BORROWER".equalsIgnoreCase(line.payer()) && !"LENDER".equalsIgnoreCase(line.payer()))
            .map(FeeLine::roundedAmount)
            .reduce(BigDecimal.ZERO.setScale(precision.moneyScale(), precision.roundingMode()), BigDecimal::add)
            .setScale(precision.moneyScale(), precision.roundingMode());
        List<String> warnings = definitions.isEmpty()
            ? List.of("no applicable published fee definitions resolved for request context")
            : List.of();
        String inputSnapshotHash = hashOf(
            request.tenantId(), request.quoteId(), request.scenarioId(), request.catalog().catalogVersionId(),
            request.loanAmount(), request.unitCount(), request.configuration(), request.passThroughFees(), request.manualFeeInputs()
        );
        String lineHash = hashOf(lines);
        FeeCalculationEvent event = new FeeCalculationEvent(
            EVENT_TOPIC,
            EVENT_TYPE,
            1,
            UUID.nameUUIDFromBytes((request.tenantId() + ":" + request.feeCalculationId() + ":" + lineHash)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            SOURCE_SERVICE,
            request.occurredAt(),
            request.tenantId() + ":" + request.quoteId(),
            request.tenantId(),
            request.feeCalculationId(),
            request.quoteId(),
            request.scenarioId(),
            request.catalog().catalogVersionId(),
            request.catalog().contentHash(),
            borrowerPaid,
            lenderPaid,
            thirdParty,
            lines.stream().map(FeeLine::lineHash).toList(),
            warnings,
            inputSnapshotHash,
            request.correlationId(),
            request.idempotencyKey()
        );
        FeeCalculationAudit audit = new FeeCalculationAudit(
            request.tenantId(),
            request.feeCalculationId(),
            "FEE_CALCULATION_COMPLETED",
            request.actorId(),
            request.correlationId(),
            request.catalog().contentHash(),
            inputSnapshotHash,
            lineHash
        );
        return new FeeCalculationResult(
            request.feeCalculationId(),
            request.catalog().catalogVersionId(),
            request.catalog().contentHash(),
            lines,
            borrowerPaid,
            lenderPaid,
            thirdParty,
            warnings,
            inputSnapshotHash,
            event,
            audit
        );
    }

    private FormulaResult evaluateFormula(FeeDefinition definition, FeeCalculationRequest request, MathContext mathContext) {
        return switch (definition.calculationMethod()) {
            case FIXED_AMOUNT -> new FormulaResult(
                numericConfig(definition, request, "amountConfigRef"),
                Map.of("amountConfigRef", definition.formulaParameters().get("amountConfigRef"))
            );
            case PERCENT_OF_LOAN_AMOUNT -> {
                BigDecimal percent = request.precisionPolicy().normalizeRate(numericConfig(definition, request, "percentConfigRef"));
                yield new FormulaResult(request.loanAmount().multiply(percent, mathContext).divide(ONE_HUNDRED, mathContext),
                    Map.of("percentConfigRef", definition.formulaParameters().get("percentConfigRef"), "loanAmount", request.loanAmount().toPlainString()));
            }
            case BPS_OF_LOAN_AMOUNT -> {
                BigDecimal bps = request.precisionPolicy().normalizeRate(numericConfig(definition, request, "bpsConfigRef"));
                yield new FormulaResult(request.loanAmount().multiply(bps, mathContext).divide(TEN_THOUSAND, mathContext),
                    Map.of("bpsConfigRef", definition.formulaParameters().get("bpsConfigRef"), "loanAmount", request.loanAmount().toPlainString()));
            }
            case PER_UNIT -> new FormulaResult(
                numericConfig(definition, request, "unitAmountConfigRef").multiply(new BigDecimal(request.unitCount()), mathContext),
                Map.of("unitAmountConfigRef", definition.formulaParameters().get("unitAmountConfigRef"), "unitCount", String.valueOf(request.unitCount()))
            );
            case PASS_THROUGH -> evaluatePassThrough(definition, request);
            case MANUAL_INPUT_ALLOWED -> evaluateManualInput(definition, request);
            case WAIVED -> new FormulaResult(BigDecimal.ZERO, Map.of("waiverPolicyRef", definition.formulaParameters().get("waiverPolicyRef")));
            case FORMULA_EXPRESSION_APPROVED -> throw new IllegalStateException(
                "approved formula expression calculation requires an executable expression engine and is blocked for this local slice"
            );
        };
    }

    private FormulaResult evaluatePassThrough(FeeDefinition definition, FeeCalculationRequest request) {
        PassThroughFeeSource supplied = request.passThroughFees().get(definition.feeCode());
        if (supplied == null) {
            throw new IllegalArgumentException("PASS_THROUGH requires trusted supplied amount for fee " + definition.feeCode());
        }
        String trustedSourceRef = definition.formulaParameters().get("trustedSourceRef");
        if (!Objects.equals(trustedSourceRef, supplied.sourceRef())) {
            throw new IllegalArgumentException("PASS_THROUGH source is not trusted for fee " + definition.feeCode());
        }
        assertBounds(definition, request, supplied.amount());
        return new FormulaResult(supplied.amount(), Map.of("trustedSourceRef", trustedSourceRef, "sourceRef", supplied.sourceRef()));
    }

    private FormulaResult evaluateManualInput(FeeDefinition definition, FeeCalculationRequest request) {
        ManualFeeInput input = request.manualFeeInputs().get(definition.feeCode());
        if (input == null) {
            throw new IllegalArgumentException("MANUAL_INPUT_ALLOWED requires supplied manual fee input for fee " + definition.feeCode());
        }
        String permissionRef = definition.formulaParameters().get("manualInputPermissionRef");
        if (!request.permissions().contains(permissionRef)) {
            throw new IllegalArgumentException("manual fee input requires permission " + permissionRef);
        }
        if (Boolean.parseBoolean(definition.formulaParameters().getOrDefault("auditReasonRequired", "false"))
            && (input.auditReason() == null || input.auditReason().isBlank())) {
            throw new IllegalArgumentException("manual fee input requires audit reason for fee " + definition.feeCode());
        }
        return new FormulaResult(input.amount(), Map.of("manualInputPermissionRef", permissionRef, "auditReason", input.auditReason()));
    }

    private CapFloorResult applyCapFloor(
        FeeDefinition definition,
        BigDecimal rawAmount,
        FeeCalculationRequest request,
        FeePrecisionPolicy precision
    ) {
        BigDecimal adjusted = rawAmount;
        String applied = "NONE";
        BigDecimal floor = optionalNumericConfig(definition, request, "floorAmountConfigRef");
        BigDecimal cap = optionalNumericConfig(definition, request, "capAmountConfigRef");
        if (floor != null && adjusted.compareTo(floor) < 0) {
            adjusted = floor;
            applied = "FLOOR";
        }
        if (cap != null && adjusted.compareTo(cap) > 0) {
            adjusted = cap;
            applied = "CAP";
        }
        return new CapFloorResult(
            precision.normalizeRate(rawAmount),
            precision.normalizeRate(adjusted),
            floor == null ? null : precision.normalizeMoney(floor),
            cap == null ? null : precision.normalizeMoney(cap),
            applied
        );
    }

    private void assertBounds(FeeDefinition definition, FeeCalculationRequest request, BigDecimal amount) {
        BigDecimal minimum = optionalNumericConfig(definition, request, "minimumAmountConfigRef");
        BigDecimal maximum = optionalNumericConfig(definition, request, "maximumAmountConfigRef");
        if (minimum != null && amount.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("PASS_THROUGH amount is below configured bounds for fee " + definition.feeCode());
        }
        if (maximum != null && amount.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("PASS_THROUGH amount is above configured bounds for fee " + definition.feeCode());
        }
    }

    private void assertCreditAllowed(FeeDefinition definition, BigDecimal amount, FeeCalculationRequest request) {
        if (amount.signum() >= 0) {
            return;
        }
        String creditAllowedRef = definition.formulaParameters().get("creditAllowedConfigRef");
        if (creditAllowedRef == null || !Boolean.TRUE.equals(request.configuration().booleanValues().get(creditAllowedRef))) {
            throw new IllegalArgumentException("negative fee requires explicit creditAllowed config for fee " + definition.feeCode());
        }
    }

    private static BigDecimal numericConfig(FeeDefinition definition, FeeCalculationRequest request, String parameterKey) {
        String configRef = definition.formulaParameters().get(parameterKey);
        if (configRef == null || configRef.isBlank()) {
            throw new IllegalArgumentException(definition.calculationMethod() + " requires formula parameter " + parameterKey);
        }
        BigDecimal value = request.configuration().numericValues().get(configRef);
        if (value == null) {
            throw new IllegalArgumentException("missing configured numeric value for " + configRef);
        }
        return value;
    }

    private static BigDecimal optionalNumericConfig(FeeDefinition definition, FeeCalculationRequest request, String parameterKey) {
        String configRef = definition.formulaParameters().get(parameterKey);
        if (configRef == null || configRef.isBlank()) {
            return null;
        }
        BigDecimal value = request.configuration().numericValues().get(configRef);
        if (value == null) {
            throw new IllegalArgumentException("missing configured numeric value for " + configRef);
        }
        return value;
    }

    private static BigDecimal sumByPayer(List<FeeLine> lines, String payer) {
        return lines.stream()
            .filter(line -> payer.equalsIgnoreCase(line.payer()))
            .map(FeeLine::roundedAmount)
            .reduce(new BigDecimal("0.00"), BigDecimal::add)
            .setScale(2);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String hashOf(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record FormulaResult(BigDecimal rawAmount, Map<String, String> formulaInputs) {
        private FormulaResult {
            Objects.requireNonNull(rawAmount, "rawAmount is required");
            formulaInputs = Map.copyOf(new TreeMap<>(formulaInputs == null ? Map.of() : formulaInputs));
        }
    }

    public record FeeCalculationRequest(
        UUID tenantId,
        UUID feeCalculationId,
        String quoteId,
        String scenarioId,
        String actorId,
        FeeCatalogVersion catalog,
        FeeCatalogRequestContext catalogContext,
        BigDecimal loanAmount,
        int unitCount,
        FeeCalculationConfiguration configuration,
        Map<String, PassThroughFeeSource> passThroughFees,
        Map<String, ManualFeeInput> manualFeeInputs,
        Set<String> permissions,
        FeePrecisionPolicy precisionPolicy,
        Instant occurredAt,
        String correlationId,
        String idempotencyKey
    ) {
        public FeeCalculationRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            feeCalculationId = feeCalculationId == null ? UUID.randomUUID() : feeCalculationId;
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            requireText(actorId, "actorId is required");
            Objects.requireNonNull(catalog, "catalog is required");
            Objects.requireNonNull(catalogContext, "catalogContext is required");
            Objects.requireNonNull(loanAmount, "loanAmount is required");
            if (loanAmount.signum() < 0) {
                throw new IllegalArgumentException("loanAmount cannot be negative");
            }
            if (unitCount < 0) {
                throw new IllegalArgumentException("unitCount cannot be negative");
            }
            configuration = configuration == null ? FeeCalculationConfiguration.empty() : configuration;
            passThroughFees = Map.copyOf(new TreeMap<>(passThroughFees == null ? Map.of() : passThroughFees));
            manualFeeInputs = Map.copyOf(new TreeMap<>(manualFeeInputs == null ? Map.of() : manualFeeInputs));
            permissions = Set.copyOf(permissions == null ? Set.of() : permissions);
            precisionPolicy = precisionPolicy == null ? FeePrecisionPolicy.defaultPolicy() : precisionPolicy;
            occurredAt = occurredAt == null ? Instant.now() : occurredAt;
            requireText(correlationId, "correlationId is required");
            requireText(idempotencyKey, "idempotencyKey is required");
        }

        private void validate() {
            if (!tenantId.equals(catalog.tenantId()) || !tenantId.equals(catalogContext.tenantId())) {
                throw new IllegalArgumentException("fee calculation tenant mismatch");
            }
            if (catalog.status() != CatalogStatus.PUBLISHED || !catalog.effectiveWindow().contains(catalogContext.quoteDate())) {
                throw new IllegalStateException("fee calculation requires a published fee catalog for quote date");
            }
            catalog.validateDefinitions();
        }
    }

    public record FeeCalculationConfiguration(Map<String, BigDecimal> numericValues, Map<String, Boolean> booleanValues) {
        public FeeCalculationConfiguration {
            numericValues = Map.copyOf(new TreeMap<>(numericValues == null ? Map.of() : numericValues));
            booleanValues = Map.copyOf(new TreeMap<>(booleanValues == null ? Map.of() : booleanValues));
        }

        public static FeeCalculationConfiguration empty() {
            return new FeeCalculationConfiguration(Map.of(), Map.of());
        }
    }

    public record PassThroughFeeSource(BigDecimal amount, String sourceRef) {
        public PassThroughFeeSource {
            Objects.requireNonNull(amount, "pass-through amount is required");
            requireText(sourceRef, "pass-through sourceRef is required");
        }
    }

    public record ManualFeeInput(BigDecimal amount, String auditReason) {
        public ManualFeeInput {
            Objects.requireNonNull(amount, "manual fee amount is required");
        }
    }

    public record CapFloorResult(BigDecimal rawAmount, BigDecimal adjustedAmount, BigDecimal floorAmount, BigDecimal capAmount, String applied) {
        public CapFloorResult {
            Objects.requireNonNull(rawAmount, "rawAmount is required");
            Objects.requireNonNull(adjustedAmount, "adjustedAmount is required");
            requireText(applied, "cap/floor applied result is required");
        }
    }

    public record FeeLine(
        UUID feeLineId,
        String feeCode,
        UUID feeDefinitionId,
        String category,
        String payer,
        String payee,
        CalculationMethod calculationMethod,
        Map<String, String> formulaInputs,
        BigDecimal rawAmount,
        BigDecimal roundedAmount,
        String roundingMode,
        CapFloorResult capFloorResult,
        String reasonCode,
        String catalogContentHash,
        int waterfallSequence,
        String lineHash
    ) {
        public FeeLine {
            Objects.requireNonNull(feeLineId, "feeLineId is required");
            requireText(feeCode, "feeCode is required");
            Objects.requireNonNull(feeDefinitionId, "feeDefinitionId is required");
            requireText(category, "category is required");
            requireText(payer, "payer is required");
            requireText(payee, "payee is required");
            Objects.requireNonNull(calculationMethod, "calculationMethod is required");
            formulaInputs = Map.copyOf(new TreeMap<>(formulaInputs == null ? Map.of() : formulaInputs));
            Objects.requireNonNull(rawAmount, "rawAmount is required");
            Objects.requireNonNull(roundedAmount, "roundedAmount is required");
            requireText(roundingMode, "roundingMode is required");
            Objects.requireNonNull(capFloorResult, "capFloorResult is required");
            requireText(reasonCode, "reasonCode is required");
            requireText(catalogContentHash, "catalogContentHash is required");
            if (waterfallSequence <= 0) {
                throw new IllegalArgumentException("waterfallSequence must be positive");
            }
            lineHash = lineHash == null || lineHash.isBlank()
                ? hashOf(feeCode, feeDefinitionId, calculationMethod, rawAmount, roundedAmount, capFloorResult, reasonCode, catalogContentHash, waterfallSequence)
                : lineHash;
        }

        private static FeeLine from(
            FeeDefinition definition,
            FormulaResult formula,
            CapFloorResult capFloor,
            BigDecimal rounded,
            int sequence,
            String catalogContentHash
        ) {
            return new FeeLine(
                UUID.nameUUIDFromBytes((definition.feeDefinitionId() + ":" + sequence + ":" + catalogContentHash)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                definition.feeCode(),
                definition.feeDefinitionId(),
                definition.category(),
                definition.payer(),
                definition.payee(),
                definition.calculationMethod(),
                formula.formulaInputs(),
                capFloor.rawAmount(),
                rounded,
                FeePrecisionPolicy.defaultPolicy().roundingMode().name(),
                capFloor,
                definition.reasonCode(),
                catalogContentHash,
                sequence,
                null
            );
        }
    }

    public record FeeCalculationResult(
        UUID feeCalculationId,
        UUID catalogVersionId,
        String catalogVersionHash,
        List<FeeLine> feeLines,
        BigDecimal totalBorrowerPaid,
        BigDecimal totalLenderPaid,
        BigDecimal totalThirdParty,
        List<String> warnings,
        String inputSnapshotHash,
        FeeCalculationEvent event,
        FeeCalculationAudit audit
    ) {
        public FeeCalculationResult {
            Objects.requireNonNull(feeCalculationId, "feeCalculationId is required");
            Objects.requireNonNull(catalogVersionId, "catalogVersionId is required");
            requireText(catalogVersionHash, "catalogVersionHash is required");
            feeLines = List.copyOf(feeLines == null ? List.of() : feeLines);
            Objects.requireNonNull(totalBorrowerPaid, "totalBorrowerPaid is required");
            Objects.requireNonNull(totalLenderPaid, "totalLenderPaid is required");
            Objects.requireNonNull(totalThirdParty, "totalThirdParty is required");
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            Objects.requireNonNull(event, "event is required");
            Objects.requireNonNull(audit, "audit is required");
        }
    }

    public record FeeCalculationEvent(
        String topic,
        String eventType,
        int eventVersion,
        UUID eventId,
        String sourceService,
        Instant occurredAt,
        String key,
        UUID tenantId,
        UUID feeCalculationId,
        String quoteId,
        String scenarioId,
        UUID catalogVersionId,
        String catalogVersionHash,
        BigDecimal totalBorrowerPaid,
        BigDecimal totalLenderPaid,
        BigDecimal totalThirdParty,
        List<String> feeLineHashes,
        List<String> warnings,
        String inputSnapshotHash,
        String correlationId,
        String idempotencyKey
    ) {
        public FeeCalculationEvent {
            requireText(topic, "topic is required");
            requireText(eventType, "eventType is required");
            Objects.requireNonNull(eventId, "eventId is required");
            requireText(sourceService, "sourceService is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
            requireText(key, "key is required");
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(feeCalculationId, "feeCalculationId is required");
            requireText(quoteId, "quoteId is required");
            requireText(scenarioId, "scenarioId is required");
            Objects.requireNonNull(catalogVersionId, "catalogVersionId is required");
            requireText(catalogVersionHash, "catalogVersionHash is required");
            feeLineHashes = List.copyOf(feeLineHashes == null ? List.of() : feeLineHashes);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            requireText(correlationId, "correlationId is required");
            requireText(idempotencyKey, "idempotencyKey is required");
        }
    }

    public record FeeCalculationAudit(
        UUID tenantId,
        UUID feeCalculationId,
        String action,
        String actorId,
        String correlationId,
        String catalogVersionHash,
        String inputSnapshotHash,
        String resultHash
    ) {
        public FeeCalculationAudit {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(feeCalculationId, "feeCalculationId is required");
            requireText(action, "action is required");
            requireText(actorId, "actorId is required");
            requireText(correlationId, "correlationId is required");
            requireText(catalogVersionHash, "catalogVersionHash is required");
            requireText(inputSnapshotHash, "inputSnapshotHash is required");
            requireText(resultHash, "resultHash is required");
        }
    }
}

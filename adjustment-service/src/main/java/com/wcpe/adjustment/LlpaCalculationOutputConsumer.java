package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Consumes configured calculation-runtime outputs as LLPA adjustment lines.
 *
 * <p>The consumer does not embed LLPA values, bands, tables, tenants, investors, or
 * policy thresholds. Output references, table versions, and output amounts must be
 * supplied by the completed calculation runtime and approved LLPA binding config.</p>
 */
public final class LlpaCalculationOutputConsumer {
    public static final String CALCULATION_MODE = "calculation-runtime-llpa";
    public static final String SOURCE = "calculation-runtime";

    public AdjustmentCalculationResult consume(ConsumerRequest request) {
        Objects.requireNonNull(request, "LLPA calculation consumer request is required");
        request.validate();

        List<LlpaOutputBinding> bindings = request.outputBindings().stream()
            .filter(LlpaOutputBinding::enabled)
            .sorted(Comparator.comparingInt(LlpaOutputBinding::priority).thenComparing(LlpaOutputBinding::outputRef))
            .toList();

        if (bindings.isEmpty()) {
            return blocked(request, "NO_APPROVED_LLPA_OUTPUT_BINDING", "no approved LLPA output bindings configured");
        }

        List<AdjustmentLine> lines = new ArrayList<>();
        BigDecimal pointsTotal = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
        boolean blocked = false;
        for (LlpaOutputBinding binding : bindings) {
            BigDecimal outputValue = request.snapshot().numericOutputs().get(binding.outputRef());
            if (outputValue == null) {
                lines.add(blockedLine(binding, request, "MISSING_LLPA_CALCULATION_OUTPUT"));
                blocked = true;
                continue;
            }
            BigDecimal amount = outputValue.setScale(6, RoundingMode.HALF_UP);
            if ("POINTS_DELTA".equals(binding.outputType())) {
                pointsTotal = pointsTotal.add(amount).setScale(6, RoundingMode.HALF_UP);
            }
            lines.add(appliedLine(binding, request, amount));
        }

        Map<String, Object> totalsByType = new LinkedHashMap<>();
        totalsByType.put("POINTS_DELTA", pointsTotal);
        totalsByType.put("LLPA_OUTPUT_REFS", bindings.stream().map(LlpaOutputBinding::outputRef).toList());
        totalsByType.put("CALCULATION_EVALUATION_ID", request.snapshot().evaluationId());
        totalsByType.put("CALCULATION_VERSION_IDS", request.snapshot().calculationVersionIds());
        totalsByType.put("INPUT_FIELD_REFS", request.snapshot().inputFieldRefs());
        totalsByType.put("FIELD_VERSION_REFS", request.snapshot().fieldVersionRefs());
        totalsByType.put("TABLE_VERSION_REFS", request.snapshot().tableVersionRefs());

        List<String> auditRefs = lines.stream()
            .map(AdjustmentLine::auditRef)
            .filter(ref -> ref != null && !ref.isBlank())
            .distinct()
            .toList();
        String resultHash = hashOf(request.tenantId(), request.tenantContext(), request.basePriceDecision(), request.quoteDate(),
            request.referenceDataVersion(), request.snapshot(), bindings, lines, totalsByType, blocked);

        return new AdjustmentCalculationResult(
            request.basePriceDecision().scenarioId(),
            request.basePriceDecision().basePriceId(),
            lines,
            pointsTotal.doubleValue(),
            request.referenceDataVersion(),
            CALCULATION_MODE,
            auditRefs,
            resultHash,
            totalsByType,
            blocked
        );
    }

    private static AdjustmentCalculationResult blocked(ConsumerRequest request, String code, String message) {
        AdjustmentLine line = new AdjustmentLine(code, 0.0, message, SOURCE, "BLOCKING_CONFLICT",
            null, code, false, message, auditRef(request, null, code), List.of(message));
        return new AdjustmentCalculationResult(
            request.basePriceDecision().scenarioId(),
            request.basePriceDecision().basePriceId(),
            List.of(line),
            0.0,
            request.referenceDataVersion(),
            CALCULATION_MODE,
            List.of(line.auditRef()),
            hashOf(request.tenantId(), request.tenantContext(), request.basePriceDecision(), code, request.snapshot()),
            Map.of("POINTS_DELTA", BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                "CALCULATION_EVALUATION_ID", request.snapshot().evaluationId()),
            true
        );
    }

    private static AdjustmentLine appliedLine(LlpaOutputBinding binding, ConsumerRequest request, BigDecimal amount) {
        return new AdjustmentLine(
            binding.factorKey(),
            amount.doubleValue(),
            binding.reasonCode(),
            SOURCE,
            binding.outputType(),
            binding.bindingId().toString(),
            binding.tableVersionRef(),
            true,
            binding.label(),
            auditRef(request, binding, null),
            traceWarnings(request.snapshot(), binding)
        );
    }

    private static AdjustmentLine blockedLine(LlpaOutputBinding binding, ConsumerRequest request, String code) {
        String message = code + ":" + binding.outputRef();
        return new AdjustmentLine(binding.factorKey(), 0.0, message, SOURCE, "BLOCKING_CONFLICT",
            binding.bindingId().toString(), binding.tableVersionRef(), false, binding.label(),
            auditRef(request, binding, code), List.of(message));
    }

    private static List<String> traceWarnings(CalculationOutputSnapshot snapshot, LlpaOutputBinding binding) {
        List<String> trace = new ArrayList<>();
        trace.add("outputRef:" + binding.outputRef());
        trace.add("tableVersionRef:" + binding.tableVersionRef());
        trace.add("calculationEvaluationId:" + snapshot.evaluationId());
        trace.add("calculationVersionIds:" + String.join(",", snapshot.calculationVersionIds()));
        trace.add("inputFieldRefs:" + String.join(",", snapshot.inputFieldRefs()));
        trace.add("fieldVersionRefs:" + String.join(",", snapshot.fieldVersionRefs().values()));
        return trace;
    }

    private static String auditRef(ConsumerRequest request, LlpaOutputBinding binding, String blockerCode) {
        CalculationOutputSnapshot snapshot = request.snapshot();
        String outputRef = binding == null ? "unbound" : binding.outputRef();
        String tableVersion = binding == null ? "unbound" : binding.tableVersionRef();
        return "tenant:" + request.tenantContext().tenantId()
            + ":calculation:" + snapshot.evaluationId()
            + ":versions:" + String.join(",", snapshot.calculationVersionIds())
            + ":table:" + tableVersion
            + ":output:" + outputRef
            + (blockerCode == null ? "" : ":blocker:" + blockerCode);
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
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record ConsumerRequest(
        UUID tenantId,
        BasePriceDecisionStub basePriceDecision,
        Instant quoteDate,
        String referenceDataVersion,
        CalculationOutputSnapshot snapshot,
        List<LlpaOutputBinding> outputBindings,
        CrossModuleTenantContext tenantContext
    ) {
        public ConsumerRequest(UUID tenantId, BasePriceDecisionStub basePriceDecision, Instant quoteDate,
            String referenceDataVersion, CalculationOutputSnapshot snapshot, List<LlpaOutputBinding> outputBindings) {
            this(tenantId, basePriceDecision, quoteDate, referenceDataVersion, snapshot, outputBindings,
                defaultTenantContext(tenantId, snapshot));
        }

        public ConsumerRequest {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(basePriceDecision, "basePriceDecision is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            requireText(referenceDataVersion, "referenceDataVersion is required");
            Objects.requireNonNull(snapshot, "calculation output snapshot is required");
            outputBindings = List.copyOf(outputBindings == null ? List.of() : outputBindings);
            Objects.requireNonNull(tenantContext, "tenant context is required");
            if (!tenantId.toString().equals(tenantContext.tenantId())) {
                throw new IllegalArgumentException("TENANT_CONTEXT_MISMATCH");
            }
        }

        void validate() {
            if (snapshot.evaluationId().equals("not-supplied")) {
                throw new IllegalArgumentException("LLPA consumption requires an explicit calculation evaluation snapshot");
            }
        }
    }

    public record CrossModuleTenantContext(String tenantId, String sourceModule, String correlationId) {
        public CrossModuleTenantContext {
            requireText(tenantId, "tenant context tenantId is required");
            sourceModule = sourceModule == null || sourceModule.isBlank() ? SOURCE : sourceModule;
            correlationId = correlationId == null || correlationId.isBlank() ? "unspecified" : correlationId;
        }
    }

    private static CrossModuleTenantContext defaultTenantContext(UUID tenantId, CalculationOutputSnapshot snapshot) {
        return new CrossModuleTenantContext(String.valueOf(tenantId), SOURCE,
            snapshot == null ? "unspecified" : snapshot.evaluationId());
    }

    public record CalculationOutputSnapshot(
        String evaluationId,
        List<String> calculationVersionIds,
        Map<String, BigDecimal> numericOutputs,
        Map<String, String> inputFieldValues,
        Map<String, String> fieldVersionRefs,
        Map<String, String> tableVersionRefs
    ) {
        public CalculationOutputSnapshot {
            requireText(evaluationId, "calculation evaluationId is required");
            calculationVersionIds = List.copyOf(calculationVersionIds == null ? List.of() : calculationVersionIds);
            numericOutputs = Map.copyOf(new TreeMap<>(numericOutputs == null ? Map.of() : numericOutputs));
            inputFieldValues = Map.copyOf(new TreeMap<>(inputFieldValues == null ? Map.of() : inputFieldValues));
            fieldVersionRefs = Map.copyOf(new TreeMap<>(fieldVersionRefs == null ? Map.of() : fieldVersionRefs));
            tableVersionRefs = Map.copyOf(new TreeMap<>(tableVersionRefs == null ? Map.of() : tableVersionRefs));
        }

        public List<String> inputFieldRefs() {
            return inputFieldValues.keySet().stream().sorted().toList();
        }
    }

    public record LlpaOutputBinding(
        UUID bindingId,
        String outputRef,
        String factorKey,
        String reasonCode,
        String outputType,
        String tableVersionRef,
        String label,
        int priority,
        boolean enabled
    ) {
        public LlpaOutputBinding {
            Objects.requireNonNull(bindingId, "bindingId is required");
            requireText(outputRef, "outputRef is required");
            requireText(factorKey, "factorKey is required");
            requireText(reasonCode, "reasonCode is required");
            outputType = outputType == null || outputType.isBlank() ? "POINTS_DELTA" : outputType;
            requireText(tableVersionRef, "tableVersionRef is required");
            label = label == null || label.isBlank() ? reasonCode : label;
            if (priority < 0) {
                throw new IllegalArgumentException("priority must be non-negative");
            }
        }
    }
}

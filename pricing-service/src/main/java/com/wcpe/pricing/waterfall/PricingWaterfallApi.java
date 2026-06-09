package com.wcpe.pricing.waterfall;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.wcpe.pricing.baserate.BaseRateSelectionApi.BaseRateSelectionResponse;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceLedgerEntry;
import com.wcpe.pricing.finalprice.FinalPriceApi.FinalPriceResponse;
import com.wcpe.pricing.missingprice.MissingPriceHandlingApi.MissingPriceHandlingResponse;
import com.wcpe.pricing.replay.PricingReplayApi.PricingReplayResponse;

public final class PricingWaterfallApi {
    public static final String WATERFALL_READ_PERMISSION = "pricing.waterfall.read";
    public static final String RESTRICTED_VALUE_PERMISSION = "pricing.waterfall.restricted.read";

    public PricingWaterfallView assemble(String tenantId, WaterfallHeaders headers, WaterfallEvidence evidence) {
        requireText(tenantId, "tenant_id is required");
        requirePermission(headers, WATERFALL_READ_PERMISSION);
        if (evidence == null) {
            throw new PricingWaterfallException("waterfall evidence is required");
        }

        List<WaterfallBlocker> blockers = new ArrayList<>();
        if (evidence.baseSelection() == null) {
            blockers.add(new WaterfallBlocker("BASE_SELECTION_MISSING", "Base grid selection evidence is required before final price can be shown.", "pricing-service.base-rate-selection"));
        }
        if (evidence.finalPrice() == null) {
            blockers.add(new WaterfallBlocker("FINAL_PRICE_MISSING", "Final price ledger evidence is required before final price can be shown.", "pricing-service.final-price"));
        }
        if (evidence.missingPrice() != null && evidence.missingPrice().error() != null) {
            blockers.add(new WaterfallBlocker(evidence.missingPrice().error().errorCode(), evidence.missingPrice().error().message(), evidence.missingPrice().auditRef()));
        }

        BaseRateSelectionResponse base = evidence.baseSelection();
        FinalPriceResponse finalPrice = evidence.finalPrice();
        boolean canSeeRestricted = headers.permissions().contains(RESTRICTED_VALUE_PERMISSION);
        List<WaterfallLedgerRow> ledgerRows = finalPrice == null ? List.of() : finalPrice.ledger().stream()
                .map(row -> ledgerRow(row, canSeeRestricted))
                .toList();
        String versionHash = finalPrice == null ? null : finalPrice.versionGraph().hash();
        String resultHash = finalPrice == null ? null : finalPrice.resultHash();
        String replayHash = evidence.replay() == null ? null : evidence.replay().replayHash();
        String evidenceHash = stableHash("pricing-waterfall", tenantId,
                base == null ? null : base.selectionId(),
                base == null ? null : base.gridVersionId(),
                finalPrice == null ? null : finalPrice.finalPriceId(),
                resultHash, versionHash, replayHash, blockers);

        return new PricingWaterfallView(
                tenantId,
                evidence.runId(),
                blockers.isEmpty() ? "READY" : "BLOCKED",
                base == null ? null : base.selectionId(),
                base == null ? null : String.valueOf(base.gridVersionId()),
                base == null ? null : restricted(base.selectedNoteRate(), canSeeRestricted, "pricing.waterfall.restricted.read permission is required for selected note rate"),
                base == null ? null : restricted(base.selectedBasePrice(), canSeeRestricted, "pricing.waterfall.restricted.read permission is required for base price"),
                finalPrice == null ? null : finalPrice.finalPriceId(),
                finalPrice == null ? null : restricted(finalPrice.roundedFinalPrice(), canSeeRestricted, "pricing.waterfall.restricted.read permission is required for rounded final price"),
                ledgerRows,
                finalPrice == null ? List.of() : finalPrice.adjustments().stream().map(adjustment -> adjustment.ruleId() + ":" + adjustment.versionRef() + ":" + adjustment.reasonCode()).toList(),
                finalPrice == null ? List.of() : finalPrice.capFloorResults().stream().map(result -> result.versionRef() + ":" + result.reasonCode() + ":" + result.action()).toList(),
                finalPrice == null ? List.of() : finalPrice.versionGraph().refs(),
                List.copyOf(blockers),
                finalPrice == null ? null : "audit:final-price:" + finalPrice.finalPriceId(),
                evidence.missingPrice() == null ? null : evidence.missingPrice().auditRef(),
                evidence.replay() == null ? null : evidence.replay().evidenceArtifactRef(),
                resultHash,
                versionHash,
                replayHash,
                evidenceHash,
                canSeeRestricted,
                headers.correlationId());
    }

    private static WaterfallLedgerRow ledgerRow(FinalPriceLedgerEntry row, boolean canSeeRestricted) {
        return new WaterfallLedgerRow(row.ordinal(), row.step(), restricted(row.inputValue(), canSeeRestricted,
                "pricing.waterfall.restricted.read permission is required for ledger input values"), row.operation(),
                restricted(row.outputValue(), canSeeRestricted,
                        "pricing.waterfall.restricted.read permission is required for ledger output values"),
                row.configRef(), row.reasonCode(), row.roundingMode() == null ? null : row.roundingMode().name());
    }

    private static RedactedValue restricted(BigDecimal value, boolean canSee, String reason) {
        if (value == null) {
            return new RedactedValue(null, false, null);
        }
        return canSee ? new RedactedValue(value.toPlainString(), false, null) : new RedactedValue(null, true, reason);
    }

    private static void requirePermission(WaterfallHeaders headers, String permission) {
        if (headers == null) {
            throw new PricingWaterfallException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!headers.permissions().contains(permission)) {
            throw new PricingWaterfallException(permission + " permission is required");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PricingWaterfallException(message);
        }
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

    public record WaterfallHeaders(Set<String> permissions, String actorId, String correlationId) {
        public WaterfallHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record WaterfallEvidence(String runId, BaseRateSelectionResponse baseSelection, FinalPriceResponse finalPrice,
            MissingPriceHandlingResponse missingPrice, PricingReplayResponse replay) {
        public WaterfallEvidence {
            requireText(runId, "run_id is required");
        }
    }

    public record PricingWaterfallView(String tenantId, String runId, String status, UUID baseSelectionId,
            String gridVersionRef, RedactedValue selectedNoteRate, RedactedValue basePrice, UUID finalPriceId,
            RedactedValue roundedFinalPrice, List<WaterfallLedgerRow> ledger, List<String> adjustmentRefs,
            List<String> marginAndBoundaryRefs, List<String> versionRefs, List<WaterfallBlocker> blockers,
            String finalPriceAuditRef, String missingPriceAuditRef, String replayEvidenceRef, String resultHash,
            String versionGraphHash, String replayHash, String evidenceHash, boolean restrictedValuesVisible,
            String correlationId) {
        public PricingWaterfallView {
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
            adjustmentRefs = adjustmentRefs == null ? List.of() : List.copyOf(adjustmentRefs);
            marginAndBoundaryRefs = marginAndBoundaryRefs == null ? List.of() : List.copyOf(marginAndBoundaryRefs);
            versionRefs = versionRefs == null ? List.of() : List.copyOf(versionRefs);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    public record WaterfallLedgerRow(int ordinal, String step, RedactedValue inputValue, String operation,
            RedactedValue outputValue, String configRef, String reasonCode, String roundingMode) {}

    public record RedactedValue(String value, boolean redacted, String reason) {}

    public record WaterfallBlocker(String code, String message, String sourceRef) {}

    public static final class PricingWaterfallException extends RuntimeException {
        public PricingWaterfallException(String message) {
            super(Objects.requireNonNull(message));
        }
    }
}

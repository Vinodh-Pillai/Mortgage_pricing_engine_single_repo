package com.wcpe.pricing.mi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MiPricingApi {
    public static final String MI_PRICE_PERMISSION = "pricing.mi.price";
    public static final String MI_RATE_CARD_READ_PERMISSION = "pricing.mi.rate-card.read";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    public MiPriceResponse price(String tenantId, MiPricingHeaders headers, MiPriceRequest request) {
        requireTenant(tenantId);
        requirePermission(headers, MI_PRICE_PERMISSION);
        validateRequest(request);

        List<MiEligibilityBlocker> blockers = new ArrayList<>();
        if (!"CONVENTIONAL".equalsIgnoreCase(request.loanType())) {
            blockers.add(new MiEligibilityBlocker("MI_CONVENTIONAL_ONLY",
                    "MI pricing is supported only for conventional loans", request.loanType(), "CONVENTIONAL"));
        }

        List<MiPriceOption> options = new ArrayList<>();
        if (blockers.isEmpty()) {
            for (MiProgram program : request.candidatePrograms()) {
                for (MiRateCard card : request.activeRateCards()) {
                    if (!carrierMatches(program.carrier(), card.carrier())) {
                        continue;
                    }
                    List<MiRateRow> rows = card.rows().stream()
                            .filter(row -> row.matches(request, program))
                            .toList();
                    for (MiRateRow row : rows) {
                        options.add(toOption(card, row, request, program));
                    }
                }
            }
        }

        if (blockers.isEmpty() && options.isEmpty()) {
            blockers.add(new MiEligibilityBlocker("MI_RATE_ROW_NOT_FOUND",
                    "No active MI rate-card row matched the requested LTV, FICO, loan amount, coverage, carrier, and premium type",
                    request.matchKey(), "active rate card row"));
        }

        options.sort(Comparator.comparing(MiPriceOption::rankingCost)
                .thenComparing(MiPriceOption::carrier)
                .thenComparing(option -> option.premiumType().name()));
        MiPriceOption selected = options.isEmpty() ? null : options.get(0).withRank(1);
        List<MiPriceOption> ranked = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            ranked.add(options.get(index).withRank(index + 1));
        }

        String replayHash = stableHash("mi-price", tenantId, request.loanType(), request.ltv(), request.fico(),
                request.loanAmount(), request.coveragePercent(), request.candidatePrograms(), request.activeRateCards(),
                ranked, blockers);
        return new MiPriceResponse(tenantId, selected, ranked, List.copyOf(blockers), replayHash,
                headers.correlationId());
    }

    public MiRateCardMetadata rateCardMetadata(String tenantId, MiPricingHeaders headers, MiRateCard card) {
        requireTenant(tenantId);
        requirePermission(headers, MI_RATE_CARD_READ_PERMISSION);
        Objects.requireNonNull(card, "rate card is required");
        return new MiRateCardMetadata(card.cardId(), card.carrier(), card.versionRef(), card.rows().size(),
                stableHash("mi-rate-card-metadata", tenantId, card.cardId(), card.carrier(), card.versionRef(), card.rows().size()),
                headers.correlationId());
    }

    public MiImportValidationResult validateImportedRateRows(String sourceType, List<Map<String, String>> rows) {
        String normalizedSource = normalizeSourceType(sourceType);
        List<String> unsupported = new ArrayList<>();
        List<Map<String, String>> canonicalRows = new ArrayList<>();
        for (Map<String, String> row : rows == null ? List.<Map<String, String>>of() : rows) {
            Map<String, String> canonical = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String canonicalField = canonicalField(entry.getKey());
                if (canonicalField == null) {
                    unsupported.add(entry.getKey());
                } else {
                    canonical.put(canonicalField, entry.getValue());
                }
            }
            canonicalRows.add(Map.copyOf(canonical));
        }
        return new MiImportValidationResult(normalizedSource, canonicalRows, unsupported.stream().distinct().sorted().toList(),
                stableHash("mi-import-validation", normalizedSource, canonicalRows, unsupported));
    }

    public static List<String> supportedCarrierNames() {
        return List.of("MGIC", "RADIAN", "ESSENT", "NATIONAL_MI", "GENWORTH");
    }

    private static MiPriceOption toOption(MiRateCard card, MiRateRow row, MiPriceRequest request, MiProgram program) {
        BigDecimal monthlyPremium = money(BigDecimal.ZERO);
        BigDecimal upfrontPremium = money(BigDecimal.ZERO);
        BigDecimal priceAdjustment = row.lenderPaidPriceAdjustment() == null
                ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP)
                : row.lenderPaidPriceAdjustment().setScale(8, RoundingMode.HALF_UP);
        if (program.premiumType() == MiPremiumType.BPMI_MONTHLY || program.premiumType() == MiPremiumType.BPMI_SPLIT) {
            monthlyPremium = money(request.loanAmount().multiply(nullToZero(row.annualRatePercent()))
                    .divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP)
                    .divide(TWELVE, 10, RoundingMode.HALF_UP));
        }
        if (program.premiumType() == MiPremiumType.BPMI_SINGLE || program.premiumType() == MiPremiumType.BPMI_SPLIT) {
            upfrontPremium = money(request.loanAmount().multiply(nullToZero(row.upfrontRatePercent()))
                    .divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP));
        }
        BigDecimal rankingCost = monthlyPremium.add(upfrontPremium).add(priceAdjustment.abs().multiply(new BigDecimal("100")))
                .setScale(4, RoundingMode.HALF_UP);
        String sourceRef = firstNonBlank(row.sourceRef(), card.cardId() + ":" + row.rowId());
        String optionHash = stableHash("mi-option", card.cardId(), card.carrier(), card.versionRef(), row, request.matchKey());
        return new MiPriceOption(card.carrier(), program.premiumType(), request.coveragePercent(), row.annualRatePercent(),
                row.upfrontRatePercent(), priceAdjustment, monthlyPremium, upfrontPremium, rankingCost, sourceRef,
                card.versionRef(), optionHash, 0, List.of("ltv=" + request.ltv(), "fico=" + request.fico(),
                "loanAmount=" + request.loanAmount(), "coveragePercent=" + request.coveragePercent()));
    }

    private static boolean carrierMatches(String requestedCarrier, String cardCarrier) {
        return normalizeCarrier(requestedCarrier).equals(normalizeCarrier(cardCarrier));
    }

    private static String normalizeCarrier(String carrier) {
        String normalized = carrier == null ? "" : carrier.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if ("NATIONALMI".equals(normalized) || "NATIONAL_MI".equals(normalized)) {
            return "NATIONAL_MI";
        }
        return normalized;
    }

    private static String normalizeSourceType(String sourceType) {
        String normalized = sourceType == null ? "UNKNOWN" : sourceType.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "OPTIMAL_BLUE", "POLLY", "LOANPASS" -> normalized;
            default -> "UNSUPPORTED:" + normalized;
        };
    }

    private static String canonicalField(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        String normalized = fieldName.trim().toLowerCase(Locale.ROOT).replace("%", "percent").replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "carrier", "mi_company", "provider" -> "carrier";
            case "premium_type", "payment_plan", "mi_plan" -> "premium_type";
            case "coverage", "coverage_percent", "coverage_pct" -> "coverage_percent";
            case "min_ltv", "ltv_min" -> "min_ltv";
            case "max_ltv", "ltv_max" -> "max_ltv";
            case "min_fico", "fico_min" -> "min_fico";
            case "max_fico", "fico_max" -> "max_fico";
            case "min_loan_amount", "loan_amount_min" -> "min_loan_amount";
            case "max_loan_amount", "loan_amount_max" -> "max_loan_amount";
            case "annual_rate_percent", "monthly_rate", "rate_pct" -> "annual_rate_percent";
            case "upfront_rate_percent", "single_premium_pct" -> "upfront_rate_percent";
            case "lender_paid_price_adjustment", "lpmi_adjustment" -> "lender_paid_price_adjustment";
            case "source_ref", "row_id", "source_row_id" -> "source_ref";
            default -> null;
        };
    }

    private static void validateRequest(MiPriceRequest request) {
        if (request == null) {
            throw new MiPricingException("mi pricing request is required");
        }
        requireText(request.loanType(), "loan_type is required");
        requirePositive(request.ltv(), "ltv must be positive");
        if (request.fico() == null || request.fico() < 300 || request.fico() > 850) {
            throw new MiPricingException("fico must be between 300 and 850");
        }
        requirePositive(request.loanAmount(), "loan_amount must be positive");
        if (request.coveragePercent() == null || request.coveragePercent() <= 0) {
            throw new MiPricingException("coverage_percent must be positive");
        }
        if (request.candidatePrograms().isEmpty()) {
            throw new MiPricingException("candidate MI programs are required");
        }
        if (request.activeRateCards().isEmpty()) {
            throw new MiPricingException("active MI rate cards are required");
        }
    }

    private static void requirePermission(MiPricingHeaders headers, String permission) {
        if (headers == null) {
            throw new MiPricingException("headers are required");
        }
        requireText(headers.actorId(), "actor_id is required");
        requireText(headers.correlationId(), "correlation_id is required");
        if (!headers.permissions().contains(permission)) {
            throw new MiPricingException(permission + " permission is required");
        }
    }

    private static void requireTenant(String tenantId) {
        requireText(tenantId, "tenant_id is required");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new MiPricingException(message);
        }
    }

    private static void requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MiPricingException(message);
        }
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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

    public record MiPricingHeaders(Set<String> permissions, String actorId, String correlationId) {
        public MiPricingHeaders {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    public record MiPriceRequest(String loanType, BigDecimal ltv, Integer fico, BigDecimal loanAmount,
            Integer coveragePercent, List<MiProgram> candidatePrograms, List<MiRateCard> activeRateCards) {
        public MiPriceRequest {
            ltv = ltv == null ? null : ltv.setScale(2, RoundingMode.HALF_UP);
            loanAmount = loanAmount == null ? null : loanAmount.setScale(2, RoundingMode.HALF_UP);
            candidatePrograms = candidatePrograms == null ? List.of() : List.copyOf(candidatePrograms);
            activeRateCards = activeRateCards == null ? List.of() : List.copyOf(activeRateCards);
        }

        String matchKey() {
            return loanType + ":" + ltv + ":" + fico + ":" + loanAmount + ":" + coveragePercent;
        }
    }

    public record MiProgram(String carrier, MiPremiumType premiumType) {
        public MiProgram {
            requireText(carrier, "carrier is required");
            premiumType = premiumType == null ? MiPremiumType.BPMI_MONTHLY : premiumType;
        }
    }

    public enum MiPremiumType {
        BPMI_MONTHLY,
        BPMI_SINGLE,
        BPMI_SPLIT,
        LPMI
    }

    public record MiRateCard(String cardId, String carrier, String versionRef, List<MiRateRow> rows) {
        public MiRateCard {
            requireText(cardId, "card_id is required");
            requireText(carrier, "carrier is required");
            requireText(versionRef, "version_ref is required");
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record MiRateRow(String rowId, BigDecimal minLtv, BigDecimal maxLtv, Integer minFico, Integer maxFico,
            BigDecimal minLoanAmount, BigDecimal maxLoanAmount, Integer coveragePercent, MiPremiumType premiumType,
            BigDecimal annualRatePercent, BigDecimal upfrontRatePercent, BigDecimal lenderPaidPriceAdjustment,
            String sourceRef) {
        public MiRateRow {
            requireText(rowId, "row_id is required");
            minLtv = minLtv == null ? BigDecimal.ZERO : minLtv.setScale(2, RoundingMode.HALF_UP);
            maxLtv = maxLtv == null ? new BigDecimal("999.99") : maxLtv.setScale(2, RoundingMode.HALF_UP);
            minLoanAmount = minLoanAmount == null ? BigDecimal.ZERO : minLoanAmount.setScale(2, RoundingMode.HALF_UP);
            maxLoanAmount = maxLoanAmount == null ? new BigDecimal("999999999.99") : maxLoanAmount.setScale(2, RoundingMode.HALF_UP);
            premiumType = premiumType == null ? MiPremiumType.BPMI_MONTHLY : premiumType;
        }

        boolean matches(MiPriceRequest request, MiProgram program) {
            return premiumType == program.premiumType()
                    && Objects.equals(coveragePercent, request.coveragePercent())
                    && between(request.ltv(), minLtv, maxLtv)
                    && between(BigDecimal.valueOf(request.fico()), BigDecimal.valueOf(minFico == null ? 0 : minFico),
                            BigDecimal.valueOf(maxFico == null ? 999 : maxFico))
                    && between(request.loanAmount(), minLoanAmount, maxLoanAmount);
        }

        private static boolean between(BigDecimal value, BigDecimal min, BigDecimal max) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        }
    }

    public record MiPriceOption(String carrier, MiPremiumType premiumType, Integer coveragePercent,
            BigDecimal annualRatePercent, BigDecimal upfrontRatePercent, BigDecimal priceAdjustment,
            BigDecimal monthlyPremium, BigDecimal upfrontPremium, BigDecimal rankingCost, String sourceRef,
            String versionRef, String replayHash, int rank, List<String> conditionEvidence) {
        public MiPriceOption {
            priceAdjustment = priceAdjustment == null ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : priceAdjustment.setScale(8, RoundingMode.HALF_UP);
            monthlyPremium = monthlyPremium == null ? money(BigDecimal.ZERO) : money(monthlyPremium);
            upfrontPremium = upfrontPremium == null ? money(BigDecimal.ZERO) : money(upfrontPremium);
            rankingCost = rankingCost == null ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP) : rankingCost.setScale(4, RoundingMode.HALF_UP);
            conditionEvidence = conditionEvidence == null ? List.of() : List.copyOf(conditionEvidence);
        }

        MiPriceOption withRank(int newRank) {
            return new MiPriceOption(carrier, premiumType, coveragePercent, annualRatePercent, upfrontRatePercent,
                    priceAdjustment, monthlyPremium, upfrontPremium, rankingCost, sourceRef, versionRef, replayHash,
                    newRank, conditionEvidence);
        }
    }

    public record MiEligibilityBlocker(String code, String message, String actualValue, String requiredValue) {}

    public record MiPriceResponse(String tenantId, MiPriceOption selectedOption, List<MiPriceOption> rankedOptions,
            List<MiEligibilityBlocker> blockers, String replayHash, String correlationId) {
        public MiPriceResponse {
            rankedOptions = rankedOptions == null ? List.of() : List.copyOf(rankedOptions);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }
    }

    public record MiRateCardMetadata(String cardId, String carrier, String versionRef, int rowCount,
            String metadataHash, String correlationId) {}

    public record MiImportValidationResult(String sourceType, List<Map<String, String>> canonicalRows,
            List<String> unsupportedFields, String validationHash) {
        public MiImportValidationResult {
            canonicalRows = canonicalRows == null ? List.of() : List.copyOf(canonicalRows);
            unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        }
    }

    public static class MiPricingException extends RuntimeException {
        public MiPricingException(String message) {
            super(message);
        }
    }
}

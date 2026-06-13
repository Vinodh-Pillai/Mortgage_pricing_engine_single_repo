package com.wcpe.adjustment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Tenant-scoped LLPA rule-book model. It stores only configurable symbolic values;
 * no LLPA rates, bands, thresholds, investors, or tenant policy are hard-coded.
 */
public record AdjustmentRuleBook(
    UUID tenantId,
    UUID ruleBookId,
    String businessKey,
    String version,
    RuleBookStatus status,
    RuleBookSelector selector,
    EffectiveWindow effectiveWindow,
    PricingPrecisionPolicy precisionPolicy,
    List<AdjustmentRule> rules,
    String createdBy,
    String approvedBy,
    Instant publishedAt,
    String contentHash,
    BigDecimal maxTotalPointsDelta,
    BigDecimal minTotalPointsDelta
) {
    public static final Set<String> SUPPORTED_CONDITION_DIMENSIONS = Set.of(
        "productFamily", "productType", "productCode", "investor", "channel", "loanPurpose",
        "refinancePurpose", "occupancy", "propertyType", "units", "state", "county",
        "countyFips", "zipCode", "msa", "ficoBandKey", "representativeFico", "ltvBandKey",
        "ltv", "cltvBandKey", "cltv", "hcltvBandKey", "hcltv", "dtiBandKey", "dti",
        "loanAmountBand", "loanAmount", "lienPosition", "amortizationType", "loanTermMonths",
        "fixedPeriodMonths", "interestOnlyFlag", "cashOutFlag", "subordinateFinancingFlag",
        "firstTimeHomebuyerFlag", "selfEmployedFlag", "nonOccupantCoBorrowerFlag", "giftFundsFlag",
        "escrowWaiverFlag", "manufacturedHomeFlag", "condoProjectType", "highBalanceFlag",
        "conformingBalanceFlag", "purchasePriceBand", "appraisedValueBand", "compensationPlanCode",
        "feeCategory", "lockPeriodDays", "commitmentType", "ausRecommendation", "documentationType",
        "mortgageInsuranceType", "impoundsFlag", "borrowerCount", "citizenshipStatus", "propertyUsage",
        "quoteDate", "referenceDataVersion"
    );

    public AdjustmentRuleBook(
        UUID tenantId,
        UUID ruleBookId,
        String businessKey,
        String version,
        RuleBookStatus status,
        RuleBookSelector selector,
        EffectiveWindow effectiveWindow,
        PricingPrecisionPolicy precisionPolicy,
        List<AdjustmentRule> rules,
        String createdBy,
        String approvedBy,
        Instant publishedAt,
        String contentHash
    ) {
        this(tenantId, ruleBookId, businessKey, version, status, selector, effectiveWindow, precisionPolicy,
            rules, createdBy, approvedBy, publishedAt, contentHash, null, null);
    }

    public AdjustmentRuleBook {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(ruleBookId, "ruleBookId is required");
        requireText(businessKey, "businessKey is required");
        requireText(version, "version is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(selector, "selector is required");
        Objects.requireNonNull(effectiveWindow, "effectiveWindow is required");
        precisionPolicy = precisionPolicy == null ? PricingPrecisionPolicy.defaultPolicy() : precisionPolicy;
        rules = List.copyOf(rules == null ? List.of() : rules);
        requireText(createdBy, "createdBy is required");
        if (status == RuleBookStatus.PUBLISHED) {
            requireText(approvedBy, "published rule book requires approvedBy");
            Objects.requireNonNull(publishedAt, "published rule book requires publishedAt");
        }
        contentHash = contentHash == null || contentHash.isBlank()
            ? hashOf(tenantId, ruleBookId, businessKey, version, status, selector, effectiveWindow, rules, maxTotalPointsDelta, minTotalPointsDelta)
            : contentHash;
    }

    public AdjustmentRuleBook publish(String approver, Instant when, List<AdjustmentRuleBook> existingRuleBooks) {
        if (status != RuleBookStatus.DRAFT && status != RuleBookStatus.VALIDATED) {
            throw new IllegalStateException("only draft or validated rule books can be published");
        }
        requireText(approver, "approver is required");
        if (approver.equals(createdBy)) {
            throw new IllegalArgumentException("submitter cannot publish the same rule book");
        }
        validateRules();
        AdjustmentRuleBook published = new AdjustmentRuleBook(
            tenantId,
            ruleBookId,
            businessKey,
            version,
            RuleBookStatus.PUBLISHED,
            selector,
            effectiveWindow,
            precisionPolicy,
            rules,
            createdBy,
            approver,
            Objects.requireNonNull(when, "publishedAt is required"),
            null,
            maxTotalPointsDelta,
            minTotalPointsDelta
        );
        RuleBookResolutionService.assertNoPublishedOverlap(published, existingRuleBooks == null ? List.of() : existingRuleBooks);
        return published;
    }

    public AdjustmentRuleBook replaceDraftRules(List<AdjustmentRule> replacementRules) {
        if (status != RuleBookStatus.DRAFT) {
            throw new IllegalStateException("published, suspended, expired, and approval-pending rule books are immutable");
        }
        return new AdjustmentRuleBook(
            tenantId,
            ruleBookId,
            businessKey,
            version,
            status,
            selector,
            effectiveWindow,
            precisionPolicy,
            replacementRules,
            createdBy,
            approvedBy,
            publishedAt,
            null,
            maxTotalPointsDelta,
            minTotalPointsDelta
        );
    }

    public List<String> validateRules() {
        List<String> errors = new ArrayList<>();
        if (rules.isEmpty()) {
            errors.add("at least one rule is required");
        }
        Set<String> ruleIds = new LinkedHashSet<>();
        for (AdjustmentRule rule : rules) {
            errors.addAll(rule.validate(precisionPolicy));
            if (!ruleIds.add(rule.ruleId().toString())) {
                errors.add("duplicate rule id: " + rule.ruleId());
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        return errors;
    }

    static void requireText(String value, String message) {
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

    public enum RuleBookStatus {
        DRAFT,
        VALIDATED,
        PENDING_APPROVAL,
        PUBLISHED,
        SUSPENDED,
        EXPIRED
    }

    public enum ConditionOperator {
        EQ,
        IN,
        RANGE_CLOSED,
        RANGE_OPEN_END,
        EXISTS,
        NOT_EXISTS,
        BOOLEAN_IS
    }

    public enum AdjustmentOutputType {
        POINTS_DELTA,
        BPS_DELTA,
        MONEY_FEE,
        PERCENT_OF_LOAN_AMOUNT,
        LABEL_ONLY,
        BLOCKING_CONFLICT
    }

    public record RuleBookSelector(String productFamily, String investor, String channel) {
        public RuleBookSelector {
            requireText(productFamily, "productFamily is required");
            requireText(investor, "investor selector is required");
            requireText(channel, "channel selector is required");
        }

        boolean matches(RuleBookSelector other) {
            return productFamily.equals(other.productFamily)
                && investor.equals(other.investor)
                && channel.equals(other.channel);
        }
    }

    public record EffectiveWindow(Instant start, Instant end) {
        public EffectiveWindow {
            Objects.requireNonNull(start, "effective start is required");
            if (end != null && !end.isAfter(start)) {
                throw new IllegalArgumentException("effective end must be after start");
            }
        }

        boolean contains(Instant instant) {
            Objects.requireNonNull(instant, "effective instant is required");
            return !instant.isBefore(start) && (end == null || instant.isBefore(end));
        }

        boolean overlaps(EffectiveWindow other) {
            Instant thisEnd = end == null ? Instant.MAX : end;
            Instant otherEnd = other.end == null ? Instant.MAX : other.end;
            return start.isBefore(otherEnd) && other.start.isBefore(thisEnd);
        }
    }

    public record PricingPrecisionPolicy(int pointsScale, int bpsScale, int moneyScale, RoundingMode roundingMode) {
        public PricingPrecisionPolicy {
            if (pointsScale < 0 || bpsScale < 0 || moneyScale < 0) {
                throw new IllegalArgumentException("precision scales must be non-negative");
            }
            roundingMode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
        }

        static PricingPrecisionPolicy defaultPolicy() {
            return new PricingPrecisionPolicy(6, 4, 2, RoundingMode.HALF_UP);
        }

        BigDecimal normalize(AdjustmentOutputType type, BigDecimal amount) {
            Objects.requireNonNull(amount, "output amount is required");
            int scale = switch (type) {
                case POINTS_DELTA, PERCENT_OF_LOAN_AMOUNT -> pointsScale;
                case BPS_DELTA -> bpsScale;
                case MONEY_FEE -> moneyScale;
                case LABEL_ONLY, BLOCKING_CONFLICT -> 0;
            };
            return amount.setScale(scale, roundingMode);
        }
    }

    public record AdjustmentCondition(String dimension, ConditionOperator operator, List<String> configuredValues) {
        public AdjustmentCondition {
            requireText(dimension, "condition dimension is required");
            Objects.requireNonNull(operator, "condition operator is required");
            configuredValues = List.copyOf(configuredValues == null ? List.of() : configuredValues);
        }

        List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (!SUPPORTED_CONDITION_DIMENSIONS.contains(dimension)) {
                errors.add("unknown condition dimension: " + dimension);
            }
            if ((operator == ConditionOperator.EQ || operator == ConditionOperator.IN
                || operator == ConditionOperator.RANGE_CLOSED || operator == ConditionOperator.RANGE_OPEN_END
                || operator == ConditionOperator.BOOLEAN_IS) && configuredValues.isEmpty()) {
                errors.add("condition " + dimension + " requires configured values");
            }
            return errors;
        }
    }

    public record AdjustmentOutput(AdjustmentOutputType type, BigDecimal configuredAmount, String configuredLabel) {
        public AdjustmentOutput {
            Objects.requireNonNull(type, "output type is required");
            if (type == AdjustmentOutputType.LABEL_ONLY || type == AdjustmentOutputType.BLOCKING_CONFLICT) {
                requireText(configuredLabel, "label or conflict text is required");
            } else {
                Objects.requireNonNull(configuredAmount, "configured amount is required");
            }
        }

        AdjustmentOutput normalized(PricingPrecisionPolicy policy) {
            if (configuredAmount == null) {
                return this;
            }
            return new AdjustmentOutput(type, policy.normalize(type, configuredAmount), configuredLabel);
        }
    }

    public record AdjustmentRule(
        UUID ruleId,
        int priority,
        List<AdjustmentCondition> conditions,
        AdjustmentOutput output,
        String reasonCode,
        String exclusivityGroup,
        boolean enabled,
        String sourceRef,
        BigDecimal minOutput,
        BigDecimal maxOutput
    ) {
        public AdjustmentRule(
            UUID ruleId,
            int priority,
            List<AdjustmentCondition> conditions,
            AdjustmentOutput output,
            String reasonCode,
            String exclusivityGroup,
            boolean enabled,
            String sourceRef
        ) {
            this(ruleId, priority, conditions, output, reasonCode, exclusivityGroup, enabled, sourceRef, null, null);
        }

        public AdjustmentRule {
            Objects.requireNonNull(ruleId, "ruleId is required");
            conditions = List.copyOf(conditions == null ? List.of() : conditions);
            Objects.requireNonNull(output, "output is required");
            requireText(reasonCode, "reasonCode is required");
            requireText(sourceRef, "sourceRef is required");
            if (minOutput != null && maxOutput != null && minOutput.compareTo(maxOutput) > 0) {
                throw new IllegalArgumentException("rule minOutput cannot exceed maxOutput");
            }
        }

        List<String> validate(PricingPrecisionPolicy policy) {
            List<String> errors = new ArrayList<>();
            if (priority < 0) {
                errors.add("priority must be non-negative");
            }
            if (conditions.isEmpty()) {
                errors.add("rule " + ruleId + " requires at least one condition");
            }
            for (AdjustmentCondition condition : conditions) {
                errors.addAll(condition.validate());
            }
            output.normalized(policy);
            return errors;
        }
    }

    public static final class RuleBookResolutionService {
        public AdjustmentRuleBook resolvePublished(
            UUID tenantId,
            RuleBookSelector selector,
            Instant quoteDate,
            List<AdjustmentRuleBook> ruleBooks
        ) {
            Objects.requireNonNull(tenantId, "tenantId is required");
            Objects.requireNonNull(selector, "selector is required");
            Objects.requireNonNull(quoteDate, "quoteDate is required");
            return ruleBooks.stream()
                .filter(book -> book.tenantId.equals(tenantId))
                .filter(book -> book.status == RuleBookStatus.PUBLISHED)
                .filter(book -> book.selector.matches(selector))
                .filter(book -> book.effectiveWindow.contains(quoteDate))
                .max(Comparator.comparing(book -> book.publishedAt))
                .orElseThrow(() -> new IllegalStateException("no published rule book resolves for selector and quote date"));
        }

        static void assertNoPublishedOverlap(AdjustmentRuleBook candidate, List<AdjustmentRuleBook> existingRuleBooks) {
            for (AdjustmentRuleBook existing : existingRuleBooks) {
                if (existing.status == RuleBookStatus.PUBLISHED
                    && existing.tenantId.equals(candidate.tenantId)
                    && existing.selector.matches(candidate.selector)
                    && existing.effectiveWindow.overlaps(candidate.effectiveWindow)) {
                    throw new IllegalArgumentException("published rule book effective window overlaps existing selector");
                }
            }
        }
    }
}

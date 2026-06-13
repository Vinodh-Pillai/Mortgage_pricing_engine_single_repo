package com.wcpe.adjustment;

import com.wcpe.adjustment.AdjustmentRuleBook.AdjustmentCondition;
import com.wcpe.adjustment.AdjustmentRuleBook.ConditionOperator;
import com.wcpe.adjustment.AdjustmentRuleBook.PricingPrecisionPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConditionCompiler {
    public CompiledCondition compile(AdjustmentCondition condition, PricingPrecisionPolicy precisionPolicy) {
        Objects.requireNonNull(condition, "condition is required");
        if (!FactMap.SUPPORTED_DIMENSIONS.contains(condition.dimension())) {
            return new BlockingCondition(condition.dimension(), "unsupported_dimension:" + condition.dimension());
        }
        return switch (condition.operator()) {
            case EQ -> new EqualsCondition(condition.dimension(), condition.configuredValues());
            case IN -> new InCondition(condition.dimension(), condition.configuredValues());
            case RANGE_CLOSED -> new RangeCondition(condition.dimension(), condition.configuredValues(), true, true);
            case RANGE_OPEN_END -> new RangeCondition(condition.dimension(), condition.configuredValues(), true, false);
            case EXISTS -> new ExistsCondition(condition.dimension());
            case NOT_EXISTS -> new NotExistsCondition(condition.dimension());
            case BOOLEAN_IS -> new BooleanCondition(condition.dimension(), condition.configuredValues());
        };
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static Optional<BigDecimal> decimal(Object value) {
        try {
            return value == null || String.valueOf(value).isBlank()
                ? Optional.empty()
                : Optional.of(new BigDecimal(String.valueOf(value).trim()));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private abstract static class SingleDimensionCondition implements CompiledCondition {
        final String dimension;

        SingleDimensionCondition(String dimension) {
            this.dimension = Objects.requireNonNull(dimension, "dimension is required");
        }

        @Override
        public Set<String> requiredDimensions() {
            return Set.of(dimension);
        }

        CompiledCondition.ConditionEvaluation missingIfRequired(FactMap facts) {
            return facts.exists(dimension) ? null : CompiledCondition.ConditionEvaluation.blocked("missing_fact:" + dimension);
        }
    }

    private static final class EqualsCondition extends SingleDimensionCondition {
        private final Set<String> values;

        EqualsCondition(String dimension, List<String> values) {
            super(dimension);
            this.values = values.stream().map(ConditionCompiler::normalize).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            var missing = missingIfRequired(facts);
            return missing != null ? missing : CompiledCondition.ConditionEvaluation.matched(values.contains(normalize(facts.get(dimension))));
        }

        @Override
        public String toDisplayString() {
            return dimension + " EQ " + values;
        }
    }

    private static final class InCondition extends SingleDimensionCondition {
        private final Set<String> values;

        InCondition(String dimension, List<String> values) {
            super(dimension);
            this.values = values.stream().map(ConditionCompiler::normalize).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            var missing = missingIfRequired(facts);
            return missing != null ? missing : CompiledCondition.ConditionEvaluation.matched(values.contains(normalize(facts.get(dimension))));
        }

        @Override
        public String toDisplayString() {
            return dimension + " IN " + values;
        }
    }

    private static final class RangeCondition extends SingleDimensionCondition {
        private final List<String> bounds;
        private final boolean lowerInclusive;
        private final boolean upperInclusive;

        RangeCondition(String dimension, List<String> bounds, boolean lowerInclusive, boolean upperInclusive) {
            super(dimension);
            this.bounds = List.copyOf(bounds);
            this.lowerInclusive = lowerInclusive;
            this.upperInclusive = upperInclusive;
        }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            var missing = missingIfRequired(facts);
            if (missing != null) {
                return missing;
            }
            if (bounds.isEmpty() || (upperInclusive && bounds.size() < 2)) {
                return CompiledCondition.ConditionEvaluation.blocked("invalid_range_condition");
            }
            Optional<BigDecimal> actual = decimal(facts.get(dimension));
            Optional<BigDecimal> lower = decimal(bounds.get(0));
            Optional<BigDecimal> upper = upperInclusive ? decimal(bounds.get(1)) : Optional.empty();
            if (actual.isEmpty() || lower.isEmpty() || (upperInclusive && upper.isEmpty())) {
                return CompiledCondition.ConditionEvaluation.blocked("invalid_range_value");
            }
            boolean lowerMatch = lowerInclusive ? actual.get().compareTo(lower.get()) >= 0 : actual.get().compareTo(lower.get()) > 0;
            boolean upperMatch = !upperInclusive || actual.get().compareTo(upper.get()) <= 0;
            return CompiledCondition.ConditionEvaluation.matched(lowerMatch && upperMatch);
        }

        @Override
        public String toDisplayString() {
            return dimension + (upperInclusive ? " RANGE_CLOSED " : " RANGE_OPEN_END ") + bounds;
        }
    }

    private static final class ExistsCondition extends SingleDimensionCondition {
        ExistsCondition(String dimension) { super(dimension); }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            return facts.exists(dimension)
                ? CompiledCondition.ConditionEvaluation.matched(true)
                : CompiledCondition.ConditionEvaluation.blocked("missing_fact:" + dimension);
        }

        @Override
        public String toDisplayString() { return dimension + " EXISTS"; }
    }

    private static final class NotExistsCondition extends SingleDimensionCondition {
        NotExistsCondition(String dimension) { super(dimension); }

        @Override
        public Set<String> requiredDimensions() { return Set.of(); }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            return CompiledCondition.ConditionEvaluation.matched(!facts.exists(dimension));
        }

        @Override
        public String toDisplayString() { return dimension + " NOT_EXISTS"; }
    }

    private static final class BooleanCondition extends SingleDimensionCondition {
        private final String expected;

        BooleanCondition(String dimension, List<String> values) {
            super(dimension);
            this.expected = values.isEmpty() ? "" : normalize(values.get(0));
        }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            var missing = missingIfRequired(facts);
            if (missing != null) {
                return missing;
            }
            String actual = booleanToken(normalize(facts.get(dimension)));
            String configured = booleanToken(expected);
            if (!isBoolean(actual) || !isBoolean(configured)) {
                return CompiledCondition.ConditionEvaluation.blocked("invalid_boolean_value");
            }
            return CompiledCondition.ConditionEvaluation.matched(actual.equals(configured));
        }

        private boolean isBoolean(String value) { return "true".equals(value) || "false".equals(value); }

        private String booleanToken(String value) {
            return switch (value) {
                case "y", "yes", "1" -> "true";
                case "n", "no", "0" -> "false";
                default -> value;
            };
        }

        @Override
        public String toDisplayString() { return dimension + " BOOLEAN_IS " + expected; }
    }

    private static final class BlockingCondition implements CompiledCondition {
        private final String dimension;
        private final String reason;

        BlockingCondition(String dimension, String reason) {
            this.dimension = dimension;
            this.reason = reason;
        }

        @Override
        public CompiledCondition.ConditionEvaluation evaluate(FactMap facts) {
            return CompiledCondition.ConditionEvaluation.blocked(reason);
        }

        @Override
        public Set<String> requiredDimensions() { return Set.of(dimension); }

        @Override
        public String toDisplayString() { return reason; }
    }
}

package com.wcpe.adjustment;

import java.util.Set;

public interface CompiledCondition {
    ConditionEvaluation evaluate(FactMap facts);
    Set<String> requiredDimensions();
    String toDisplayString();

    record ConditionEvaluation(boolean matched, boolean blocked, String reason) {
        public static ConditionEvaluation matched(boolean matched) {
            return new ConditionEvaluation(matched, false, "");
        }

        public static ConditionEvaluation blocked(String reason) {
            return new ConditionEvaluation(false, true, reason);
        }
    }
}

package com.wcpe.eligibility.domain.extension;

/**
 * Extension contract for future product-family rule modules.
 * Modules receive immutable evaluation inputs and must own only their own config.
 */
public interface EligibilityRuleModule {
    RuleDecisionSet evaluate(EvaluationContext context);
}

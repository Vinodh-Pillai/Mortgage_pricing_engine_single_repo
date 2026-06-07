package com.wcpe.observability.cache;

public record CacheEligibilityPolicy(
    boolean featureEnabled,
    boolean requestComplete,
    boolean deterministicRequest,
    boolean manualOverrideDraft,
    boolean experimentalRules,
    boolean unsupportedProductOrChannel) {
  public CacheEligibilityDecision evaluate() {
    if (!featureEnabled) {
      return CacheEligibilityDecision.bypass(CacheBypassReason.FEATURE_DISABLED);
    }
    if (!requestComplete) {
      return CacheEligibilityDecision.bypass(CacheBypassReason.INCOMPLETE_REQUEST);
    }
    if (!deterministicRequest) {
      return CacheEligibilityDecision.bypass(CacheBypassReason.NON_DETERMINISTIC_REQUEST);
    }
    if (manualOverrideDraft) {
      return CacheEligibilityDecision.bypass(CacheBypassReason.MANUAL_OVERRIDE_DRAFT);
    }
    if (experimentalRules) {
      return CacheEligibilityDecision.bypass(CacheBypassReason.EXPERIMENTAL_RULES);
    }
    if (unsupportedProductOrChannel) {
      return CacheEligibilityDecision.bypass(CacheBypassReason.UNSUPPORTED_PRODUCT_OR_CHANNEL);
    }
    return CacheEligibilityDecision.cacheAllowed();
  }
}

export type EligibilityDecision = 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL';
export type CacheFreshness = 'FRESH' | 'STALE' | 'EXPIRED' | 'MISSING';
export type BlockerSeverity = 'blocking' | 'warning' | 'info';

export type EligibilityReason = {
  reasonCode: string;
  description: string;
  factRef?: string;
  message: string;
  severity: BlockerSeverity;
  overlayRef?: string;
  ruleRef?: string;
  remediation?: string;
};

export type EligibilityFact = {
  factRef: string;
  label: string;
  value: string | number | null;
  source: 'intake' | 'derived' | 'backend';
  quality: 'verified' | 'missing' | 'review';
  intakeStep?: string;
  linkedRuleRefs: string[];
};

export type EligibilityOverlayRef = {
  overlayId: string;
  overlayType: 'investor' | 'product' | 'state' | 'governance';
  version: string;
  ruleRef: string;
  effect: string;
  target: string;
};

export type EligibilityCacheHealth = {
  freshness: CacheFreshness;
  cacheRef: string;
  observedAt: string | null;
  ttlSeconds: number | null;
  dependencyRefs: string[];
  refreshPermitted: boolean;
};

export type RequiredNextFact = {
  factRef: string;
  label: string;
  reason: string;
  intakeStep: string;
};

export type EligibilityModuleView = {
  tenantContext: string;
  runId: string;
  optionId: string;
  decisionId: string;
  decision: EligibilityDecision;
  status: 'READY' | 'BLOCKED' | 'LOADING' | string;
  timestamp: string;
  explanation: string;
  correlationId: string;
  uiTraceId: string;
  reasonCodes: EligibilityReason[];
  facts: EligibilityFact[];
  overlayRefs: EligibilityOverlayRef[];
  cache: EligibilityCacheHealth;
  requiredNextFacts: RequiredNextFact[];
  allDecisions: Array<{ decisionId: string; decision: EligibilityDecision; optionId: string; summary: string }>;
  backendRefs: string[];
};

export const eligibleEligibilityFixture: EligibilityModuleView = {
  tenantContext: 'tenant-fixture',
  runId: 'run-preview-001',
  optionId: 'offer-a',
  decisionId: 'eligibility-decision-a',
  decision: 'ELIGIBLE',
  status: 'READY',
  timestamp: '2026-06-11T18:45:00Z',
  explanation: 'Eligibility-service returned an eligible decision for the selected quote option. The UI displays the service response and does not evaluate rules locally.',
  correlationId: 'corr-eligibility-a',
  uiTraceId: 'pii-24-s13-local-fixture-eligible',
  reasonCodes: [
    {
      reasonCode: 'ELIGIBILITY_PASS',
      description: 'Backend eligibility evaluation completed without blockers.',
      message: 'No required fact or overlay blocker was returned for this option.',
      severity: 'info',
      ruleRef: 'eligibility-service:decision-summary',
    },
  ],
  facts: [
    { factRef: 'fact:credit-profile', label: 'Credit profile package', value: 'verified by intake', source: 'intake', quality: 'verified', intakeStep: 'borrower', linkedRuleRefs: ['rule:credit-profile-read'] },
    { factRef: 'fact:property-state', label: 'Property state', value: 'backend supplied', source: 'intake', quality: 'verified', intakeStep: 'property', linkedRuleRefs: ['rule:state-overlay-check'] },
  ],
  overlayRefs: [
    { overlayId: 'overlay-investor-a', overlayType: 'investor', version: 'v2026.06', ruleRef: 'rule:investor-a-eligibility', effect: 'No blocker returned.', target: '/pricing/adjustments?ref=overlay-investor-a' },
    { overlayId: 'governance-eligibility-audit', overlayType: 'governance', version: 'audit-v1', ruleRef: 'governance:decision-trace', effect: 'Trace retained for compliance review.', target: '/audit/replay?ref=corr-eligibility-a' },
  ],
  cache: {
    freshness: 'FRESH',
    cacheRef: 'eligibility-cache:run-preview-001:offer-a',
    observedAt: '2026-06-11T18:45:00Z',
    ttlSeconds: 60,
    dependencyRefs: ['eligibility-service:decision', 'quote-service:selected-offer'],
    refreshPermitted: false,
  },
  requiredNextFacts: [],
  allDecisions: [
    { decisionId: 'eligibility-decision-a', decision: 'ELIGIBLE', optionId: 'offer-a', summary: 'Selected offer has no backend-returned blockers.' },
    { decisionId: 'eligibility-decision-b', decision: 'CONDITIONAL', optionId: 'offer-b', summary: 'Alternative offer requires additional intake facts.' },
  ],
  backendRefs: ['GET /api/v1/tenants/{tenantId}/quote-runs/{runId}/eligibility', 'eligibility-service:fixture-shaped-response'],
};

export const conditionalEligibilityFixture: EligibilityModuleView = {
  ...eligibleEligibilityFixture,
  optionId: 'offer-b',
  decisionId: 'eligibility-decision-b',
  decision: 'CONDITIONAL',
  timestamp: '2026-06-11T18:46:00Z',
  explanation: 'Eligibility-service returned a conditional decision because required intake facts are missing or need review.',
  correlationId: 'corr-eligibility-b',
  uiTraceId: 'pii-24-s13-local-fixture-conditional',
  reasonCodes: [
    {
      reasonCode: 'MISSING_REQUIRED_FACT',
      description: 'The backend response says a required fact is absent or stale.',
      factRef: 'fact:income-verification-package',
      message: 'Income verification package is required before final eligibility can be confirmed.',
      severity: 'blocking',
      overlayRef: 'overlay-product-income-docs',
      ruleRef: 'eligibility-service:missing-fact',
      remediation: 'Complete the income section in intake and rerun eligibility.',
    },
    {
      reasonCode: 'CACHE_STALE',
      description: 'Backend cache metadata reports stale eligibility dependencies.',
      factRef: 'fact:credit-profile',
      message: 'Refresh is recommended before final disclosure workflow.',
      severity: 'warning',
      ruleRef: 'eligibility-service:cache-health',
      remediation: 'Request a backend refresh when connected services are available.',
    },
  ],
  facts: [
    { factRef: 'fact:income-verification-package', label: 'Income verification package', value: null, source: 'intake', quality: 'missing', intakeStep: 'income', linkedRuleRefs: ['eligibility-service:missing-fact'] },
    { factRef: 'fact:credit-profile', label: 'Credit profile package', value: 'needs refresh', source: 'backend', quality: 'review', intakeStep: 'borrower', linkedRuleRefs: ['eligibility-service:cache-health'] },
  ],
  overlayRefs: [
    { overlayId: 'overlay-product-income-docs', overlayType: 'product', version: 'backend-v2026.06', ruleRef: 'eligibility-service:missing-fact', effect: 'Requires intake completion before final eligibility.', target: '/pricing/margins?ref=overlay-product-income-docs' },
  ],
  cache: {
    freshness: 'STALE',
    cacheRef: 'eligibility-cache:run-preview-001:offer-b',
    observedAt: '2026-06-11T18:32:00Z',
    ttlSeconds: 60,
    dependencyRefs: ['eligibility-service:decision', 'quote-service:selected-offer', 'intake-service:facts'],
    refreshPermitted: true,
  },
  requiredNextFacts: [
    { factRef: 'fact:income-verification-package', label: 'Income verification package', reason: 'Required by backend eligibility response.', intakeStep: 'income' },
  ],
};

export const ineligibleEligibilityFixture: EligibilityModuleView = {
  ...conditionalEligibilityFixture,
  optionId: 'offer-c',
  decisionId: 'eligibility-decision-c',
  decision: 'INELIGIBLE',
  explanation: 'Eligibility-service returned an ineligible decision with backend-supplied blockers. The UI does not calculate or infer the blockers.',
  correlationId: 'corr-eligibility-c',
  uiTraceId: 'pii-24-s13-local-fixture-ineligible',
  reasonCodes: [
    {
      reasonCode: 'INVESTOR_OVERLAY_FAILED',
      description: 'Investor overlay response returned a hard blocker.',
      factRef: 'fact:investor-overlay-result',
      message: 'Selected product is blocked by a backend investor overlay response.',
      severity: 'blocking',
      overlayRef: 'overlay-investor-hard-stop',
      ruleRef: 'eligibility-service:investor-overlay',
      remediation: 'Review alternate products or send to exception workflow if business policy permits.',
    },
  ],
  facts: [
    { factRef: 'fact:investor-overlay-result', label: 'Investor overlay result', value: 'hard blocker returned', source: 'backend', quality: 'verified', linkedRuleRefs: ['eligibility-service:investor-overlay'] },
  ],
  overlayRefs: [
    { overlayId: 'overlay-investor-hard-stop', overlayType: 'investor', version: 'backend-v2026.06', ruleRef: 'eligibility-service:investor-overlay', effect: 'Blocks selected option.', target: '/pricing/adjustments?ref=overlay-investor-hard-stop' },
  ],
  cache: {
    freshness: 'EXPIRED',
    cacheRef: 'eligibility-cache:run-preview-001:offer-c',
    observedAt: '2026-06-11T17:40:00Z',
    ttlSeconds: 60,
    dependencyRefs: ['eligibility-service:decision', 'investor-overlay-service:result'],
    refreshPermitted: true,
  },
  requiredNextFacts: [
    { factRef: 'fact:alternate-product-selection', label: 'Alternate product selection', reason: 'Current option has a backend hard blocker.', intakeStep: 'product' },
  ],
};

export const blockedEligibilityFixture: EligibilityModuleView = {
  ...eligibleEligibilityFixture,
  decisionId: 'eligibility-decision-blocked',
  decision: 'CONDITIONAL',
  status: 'BLOCKED',
  explanation: 'Eligibility-service contract was unavailable, so only a blocked local fallback can be displayed.',
  reasonCodes: [],
  facts: [],
  overlayRefs: [],
  cache: {
    freshness: 'MISSING',
    cacheRef: 'eligibility-cache:unavailable',
    observedAt: null,
    ttlSeconds: null,
    dependencyRefs: ['eligibility-service:unavailable'],
    refreshPermitted: false,
  },
  requiredNextFacts: [{ factRef: 'fact:eligibility-service-contract', label: 'Eligibility service response', reason: 'Connected runtime is unavailable in local harness.', intakeStep: 'review' }],
};

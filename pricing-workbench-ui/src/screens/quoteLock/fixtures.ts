import type { LockConfirmationResult, LockWorkflowView } from '../../lib/api/quoteRuns';

export const deterministicLockWorkflow: LockWorkflowView = {
  tenantContext: 'tenant-fixture',
  runId: 'run-preview-001',
  selectedOfferId: 'offer-a',
  status: 'READY',
  lockIdPreview: 'lock-preview-offer-a',
  lockId: null,
  terms: {
    productLabel: 'Conventional 30 year fixed',
    investor: 'Backend Investor A',
    channel: 'Retail',
    noteRate: '6.500%',
    finalPriceBps: '101.125',
    lockPeriodDays: 45,
    expiresAt: new Date(Date.now() + 26 * 60 * 60 * 1000).toISOString(),
    waterfallRef: 'waterfall:offer-a:final',
    adjustmentRefs: ['adjustment:llpa:backend-ref', 'adjustment:state:backend-ref'],
    marginRefs: ['margin:retail:backend-ref'],
    investorConfirmationRequired: true,
  },
  disclosures: [
    {
      disclosureId: 'lock-disclosure-fixture',
      title: 'Rate lock disclosure',
      text: 'Borrower and loan officer acknowledge that lock terms, fees, expiration, and post-lock options are controlled by backend lock, quote, investor, and compliance services. This local screen renders returned values only and does not calculate rates or eligibility.',
      complianceRef: 'compliance:rate-lock:fixture',
    },
  ],
  lockDisabled: false,
  lockDisabledReason: null,
  blockers: [],
  postLockActions: [
    { action: 'extend', label: 'Extend Lock', eligible: true, fee: '$125', maxDays: 15, approvalRequired: true, terms: 'Backend returned one extension option for this lock.' },
    { action: 'relock', label: 'Relock', eligible: true, fee: '$0', maxDays: null, approvalRequired: true, terms: 'Backend returned relock eligibility for current market terms.' },
    { action: 'float_down', label: 'Float Down', eligible: false, fee: null, maxDays: null, approvalRequired: true, terms: 'Backend did not return float-down eligibility.', blocker: 'FLOAT_DOWN_NOT_ELIGIBLE' },
  ],
  history: [
    { eventId: 'evt-created', eventType: 'created', timestamp: '2026-06-11T18:00:00Z', actor: 'loan-officer-fixture', terms: 'Workflow created from selected offer offer-a.', approvalRef: null, auditRef: 'audit:lock-created' },
  ],
  uiTraceId: 'pii-24-s12-local-trace',
  events: ['lock.workflow.fixture.loaded'],
  fallbackReason: 'Local deterministic fixture used when lock-service and compliance-service endpoints are unavailable.',
};

export const blockedLockWorkflow: LockWorkflowView = {
  ...deterministicLockWorkflow,
  status: 'BLOCKED',
  lockDisabled: true,
  lockDisabledReason: 'Backend returned unresolved compliance disclosure blockers.',
  blockers: [
    { code: 'DISCLOSURE_MISSING', message: 'Compliance disclosure package is unavailable.', remediation: 'Return to offers or retry after compliance evidence is available.', sourceRef: 'compliance:disclosure:missing' },
  ],
};

export const confirmedLockWorkflow: LockWorkflowView = {
  ...deterministicLockWorkflow,
  status: 'CONFIRMED',
  lockId: 'lock-confirmed-offer-a',
  history: [
    ...deterministicLockWorkflow.history,
    { eventId: 'evt-confirmed', eventType: 'confirmed', timestamp: '2026-06-11T18:05:00Z', actor: 'loan-officer-fixture', terms: 'Lock confirmed with backend returned terms.', approvalRef: 'approval:investor:pending', auditRef: 'audit:lock-confirmed' },
  ],
};

export const deterministicLockConfirmation: LockConfirmationResult = {
  status: 'CONFIRMED',
  lockId: 'lock-confirmed-offer-a',
  expiresAt: deterministicLockWorkflow.terms.expiresAt,
  message: 'Lock confirmed from deterministic fixture.',
  conflictResolution: null,
  auditRef: 'audit:lock-confirmed',
  historyEvent: { eventId: 'evt-confirmed', eventType: 'confirmed', timestamp: '2026-06-11T18:05:00Z', actor: 'loan-officer-fixture', terms: 'Lock confirmed with backend returned terms.', approvalRef: 'approval:investor:pending', auditRef: 'audit:lock-confirmed' },
};

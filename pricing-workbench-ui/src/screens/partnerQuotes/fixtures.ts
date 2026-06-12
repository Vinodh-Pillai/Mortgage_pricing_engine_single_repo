import type { PartnerQuoteDetailView, PartnerQuotesView } from './types';

export const partnerQuotesFixture: PartnerQuotesView = {
  partnerId: 'partner-fixture',
  tenantContext: 'tenant-fixture',
  statusFilter: '',
  dependencyStatus: 'PARTNER_QUOTES_REFS_SUPPLIED_WITH_ACTION_CONTRACT_GAPS',
  fallbackReason: 'Live partner pricing logic, SLA calculation, Storybook, and E2E runtime are unavailable in this local lane; UI renders backend-owned refs and deterministic fixtures only.',
  uiTraceId: 'partner-quotes-s21-local-trace',
  events: ['PartnerQuotesOpened', 'PartnerQuoteDetailViewed'],
  quotes: [
    {
      quoteId: 'pq-fixture-001',
      borrowerLabel: 'Borrower fixture A',
      status: 'SUBMITTED',
      slaState: 'ON_TRACK',
      lockState: 'UNLOCKED',
      errorFlags: [],
      partner: 'partner-fixture',
      createdRef: 'created-ref:pq-fixture-001',
      updatedRef: 'updated-ref:pq-fixture-001',
      requestedBy: 'requested-by-ref:partner-manager',
      guidance: 'Review backend quote evidence before requesting a reprice; pricing calculations remain service-owned.',
      errorDetails: [],
      lifecycleEvents: [
        { eventId: 'event-submit', eventType: 'SUBMITTED', timestampRef: 'time-ref:submitted', summary: 'Partner submitted quote package.' },
        { eventId: 'event-pricing', eventType: 'PRICING', timestampRef: 'time-ref:pricing-started', summary: 'Pricing service accepted quote for processing.' },
      ],
      slaTargetRef: 'sla-target-ref:pq-fixture-001',
      slaElapsedRef: 'sla-elapsed-ref:pq-fixture-001',
      slaRemainingRef: 'sla-remaining-ref:pq-fixture-001',
      breachPredictionRef: 'sla-breach-prediction-ref:pq-fixture-001',
      supportHandoffRoute: '/partners/support/reprice',
    },
    {
      quoteId: 'pq-fixture-002',
      borrowerLabel: 'Borrower fixture B',
      status: 'LOCKED',
      slaState: 'AT_RISK',
      lockState: 'LOCKED',
      errorFlags: ['VALIDATION_FAILED'],
      partner: 'correspondent-fixture',
      createdRef: 'created-ref:pq-fixture-002',
      updatedRef: 'updated-ref:pq-fixture-002',
      requestedBy: 'requested-by-ref:correspondent-manager',
      guidance: 'Resolve validation refs before reprice request can be submitted.',
      errorDetails: ['validation-ref:borrower-package-incomplete'],
      lifecycleEvents: [
        { eventId: 'event-locked', eventType: 'LOCKED', timestampRef: 'time-ref:locked', summary: 'Lock-service supplied lock state.' },
      ],
      slaTargetRef: 'sla-target-ref:pq-fixture-002',
      slaElapsedRef: 'sla-elapsed-ref:pq-fixture-002',
      slaRemainingRef: 'sla-remaining-ref:pq-fixture-002',
      breachPredictionRef: 'sla-breach-prediction-ref:pq-fixture-002',
      supportHandoffRoute: '/partners/support/reprice',
    },
  ],
};

export const partnerQuoteDetailFixture: PartnerQuoteDetailView = {
  ...partnerQuotesFixture.quotes[0],
  tenantContext: 'tenant-fixture',
  partnerId: 'partner-fixture',
  actions: {
    reprice: {
      visible: true,
      permitted: true,
      guidance: 'Request reprice records an action request only; pricing-service owns recalculation.',
      supportHandoffRoute: '/partners/support/reprice',
    },
  },
  uiTraceId: 'partner-quotes-s21-local-trace',
};

export const blockedPartnerQuotesFixture: PartnerQuotesView = {
  ...partnerQuotesFixture,
  dependencyStatus: 'BLOCKED_PARTNER_QUOTES_CONTRACT_REQUIRED',
  fallbackReason: 'Partner quote read model is unavailable; UI does not synthesize quotes, SLA calculations, locks, or pricing results.',
  quotes: [],
};

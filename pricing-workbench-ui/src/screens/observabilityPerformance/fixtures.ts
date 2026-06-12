import type { PerformanceDashboardView } from '../../lib/api/observabilityPerformance';

export const performanceDashboardFixture: PerformanceDashboardView = {
  tenantContext: 'tenant-fixture',
  dependencyStatus: 'OBSERVABILITY_REFS_SUPPLIED_WITH_ACTION_CONTRACT_GAPS',
  signalGroups: [
    {
      serviceName: 'pricing-service',
      tenantContext: 'tenant-fixture',
      correlationId: 'corr-ref:pricing-latency-window',
      freshness: 'STALE',
      signals: [
        { signalId: 'pricing-latency', label: 'Pricing latency', freshness: 'STALE', source: 'observability-service', sourceRef: 'metric-ref:pricing-latency-p95', evidenceRefs: ['trace-ref:pricing-latency', 'dashboard-ref:pricing-service'] },
        { signalId: 'pricing-errors', label: 'Pricing error rate', freshness: 'LIVE', source: 'observability-service', sourceRef: 'metric-ref:pricing-errors', evidenceRefs: ['log-ref:pricing-errors'] },
      ],
      blockers: [
        { code: 'MISSING_BACKPRESSURE_REF', owner: 'sre-pricing', message: 'Backpressure source ref unavailable from observability-service.', caseId: 'ops-pricing-review' },
      ],
    },
    {
      serviceName: 'rate-feed-service',
      tenantContext: 'tenant-fixture',
      correlationId: 'corr-ref:rate-feed-freshness-window',
      freshness: 'EXPIRED',
      signals: [
        { signalId: 'rate-feed-cache', label: 'Rate feed cache freshness', freshness: 'EXPIRED', source: 'observability-service', sourceRef: 'metric-ref:rate-feed-cache-age', evidenceRefs: ['dashboard-ref:rate-feed-ops'] },
        { signalId: 'rate-feed-ingest', label: 'Rate feed ingest status', freshness: 'MISSING', source: 'rate-feed-service', sourceRef: 'event-ref:rate-feed-ingest', evidenceRefs: [] },
      ],
      blockers: [],
    },
  ],
  impacts: [
    { impactCode: 'LATENCY_SPIKE', severity: 'HIGH', summary: 'Quote response latency evidence needs SRE review.', source: 'observability-service', recoveryOwner: 'sre-pricing', runbookRef: 'runbook-ref:pricing-latency' },
    { impactCode: 'CACHE_STALENESS', severity: 'MEDIUM', summary: 'Rate feed cache freshness refs are expired.', source: 'rate-feed-service', recoveryOwner: 'rate-feed-ops', runbookRef: '' },
  ],
  evidenceLinks: [
    'pricing-service|METRIC|Pricing latency dashboard|/audit/replay?ref=pricing-latency',
    'pricing-service|TRACE|Pricing latency trace|/audit/replay?ref=pricing-trace',
    'rate-feed-service|DASHBOARD|Rate feed operations|/ops/rate-feeds',
    'rate-feed-service|LOG|Rate feed ingest logs|/audit/replay?ref=rate-feed-logs',
  ],
  blockers: [
    { code: 'RUNBOOK_OWNER_REQUIRED', owner: 'sre-lead', message: 'Recovery owner must confirm the pricing latency runbook.', caseId: 'ops-pricing-review' },
    { code: 'EVIDENCE_LINK_MISSING', owner: 'rate-feed-ops', message: 'Rate feed ingest signal has no trace evidence link yet.' },
  ],
  actionsDisabled: true,
  fallbackReason: 'Live observability-service write actions, Storybook visual states, and E2E evidence navigation are unavailable in this local lane; UI renders supplied refs only.',
  uiTraceId: 'perf-s20-local-trace',
  events: ['PerformanceDashboardOpened', 'SignalGroupExpanded', 'ImpactRunbookViewed'],
};

export const blockedPerformanceDashboardFixture: PerformanceDashboardView = {
  ...performanceDashboardFixture,
  dependencyStatus: 'BLOCKED_OBSERVABILITY_CONTRACT_REQUIRED',
  signalGroups: [],
  impacts: [],
  evidenceLinks: [],
  blockers: [{ code: 'OBSERVABILITY_CONTRACT_REQUIRED', owner: 'platform-observability', message: 'Performance API contract is unavailable.' }],
  fallbackReason: 'Configured observability-service read model is unavailable; no local metrics, thresholds, or alerting rules are synthesized.',
};

import type { OpsCaseListView, OpsCaseDetail } from '../../lib/api/opsCases';

export const opsCasesFixture: OpsCaseListView = {
  tenantContext: 'tenant-fixture',
  dependencyStatus: 'OPERATIONS_SERVICE_REFS_SUPPLIED_WITH_ACTION_CONTRACT_GAPS',
  dashboardMetrics: [
    { metricId: 'open-cases', label: 'Open Cases', value: 'count-ref:ops-open-cases', sourceRef: 'operations-service:case-counts' },
    { metricId: 'sla-breaches', label: 'SLA Breaches', value: 'count-ref:ops-sla-breaches', sourceRef: 'operations-service:sla-state-rollup', status: 'needs-attention' },
    { metricId: 'avg-resolution', label: 'Avg Resolution Time', value: 'duration-ref:ops-average-resolution', sourceRef: 'operations-service:resolution-metrics' },
    { metricId: 'my-assignments', label: 'My Assignments', value: 'count-ref:ops-my-assignments', sourceRef: 'operations-service:assignment-rollup' },
  ],
  charts: [
    { chartId: 'priority', label: 'Cases by Priority', segments: [{ label: 'P1', value: 'count-ref:p1-cases', sourceRef: 'operations-service:priority-rollup' }, { label: 'P2', value: 'count-ref:p2-cases', sourceRef: 'operations-service:priority-rollup' }] },
    { chartId: 'queue', label: 'Cases by Queue', segments: [{ label: 'Pricing', value: 'count-ref:pricing-queue', sourceRef: 'operations-service:queue-rollup' }, { label: 'Lock', value: 'count-ref:lock-queue', sourceRef: 'operations-service:queue-rollup' }] },
    { chartId: 'sla-trend', label: 'SLA Trend', segments: [{ label: 'On track', value: 'trend-ref:on-track', sourceRef: 'operations-service:sla-trend' }, { label: 'At risk', value: 'trend-ref:at-risk', sourceRef: 'operations-service:sla-trend' }] },
    { chartId: 'resolution', label: 'Resolution Time Distribution', segments: [{ label: 'Same day', value: 'bucket-ref:same-day', sourceRef: 'operations-service:resolution-distribution' }, { label: 'Multi day', value: 'bucket-ref:multi-day', sourceRef: 'operations-service:resolution-distribution' }] },
  ],
  queues: [
    { queueId: 'PRICING', label: 'Pricing', caseCount: 'count-ref:pricing-open', avgAgeRef: 'age-ref:pricing-average', slaBreachCountRef: 'count-ref:pricing-breaches', oldestCaseRef: 'case-ref:pricing-oldest', ownerRef: 'owner-ref:pricing-team', priorityRefs: ['priority-ref:p1', 'priority-ref:p2'] },
    { queueId: 'LOCK', label: 'Lock', caseCount: 'count-ref:lock-open', avgAgeRef: 'age-ref:lock-average', slaBreachCountRef: 'count-ref:lock-breaches', oldestCaseRef: 'case-ref:lock-oldest', ownerRef: 'owner-ref:lock-team', priorityRefs: ['priority-ref:p1'] },
    { queueId: 'ELIGIBILITY', label: 'Eligibility', caseCount: 'count-ref:eligibility-open', avgAgeRef: 'age-ref:eligibility-average', slaBreachCountRef: 'count-ref:eligibility-breaches', oldestCaseRef: 'case-ref:eligibility-oldest', ownerRef: 'owner-ref:eligibility-team', priorityRefs: ['priority-ref:p3'] },
    { queueId: 'EXCEPTION', label: 'Exception', caseCount: 'count-ref:exception-open', avgAgeRef: 'age-ref:exception-average', slaBreachCountRef: 'count-ref:exception-breaches', oldestCaseRef: 'case-ref:exception-oldest', ownerRef: 'owner-ref:exception-team', priorityRefs: ['priority-ref:p2'] },
    { queueId: 'COMPLIANCE', label: 'Compliance', caseCount: 'count-ref:compliance-open', avgAgeRef: 'age-ref:compliance-average', slaBreachCountRef: 'count-ref:compliance-breaches', oldestCaseRef: 'case-ref:compliance-oldest', ownerRef: 'owner-ref:compliance-team', priorityRefs: ['priority-ref:p1'] },
    { queueId: 'INTEGRATION', label: 'Integration', caseCount: 'count-ref:integration-open', avgAgeRef: 'age-ref:integration-average', slaBreachCountRef: 'count-ref:integration-breaches', oldestCaseRef: 'case-ref:integration-oldest', ownerRef: 'owner-ref:integration-team', priorityRefs: ['priority-ref:p4'] },
  ],
  cases: [
    { caseId: 'ops-lock-blocked', queue: 'LOCK', priority: 'P1', ageLabel: 'age-ref:backend-owned-lock-case-age', slaState: 'BREACHED', owner: 'Unassigned', status: 'OPEN', contextSummary: 'Blocked lock workflow requires operations triage.', sourceRefs: ['lock-service:lock-workflow-ref', 'operations-service:case-ref'] },
    { caseId: 'ops-pricing-review', queue: 'PRICING', priority: 'P2', ageLabel: 'age-ref:backend-owned-pricing-case-age', slaState: 'AT_RISK', owner: 'ops-pricing-lead', status: 'ASSIGNED', contextSummary: 'Pricing waterfall review needs backend evidence packet.', sourceRefs: ['pricing-service:waterfall-ref', 'operations-service:case-ref'] },
    { caseId: 'ops-compliance-check', queue: 'COMPLIANCE', priority: 'P3', ageLabel: 'age-ref:backend-owned-compliance-case-age', slaState: 'ON_TRACK', owner: 'compliance-ops', status: 'IN_PROGRESS', contextSummary: 'Compliance evidence review is awaiting audit packet.', sourceRefs: ['compliance-service:evidence-ref', 'audit-replay-service:packet-ref'] },
  ],
  escalations: [
    { escalationId: 'esc-lock-breach', caseId: 'ops-lock-blocked', escalationLevel: 'L2', escalatedTo: 'owner-ref:lock-ops-lead', escalatedAtRef: 'time-ref:escalated-at', slaBreachTimeRef: 'time-ref:sla-breach', reason: 'Configured SLA state is breached at operations-service.', status: 'OPEN', auditRefs: ['audit-ref:esc-lock-breach'] },
  ],
  filters: {
    queues: ['PRICING', 'LOCK', 'ELIGIBILITY', 'EXCEPTION', 'COMPLIANCE', 'INTEGRATION'],
    priorities: ['P1', 'P2', 'P3', 'P4'],
    slaStates: ['ON_TRACK', 'AT_RISK', 'BREACHED'],
    owners: ['Unassigned', 'ops-pricing-lead', 'compliance-ops'],
    statuses: ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED'],
  },
  actionsDisabled: true,
  fallbackReason: 'Live operations-service, exception-service, Storybook, E2E, export, routing, SLA calculation, and domain-owned action contracts are unavailable in this local lane; UI renders backend-owned refs only.',
  uiTraceId: 'ops-s19-local-trace',
  events: ['OpsCasesDashboardOpened', 'OpsCaseQueueViewed'],
};

export const opsCaseDetailFixture: OpsCaseDetail = {
  ...opsCasesFixture.cases[0],
  tenantContext: 'tenant-fixture',
  timeline: [
    { eventId: 'timeline-opened', eventType: 'OpsCaseOpened', summary: 'Operations case context opened by operations-service.', timestampRef: 'time-ref:case-opened', actorRef: 'actor-ref:ops-system', evidenceRefs: ['audit-ref:case-opened'] },
    { eventId: 'timeline-breached', eventType: 'SlaStateChanged', summary: 'SLA state supplied as breached by backend read model.', timestampRef: 'time-ref:sla-state-change', actorRef: 'actor-ref:operations-service', evidenceRefs: ['audit-ref:sla-state'] },
  ],
  evidencePacketIds: ['packet-ref:lock-case-evidence', 'packet-ref:pricing-context'],
  evidencePackets: [
    { packetId: 'packet-ref:lock-case-evidence', packetType: 'LOCK_WORKFLOW', sourceRef: 'lock-service:evidence-packet', downloadRef: 'download-ref:backend-contract-required' },
    { packetId: 'packet-ref:pricing-context', packetType: 'PRICING_CONTEXT', sourceRef: 'pricing-service:evidence-packet', downloadRef: 'download-ref:backend-contract-required' },
  ],
  assignmentRefs: ['assignment-ref:owner-contract-required'],
  noteRefs: ['note-ref:write-contract-required'],
  statusRefs: ['status-ref:update-contract-required'],
  escalationRefs: ['escalation-ref:exception-service-contract-required'],
  uiTraceId: 'ops-s19-local-trace',
  events: ['OpsCaseOpened'],
};

export const blockedOpsCasesFixture: OpsCaseListView = {
  ...opsCasesFixture,
  dependencyStatus: 'BLOCKED_OPERATIONS_CASE_CONTRACT_REQUIRED',
  dashboardMetrics: [],
  charts: [],
  queues: [],
  cases: [],
  escalations: [],
  actionsDisabled: true,
  fallbackReason: 'Configured operations-service case read model is unavailable; no local case routing or SLA calculation is synthesized.',
};

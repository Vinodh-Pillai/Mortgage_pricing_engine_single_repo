import type { AdjustmentEvidenceView } from '../../lib/api/adjustments';

export const adjustmentEvidenceFixture: AdjustmentEvidenceView = {
  tenantContext: 'tenant-fixture',
  dependencyStatus: 'Connected adjustment evidence fixture is available for local UI validation.',
  status: 'READY_WITH_BACKEND_REFS',
  adjustments: [
    {
      adjustmentId: 'adj-fixture-001',
      label: 'Credit and collateral adjustment bundle',
      category: 'FICO_LTV_LLPA',
      status: 'ACTIVE',
      factRefs: ['fact:credit-package', 'fact:collateral-package'],
      sourceRef: 'adjustment-service:bundle-001',
      sourceVersionRef: 'adjustment-config:v2026-06-fixture',
      summary: 'Backend supplied this adjustment evidence row and source references.',
      compensationHooks: ['hook:lo-compensation-ref-001', 'hook:branch-compensation-ref-001'],
      conflictIds: ['conflict-fixture-001'],
    },
    {
      adjustmentId: 'adj-fixture-002',
      label: 'State and fee adjustment bundle',
      category: 'FEES',
      status: 'SKIPPED',
      factRefs: ['fact:property-state', 'fact:fee-package'],
      sourceRef: 'adjustment-service:bundle-002',
      sourceVersionRef: 'adjustment-config:v2026-06-fixture',
      summary: 'Backend marked this adjustment skipped; the UI does not infer why beyond supplied refs.',
      compensationHooks: [],
      conflictIds: [],
    },
  ],
  conflicts: [
    {
      conflictId: 'conflict-fixture-001',
      severity: 'HIGH',
      reasonCode: 'CONFLICTING_FACT_REFS',
      reason: 'Adjustment service supplied conflicting fact references for analyst review.',
      resolutionOwner: 'pricing-ops',
      affectedAdjustmentIds: ['adj-fixture-001'],
    },
  ],
  blockers: [
    {
      reasonCode: 'LIVE_SERVICE_NOT_CONNECTED',
      message: 'Connected adjustment-service runtime is not required for fixture validation.',
      sourceRef: 'adjustment-service:evidence-api',
      resolutionOwner: 'platform-ops',
    },
  ],
  summaries: [
    {
      category: 'FICO_LTV_LLPA',
      summary: 'Backend summary indicates one active adjustment evidence row.',
      evidenceRefs: ['summary:fico-ltv-fixture', 'audit:adjustment-summary-001'],
    },
    {
      category: 'FEES',
      summary: 'Backend summary indicates one skipped adjustment evidence row.',
      evidenceRefs: ['summary:fees-fixture'],
    },
  ],
  versionRefs: ['adjustment-config:v2026-06-fixture', 'governance-config:adjustments-fixture'],
  auditRefs: ['audit:adjustment-view-001', 'audit:adjustment-conflict-001'],
  replayHash: 'replay-hash-adjustment-evidence-001',
  uiTraceId: 'adjustment-s15-local-trace',
  events: ['adjustment-expand', 'conflict-drilldown', 'compensation-hook-trace', 'summary-export'],
  fallbackReason: 'Fixture values use backend-shaped strings only; no adjustment, fee, LLPA, price, or compensation formula is calculated locally.',
};

export const blockedAdjustmentEvidenceFixture: AdjustmentEvidenceView = {
  ...adjustmentEvidenceFixture,
  dependencyStatus: 'Adjustment evidence API is unavailable in the local harness.',
  status: 'BLOCKED',
  adjustments: [],
  conflicts: [],
  summaries: [],
  fallbackReason: 'Connected adjustment-service evidence is required before live rows can render.',
};

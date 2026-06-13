import type { PricingWaterfallView, WaterfallLedgerRow } from '../../lib/api/quoteRuns';

const sections = ['Base Rate', 'Adjustments', 'Margins', 'Rounding'] as const;
const adjustmentReasonCodes = ['FICO_720_739', 'LTV_80_85', 'CASH_OUT_REFI'] as const;

export const deterministicPricingWaterfall: PricingWaterfallView = {
  tenantContext: 'tenant-fixture',
  runId: 'run-preview-001',
  status: 'READY',
  restrictedValuesVisible: false,
  dependencyStatus: 'fixture-backed',
  baseSelection: {
    selectionId: 'base-selection-standalone-001',
    gridVersionRef: 'grid:v2026-06-waterfall-fixture',
    selectedNoteRate: { value: '6.500%', redacted: false, reason: null },
    basePrice: { value: '101.125', redacted: false, reason: null },
    ledgerSteps: ['base-grid', 'adjustments', 'margin', 'rounding'],
  },
  finalPrice: {
    finalPriceId: 'final-price-standalone-001',
    roundedFinalPrice: { value: '100.875', redacted: false, reason: null },
    adjustmentRefs: ['adjustment-service:adj-001', 'adjustment-service:adj-redacted-002'],
    marginRefs: ['margin-service:branch-margin-001'],
    roundingTraceRefs: ['pricing-service:rounding-trace-001'],
    roundingMode: 'nearest-eighth-from-backend',
    precision: '0.125',
    ledger: buildLedgerRows(220),
  },
  blockers: [],
  versionRefs: ['pricing-config:v2026-06', 'margin-config:v2026-06', 'adjustment-config:v2026-06'],
  auditRefs: ['audit:waterfall-001', 'audit:redaction-001', 'audit:pricing-replay-001'],
  replayHash: 'replay-hash-waterfall-001',
  versionGraphHash: 'version-graph-hash-waterfall-001',
  resultHash: 'result-hash-waterfall-001',
  evidenceHash: 'evidence-hash-waterfall-001',
  uiTraceId: 'pii-24-s14-waterfall-fixture',
  events: ['fixture:pricing-waterfall-rendered', 'fixture:redaction-metadata-present'],
  fallbackReason: '',
};

export const blockedPricingWaterfall: PricingWaterfallView = {
  ...deterministicPricingWaterfall,
  status: 'BLOCKED',
  blockers: [
    {
      code: 'WATERFALL_SOURCE_UNAVAILABLE',
      message: 'Pricing-service waterfall evidence is unavailable for this run.',
      sourceRef: 'pricing-service:waterfall',
      remediation: 'Retry after pricing evidence refresh completes.',
    },
  ],
};

function buildLedgerRows(count: number): WaterfallLedgerRow[] {
  return Array.from({ length: count }, (_, index) => {
    const ordinal = index + 1;
    const section = sections[index % sections.length];
    const adjustmentReasonCode = adjustmentReasonCodes[index % adjustmentReasonCodes.length];
    const redacted = ordinal === 7 || ordinal === 118;
    return {
      ordinal,
      section,
      step: section === 'Adjustments' ? `Adjustment ${adjustmentReasonCode} step ${ordinal}` : `${section} step ${ordinal}`,
      inputValue: redacted ? { value: null, redacted: true, reason: 'MARGIN_CONFIDENTIAL', auditRef: 'audit:redaction-001' } : { value: `backend-input-${ordinal}`, redacted: false, reason: null },
      operation: `BACKEND_${section.toUpperCase().replace(/\s+/g, '_')}`,
      outputValue: redacted ? { value: null, redacted: true, reason: 'MARGIN_CONFIDENTIAL', auditRef: 'audit:redaction-001' } : { value: `backend-output-${ordinal}`, redacted: false, reason: null },
      configRef: section === 'Margins' ? 'margin-service:branch-margin-001' : section === 'Adjustments' ? 'adjustment-service:adj-001' : 'pricing-service:grid-config-001',
      reasonCode: redacted ? 'REDACTED_MARGIN' : section === 'Adjustments' ? adjustmentReasonCode : section.toUpperCase().replace(/\s+/g, '_'),
      roundingMode: section === 'Rounding' ? 'nearest-eighth-from-backend' : null,
      inputDetails: section === 'Adjustments' ? [`Backend input ref ${ordinal}`, `Reason code ${adjustmentReasonCode}`, `Conditions supplied by adjustment-service for row ${ordinal}`] : [`Backend input ref ${ordinal}`, `No local pricing calculation for row ${ordinal}`],
      outputDetails: [`Backend output ref ${ordinal}`],
      adjustmentRefs: section === 'Adjustments' ? [`adjustment-service:${adjustmentReasonCode.toLowerCase()}`] : [],
      marginRefs: section === 'Margins' ? ['margin-service:branch-margin-001'] : [],
    };
  });
}

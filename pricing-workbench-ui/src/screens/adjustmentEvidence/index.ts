import { lazy } from 'react';
import type React from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import AdjustmentEvidenceScreen from './AdjustmentEvidenceScreen';

export { default as AdjustmentEvidenceScreen, exportAdjustmentEvidenceJson, stateForAdjustmentEvidence } from './AdjustmentEvidenceScreen';
export { adjustmentEvidenceFixture, blockedAdjustmentEvidenceFixture } from './fixtures';

export const adjustmentEvidenceScreenModule = createScreenModule({
  id: 'adjustment-evidence',
  label: 'Adjustment evidence',
  routePattern: '/pricing/adjustments',
  breadcrumb: 'Adjustment Evidence',
  screenPackage: 'screens/adjustmentEvidence',
  dataBoundary: 'lib/api/adjustments.fetchAdjustmentEvidence',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready', 'load-state', 'ids', 'fact-refs', 'conflicts', 'compensation-hooks', 'summaries'] as unknown as ScreenVisualState[],
  dependencyStatus: 'Consumes adjustment-service evidence with pricing waterfall, margin compensation, governance, and audit refs when connected runtime is available.',
  adapterStatus: 'Renders backend or deterministic fixture values only; no local LLPA, fee, price, or compensation calculations.',
  evidenceTarget: getEvidenceTarget('adjustment-evidence', 'PII-24-S15'),
  Component: lazy(async () => ({ default: AdjustmentEvidenceScreen })) as unknown as React.LazyExoticComponent<React.ComponentType<ScreenProps>>,
});

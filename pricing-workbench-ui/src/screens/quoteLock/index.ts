import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import QuoteLockScreen from './LockWorkflow';

export { default as QuoteLockScreen } from './LockWorkflow';
export { deterministicLockWorkflow } from './fixtures';
export { countdownWarning, stateForLockWorkflow } from './LockWorkflow';

export const quoteLockScreenModule = createScreenModule({
  id: 'quote-lock',
  label: 'Quote lock workflow',
  routePattern: '/quote/:runId/lock',
  breadcrumb: 'Lock Workflow',
  screenPackage: 'screens/quoteLock',
  dataBoundary: 'lib/api/quoteRuns.fetchLockWorkflow',
  stateCoverage: ['loading', 'load-state', 'empty', 'blocked', 'needs-attention', 'ready', 'confirmed', 'expired', 'extended', 'relocked', 'float-down'] as unknown as import('../contract/ScreenProps').ScreenVisualState[],
  dependencyStatus: 'Consumes backend lock, quote, investor, and compliance boundaries when available.',
  adapterStatus: 'Renders backend or deterministic fixture values only; no local pricing or eligibility decisions.',
  evidenceTarget: getEvidenceTarget('quote-lock', 'PII-24-S12'),
  Component: lazy(async () => ({ default: QuoteLockScreen })),
});

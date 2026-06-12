import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import PricingWaterfallScreen from './PricingWaterfallScreen';

export { default as PricingWaterfallScreen } from './PricingWaterfallScreen';
export { deterministicPricingWaterfall, blockedPricingWaterfall } from './fixtures';
export { exportWaterfallCsv, exportWaterfallJson, stateForWaterfall } from './PricingWaterfallScreen';

export const pricingWaterfallScreenModule = createScreenModule({
  id: 'pricing-waterfall',
  label: 'Pricing waterfall',
  routePattern: '/quote/:runId/pricing-waterfall',
  breadcrumb: 'Pricing Waterfall',
  screenPackage: 'screens/pricingWaterfall',
  dataBoundary: 'lib/api/quoteRuns.fetchPricingWaterfall',
  stateCoverage: ['loading', 'load-state', 'empty', 'blocked', 'needs-attention', 'ready', 'redacted', 'replay-evidence'] as unknown as import('../contract/ScreenProps').ScreenVisualState[],
  dependencyStatus: 'Consumes pricing-service waterfall evidence plus adjustment, margin, governance, and audit refs when connected runtime is available.',
  adapterStatus: 'Renders backend or deterministic fixture values only; no local pricing calculations.',
  evidenceTarget: getEvidenceTarget('pricing-waterfall', 'PII-24-S14'),
  Component: lazy(async () => ({ default: PricingWaterfallScreen })),
});

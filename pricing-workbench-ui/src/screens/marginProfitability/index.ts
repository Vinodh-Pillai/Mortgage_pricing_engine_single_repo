import { lazy } from 'react';
import type React from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import MarginProfitabilityScreen from './MarginProfitabilityScreen';

export { default as MarginProfitabilityScreen } from './MarginProfitabilityScreen';
export { blockedMarginProfitabilityFixture, marginProfitabilityFixture } from './fixtures';
export { exportMarginProfitabilityJson, stateForMarginProfitability } from './MarginProfitabilityScreen';

export const marginProfitabilityScreenModule = createScreenModule({
  id: 'margin-profitability',
  label: 'Margin profitability',
  routePattern: '/pricing/margins',
  breadcrumb: 'Margin Profitability',
  screenPackage: 'screens/marginProfitability',
  dataBoundary: 'lib/api/marginProfitability.fetchMarginProfitability',
  stateCoverage: ['loading', 'load-state', 'empty', 'blocked', 'needs-attention', 'ready', 'redacted', 'floor-evidence', 'approval', 'replay-evidence'] as unknown as ScreenVisualState[],
  dependencyStatus: 'Consumes margin-service profitability evidence plus adjustment, compensation, governance, exception, and audit refs when connected runtime is available.',
  adapterStatus: 'Renders backend or deterministic fixture refs only; no local margin, floor, price, or compensation calculations.',
  evidenceTarget: getEvidenceTarget('margin-profitability', 'PII-24-S16'),
  match: (pathname) => pathname.startsWith('/pricing/margins'),
  Component: lazy(async () => ({ default: MarginProfitabilityScreen })) as unknown as React.LazyExoticComponent<React.ComponentType<ScreenProps>>,
});

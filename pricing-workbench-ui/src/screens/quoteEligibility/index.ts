import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import QuoteEligibilityScreen from './QuoteEligibilityScreen';

export { default as QuoteEligibilityScreen, stateForEligibility } from './QuoteEligibilityScreen';
export { EligibilityLayout } from './EligibilityLayout';
export * from './fixtures';

export const quoteEligibilityScreenModule = createScreenModule({
  id: 'quote-eligibility',
  label: 'Eligibility explanation',
  routePattern: '/quote/:runId/eligibility',
  breadcrumb: 'Eligibility Explanation',
  screenPackage: 'screens/quoteEligibility',
  dataBoundary: 'lib/api/eligibility.fetchEligibilityModuleView',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready', 'eligible', 'ineligible', 'conditional', 'explanation', 'cache-health'],
  dependencyStatus: 'Consumes eligibility-service and quote-service response shapes when connected runtime is available.',
  adapterStatus: 'Renders backend or deterministic fixture values only; no local eligibility calculations.',
  evidenceTarget: getEvidenceTarget('quote-eligibility', 'PII-24-S13'),
  Component: lazy(async () => ({ default: QuoteEligibilityScreen })),
});

import { lazy } from 'react';
import { createScreenModule } from '../contract/ScreenModule';
import { allVisualStates } from '../shared/MajorFunctionalityPage';
import { pricingAnalysisEvidenceTarget } from './PricingAnalysisScreen';

export const pricingAnalysisScreenModule = createScreenModule({
  id: 'pricing-analysis',
  label: 'Pricing Analysis',
  routePattern: '/pricing/analysis',
  breadcrumb: 'Pricing Analysis',
  screenPackage: 'screens/pricing',
  dataBoundary: 'pricing-service analysis APIs',
  stateCoverage: allVisualStates,
  personaVisibility: ['pricing-analyst', 'loan-officer', 'admin'],
  dependencyStatus: 'Pricing waterfall and margin refs are backend-owned and may block export readiness.',
  adapterStatus: 'Read-only analysis screen renders evidence refs and comparison surfaces without local pricing decisions.',
  evidenceTarget: pricingAnalysisEvidenceTarget,
  match: (pathname) => pathname.startsWith('/pricing/analysis'),
  Component: lazy(() => import('./PricingAnalysisScreen').then((module) => ({ default: module.PricingAnalysisScreen }))),
});

export { PricingAnalysisScreen, pricingAnalysisEvidenceTarget } from './PricingAnalysisScreen';
export default pricingAnalysisScreenModule;

import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';

export const quoteIntakeEvidenceTarget = getEvidenceTarget('quote-intake', 'PII-25-S05');

export const quoteIntakeScreenModule = createScreenModule({
  id: 'quote-intake',
  label: 'Progressive Quote Intake',
  routePattern: '/quote/start',
  breadcrumb: 'Start Quote',
  screenPackage: 'screens/quoteIntake',
  dataBoundary: 'scenario-service draft APIs and quote-service launch APIs',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  personaVisibility: ['loan-officer', 'borrower', 'pricing-analyst', 'admin'],
  dependencyStatus: 'Intake metadata, scenario draft, validation, and quote launch APIs drive progressive disclosure.',
  adapterStatus: 'RouteGuard protects /quote/start; flow shows one metadata-driven step at a time.',
  evidenceTarget: quoteIntakeEvidenceTarget,
  match: (pathname) => pathname === '/quote/start',
  Component: lazy(() => import('./QuoteIntakeScreen').then((module) => ({ default: module.QuoteIntakeScreen }))),
});

export { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
export { QuoteIntakeScreen } from './QuoteIntakeScreen';
export { ProgressIndicator } from './ProgressIndicator';
export { ResumeDraft } from './ResumeDraft';
export default quoteIntakeScreenModule;

import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';

export const quoteIntakeEvidenceTarget = getEvidenceTarget('quote-intake', 'PII-26-S09');

export const quoteIntakeScreenModule = createScreenModule({
  id: 'quote-intake',
  label: 'Pipeline Intake',
  routePattern: '/pipeline',
  breadcrumb: 'Pipeline Intake',
  screenPackage: 'screens/quoteIntake',
  dataBoundary: 'scenario-service draft APIs and quote-service launch APIs',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  personaVisibility: ['loan-officer', 'borrower', 'pricing-analyst', 'admin'],
  dependencyStatus: 'Intake metadata, scenario draft, validation, and quote launch APIs drive progressive disclosure.',
  adapterStatus: 'RouteGuard protects /pipeline; flow shows all metadata-driven sections on one page.',
  evidenceTarget: quoteIntakeEvidenceTarget,
  match: (pathname) => pathname === '/pipeline' || pathname === '/quote/start',
  Component: lazy(() => import('./QuoteIntakeScreen').then((module) => ({ default: module.QuoteIntakeScreen }))),
});

export { QuoteIntakeFlow, initialQuoteIntake } from './QuoteIntakeFlow';
export { PipelineIntakePage } from './PipelineIntakePage';
export { QuoteIntakeScreen } from './QuoteIntakeScreen';
export { ProgressIndicator } from './ProgressIndicator';
export { ResumeDraft } from './ResumeDraft';
export default quoteIntakeScreenModule;

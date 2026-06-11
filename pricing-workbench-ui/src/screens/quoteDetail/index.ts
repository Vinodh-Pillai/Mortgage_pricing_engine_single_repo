import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import QuoteDetailScreen from './QuoteDetailScreen';

export { default as QuoteDetailScreen } from './QuoteDetailScreen';
export { deterministicQuoteDetail } from './fixtures';

export const quoteDetailScreenModule = createScreenModule({
  id: 'quote-detail',
  label: 'Quote detail waterfall',
  routePattern: '/quote/:runId/offers/:optionId',
  breadcrumb: 'Quote Detail',
  screenPackage: 'screens/quoteDetail',
  dataBoundary: 'lib/api/offers.fetchQuoteDetail',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  dependencyStatus: 'Consumes backend quote detail, pricing waterfall, compliance, and audit evidence boundaries.',
  adapterStatus: 'Renders backend or fixture values only; no local pricing calculations.',
  evidenceTarget: getEvidenceTarget('quote-detail', 'PII-24-S11'),
  Component: lazy(async () => ({ default: QuoteDetailScreen })),
});

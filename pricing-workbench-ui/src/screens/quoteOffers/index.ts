import { lazy } from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import QuoteOffersScreen from './QuoteOffersScreen';

export { default as QuoteOffersScreen } from './QuoteOffersScreen';
export * from './offerComparison';

export const quoteOffersScreenModule = createScreenModule({
  id: 'quote-offers',
  label: 'Offer comparison',
  routePattern: '/quote/:runId/offers',
  breadcrumb: 'Compare Offers',
  screenPackage: 'screens/quoteOffers',
  dataBoundary: 'lib/api/offers',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  evidenceTarget: getEvidenceTarget('quote-offers', 'PII-24-S10'),
  Component: lazy(async () => ({ default: QuoteOffersScreen })),
});

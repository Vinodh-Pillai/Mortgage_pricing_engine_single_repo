import { lazy } from 'react';
import type React from 'react';
import { createScreenModule, getEvidenceTarget } from '../contract/ScreenModule';
import type { ScreenProps, ScreenVisualState } from '../contract/ScreenProps';
import PartnerQuotesLayout from './PartnerQuotesLayout';

export { default as PartnerQuotesLayout, collectPartnerQuoteRefs, partnerQuotesEvidenceTarget, stateForPartnerQuotes } from './PartnerQuotesLayout';
export { QuoteDetail } from './QuoteDetail';
export { QuoteList, filterAndSortQuotes } from './QuoteList';
export { RepriceModal } from './RepriceModal';
export { blockedPartnerQuotesFixture, partnerQuoteDetailFixture, partnerQuotesFixture } from './fixtures';
export * from './types';

export const partnerQuotesScreenModule = createScreenModule({
  id: 'partner-quotes',
  label: 'Partner quotes',
  routePattern: '/partners/quotes',
  breadcrumb: 'Partner Quotes',
  screenPackage: 'screens/partnerQuotes',
  dataBoundary: 'lib/api/partnerQuotes.fetchPartnerQuotes',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready', 'load-state'] as unknown as ScreenVisualState[],
  dependencyStatus: 'Consumes partner quote list, detail, lifecycle, lock, SLA state, error flag, and reprice action refs when connected runtime is available.',
  adapterStatus: 'Renders backend or deterministic fixture refs only; no partner pricing logic, SLA calculation, rate, or lock calculation is performed in the browser.',
  evidenceTarget: getEvidenceTarget('partner-quotes', 'PII-24-S21'),
  match: (pathname) => pathname.startsWith('/partners/quotes'),
  Component: lazy(async () => ({ default: PartnerQuotesLayout })) as unknown as React.LazyExoticComponent<React.ComponentType<ScreenProps>>,
});

export default PartnerQuotesLayout;

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

vi.mock('./screens/quoteIntake/QuoteIntakeScreen', () => ({
  QuoteIntakeScreen: () => (
    <section>
      <h1>New prospect intake</h1>
      <p>Capture the borrower and loan facts needed to start a pricing run.</p>
    </section>
  ),
}));

const authUser = {
  id: 'test-admin',
  email: 'admin@example.test',
  fullName: 'Test Admin',
  role: 'admin',
};

const bffBaseUrl = 'https://pricing-bff.example.test';

function intakeMetadataFixture() {
  return {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'NO_UPSTREAMS_CONFIGURED',
    fieldGroups: [],
    decisionControls: ['configuration-owned facts only'],
    validationIssues: [],
    auditPackageId: 'review-package',
    replayHashRef: 'review-ref',
    fallbackReason: 'FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE',
    uiTraceId: 'brw-s01-local-trace',
    quickQuoteState: {
      minimalFirstStepFields: ['quoteIntent', 'channel'],
      progressiveSectionOrder: ['identity', 'borrower', 'loan', 'property', 'income', 'launch'],
      quoteServiceRequiredFacts: ['borrowerName', 'loanAmount', 'propertyState'],
      backendOwnedFactSources: ['scenario-service', 'quote-service'],
      blockedByContracts: ['SLA contract required'],
      fallbackReason: 'NO_UPSTREAMS_CONFIGURED',
    },
  };
}

function renderApp(initialPath = '/pipeline') {
  window.history.pushState({}, '', initialPath);
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App routing shell', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.stubEnv('VITE_BFF_API_BASE_URL', bffBaseUrl);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(input.toString(), window.location.origin);
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/intake-metadata') {
          return { ok: true, status: 200, json: async () => intakeMetadataFixture() };
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/scenarios' && url.searchParams.has('borrowerLastName')) {
          return { ok: true, status: 200, json: async () => ({ items: [] }) };
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/products') {
          return { ok: true, status: 200, json: async () => ({ products: [], totalCount: 0, availableFilters: { productTypes: [], investors: [], channels: [], statuses: [] } }) };
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-internal-ref-cleanup/offers') {
          return {
            ok: true,
            status: 200,
            json: async () => ({
              runId: 'run-internal-ref-cleanup',
              status: 'READY',
              fallbackReason: 'Quote service LoanPass execute summary response was normalized by pricing workbench service; browser did not call quote service directly',
              backendRefs: ['LoanPass', 'LoanHouse', 'source_url:https://example.invalid/quickpricer/get-generic-quote-summary'],
              requiredFacts: ['pricing-service quote price'],
              commitBlocked: false,
              uiTraceId: 'trace-internal-ref-cleanup',
              offers: [
                {
                  offerId: 'offer-clean',
                  rank: 1,
                  productLabel: 'Expanded Prime Plus 30 Year Fixed',
                  payment: '2627.72',
                  apr: '6.99',
                  rationaleChips: [
                    'Approved',
                    'Quote service LoanPass execute summary response was normalized by pricing workbench service; browser did not call quote service directly',
                  ],
                  scenarioFlags: ['APPROVED'],
                  explanationStatus: 'AVAILABLE',
                  upstreamRefs: ['LoanHouse/LoanPass source url quickpricer get generic quote summary'],
                  lockEligibilityRefs: ['lock-period:60'],
                  snapshotRefs: ['snapshot:offer-clean'],
                  auditIds: ['review:offer-clean'],
                  explanationSections: ['summary'],
                },
              ],
            }),
          };
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-internal-ref-cleanup/offers/offer-clean/explain') {
          return {
            ok: true,
            status: 200,
            json: async () => ({
              runId: 'run-internal-ref-cleanup',
              offerId: 'offer-clean',
              status: 'AVAILABLE',
              rationaleLines: [
                'Explanation ready for borrower review.',
                'Quote service LoanPass execute summary response was normalized by pricing workbench service; browser did not call quote service directly',
              ],
              scenarioFlags: [],
              upstreamRefs: ['LoanPass LoanHouse source url quickpricer get generic quote summary'],
              snapshotRefs: [],
              auditIds: [],
              explanationSections: ['summary'],
              commitBlocked: false,
              message: '',
              uiTraceId: 'trace-internal-ref-cleanup-explain',
            }),
          };
        }
        if (url.href === `${bffBaseUrl}/api/auth/me`) {
          return { ok: true, status: 200, json: async () => ({ user: authUser }) };
        }
        return { ok: true, status: 200, json: async () => ({}) };
      }),
    );
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('renders the routed pipeline shell inside a router context', async () => {
    renderApp('/pipeline');

    expect(await screen.findByRole('heading', { name: 'New prospect intake' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toBeInTheDocument();
    expect(screen.getByText(/Capture the borrower and loan facts needed to start a pricing run/i)).toBeInTheDocument();
  });

  it('keeps the legacy quote start path as a redirect to the pipeline route', async () => {
    renderApp('/quote/start');

    expect(await screen.findByRole('heading', { name: 'New prospect intake' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'QuickQuote breadcrumb' })).toHaveAttribute('href', '/quote/start');
  });

  it('renders the requested Pricing Waterfall preview evidence direct route', async () => {
    renderApp('/pricing/waterfall');

    expect(await screen.findByRole('heading', { name: /Pricing Waterfall preview/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getAllByText(/Preview evidence page · non-production/i).length).toBeGreaterThan(0);
  });

  it('renders the requested Quote Journey Map preview evidence direct route', async () => {
    renderApp('/journey-map');

    expect(await screen.findByRole('heading', { name: /Quote Journey Map preview/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getAllByText(/Preview evidence page · non-production/i).length).toBeGreaterThan(0);
  });

  it('sanitizes internal provider and source references on the live Compare Offers route', async () => {
    renderApp('/quote/run-internal-ref-cleanup/offers');

    expect(await screen.findByRole('heading', { name: /Compare offers for run run-internal-ref-cleanup/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getAllByText('Pricing service evidence available').length).toBeGreaterThanOrEqual(2);

    const visibleTextBeforeInspect = document.body.textContent ?? '';
    expect(visibleTextBeforeInspect).not.toMatch(/LoanPass|LoanHouse|source url|source_url|quickpricer|get generic quote summary/i);

    fireEvent.click(screen.getByRole('button', { name: /Inspect explanation for offer offer-clean/i }));
    expect(await screen.findByText('Explanation ready for borrower review.')).toBeInTheDocument();

    const visibleTextAfterInspect = document.body.textContent ?? '';
    expect(visibleTextAfterInspect).not.toMatch(/LoanPass|LoanHouse|source url|source_url|quickpricer|get generic quote summary/i);
  });
});

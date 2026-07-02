import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useEffect } from 'react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

vi.mock('./screens/quoteIntake/QuoteIntakeScreen', () => ({
  QuoteIntakeScreen: () => (
    <section>
      <h1>Pipeline intake</h1>
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

function RouteController({ path }: { path: string }) {
  const navigate = useNavigate();
  useEffect(() => {
    navigate(path);
  }, [navigate, path]);
  return null;
}

function renderAppWithControlledPath(initialPath = '/pipeline') {
  function TestShell({ path }: { path: string }) {
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <RouteController path={path} />
        <App />
      </MemoryRouter>
    );
  }

  const view = render(<TestShell path={initialPath} />);
  return {
    ...view,
    navigateTo(path: string) {
      window.history.pushState({}, '', path);
      view.rerender(<TestShell path={path} />);
    },
  };
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

    expect(await screen.findByRole('heading', { name: 'Pipeline intake' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toBeInTheDocument();
    expect(screen.getByText(/Capture the borrower and loan facts needed to start a pricing run/i)).toBeInTheDocument();
  });

  it('keeps the legacy quote start path as a redirect to the pipeline route', async () => {
    renderApp('/quote/start');

    expect(await screen.findByRole('heading', { name: 'Pipeline intake' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'QuickQuote breadcrumb' })).toHaveAttribute('href', '/quote/start');
  });

  it('blocks the user-facing Pricing Waterfall direct route until a run is selected', async () => {
    renderApp('/pricing/waterfall');

    expect(await screen.findByRole('heading', { name: /Pricing Waterfall/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText(/Live run required/i)).toBeInTheDocument();
    expect(screen.getByText('/quote/:runId/pricing-waterfall')).toBeInTheDocument();
  });

  it('blocks the user-facing Quote Journey Map direct route until a run is selected', async () => {
    renderApp('/journey-map');

    expect(await screen.findByRole('heading', { name: /Quote Journey Map/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText(/Live run required/i)).toBeInTheDocument();
    expect(screen.getByText('/quote/:runId/journey')).toBeInTheDocument();
  });

  it('keeps deterministic waterfall and journey fixtures behind explicit preview routes', async () => {
    const app = renderAppWithControlledPath('/pricing/waterfall/preview');
    expect(await screen.findByRole('heading', { name: /Pricing Waterfall for run run-test/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getAllByText(/Preview evidence page · non-production/i).length).toBeGreaterThan(0);

    app.navigateTo('/journey-map/preview');
    expect(await screen.findByRole('heading', { name: /Quote Journey Map for run run-test/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getAllByText(/Preview evidence page · non-production/i).length).toBeGreaterThan(0);
  });

  it('uses the URL tenant context for live run-specific pricing waterfall and quote journey routes', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(input.toString(), window.location.origin);
      if (url.href === `${bffBaseUrl}/api/auth/me`) return { ok: true, status: 200, json: async () => ({ user: authUser }) };
      if (url.pathname === '/api/v1/tenants/tenant-live/quote-runs/run-live-waterfall/pricing-waterfall') {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            tenantContext: 'tenant-live',
            runId: 'run-live-waterfall',
            status: 'READY',
            restrictedValuesVisible: false,
            dependencyStatus: 'READY',
            baseSelection: { selectionId: 'selection-live', gridVersionRef: 'grid-live', selectedNoteRate: { value: '6.5', redacted: false, reason: null }, basePrice: { value: '101.0', redacted: false, reason: null }, ledgerSteps: ['base'] },
            finalPrice: { finalPriceId: 'final-live', roundedFinalPrice: { value: '100.5', redacted: false, reason: null }, ledger: [], adjustmentRefs: [], marginRefs: [], roundingTraceRefs: [] },
            blockers: [],
            versionRefs: [],
            auditRefs: [],
            replayHash: 'replay-live',
            versionGraphHash: 'graph-live',
            resultHash: 'result-live',
            evidenceHash: 'evidence-live',
            uiTraceId: 'waterfall-live-test',
            events: [],
            fallbackReason: '',
          }),
        };
      }
      if (url.pathname === '/api/v1/tenants/tenant-live/quote-runs/run-live-journey/journey') {
        return {
          ok: true,
          status: 200,
          json: async () => ({
            tenantContext: 'tenant-live',
            runId: 'run-live-journey',
            status: 'READY',
            dependencyStatus: 'READY',
            nodes: [],
            blockers: [],
            serviceContracts: [],
            events: [],
            fallbackReason: '',
            uiTraceId: 'journey-live-test',
          }),
        };
      }
      return { ok: true, status: 200, json: async () => ({}) };
    });
    vi.stubGlobal('fetch', fetchMock);

    const app = renderAppWithControlledPath('/quote/run-live-waterfall/pricing-waterfall?tenantId=tenant-live');
    expect(await screen.findByRole('heading', { name: /Pricing Waterfall for run run-live-waterfall/i }, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/tenant-live/quote-runs/run-live-waterfall/pricing-waterfall', expect.any(Object)));
    expect(fetchMock.mock.calls.map(([input]) => input.toString()).join('\n')).not.toContain('/api/v1/tenants/ui-preview-tenant/quote-runs/run-live-waterfall/pricing-waterfall');

    app.navigateTo('/quote/run-live-journey/journey?tenantId=tenant-live');
    expect(await screen.findByRole('heading', { name: /Quote Journey Map for run run-live-journey/i }, { timeout: 5000 })).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/v1/tenants/tenant-live/quote-runs/run-live-journey/journey', expect.any(Object)));
    expect(fetchMock.mock.calls.map(([input]) => input.toString()).join('\n')).not.toContain('/api/v1/tenants/ui-preview-tenant/quote-runs/run-live-journey/journey');
  });

  it('sanitizes internal provider and source references on the live Compare Offers route', async () => {
    renderApp('/quote/run-internal-ref-cleanup/offers');

    expect((await screen.findAllByRole('heading', { name: /^Offer Comparison$/i }, { timeout: 5000 })).length).toBeGreaterThan(0);
    expect(screen.getByRole('table', { name: /Offer comparison rows/i })).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: /Offer cards/i })).not.toBeInTheDocument();
    expect(screen.getAllByText('Connected pricing review reference available').length).toBeGreaterThanOrEqual(1);

    const visibleTextBeforeInspect = document.body.textContent ?? '';
    expect(visibleTextBeforeInspect).not.toMatch(/LoanPass|LoanHouse|source url|source_url|quickpricer|get generic quote summary/i);

    fireEvent.click(screen.getByRole('button', { name: /Select offer offer-clean for comparison/i }));
    expect(screen.getByRole('status')).toHaveTextContent(/Offer offer-clean is selected in this comparison/i);

    fireEvent.click(screen.getByRole('button', { name: /Inspect explanation for offer offer-clean/i }));
    expect(await screen.findByText('Explanation ready for borrower review.')).toBeInTheDocument();

    const visibleTextAfterInspect = document.body.textContent ?? '';
    expect(visibleTextAfterInspect).not.toMatch(/LoanPass|LoanHouse|source url|source_url|quickpricer|get generic quote summary/i);
  }, 30000);

  it('does not treat a stale delayed explanation response as evidence for a newly selected offer', async () => {
    let resolveOfferAExplanation: (response: Response) => void = () => undefined;
    const delayedOfferAExplanation = new Promise<Response>((resolve) => {
      resolveOfferAExplanation = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = new URL(input.toString(), window.location.origin);
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-stale-explanation/offers') {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({
              runId: 'run-stale-explanation',
              status: 'READY',
              sortOptions: ['payment', 'apr', 'rate', 'rank'],
              selectedOfferId: null,
              backendRefs: ['pricing-review:available'],
              requiredFacts: ['pricing-service quote price'],
              commitBlocked: false,
              uiTraceId: 'trace-stale-explanation',
              offers: [
                {
                  offerId: 'offer-a',
                  rank: 1,
                  productLabel: 'Offer A',
                  payment: '2500.00',
                  apr: '6.50',
                  rationaleChips: ['Explanation can be requested'],
                  scenarioFlags: ['APPROVED'],
                  explanationStatus: 'AVAILABLE',
                },
                {
                  offerId: 'offer-b',
                  rank: 2,
                  productLabel: 'Offer B',
                  payment: '2600.00',
                  apr: '6.75',
                  rationaleChips: ['Requires its own explanation'],
                  scenarioFlags: ['APPROVED'],
                  explanationStatus: 'AVAILABLE',
                },
              ],
              events: [],
            }),
          } as Response);
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-stale-explanation/offers/offer-a/explain') {
          return delayedOfferAExplanation;
        }
        if (url.href === `${bffBaseUrl}/api/auth/me`) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ user: authUser }) } as Response);
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) } as Response);
      }),
    );

    renderApp('/quote/run-stale-explanation/offers');

    expect((await screen.findAllByRole('heading', { name: /^Offer Comparison$/i }, { timeout: 5000 })).length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole('button', { name: /Inspect explanation for offer offer-a/i }));
    fireEvent.click(screen.getByRole('button', { name: /Select offer offer-b for comparison/i }));

    expect(screen.getByRole('status')).toHaveTextContent(/Offer offer-b is selected in this comparison/i);
    expect(screen.getByRole('button', { name: /Continue to lock workflow/i })).toBeDisabled();

    await act(async () => {
      resolveOfferAExplanation({
        ok: true,
        status: 200,
        json: async () => ({
          runId: 'run-stale-explanation',
          offerId: 'offer-a',
          status: 'AVAILABLE',
          rationaleLines: ['Stale offer A explanation ready.'],
          scenarioFlags: [],
          upstreamRefs: ['pricing-review:offer-a'],
          snapshotRefs: ['snapshot:offer-a'],
          auditIds: ['audit:offer-a'],
          explanationSections: ['Offer A section'],
          commitBlocked: false,
          message: '',
          uiTraceId: 'trace-stale-explanation-offer-a',
        }),
      } as Response);
      await delayedOfferAExplanation;
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Continue to lock workflow/i })).toBeDisabled();
    });
    expect(screen.queryByText('Stale offer A explanation ready.')).not.toBeInTheDocument();
  }, 30000);

  it('invalidates a pending explanation when navigation changes the active pricing run', async () => {
    let resolveOldRunExplanation: (response: Response) => void = () => undefined;
    const delayedOldRunExplanation = new Promise<Response>((resolve) => {
      resolveOldRunExplanation = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = new URL(input.toString(), window.location.origin);
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-before-navigation/offers') {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({
              runId: 'run-before-navigation',
              status: 'READY',
              sortOptions: ['payment', 'apr', 'rate', 'rank'],
              selectedOfferId: null,
              backendRefs: ['pricing-review:before-navigation'],
              requiredFacts: [],
              commitBlocked: false,
              uiTraceId: 'trace-before-navigation',
              offers: [
                {
                  offerId: 'offer-old-run',
                  rank: 1,
                  productLabel: 'Old Run Offer',
                  payment: '2500.00',
                  apr: '6.50',
                  rationaleChips: ['Old run explanation can be requested'],
                  scenarioFlags: ['APPROVED'],
                  explanationStatus: 'AVAILABLE',
                },
              ],
              events: [],
            }),
          } as Response);
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-before-navigation/offers/offer-old-run/explain') {
          return delayedOldRunExplanation;
        }
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-after-navigation/offers') {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({
              runId: 'run-after-navigation',
              status: 'READY',
              sortOptions: ['payment', 'apr', 'rate', 'rank'],
              selectedOfferId: null,
              backendRefs: ['pricing-review:after-navigation'],
              requiredFacts: [],
              commitBlocked: false,
              uiTraceId: 'trace-after-navigation',
              offers: [
                {
                  offerId: 'offer-new-run',
                  rank: 1,
                  productLabel: 'New Run Offer',
                  payment: '2550.00',
                  apr: '6.60',
                  rationaleChips: ['New run requires its own explanation'],
                  scenarioFlags: ['APPROVED'],
                  explanationStatus: 'AVAILABLE',
                },
              ],
              events: [],
            }),
          } as Response);
        }
        if (url.href === `${bffBaseUrl}/api/auth/me`) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ user: authUser }) } as Response);
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) } as Response);
      }),
    );

    const app = renderAppWithControlledPath('/quote/run-before-navigation/offers');

    expect(await screen.findByText('Old Run Offer', {}, { timeout: 5000 })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Inspect explanation for offer offer-old-run/i }));
    app.navigateTo('/quote/run-after-navigation/offers');

    expect(await screen.findByText('New Run Offer', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Continue to lock workflow/i })).toBeDisabled();

    await act(async () => {
      resolveOldRunExplanation({
        ok: true,
        status: 200,
        json: async () => ({
          runId: 'run-before-navigation',
          offerId: 'offer-old-run',
          status: 'AVAILABLE',
          rationaleLines: ['Old run explanation must not appear after navigation.'],
          scenarioFlags: [],
          upstreamRefs: ['pricing-review:old-run'],
          snapshotRefs: ['snapshot:old-run'],
          auditIds: ['audit:old-run'],
          explanationSections: ['Old run section'],
          commitBlocked: false,
          message: '',
          uiTraceId: 'trace-old-run-explanation',
        }),
      } as Response);
      await delayedOldRunExplanation;
    });

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Continue to lock workflow/i })).toBeDisabled();
    });
    expect(screen.queryByText('Old run explanation must not appear after navigation.')).not.toBeInTheDocument();
  }, 30000);

  it('uses no-comparable-products copy when a pricing run returns no loaded offers', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) => {
        const url = new URL(input.toString(), window.location.origin);
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-empty-offers/offers') {
          return Promise.resolve({
            ok: true,
            status: 200,
            json: async () => ({
              runId: 'run-empty-offers',
              status: 'READY',
              sortOptions: ['payment', 'apr', 'rate', 'rank'],
              selectedOfferId: null,
              backendRefs: ['pricing-review:no-comparable-products'],
              requiredFacts: [],
              commitBlocked: false,
              fallbackReason: null,
              uiTraceId: 'trace-empty-offers',
              offers: [],
              events: [],
            }),
          } as Response);
        }
        if (url.href === `${bffBaseUrl}/api/auth/me`) {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({ user: authUser }) } as Response);
        }
        return Promise.resolve({ ok: true, status: 200, json: async () => ({}) } as Response);
      }),
    );

    renderApp('/quote/run-empty-offers/offers');

    expect(await screen.findByText(/No comparable offers are loaded for this pricing run/i, {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByText(/Selection remains blocked until comparable products are returned/i)).toBeInTheDocument();
    expect(screen.queryByText(/Selection is blocked until explanation data is available/i)).not.toBeInTheDocument();
  }, 30000);
});

import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
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
    expect(screen.getByRole('link', { name: 'Pipeline' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText(/Capture the borrower and loan facts needed to start a pricing run/i)).toBeInTheDocument();
  });

  it('keeps the legacy quote start path as a redirect to the pipeline route', async () => {
    renderApp('/quote/start');

    expect(await screen.findByRole('heading', { name: 'New prospect intake' }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pipeline' })).toHaveAttribute('aria-current', 'page');
  });
});

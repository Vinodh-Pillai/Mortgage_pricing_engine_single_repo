import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { deterministicLockWorkflow } from './screens/quoteLock/fixtures';

const authUser = {
  id: 'test-admin',
  email: 'admin@example.test',
  fullName: 'Test Admin',
  role: 'admin',
};

const bffBaseUrl = 'https://pricing-bff.example.test';

function renderApp(initialPath: string) {
  window.history.pushState({}, '', initialPath);
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App quote lock route', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.stubEnv('VITE_BFF_API_BASE_URL', bffBaseUrl);
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = new URL(input.toString(), window.location.origin);
        if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-preview-001/lock') {
          return { ok: true, status: 200, json: async () => ({ ...deterministicLockWorkflow, tenantContext: 'ui-preview-tenant' }) };
        }
        if (url.href === `${bffBaseUrl}/api/auth/me`) {
          return { ok: true, status: 200, json: async () => ({ user: authUser }) };
        }
        if (url.pathname === '/api/v1/tenants/tenant-live/locks') {
          return { ok: true, status: 200, json: async () => ({ tenantContext: 'tenant-live', dependencyStatus: 'LOCK_SERVICE_LIVE_READ_READY', uiTraceId: 'locks-route-test', events: [], pendingCount: 1, expiringCount: 0, locks: [{ lockId: 'lock-route-1', runId: 'run-route-1', borrowerRef: 'quote:run-route-1', status: 'REQUESTED', expiresAt: null, investorDeliveryStatus: 'not supplied by lock-service list contract', auditRefs: [], blockers: [], availableActions: ['read', 'detail'], actionBlockers: { extend: 'LOCK_EXTENSION_REQUIRED_FIELDS_NOT_SUPPLIED', relock: 'LOCK_RELOCK_REQUIRED_FIELDS_NOT_SUPPLIED', deliver: 'LOCK_INVESTOR_DELIVERY_ROUTE_NOT_EXPOSED_BY_LOCK_SERVICE' } }] }) };
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

  it('routes /quote/:runId/lock to the richer disclosure, signature, and action workflow', async () => {
    window.sessionStorage.setItem('loanweft:selectedOfferId:run-preview-001', 'offer-a');

    renderApp('/quote/run-preview-001/lock');

    expect(await screen.findByRole('heading', { name: /^Lock Workflow$/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByLabelText(/Disclosure text/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Digital signature/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Extend Lock/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Lock This Rate/i })).toBeDisabled();
    expect(screen.queryByRole('button', { name: /^Request lock$/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/Lock terms for run/i)).not.toBeInTheDocument();
  });

  it('renders degraded lock UX instead of staying on the route loading fallback when the contract is unavailable', async () => {
    vi.mocked(fetch).mockImplementation(async (input: RequestInfo | URL) => {
      const url = new URL(input.toString(), window.location.origin);
      if (url.pathname === '/api/v1/tenants/ui-preview-tenant/quote-runs/run-degraded/lock') {
        return { ok: false, status: 503, json: async () => ({ message: 'lock-service unavailable' }) } as Response;
      }
      if (url.href === `${bffBaseUrl}/api/auth/me`) {
        return { ok: true, status: 200, json: async () => ({ user: authUser }) } as Response;
      }
      return { ok: true, status: 200, json: async () => ({}) } as Response;
    });

    renderApp('/quote/run-degraded/lock');

    expect(await screen.findByText(/Degraded lock workflow/i, {}, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.queryByText(/Loading route/i)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^Confirm Lock$/i })).toBeDisabled();
  });

  it('routes /lock-management directly to the Lock Management page', async () => {
    renderApp('/lock-management?tenantId=tenant-live');

    expect(await screen.findByRole('table', { name: /Lock management records/i }, { timeout: 5000 })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /^Lock Management$/i })).toBeInTheDocument();
  });
});

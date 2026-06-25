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
});

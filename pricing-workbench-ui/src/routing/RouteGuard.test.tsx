import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, useAuth } from '../lib/auth/AuthContext';
import type { User } from '../lib/api/auth';
import { RouteGuard } from './RouteGuard';

const opsUser: User = { id: 'user-ops', email: 'ops@example.com', fullName: 'Ops Lead', role: 'operations_lead' };
const borrowerUser: User = { id: 'user-borrower', email: 'borrower@example.com', fullName: 'Borrower', role: 'borrower' };
let sessionUser: User | null;

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

function installFetchMock() {
  vi.stubEnv('VITE_TENANT_CONTEXT_AUTH_BASE_URL', 'http://tenant-context.local');
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    if (url.pathname === '/api/auth/me') {
      return sessionUser ? jsonResponse({ user: sessionUser }) : jsonResponse({ error: 'Not authenticated' }, 401);
    }
    if (url.pathname === '/api/auth/login' && init?.method === 'POST') {
      sessionUser = opsUser;
      return jsonResponse({ user: opsUser });
    }
    return jsonResponse({ error: 'Not found' }, 404);
  }));
}

function LoginProbe() {
  const location = useLocation();
  const { login } = useAuth();
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? 'none';
  return <button type="button" onClick={() => login('ops@example.com', 'Password123!')}>login from {from}</button>;
}

function renderGuard(initialEntry: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route path="/ops/dashboard" element={<RouteGuard><p>ops allowed</p></RouteGuard>} />
          <Route path="/admin/governance" element={<RouteGuard><p>admin allowed</p></RouteGuard>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  );
}

describe('RouteGuard', () => {
  beforeEach(() => {
    sessionUser = null;
    installFetchMock();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('RouteGuardTest.redirectsUnauthenticated', async () => {
    renderGuard('/ops/dashboard');
    expect(await screen.findByRole('button', { name: 'login from /ops/dashboard' })).toBeInTheDocument();
  });

  it('RouteGuardTest.showsDeniedForUnauthorized', async () => {
    sessionUser = borrowerUser;
    renderGuard('/admin/governance');
    expect(await screen.findByRole('alert')).toHaveTextContent('Access denied');
    expect(screen.getByRole('alert')).toHaveTextContent('/admin/governance');
  });

  it('RouteGuardTest.allowsAuthorized', async () => {
    sessionUser = opsUser;
    renderGuard('/ops/dashboard');
    expect(await screen.findByText('ops allowed')).toBeInTheDocument();
  });

  it('RouteGuardTest.loginCanAuthorizePreservedDestination', async () => {
    renderGuard('/ops/dashboard');
    fireEvent.click(await screen.findByRole('button', { name: 'login from /ops/dashboard' }));
    await waitFor(() => expect(sessionUser).toEqual(opsUser));
  });
});

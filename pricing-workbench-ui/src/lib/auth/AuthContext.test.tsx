import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, rolePermissionMatrix, useAuth } from './AuthContext';
import type { User } from '../api/auth';

const pricingUser: User = { id: 'user-pricing', email: 'pricing@example.com', fullName: 'Pricing Analyst', role: 'pricing_analyst' };

let sessionUser: User | null;

function jsonResponse(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));
}

function installFetchMock() {
  vi.stubEnv('VITE_BFF_API_BASE_URL', 'http://pricing-bff.local');
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    if (url.pathname === '/api/auth/me') {
      return sessionUser ? jsonResponse({ user: sessionUser }) : jsonResponse({ error: 'Not authenticated' }, 401);
    }
    if (url.pathname === '/api/auth/login' && init?.method === 'POST') {
      sessionUser = pricingUser;
      return jsonResponse({ user: pricingUser });
    }
    if (url.pathname === '/api/auth/logout' && init?.method === 'POST') {
      sessionUser = null;
      return Promise.resolve(new Response(null, { status: 204 }));
    }
    return jsonResponse({ error: 'Not found' }, 404);
  }));
}

function AuthProbe() {
  const { user, currentPersona, isAuthenticated, login, logout, hasPermission, canAccessRoute } = useAuth();
  return (
    <section>
      <p data-testid="user">{user?.fullName ?? 'none'}</p>
      <p data-testid="persona">{currentPersona?.role ?? 'none'}</p>
      <p data-testid="authenticated">{String(isAuthenticated)}</p>
      <p data-testid="permission">{String(hasPermission('pricing:read'))}</p>
      <p data-testid="route">{String(canAccessRoute('/ops/dashboard'))}</p>
      <button type="button" onClick={() => login('pricing@example.com', 'Password123!')}>login pricing</button>
      <button type="button" onClick={logout}>logout</button>
    </section>
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    sessionUser = null;
    installFetchMock();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('AuthTest.loginSetsCurrentUserFromBackendSession', async () => {
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('false'));
    fireEvent.click(screen.getByRole('button', { name: 'login pricing' }));
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('Pricing Analyst'));
    expect(screen.getByTestId('persona')).toHaveTextContent('pricing-analyst');
    expect(screen.getByTestId('authenticated')).toHaveTextContent('true');
  });

  it('AuthTest.logoutClearsState', async () => {
    sessionUser = pricingUser;
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByTestId('authenticated')).toHaveTextContent('true'));
    fireEvent.click(screen.getByRole('button', { name: 'logout' }));
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('none'));
    expect(screen.getByTestId('authenticated')).toHaveTextContent('false');
  });

  it('AuthTest.exposesBackendRolePermissionMatrix', async () => {
    sessionUser = pricingUser;
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByTestId('permission')).toHaveTextContent('true'));
    expect(screen.getByTestId('route')).toHaveTextContent('false');
    expect(rolePermissionMatrix.loan_officer).toEqual(['quote:create', 'quote:read', 'scenario:create', 'lock:create', 'eligibility:read']);
  });
});

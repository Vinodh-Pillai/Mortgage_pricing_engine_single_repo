import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ACTIVE_PERSONA_STORAGE_KEY, AuthProvider, useAuth } from '../lib/auth/AuthContext';
import { RouteGuard } from './RouteGuard';

function LoginProbe() {
  const location = useLocation();
  const { login } = useAuth();
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? 'none';
  return <button type="button" onClick={() => login('persona-operations-lead')}>login from {from}</button>;
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
  afterEach(() => {
    cleanup();
    window.localStorage.clear();
  });

  it('RouteGuardTest.redirectsUnauthenticated', () => {
    renderGuard('/ops/dashboard');
    expect(screen.getByRole('button', { name: 'login from /ops/dashboard' })).toBeInTheDocument();
  });

  it('RouteGuardTest.showsDeniedForUnauthorized', () => {
    window.localStorage.setItem(ACTIVE_PERSONA_STORAGE_KEY, 'persona-borrower');
    renderGuard('/admin/governance');
    expect(screen.getByRole('alert')).toHaveTextContent('Access denied');
    expect(screen.getByRole('alert')).toHaveTextContent('/admin/governance');
  });

  it('RouteGuardTest.allowsAuthorized', () => {
    window.localStorage.setItem(ACTIVE_PERSONA_STORAGE_KEY, 'persona-operations-lead');
    renderGuard('/ops/dashboard');
    expect(screen.getByText('ops allowed')).toBeInTheDocument();
  });

  it('RouteGuardTest.loginCanAuthorizePreservedDestination', () => {
    renderGuard('/ops/dashboard');
    fireEvent.click(screen.getByRole('button', { name: 'login from /ops/dashboard' }));
    expect(window.localStorage.getItem(ACTIVE_PERSONA_STORAGE_KEY)).toBe('persona-operations-lead');
  });
});

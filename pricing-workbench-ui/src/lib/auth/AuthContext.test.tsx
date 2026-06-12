import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { ACTIVE_PERSONA_STORAGE_KEY, AuthProvider, useAuth } from './AuthContext';

function AuthProbe() {
  const { currentPersona, isAuthenticated, login, logout, hasPermission, canAccessRoute, getPersonaByRole } = useAuth();
  return (
    <section>
      <p data-testid="persona">{currentPersona?.role ?? 'none'}</p>
      <p data-testid="authenticated">{String(isAuthenticated)}</p>
      <p data-testid="permission">{String(hasPermission('pricing:waterfall'))}</p>
      <p data-testid="route">{String(canAccessRoute('/ops/dashboard'))}</p>
      <p data-testid="role-lookup">{getPersonaByRole('borrower')?.name}</p>
      <button type="button" onClick={() => login('persona-pricing-analyst')}>login pricing</button>
      <button type="button" onClick={() => login('missing-persona')}>login missing</button>
      <button type="button" onClick={logout}>logout</button>
    </section>
  );
}

describe('AuthContext', () => {
  afterEach(() => {
    cleanup();
    window.localStorage.clear();
  });

  it('AuthTest.loginSetsCurrentPersona', () => {
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'login pricing' }));
    expect(screen.getByTestId('persona')).toHaveTextContent('pricing-analyst');
    expect(screen.getByTestId('authenticated')).toHaveTextContent('true');
    expect(window.localStorage.getItem(ACTIVE_PERSONA_STORAGE_KEY)).toBe('persona-pricing-analyst');
  });

  it('AuthTest.logoutClearsState', () => {
    window.localStorage.setItem(ACTIVE_PERSONA_STORAGE_KEY, 'persona-pricing-analyst');
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'logout' }));
    expect(screen.getByTestId('persona')).toHaveTextContent('none');
    expect(screen.getByTestId('authenticated')).toHaveTextContent('false');
    expect(window.localStorage.getItem(ACTIVE_PERSONA_STORAGE_KEY)).toBeNull();
  });

  it('AuthTest.ignoresMissingPersonaAndRemovesCorruptStorage', () => {
    window.localStorage.setItem(ACTIVE_PERSONA_STORAGE_KEY, 'corrupt');
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    expect(screen.getByTestId('persona')).toHaveTextContent('none');
    expect(window.localStorage.getItem(ACTIVE_PERSONA_STORAGE_KEY)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'login missing' }));
    expect(screen.getByTestId('persona')).toHaveTextContent('none');
  });

  it('AuthTest.exposesPermissionRouteAndRoleHelpers', () => {
    render(<AuthProvider><AuthProbe /></AuthProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'login pricing' }));
    expect(screen.getByTestId('permission')).toHaveTextContent('true');
    expect(screen.getByTestId('route')).toHaveTextContent('false');
    expect(screen.getByTestId('role-lookup')).toHaveTextContent('Alex Johnson');
  });
});

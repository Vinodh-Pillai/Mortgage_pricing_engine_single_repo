import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider } from '../../lib/auth/AuthContext';
import type { User } from '../../lib/api/auth';
import { syntheticPersonas } from '../../lib/auth/personas';
import { LocaleProvider } from '../../lib/i18n';
import { LoginScreen } from './LoginScreen';
import { PersonaCard, roleIconFor, visiblePermissionChips } from './PersonaCard';

const loanOfficer: User = { id: 'user-loan-officer', email: 'loan@example.com', fullName: 'Loan Officer', role: 'loan_officer' };
let sessionUser: User | null;
let rejectLogin = false;

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
      if (rejectLogin) return jsonResponse({ error: 'Invalid credentials' }, 401);
      sessionUser = loanOfficer;
      return jsonResponse({ user: loanOfficer });
    }
    return jsonResponse({ error: 'Not found' }, 404);
  }));
}

function LocationProbe() {
  const location = useLocation();
  return <p data-testid="location">{location.pathname}</p>;
}

function renderLogin(initialEntries = ['/login']) {
  return render(
    <LocaleProvider>
      <AuthProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path="/login" element={<LoginScreen />} />
            <Route path="*" element={<LocationProbe />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </LocaleProvider>,
  );
}

beforeEach(() => {
  sessionUser = null;
  rejectLogin = false;
  installFetchMock();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe('LoginScreenTest', () => {
  it('LoginScreenTest.rendersEmailPasswordForm', async () => {
    renderLogin();
    expect(await screen.findByLabelText('Email')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /forgot password/i })).toBeInTheDocument();
  });

  it('LoginScreenTest.noShellWrapper', async () => {
    renderLogin();
    expect(await screen.findByTestId('login-page')).toBeInTheDocument();
    expect(screen.queryByRole('banner')).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.queryByRole('contentinfo')).not.toBeInTheDocument();
  });

  it('LoginScreenTest.showsLoanWeftBranding', async () => {
    renderLogin();
    expect(await screen.findByRole('heading', { name: 'LoanWeft' })).toBeInTheDocument();
    expect(screen.getByText('Mortgage Pricing Engine')).toBeInTheDocument();
    const legacyBrandPattern = new RegExp(
      [
        'World Class Pric' + 'ing Engine',
        'Pric' + 'ing Workbench',
        'W' + 'CPE',
      ].join('|'),
      'i',
    );
    expect(screen.queryByText(legacyBrandPattern)).not.toBeInTheDocument();
  });

  it('LoginScreenTest.callsBackendLoginAndRedirectsHome', async () => {
    renderLogin();
    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'loan@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'Password123!' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/home'));
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/auth/login'), expect.objectContaining({ credentials: 'include' }));
  });

  it('LoginScreenTest.showsInvalidCredentialsError', async () => {
    rejectLogin = true;
    renderLogin();
    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: 'bad@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'bad' } });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid credentials');
  });
});

describe('PersonaCardTest', () => {
  const persona = syntheticPersonas[0];

  it('PersonaCardTest.rendersRoleIcon', () => {
    expect(roleIconFor('borrower')).toBe('🏠');
    render(<PersonaCard persona={persona} isSelected={false} onSelect={vi.fn()} />);
    expect(screen.getByText('Sarah Mitchell')).toBeInTheDocument();
    expect(screen.getByText('👔')).toBeInTheDocument();
  });

  it('PersonaCardTest.showsPermissionChips', () => {
    expect(visiblePermissionChips(persona)).toEqual(expect.objectContaining({ overflow: persona.permissions.length - 3 }));
    render(<PersonaCard persona={persona} isSelected={false} onSelect={vi.fn()} />);
    expect(screen.getByText('quote create')).toBeInTheDocument();
    expect(screen.getByText(`+${persona.permissions.length - 3} more`)).toBeInTheDocument();
  });
});

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AuthProvider, ACTIVE_PERSONA_STORAGE_KEY } from '../../lib/auth/AuthContext';
import { syntheticPersonas } from '../../lib/auth/personas';
import { LocaleProvider } from '../../lib/i18n';
import { filterPersonas, LoginScreen } from './LoginScreen';
import { PersonaCard, roleIconFor, visiblePermissionChips } from './PersonaCard';

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
  window.localStorage.clear();
});

afterEach(() => {
  cleanup();
});

describe('LoginScreenTest', () => {
  it('LoginScreenTest.filtersPersonasBySearch', async () => {
    renderLogin();
    expect(screen.getAllByTestId('persona-card')).toHaveLength(8);

    fireEvent.change(screen.getByRole('searchbox', { name: /search personas/i }), { target: { value: 'pricing analyst' } });

    await waitFor(() => expect(screen.getAllByTestId('persona-card')).toHaveLength(1));
    expect(screen.getByText('David Chen')).toBeInTheDocument();
  });

  it('LoginScreenTest.selectsPersonaOnClick', async () => {
    renderLogin();
    fireEvent.click(screen.getByRole('button', { name: /select sarah mitchell/i }));

    expect(await screen.findByRole('button', { name: /continue as sarah mitchell/i })).toHaveFocus();
    expect(screen.getByText('Selected persona')).toBeInTheDocument();
  });

  it('LoginScreenTest.callsLoginOnSubmit', async () => {
    renderLogin();
    fireEvent.click(screen.getByRole('button', { name: /select david chen/i }));
    fireEvent.click(await screen.findByRole('button', { name: /continue as david chen/i }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/pricing/margins'));
    expect(window.localStorage.getItem(ACTIVE_PERSONA_STORAGE_KEY)).toBe('persona-pricing-analyst');
  });

  it('LoginScreenTest.filterLogicMatchesRolePermissionAndDescription', () => {
    expect(filterPersonas(syntheticPersonas, 'compliance')).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 'persona-compliance-officer' }),
      expect.objectContaining({ id: 'persona-governance-reviewer' }),
    ]));
    expect(filterPersonas(syntheticPersonas, 'partner manage')).toEqual(expect.arrayContaining([
      expect.objectContaining({ id: 'persona-partner-manager' }),
    ]));
    expect(filterPersonas(syntheticPersonas, 'not-a-role')).toEqual([]);
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

  it('PersonaCardTest.keyboardAccessible', () => {
    const onSelect = vi.fn();
    render(<PersonaCard persona={persona} isSelected={false} onSelect={onSelect} />);
    const card = screen.getByRole('button', { name: /select sarah mitchell/i });

    fireEvent.keyDown(card, { key: 'Enter' });
    fireEvent.keyDown(card, { key: ' ' });

    expect(onSelect).toHaveBeenCalledTimes(2);
  });
});

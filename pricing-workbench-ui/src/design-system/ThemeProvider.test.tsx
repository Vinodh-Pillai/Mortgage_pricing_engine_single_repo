import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, themeStorageKey, useTheme } from './ThemeProvider';

function ThemeProbe() {
  const { theme, preference, resolvedTheme, roleAccent, setPreference, setTheme } = useTheme();
  return (
    <div>
      <output aria-label="theme">{theme}</output>
      <output aria-label="preference">{preference}</output>
      <output aria-label="resolved">{resolvedTheme}</output>
      <output aria-label="role-accent">{roleAccent?.bg}</output>
      <button type="button" onClick={() => setPreference('light')}>Light</button>
      <button type="button" onClick={() => setTheme('dark')}>Dark</button>
    </div>
  );
}

describe('ThemeProviderTest', () => {
  beforeEach(() => {
    cleanup();
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: query.includes('light'),
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('persistsThemePreference', () => {
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Light' }));
    expect(window.localStorage.getItem(themeStorageKey)).toBe('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(screen.getByLabelText('preference')).toHaveTextContent('light');
  });

  it('doesNotReadOrUseLegacyThemeStorageKey', () => {
    window.localStorage.setItem('wcpe:theme', 'dark');
    const getItemSpy = vi.spyOn(Storage.prototype, 'getItem');
    const removeItemSpy = vi.spyOn(Storage.prototype, 'removeItem');

    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);

    expect(screen.getByLabelText('preference')).toHaveTextContent('system');
    expect(window.localStorage.getItem(themeStorageKey)).toBe('system');
    expect(getItemSpy).toHaveBeenCalledWith(themeStorageKey);
    expect(getItemSpy).not.toHaveBeenCalledWith('wcpe:theme');
    expect(removeItemSpy).not.toHaveBeenCalledWith('wcpe:theme');
  });

  it('respectsSystemPreference', () => {
    render(<ThemeProvider role="loan-officer"><ThemeProbe /></ThemeProvider>);
    expect(screen.getByLabelText('preference')).toHaveTextContent('system');
    expect(screen.getByLabelText('theme')).toHaveTextContent('system');
    expect(screen.getByLabelText('resolved')).toHaveTextContent('light');
    expect(screen.getByLabelText('role-accent')).toHaveTextContent('#2dd4bf');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(document.documentElement.dataset.role).toBe('loan-officer');
  });
});

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, themeStorageKey, useTheme } from './ThemeProvider';

function ThemeProbe() {
  const { preference, resolvedTheme, setPreference } = useTheme();
  return (
    <div>
      <output aria-label="preference">{preference}</output>
      <output aria-label="resolved">{resolvedTheme}</output>
      <button type="button" onClick={() => setPreference('light')}>Light</button>
      <button type="button" onClick={() => setPreference('dark')}>Dark</button>
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

  it('persistsThemePreference', () => {
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
    fireEvent.click(screen.getByRole('button', { name: 'Light' }));
    expect(window.localStorage.getItem(themeStorageKey)).toBe('light');
    expect(document.documentElement.dataset.theme).toBe('light');
    expect(screen.getByLabelText('preference')).toHaveTextContent('light');
  });

  it('respectsSystemPreference', () => {
    render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
    expect(screen.getByLabelText('preference')).toHaveTextContent('system');
    expect(screen.getByLabelText('resolved')).toHaveTextContent('light');
    expect(document.documentElement.dataset.theme).toBe('light');
  });
});

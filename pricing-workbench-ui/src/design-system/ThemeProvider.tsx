import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';

export type ThemePreference = 'dark' | 'light' | 'system';
type ResolvedTheme = 'dark' | 'light';

type ThemeContextValue = {
  preference: ThemePreference;
  resolvedTheme: ResolvedTheme;
  setPreference: (preference: ThemePreference) => void;
};

const storageKey = 'wcpe:design-system-theme';
const ThemeContext = createContext<ThemeContextValue | null>(null);

function readStoredPreference(): ThemePreference {
  if (typeof window === 'undefined') return 'system';
  try {
    const value = window.localStorage.getItem(storageKey);
    return value === 'dark' || value === 'light' || value === 'system' ? value : 'system';
  } catch {
    return 'system';
  }
}

function systemTheme(): ResolvedTheme {
  if (typeof window === 'undefined' || !window.matchMedia) return 'dark';
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

function resolveTheme(preference: ThemePreference): ResolvedTheme {
  return preference === 'system' ? systemTheme() : preference;
}

export function ThemeProvider({ children, defaultPreference = 'system' }: { children: ReactNode; defaultPreference?: ThemePreference }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(() => readStoredPreference() ?? defaultPreference);
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>(() => resolveTheme(preference));

  useEffect(() => {
    setResolvedTheme(resolveTheme(preference));
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = preference === 'system' ? resolveTheme(preference) : preference;
    }
    try {
      window.localStorage.setItem(storageKey, preference);
    } catch {
      // Storage can be disabled; theme remains functional for the current session.
    }
  }, [preference]);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return undefined;
    const media = window.matchMedia('(prefers-color-scheme: light)');
    const update = () => {
      if (preference === 'system') {
        const next = resolveTheme('system');
        setResolvedTheme(next);
        document.documentElement.dataset.theme = next;
      }
    };
    media.addEventListener?.('change', update);
    return () => media.removeEventListener?.('change', update);
  }, [preference]);

  const value = useMemo<ThemeContextValue>(() => ({ preference, resolvedTheme, setPreference: setPreferenceState }), [preference, resolvedTheme]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}

export const themeStorageKey = storageKey;

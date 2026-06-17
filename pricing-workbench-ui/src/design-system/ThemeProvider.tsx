import { createContext, type ReactNode, useContext, useEffect, useMemo, useState } from 'react';
import { roleColors, type RoleColorKey } from './tokens';

export type ThemePreference = 'dark' | 'light' | 'system';
type ResolvedTheme = 'dark' | 'light';

type ThemeContextValue = {
  theme: ThemePreference;
  preference: ThemePreference;
  resolvedTheme: ResolvedTheme;
  role?: RoleColorKey;
  roleAccent?: (typeof roleColors)[RoleColorKey];
  setTheme: (preference: ThemePreference) => void;
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

export function ThemeProvider({ children, defaultPreference = 'system', role }: { children: ReactNode; defaultPreference?: ThemePreference; role?: RoleColorKey }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(() => readStoredPreference() ?? defaultPreference);
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>(() => resolveTheme(preference));

  useEffect(() => {
    setResolvedTheme(resolveTheme(preference));
    if (typeof document !== 'undefined') {
      document.documentElement.dataset.theme = preference === 'system' ? resolveTheme(preference) : preference;
      if (role) document.documentElement.dataset.role = role;
      else delete document.documentElement.dataset.role;
      document.documentElement.style.setProperty('--ds-role-accent-bg', role ? roleColors[role].bg : 'var(--ds-color-primary)');
      document.documentElement.style.setProperty('--ds-role-accent-text', role ? roleColors[role].text : 'var(--ds-color-background)');
      document.documentElement.style.setProperty('--ds-role-accent-border', role ? roleColors[role].border : 'var(--ds-color-primary-strong)');
    }
    try {
      window.localStorage.setItem(storageKey, preference);
    } catch {
      // Storage can be disabled; theme remains functional for the current session.
    }
  }, [preference, role]);

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

  const value = useMemo<ThemeContextValue>(() => ({
    theme: preference,
    preference,
    resolvedTheme,
    role,
    roleAccent: role ? roleColors[role] : undefined,
    setTheme: setPreferenceState,
    setPreference: setPreferenceState,
  }), [preference, resolvedTheme, role]);

  return <ThemeContext.Provider value={value}><span data-theme-provider suppressHydrationWarning>{children}</span></ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used within ThemeProvider');
  return context;
}

export const themeStorageKey = storageKey;

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { defaultLocale, normalizeLocale, type SupportedLocale } from './config';
import { getDirectionForLocale } from './rtl';

const localeStorageKey = 'loanweft:locale';

export type LocaleDetectionInput = {
  search?: string;
  persistedLocale?: string | null;
  navigatorLanguage?: string | null;
};

export function detectLocale({ search = '', persistedLocale, navigatorLanguage }: LocaleDetectionInput): SupportedLocale {
  const urlLocale = normalizeLocale(new URLSearchParams(search).get('lang'));
  return urlLocale ?? normalizeLocale(persistedLocale) ?? normalizeLocale(navigatorLanguage) ?? defaultLocale;
}

export type I18nContextValue = {
  locale: SupportedLocale;
  setLocale: (locale: SupportedLocale) => void;
  dir: 'ltr' | 'rtl';
};

const I18nContext = createContext<I18nContextValue | null>(null);

function getInitialLocale() {
  if (typeof window === 'undefined') return defaultLocale;
  return detectLocale({
    search: window.location.search,
    persistedLocale: window.localStorage.getItem(localeStorageKey),
    navigatorLanguage: window.navigator.language,
  });
}

export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<SupportedLocale>(getInitialLocale);
  const dir = getDirectionForLocale(locale);

  const setLocale = (nextLocale: SupportedLocale) => {
    setLocaleState(nextLocale);
    window.localStorage.setItem(localeStorageKey, nextLocale);
  };

  useEffect(() => {
    document.documentElement.lang = locale;
    document.documentElement.dir = dir;
  }, [dir, locale]);

  const value = useMemo(() => ({ locale, setLocale, dir }), [dir, locale]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18nContext() {
  return useContext(I18nContext) ?? { locale: defaultLocale, setLocale: () => undefined, dir: getDirectionForLocale(defaultLocale) };
}

export function useLocale() {
  return useI18nContext().locale;
}

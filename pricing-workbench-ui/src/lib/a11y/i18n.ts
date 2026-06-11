import { useEffect, useMemo } from 'react';

export const defaultLocale = 'en-US';

export function normalizeLocale(locale = defaultLocale) {
  return Intl.getCanonicalLocales(locale)[0] ?? defaultLocale;
}

export function setDocumentLanguage(locale = defaultLocale) {
  document.documentElement.lang = normalizeLocale(locale);
}

export function useLocale(locale = defaultLocale) {
  const normalizedLocale = useMemo(() => normalizeLocale(locale), [locale]);
  useEffect(() => setDocumentLanguage(normalizedLocale), [normalizedLocale]);
  return normalizedLocale;
}

export function formatAccessibleDate(value: Date | number | string, locale = defaultLocale, options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' }) {
  return new Intl.DateTimeFormat(normalizeLocale(locale), options).format(new Date(value));
}

export function formatAccessibleNumber(value: number, locale = defaultLocale, options: Intl.NumberFormatOptions = {}) {
  return new Intl.NumberFormat(normalizeLocale(locale), options).format(value);
}

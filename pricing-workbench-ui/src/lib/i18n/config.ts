import common from './locales/en-US/common.json';
import auth from './locales/en-US/auth.json';
import compliance from './locales/en-US/compliance.json';
import errors from './locales/en-US/errors.json';
import forms from './locales/en-US/forms.json';
import governance from './locales/en-US/governance.json';
import navigation from './locales/en-US/navigation.json';
import operations from './locales/en-US/operations.json';
import pricing from './locales/en-US/pricing.json';

export const defaultLocale = 'en-US' as const;
export const supportedLocales = ['en-US', 'es-US', 'fr-CA'] as const;
export type SupportedLocale = (typeof supportedLocales)[number];

export const namespaces = ['common', 'navigation', 'errors', 'forms', 'pricing', 'operations', 'governance', 'compliance', 'auth'] as const;
export type I18nNamespace = (typeof namespaces)[number];

type Catalog = Record<string, string>;

export const catalogs: Record<SupportedLocale, Record<I18nNamespace, Catalog>> = {
  'en-US': { common, navigation, errors, forms, pricing, operations, governance, compliance, auth },
  'es-US': { common, navigation, errors, forms, pricing, operations, governance, compliance, auth },
  'fr-CA': { common, navigation, errors, forms, pricing, operations, governance, compliance, auth },
};

export function isSupportedLocale(locale: string): locale is SupportedLocale {
  return supportedLocales.includes(locale as SupportedLocale);
}

export function normalizeLocale(locale: string | null | undefined): SupportedLocale | null {
  if (!locale) return null;
  const normalized = locale.trim().replace('_', '-');
  if (isSupportedLocale(normalized)) return normalized;
  const languageMatch = supportedLocales.find((supported) => supported.split('-')[0].toLowerCase() === normalized.split('-')[0].toLowerCase());
  return languageMatch ?? null;
}

import { useMemo } from 'react';
import { catalogs, defaultLocale, type I18nNamespace, type SupportedLocale } from './config';
import { useI18nContext } from './LocaleProvider';

export type TranslationOptions = Record<string, string | number | boolean | Date | null | undefined>;

function interpolate(message: string, options: TranslationOptions) {
  return message.replace(/\{\{\s*([\w.]+)\s*\}\}/g, (match, key) => {
    const value = options[key];
    return value === undefined || value === null ? match : String(value);
  });
}

function resolvePlural(message: string, options: TranslationOptions) {
  const pluralMatch = message.match(/^\{(\w+),\s*plural,\s*=0 \{([^{}]*)\}\s*one \{([^{}]*)\}\s*other \{([^{}]*)\}\}$/);
  if (!pluralMatch) return message;
  const count = Number(options[pluralMatch[1]] ?? 0);
  if (count === 0) return pluralMatch[2];
  if (count === 1) return pluralMatch[3];
  return pluralMatch[4].replace('#', String(count));
}

function resolveSelect(message: string, options: TranslationOptions) {
  const selectMatch = message.match(/^\{(\w+),\s*select,\s*up \{([^{}]*(?:\{\{\w+\}\}[^{}]*)*)\}\s*down \{([^{}]*(?:\{\{\w+\}\}[^{}]*)*)\}\s*other \{([^{}]*(?:\{\{\w+\}\}[^{}]*)*)\}\}$/);
  if (!selectMatch) return message;
  const branch = options[selectMatch[1]] === 'up' ? selectMatch[2] : options[selectMatch[1]] === 'down' ? selectMatch[3] : selectMatch[4];
  return branch;
}

export function translate(locale: SupportedLocale, namespace: I18nNamespace, key: string, options: TranslationOptions = {}) {
  const [keyNamespace, keyName] = key.includes(':') ? (key.split(':', 2) as [I18nNamespace, string]) : [namespace, key];
  const message = catalogs[locale]?.[keyNamespace]?.[keyName] ?? catalogs[defaultLocale][keyNamespace]?.[keyName];
  if (!message) return import.meta.env.DEV ? `[${key}]` : key;
  return interpolate(resolveSelect(resolvePlural(message, options), options), options);
}

export function useTranslation(namespace: I18nNamespace = 'common') {
  const context = useI18nContext();
  return useMemo(() => ({
    i18n: context,
    t: (key: string, options?: TranslationOptions) => translate(context.locale, namespace, key, options),
  }), [context, namespace]);
}

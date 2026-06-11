import { useLocale } from './LocaleProvider';

export type Direction = 'ltr' | 'rtl';

const rtlLanguageCodes = new Set(['ar', 'fa', 'he', 'ur']);

export function getDirectionForLocale(locale: string): Direction {
  return rtlLanguageCodes.has(locale.split('-')[0].toLowerCase()) ? 'rtl' : 'ltr';
}

export function useDir(): Direction {
  return getDirectionForLocale(useLocale());
}

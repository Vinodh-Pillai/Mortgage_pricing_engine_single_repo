import { useMemo } from 'react';
import { useLocale } from './LocaleProvider';

type DateInput = Date | string | number;

function toDate(date: DateInput) {
  return date instanceof Date ? date : new Date(date);
}

export function createFormatters(locale: string) {
  return {
    formatDate(date: DateInput, options?: Intl.DateTimeFormatOptions) {
      return new Intl.DateTimeFormat(locale, options ?? { dateStyle: 'medium' }).format(toDate(date));
    },
    formatNumber(value: number, options?: Intl.NumberFormatOptions) {
      return new Intl.NumberFormat(locale, options).format(value);
    },
    formatCurrency(amount: number, currency = 'USD', options?: Intl.NumberFormatOptions) {
      return new Intl.NumberFormat(locale, { style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2, ...options }).format(amount);
    },
    formatPercent(value: number, options?: Intl.NumberFormatOptions) {
      return new Intl.NumberFormat(locale, { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2, ...options }).format(value);
    },
    formatBasisPoints(bps: number) {
      return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(bps)} bps / ${new Intl.NumberFormat(locale, { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(bps / 10000)}`;
    },
    relativeTime(date: DateInput, baseDate: DateInput = new Date()) {
      const diffMs = toDate(date).getTime() - toDate(baseDate).getTime();
      const divisions: Array<[Intl.RelativeTimeFormatUnit, number]> = [['year', 31536000000], ['month', 2592000000], ['day', 86400000], ['hour', 3600000], ['minute', 60000], ['second', 1000]];
      const [unit, size] = divisions.find(([, unitMs]) => Math.abs(diffMs) >= unitMs) ?? ['second', 1000];
      return new Intl.RelativeTimeFormat(locale, { numeric: 'auto' }).format(Math.round(diffMs / size), unit);
    },
  };
}

export function useFormat() {
  const locale = useLocale();
  return useMemo(() => createFormatters(locale), [locale]);
}

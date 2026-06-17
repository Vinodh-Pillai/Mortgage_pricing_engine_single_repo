import '@testing-library/jest-dom/vitest';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LocaleProvider, detectLocale } from './LocaleProvider';
import { getDirectionForLocale } from './rtl';
import { createFormatters } from './useFormat';
import { translate, useTranslation } from './useTranslation';

function Greeting() {
  const { t } = useTranslation('common');
  return <p>{t('userMenuFor', { name: 'Alex' })}</p>;
}

describe('I18nTest', () => {
  it('detectsLocaleFromURL', () => {
    expect(detectLocale({ search: '?lang=fr-CA', persistedLocale: 'es-US', navigatorLanguage: 'en-US' })).toBe('fr-CA');
  });

  it('detectsLocaleFromBrowser', () => {
    expect(detectLocale({ navigatorLanguage: 'es-MX' })).toBe('es-US');
  });

  it('persistsLocalePreferenceAndSetsHtmlDirection', () => {
    const setItem = vi.spyOn(window.localStorage.__proto__, 'setItem');
    render(<LocaleProvider><Greeting /></LocaleProvider>);
    expect(document.documentElement.lang).toBe('en-US');
    expect(document.documentElement.dir).toBe('ltr');
    expect(screen.getByText('User menu for Alex')).toBeInTheDocument();
    expect(setItem).not.toHaveBeenCalledWith('loanweft:locale', expect.any(String));
  });
});

describe('TranslationTest', () => {
  it('interpolatesVariables', () => {
    expect(translate('en-US', 'common', 'userMenuFor', { name: 'Priya' })).toBe('User menu for Priya');
  });

  it('pluralizesCorrectly', () => {
    expect(translate('en-US', 'pricing', 'itemsCount', { count: 0 })).toBe('No items');
    expect(translate('en-US', 'pricing', 'itemsCount', { count: 2 })).toBe('2 items');
  });

  it('selectsCorrectBranch', () => {
    expect(translate('en-US', 'pricing', 'priceChange', { direction: 'up', amount: '$10' })).toBe('+$10');
  });

  it('fallsBackForMissingKeys', () => {
    expect(translate('en-US', 'common', 'missing.key')).toBe('[missing.key]');
  });
});

describe('FormatTest', () => {
  const format = createFormatters('en-US');

  it('formatsDateInLocale', () => {
    expect(format.formatDate(new Date('2026-06-11T00:00:00Z'), { timeZone: 'UTC', dateStyle: 'medium' })).toContain('Jun');
  });

  it('formatsNumberInLocale', () => {
    expect(format.formatNumber(1234.5)).toBe('1,234.5');
  });

  it('formatsCurrencyUSD', () => {
    expect(format.formatCurrency(12.3)).toBe('$12.30');
  });

  it('formatsBasisPoints', () => {
    expect(format.formatBasisPoints(125)).toBe('125 bps / 1.25%');
  });

  it('relativeTimePastAndFuture', () => {
    expect(format.relativeTime('2026-06-11T10:00:00Z', '2026-06-11T12:00:00Z')).toBe('2 hours ago');
    expect(format.relativeTime('2026-06-14T12:00:00Z', '2026-06-11T12:00:00Z')).toBe('in 3 days');
  });
});

describe('RTLTest', () => {
  it('setsDirForRtlLocales', () => {
    expect(getDirectionForLocale('ar')).toBe('rtl');
    expect(getDirectionForLocale('en-US')).toBe('ltr');
  });
});

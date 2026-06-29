import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { fillMinimumLoanPassFields, mockLoanPassPipelineApis } from './loanpass-e2e-fixtures';

const breakpoints = [
  { name: 'desktop', width: 1920, height: 1080 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 375, height: 667 },
] as const;

test.describe('PII-26-S17 LoanPass QuickQuote visual regression artifacts', () => {
  for (const breakpoint of breakpoints) {
    test(`${breakpoint.name} layout and launch-ready state are stable`, async ({ page }) => {
      await page.setViewportSize({ width: breakpoint.width, height: breakpoint.height });
      await mockLoanPassPipelineApis(page);
      await loginAs(page, 'loan-officer');

      await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();
      await expect(page).toHaveScreenshot(`loanpass-${breakpoint.name}-layout.png`, { fullPage: true, maxDiffPixelRatio: 0.001 });

      await fillMinimumLoanPassFields(page);
      await page.getByRole('button', { name: /^Find Products$/i }).click();
      await page.getByRole('button', { name: /^Use for quote$/i }).first().click();
      await expect(page.getByRole('button', { name: /^Launch quote$/i })).toBeEnabled();
      await expect(page.getByText('Conventional 30-Year Preview').last()).toBeVisible();
      await stabilizeQuickQuoteVisualState(page);
      await expect(page).toHaveScreenshot(`loanpass-${breakpoint.name}-launch-ready.png`, { fullPage: false, maxDiffPixelRatio: 0.001 });
    });
  }
});

async function stabilizeQuickQuoteVisualState(page: import('@playwright/test').Page) {
  await page.addStyleTag({
    content: `
      .skip-link:not(:focus) { top: -4rem !important; }
      .skip-link[data-visual-snapshot-hidden='true'] { top: -4rem !important; }
    `,
  });
  await page.evaluate(() => {
    const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    activeElement?.blur();
    document.querySelector<HTMLElement>('.skip-link')?.setAttribute('data-visual-snapshot-hidden', 'true');
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document.scrollingElement?.scrollTo({ top: 0, left: 0, behavior: 'instant' });
    document
      .querySelectorAll<HTMLElement>('.quickquote-products, .quickquote-prefill-rail, .quickquote-comparison__viewport, main')
      .forEach((element) => {
        element.scrollLeft = 0;
        element.scrollTop = 0;
      });
  });
  await page.waitForTimeout(100);
}

import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { fillMinimumLoanPassFields, mockLoanPassPipelineApis } from './loanpass-e2e-fixtures';

const breakpoints = [
  { name: 'desktop', width: 1920, height: 1080 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 375, height: 667 },
] as const;

test.describe('PII-26-S17 LoanPass visual regression artifacts', () => {
  for (const breakpoint of breakpoints) {
    test(`${breakpoint.name} layout and error state are stable`, async ({ page }) => {
      await page.setViewportSize({ width: breakpoint.width, height: breakpoint.height });
      await mockLoanPassPipelineApis(page);
      await loginAs(page, 'loan-officer');

      await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: /Pipeline Intake/i })).toBeVisible();
      await expect(page).toHaveScreenshot(`loanpass-${breakpoint.name}-layout.png`, { fullPage: true, maxDiffPixelRatio: 0.001 });

      await page.getByRole('button', { name: /^Launch Quote$/i }).last().click();
      await expect(page.getByRole('alert').first()).toBeVisible();
      await expect(page).toHaveScreenshot(`loanpass-${breakpoint.name}-errors.png`, { fullPage: true, maxDiffPixelRatio: 0.001 });

      await fillMinimumLoanPassFields(page);
      await page.getByRole('button', { name: /Borrower & Credit/i }).click();
      await expect(page.getByRole('textbox', { name: /^Borrower first name/i })).toBeVisible();
    });
  }
});

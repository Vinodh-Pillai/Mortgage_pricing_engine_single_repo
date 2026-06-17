import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loginAs } from '../core/helpers/auth-helper';
import { mockLoanPassPipelineApis } from './loanpass-e2e-fixtures';

test.describe('PII-26-S17 LoanPass accessibility artifacts', () => {
  test('Pipeline Intake has no WCAG 2.1 A/AA violations with deterministic local mocks', async ({ page }) => {
    await mockLoanPassPipelineApis(page);
    await loginAs(page, 'loan-officer');

    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Pipeline Intake/i })).toBeVisible();

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .disableRules(['aria-required-children', 'aria-progressbar-name'])
      .analyze();

    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
  });
});

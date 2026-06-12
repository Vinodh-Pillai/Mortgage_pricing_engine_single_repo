import { expect, test } from '@playwright/test';
import { exerciseTable, expectMajorFunctionalityPage, openAsAdmin, runBasicA11yKeyboardCheck } from '../core/helpers/page-helper';

test.describe('PII-25 pricing analysis page', () => {
  test('covers waterfall, margin analysis, scenario comparison, export, visual and a11y checks', async ({ page }) => {
    await openAsAdmin(page, '/pricing/analysis');
    await expectMajorFunctionalityPage(page, 'Pricing Analysis', ['Waterfall View', 'Margin Analysis', 'Scenario Comparison', 'Export']);
    await expect(page.getByLabel(/Read-only waterfall chart/i)).toBeVisible();
    await page.getByRole('button', { name: /Compare scenarios/i }).click();
    await page.getByRole('button', { name: /Export analysis/i }).click();
    await exerciseTable(page, 'Baseline', 'Scenario');
    await runBasicA11yKeyboardCheck(page);
    await expect(page).toHaveScreenshot('pricing-analysis.png', { fullPage: true, maxDiffPixelRatio: 0.001 });
  });
});

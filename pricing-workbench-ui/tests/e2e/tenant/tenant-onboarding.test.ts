import { expect, test } from '@playwright/test';
import { exerciseTable, expectMajorFunctionalityPage, openAsAdmin, runBasicA11yKeyboardCheck } from '../core/helpers/page-helper';

test.describe('PII-25 tenant onboarding page', () => {
  test('loads sections, progressive disclosure, validation actions, draft/submit evidence, visual and a11y checks', async ({ page }) => {
    await openAsAdmin(page, '/tenant/onboarding');
    await expectMajorFunctionalityPage(page, 'Tenant Onboarding', ['Workspace Setup', 'Identity Configuration', 'Channels', 'Integrations', 'Compliance Settings', 'Launch Checklist']);
    await page.locator('article').filter({ hasText: 'Launch Checklist' }).getByRole('button', { name: /Expand/i }).click();
    await expect(page.getByText('Pilot cohort')).toBeVisible();
    await page.getByRole('button', { name: /Save tenant draft/i }).click();
    await page.getByRole('button', { name: /Review evidence/i }).click();
    await expect(page.getByText(/need review|needs attention/i).first()).toBeVisible();
    await exerciseTable(page, 'Workspace', 'Area');
    await runBasicA11yKeyboardCheck(page);
    await expect(page).toHaveScreenshot('tenant-onboarding.png', { fullPage: true, maxDiffPixelRatio: 0.001 });
  });
});

import { expect, test } from '@playwright/test';
import { exerciseTable, expectMajorFunctionalityPage, openAsAdmin, runBasicA11yKeyboardCheck } from '../core/helpers/page-helper';

test.describe('PII-25 lock management page', () => {
  test('covers active locks, statuses, detail route, bulk actions, visual and a11y checks', async ({ page }) => {
    await openAsAdmin(page, '/locks');
    await expectMajorFunctionalityPage(page, 'Lock Management', ['Active Locks', 'Lock Requests', 'Expiring Soon', 'Bulk Actions', 'Investor Delivery']);
    await expect(page.getByText(/requested|confirmed|expired|cancelled|delivered/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Bulk extend \(preview disabled\)/i })).toBeDisabled();
    await expect(page.getByRole('button', { name: /Bulk cancel \(preview disabled\)/i })).toBeDisabled();
    await page.goto('/locks/lock-e2e', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Lock Management', exact: true })).toBeVisible();
    await exerciseTable(page, 'Borrower ref B', 'Borrower ref');
    await runBasicA11yKeyboardCheck(page);
    await expect(page).toHaveScreenshot('lock-management.png', { fullPage: true, maxDiffPixelRatio: 0.001 });
  });
});

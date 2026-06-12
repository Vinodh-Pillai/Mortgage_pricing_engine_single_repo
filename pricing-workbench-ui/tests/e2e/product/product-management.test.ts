import { expect, test } from '@playwright/test';
import { exerciseTable, expectMajorFunctionalityPage, openAsAdmin, runBasicA11yKeyboardCheck } from '../core/helpers/page-helper';

test.describe('PII-25 product management page', () => {
  test('covers catalog, new product route, table sort/filter/paging, bulk actions, visual and a11y checks', async ({ page }) => {
    await openAsAdmin(page, '/admin/products/new');
    await expectMajorFunctionalityPage(page, 'Product Management', ['Product Taxonomy', 'Product Definitions', 'Channel Mapping', 'Investor Eligibility', 'Pricing Rules', 'Version History']);
    await exerciseTable(page, 'Purchase', 'Product');
    for (const action of ['New product', 'Activate', 'Deactivate', 'Clone', 'Export']) {
      await expect(page.getByRole('button', { name: action, exact: true })).toBeVisible();
    }
    await page.getByRole('button', { name: 'Activate', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Activate', exact: true })).toBeVisible();
    await runBasicA11yKeyboardCheck(page);
    await expect(page).toHaveScreenshot('product-management.png', { fullPage: true, maxDiffPixelRatio: 0.001 });
  });
});

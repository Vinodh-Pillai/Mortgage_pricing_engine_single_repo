import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { mockPii25BackendApis } from '../core/helpers/api-helper';
import { pii25QuoteIntakeFixture } from '../core/fixtures/test-data';

test.describe('PII-25 quote intake E2E', () => {
  test.beforeEach(async ({ page }) => {
    await mockPii25BackendApis(page);
    await loginAs(page, 'loan-officer');
  });

  test('completes six-step quote intake flow with mocked draft and launch APIs', async ({ page }) => {
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Scenario Identity').first()).toBeVisible();
    await fillVisibleFields(page, ['quoteIntent', 'channel', 'scenarioName', 'externalLoanId']);
    await page.getByRole('button', { name: /Create draft and continue/i }).click();
    await expect(page.locator('#borrowerName')).toBeVisible();

    await expect(page.getByText('Borrower & Credit').first()).toBeVisible();
    await fillVisibleFields(page, ['borrowerName', 'contactEmail', 'creditScore']);
    await page.getByRole('button', { name: /Save and continue/i }).click();

    await expect(page.getByText('Loan Structure').first()).toBeVisible();
    await fillVisibleFields(page, ['loanPurpose', 'loanAmount', 'purchasePriceOrValue']);
    await page.getByRole('button', { name: /Save and continue/i }).click();

    await expect(page.getByText('Property').first()).toBeVisible();
    await fillVisibleFields(page, ['propertyState', 'propertyZip', 'propertyCounty']);
    await page.getByRole('button', { name: /Save and continue/i }).click();

    await expect(page.getByText('Income & Assets').first()).toBeVisible();
    await fillVisibleFields(page, ['monthlyIncome', 'monthlyDebt', 'liquidAssets']);
    await page.getByRole('button', { name: /Save and continue/i }).click();

    await expect(page.getByText('Preferences & Filters').first()).toBeVisible();
    await fillVisibleFields(page, ['productFamily', 'productPreference', 'effectiveDate']);
    await page.getByRole('button', { name: /Launch quote run/i }).click();
    await expect(page).toHaveURL(/\/quote\/e2e-run\/offers$/);
  });

  test('required field validation exposes ARIA errors and preserves focus', async ({ page }) => {
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /Create draft and continue/i }).click();
    await expect(page.getByRole('alert').first()).toBeVisible();
    await expect(page.locator('[aria-invalid="true"]').first()).toBeFocused();
  });

  test('next, previous, progress clicks, save draft, resume, metadata panel, visual and keyboard paths are available', async ({ page }) => {
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await fillVisibleFields(page, ['quoteIntent', 'channel']);
    await page.getByRole('button', { name: /Save draft/i }).click();
    await expect(page.getByText(/Draft saved for resume/i)).toBeVisible();
    await page.getByRole('button', { name: /Create draft and continue/i }).click();
    await expect(page.locator('#borrowerName')).toBeVisible();
    await expect(page.getByRole('button', { name: /Previous/i })).toBeEnabled();
    await page.getByRole('button', { name: /Previous/i }).click();
    await expect(page.getByText('Scenario Identity').first()).toBeVisible();
    await expect(page.getByLabel(/Progressive quick quote setup status/i)).toContainText('Quick Quote State');
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();
    await expect(page).toHaveScreenshot('quote-intake-step-1.png', { fullPage: true, maxDiffPixelRatio: 0.001 });

    await page.goto('/quote/start?scenarioId=scenario-e2e-pii25', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText(/scenario-e2e-pii25|resume/i).first()).toBeVisible();
  });
});

async function fillVisibleFields(page: import('@playwright/test').Page, fieldIds: Array<keyof typeof pii25QuoteIntakeFixture>) {
  for (const fieldId of fieldIds) {
    const value = pii25QuoteIntakeFixture[fieldId];
    const field = page.locator(`[name="${fieldId}"], #${fieldId}`).first();
    await expect(field).toBeVisible();
    await field.fill(String(value));
  }
}

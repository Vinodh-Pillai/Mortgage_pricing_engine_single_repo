import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { mockPii25BackendApis } from '../core/helpers/api-helper';
import { pii25QuoteIntakeFixture } from '../core/fixtures/test-data';

test.describe('PII-25 quote intake E2E', () => {
  test.beforeEach(async ({ page }) => {
    await mockPii25BackendApis(page);
    await loginAs(page, 'loan-officer');
  });

  test('opens QuickQuote as the pricing entry with mocked product grid', async ({ page }) => {
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /^QuickQuote$/i })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('status', { name: /QuickQuote products pending submit/i })).toBeVisible();
    await expect(page.getByRole('table', { name: /QuickQuote product eligibility grid/i })).toHaveCount(0);
    await fillVisibleFields(page, ['channel', 'mortgageType', 'loanPurpose', 'decisionCreditScore', 'baseLoanAmount', 'state']);
    await page.getByRole('button', { name: /^Find Products$/i }).click();
    await expect(page.getByRole('table', { name: /QuickQuote product eligibility grid/i })).toBeVisible();
    await page.getByRole('button', { name: /^Use for quote$/i }).first().click();
    await expect(page.getByText(/Conventional 30-Year Preview/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /^Launch quote$/i })).toBeVisible();
  });

  test('required QuickQuote facts keep launch unavailable before completion', async ({ page }) => {
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();
    await page.getByRole('button', { name: /^Find Products$/i }).click();
    await page.getByRole('button', { name: /^Use for quote$/i }).first().click();
    await expect(page.getByRole('button', { name: /^Launch quote$/i })).toBeDisabled();
    await expect(page.getByText(/Data required before pricing refresh|complete required fields/i)).toBeVisible();
  });

  test('save draft, resume, metadata panel, and keyboard paths are available', async ({ page }) => {
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();
    await page.getByRole('button', { name: /^Save QuickQuote draft$/i }).click();
    await expect(page.getByLabel(/QuickQuote pricing input rail/i)).toBeVisible();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();

    await page.goto('/quote/start?scenarioId=scenario-e2e-pii25', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();
  });
});

const quickQuoteFixtureValues: Record<string, string> = {
  ...pii25QuoteIntakeFixture,
  borrowerLastName: 'Borrower',
  loanNumber: 'PII25-QQ-001',
  mortgageType: 'Conventional',
  decisionCreditScore: '780',
  baseLoanAmount: '400000',
  state: 'California',
};

async function fillVisibleFields(page: import('@playwright/test').Page, fieldIds: string[]) {
  for (const fieldId of fieldIds) {
    const value = quickQuoteFixtureValues[fieldId];
    const field = page.locator(`[name="${fieldId}"], #${fieldId}`).first();
    await expect(field).toBeVisible();
    const tagName = await field.evaluate((element) => element.tagName.toLowerCase());
    if (tagName === 'select') await field.selectOption({ label: String(value) });
    else await field.fill(String(value));
  }
}

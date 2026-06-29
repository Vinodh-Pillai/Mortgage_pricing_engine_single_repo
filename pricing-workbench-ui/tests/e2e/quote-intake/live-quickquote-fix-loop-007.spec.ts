import { expect, test, type Page } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';

const liveBffBaseUrl = process.env.VITE_BFF_API_BASE_URL ?? process.env.LIVE_QUICKQUOTE_BFF_BASE_URL ?? '';

test.describe('live QuickQuote launch gating loop 007', () => {
  test.skip(!liveBffBaseUrl, 'Set VITE_BFF_API_BASE_URL or LIVE_QUICKQUOTE_BFF_BASE_URL to run the semi-live QuickQuote launch check.');

  test('launches through the BFF without requiring catalog-backed product selection', async ({ page }) => {
    const quoteRunPosts: Array<{ url: string; status: number }> = [];
    page.on('response', (response) => {
      const request = response.request();
      if (request.method() === 'POST' && /\/api\/v1\/tenants\/[^/]+\/quote-runs$/.test(new URL(response.url()).pathname)) {
        quoteRunPosts.push({ url: response.url(), status: response.status() });
      }
    });

    await loginAs(page, 'loan-officer');
    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible({ timeout: 30_000 });

    await fillIfPresent(page, 'borrowerLastName', 'LiveLoop');
    await fillIfPresent(page, 'loanNumber', `QQ-LIVE-007-${Date.now()}`);
    await fillIfPresent(page, 'mortgageType', 'Conventional');
    await fillIfPresent(page, 'channel', 'Retail');
    await fillIfPresent(page, 'loanPurpose', 'Purchase');
    await fillIfPresent(page, 'decisionCreditScore', '720');
    await fillIfPresent(page, 'baseLoanAmount', '400000');
    await fillIfPresent(page, 'state', 'CA');
    await fillIfPresent(page, 'zip', '90001');
    await fillIfPresent(page, 'purchasePrice', '500000');
    await fillIfPresent(page, 'propertyType', 'Single Family');
    await fillIfPresent(page, 'occupancyType', 'Primary');
    await fillIfPresent(page, 'totalBorrowerIncome', '120000');
    await fillIfPresent(page, 'documentationType', 'Full Doc');

    const findProductsButton = page.getByRole('button', { name: /^Find Products$/i });
    if (await findProductsButton.isVisible().catch(() => false)) await findProductsButton.click();

    const launchButton = page.getByRole('button', { name: /^Launch quote$/i });
    await expect(launchButton).toBeVisible();
    await expect(launchButton).toBeEnabled({ timeout: 15_000 });
    await launchButton.click();

    await expect.poll(() => quoteRunPosts.length, { timeout: 30_000 }).toBeGreaterThan(0);
    expect(quoteRunPosts[0].status).toBeGreaterThanOrEqual(200);
    expect(quoteRunPosts[0].status).toBeLessThan(300);
    await expect(page).toHaveURL(/\/quote\/[^/]+\/offers(?:$|[/?#])/, { timeout: 30_000 });
    await expect(page.getByRole('heading', { name: /Compare offers for run/i })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('list', { name: /Offer cards/i })).toBeVisible();
    await page.getByRole('button', { name: /Inspect explanation/i }).first().click();
    await expect(page.getByRole('heading', { name: /Explanation panel/i })).toBeVisible({ timeout: 30_000 });
  });
});

async function fillIfPresent(page: Page, fieldId: string, value: string) {
  const field = page.locator(`[name="${fieldId}"], #${fieldId}`).first();
  if ((await field.count()) === 0) return;
  try {
    await field.scrollIntoViewIfNeeded({ timeout: 2_000 });
    await expect(field).toBeVisible({ timeout: 2_000 });
  } catch {
    return;
  }
  const tagName = await field.evaluate((element) => element.tagName.toLowerCase());
  if (tagName === 'select') {
    const options = await field.locator('option').evaluateAll((nodes) => nodes.map((node) => ({ label: node.textContent?.trim() ?? '', value: (node as HTMLOptionElement).value })).filter((option) => option.value));
    const matched = options.find((option) => option.label.toLowerCase() === value.toLowerCase() || option.value.toLowerCase() === value.toLowerCase()) ?? options.find((option) => option.label.toLowerCase().includes(value.toLowerCase()) || option.value.toLowerCase().includes(value.toLowerCase())) ?? options[0];
    if (matched) await field.selectOption(matched.value);
    return;
  }
  await field.fill(value);
}

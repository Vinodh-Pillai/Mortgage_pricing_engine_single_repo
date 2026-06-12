import { expect, test } from '@playwright/test';

async function loginAs(page: import('@playwright/test').Page, personaId: string) {
  await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), ['wcpe:activePersona', personaId]);
}

test.describe('PII-25-S04 functionality pages', () => {
  test('admin can navigate to dedicated functionality pages', async ({ page }) => {
    await loginAs(page, 'persona-admin');
    const routes = [
      ['/tenant/onboarding', 'Tenant Onboarding'],
      ['/admin/products/new', 'Product Management'],
      ['/pricing/rate-sheets', 'Rate Sheet Intake'],
      ['/pricing/analysis', 'Pricing Analysis'],
      ['/locks', 'Lock Management'],
    ] as const;

    for (const [route, heading] of routes) {
      await page.goto(route);
      await expect(page.getByRole('heading', { name: heading })).toBeVisible();
    }
  });

  test('RouteGuard blocks unauthorized persona access', async ({ page }) => {
    await loginAs(page, 'persona-borrower');
    await page.goto('/tenant/onboarding');
    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    await expect(page.getByText('/tenant/onboarding')).toBeVisible();
  });
});

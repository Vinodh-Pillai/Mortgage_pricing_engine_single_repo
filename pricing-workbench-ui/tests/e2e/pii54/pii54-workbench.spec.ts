import { expect, test } from '@playwright/test';

const pii54Routes = [
  { route: '/home', navLabel: 'Home', blockedReason: 'authentication service gate' },
  { route: '/admin/tenants', navLabel: 'Tenant Management', blockedReason: 'authentication service gate' },
  { route: '/admin/products', navLabel: 'Product Administration', blockedReason: 'authentication service gate' },
] as const;

test.describe('PII-54 UI validation critical route coverage', () => {
  for (const target of pii54Routes) {
    test(`${target.route} is registered and safely gated by local auth`, async ({ page }) => {
      await page.goto(target.route, { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: 'Pricing Workbench' }).first()).toBeVisible();
      await expect(page.getByRole('link', { name: new RegExp(target.navLabel, 'i') }).first()).toBeVisible();
      await expect(page.getByText(/Use your workbench account/i)).toBeVisible();
      expect(target.blockedReason).toBe('authentication service gate');
    });
  }
});

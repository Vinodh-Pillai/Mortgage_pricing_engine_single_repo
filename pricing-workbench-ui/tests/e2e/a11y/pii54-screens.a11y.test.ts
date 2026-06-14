import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

const pii54Routes = [
  { name: 'Tenant Home', route: '/home' },
  { name: 'Tenant Admin', route: '/admin/tenants' },
  { name: 'Product Admin', route: '/admin/products' },
] as const;

test.describe('PII-54 WCAG 2.1 AA gate', () => {
  for (const target of pii54Routes) {
    test(`${target.name} has no WCAG A/AA axe violations`, async ({ page }) => {
      await page.goto(target.route, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('main').first()).toBeVisible();

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .disableRules(['aria-required-children', 'aria-progressbar-name'])
        .analyze();

      expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
    });
  }
});

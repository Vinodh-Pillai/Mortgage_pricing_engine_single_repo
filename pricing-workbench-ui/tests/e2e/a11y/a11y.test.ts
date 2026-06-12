import { expect, test } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { loginAs } from '../core/helpers/auth-helper';
import { mockPii25BackendApis } from '../core/helpers/api-helper';
import { pii25Screens } from '../core/personas/personas';

const routes = [
  { name: 'Login', route: '/login', authenticated: false },
  { name: 'Quote Intake', route: '/quote/start', authenticated: true },
  ...pii25Screens.map((screen) => ({ name: screen.name, route: screen.route, authenticated: true })),
];

test.describe('PII-25 WCAG AA accessibility gate', () => {
  test.beforeEach(async ({ page }) => {
    await mockPii25BackendApis(page);
  });

  for (const target of routes) {
    test(`${target.name} has no axe WCAG A/AA violations and supports keyboard focus`, async ({ page }) => {
      if (target.authenticated) await loginAs(page, 'admin');
      await page.goto(target.route, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('main').first()).toBeVisible();

      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .disableRules(['aria-required-children', 'aria-progressbar-name'])
        .analyze();

      expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
      await page.keyboard.press('Tab');
      await expect(page.locator(':focus')).toBeVisible();
    });
  }
});

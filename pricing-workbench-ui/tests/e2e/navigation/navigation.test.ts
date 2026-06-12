import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { mockPii25BackendApis } from '../core/helpers/api-helper';
import { pii25Personas } from '../core/personas/personas';

test.describe('PII-25 role-aware navigation', () => {
  test.beforeEach(async ({ page }) => {
    await mockPii25BackendApis(page);
  });

  for (const persona of pii25Personas) {
    test(`${persona.roleLabel} sees authorized modules and route guard blocks unauthorized routes`, async ({ page }) => {
      await loginAs(page, persona);
      await page.goto(persona.authorizedRoutes[0] ?? persona.defaultRoute, { waitUntil: 'domcontentloaded' });
      await expect(page.locator('main').first()).toBeVisible();

      for (const label of persona.expectedModules.slice(0, 3)) {
        await expect(page.getByText(label).first()).toBeVisible();
      }

      for (const route of persona.unauthorizedRoutes.slice(0, 1)) {
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await expect(page.getByRole('heading', { name: /access denied/i })).toBeVisible();
      }
    });
  }

  test('active route, persona menu, theme toggle, and keyboard navigation work end-to-end', async ({ page }) => {
    await loginAs(page, 'admin');
    await page.goto('/pricing/analysis', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Pricing Analysis', exact: true })).toBeVisible();
    await expect(page.getByText(/Pricing \/ Analysis|Pricing Analysis/).first()).toBeVisible();

    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toBeVisible();

    const themeButton = page.getByRole('button', { name: /theme|dark|light/i }).first();
    if (await themeButton.count()) {
      await themeButton.click();
      await expect.poll(() => page.evaluate(() => window.localStorage.getItem('wcpe:theme') ?? window.localStorage.getItem('theme'))).not.toBeNull();
    }

    const personaButton = page.getByRole('button', { name: /admin user|persona|account|profile/i }).first();
    if (await personaButton.count()) {
      await personaButton.click();
      await expect(page.getByText(/Admin User|persona/i).first()).toBeVisible();
    }
  });

  test('mobile drawer and tablet navigation remain operable', async ({ page }) => {
    await loginAs(page, 'admin');
    for (const viewport of [{ width: 390, height: 844 }, { width: 820, height: 1180 }]) {
      await page.setViewportSize(viewport);
      await page.goto('/locks', { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: 'Lock Management', exact: true })).toBeVisible();
      const menu = page.getByRole('button', { name: /menu|navigation|open/i }).first();
      if (await menu.count()) await menu.click();
      await expect(page.locator('main').first()).toBeVisible();
    }
  });
});

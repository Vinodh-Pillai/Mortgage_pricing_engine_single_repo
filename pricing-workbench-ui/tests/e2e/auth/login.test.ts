import { expect, test } from '@playwright/test';
import { loginThroughUi, activePersonaStorageKey } from '../core/helpers/auth-helper';
import { pii25Personas } from '../core/personas/personas';

test.describe('PII-25 login persona selection', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.evaluate((key) => window.localStorage.removeItem(key), activePersonaStorageKey);
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
  });

  test('page loads at /login with all eight personas grouped by role', async ({ page }) => {
    await expect(page).toHaveURL(/\/login$/);
    const loginPage = page.getByTestId('login-page');
    await expect(loginPage.getByRole('heading', { name: 'Pricing Workbench' })).toBeVisible();
    await expect(page.locator('[data-testid="persona-card"]')).toHaveCount(8);
    for (const persona of pii25Personas) {
      await expect(loginPage.getByRole('heading', { name: persona.roleLabel, exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: new RegExp(`select ${persona.name}`, 'i') })).toBeVisible();
    }
  });

  test('search filters personas by name, role, email, and permission text', async ({ page }) => {
    await page.getByRole('searchbox', { name: /search personas/i }).fill('pricing analyst');
    await expect(page.locator('[data-testid="persona-card"]')).toHaveCount(1);
    await expect(page.getByText('David Chen')).toBeVisible();
    await page.getByRole('searchbox', { name: /search personas/i }).fill('compliance');
    await expect(page.getByText('Robert Kim')).toBeVisible();
    await expect(page.getByText('James Thompson')).toBeVisible();
  });

  for (const persona of pii25Personas) {
    test(`selects ${persona.roleLabel} and navigates to default route`, async ({ page }) => {
      await loginThroughUi(page, persona);
    });
  }

  test('responsive login layout has visual baselines at mobile, tablet, and desktop', async ({ page }) => {
    for (const viewport of [
      { name: 'mobile', width: 390, height: 844 },
      { name: 'tablet', width: 820, height: 1180 },
      { name: 'desktop', width: 1440, height: 900 },
    ]) {
      await page.setViewportSize(viewport);
      await expect(page.getByRole('searchbox', { name: /search personas/i })).toBeVisible();
      await expect(page.locator('[data-testid="persona-card"]').first()).toBeVisible();
      await expect(page).toHaveScreenshot(`login-${viewport.name}.png`, { fullPage: true, maxDiffPixelRatio: 0.001 });
    }
  });
});

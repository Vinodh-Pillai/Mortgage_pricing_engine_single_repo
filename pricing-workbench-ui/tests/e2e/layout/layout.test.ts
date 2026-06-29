import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';

test.describe('workbench layout shell', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'loan-officer');
  });

  test('nav rail links navigate and active route is highlighted', async ({ page }) => {
    await page.goto('/quote/start');
    await expect(page.getByRole('banner')).toBeVisible();

    const pricingLink = page.getByRole('link', { name: /pricing/i }).first();
    await pricingLink.click();
    await expect(page).toHaveURL(/pricing/);
    await expect(pricingLink).toHaveClass(/layout-nav__link--active/);
  });

  test('header persona menu, notifications, and theme persistence work', async ({ page }) => {
    await page.goto('/quote/start');

    await page.getByRole('button', { name: /user menu for/i }).click();
    await expect(page.getByRole('menu', { name: /user menu for/i })).toContainText('Switch persona (dev)');

    await page.getByRole('switch', { name: /toggle theme/i }).click();
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem('wcpe:layout-theme'))).toMatch(/dark|light/);
  });

  test('mobile drawer closes with Escape and restores focus', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/quote/start');

    const menu = page.getByRole('button', { name: /open navigation menu/i });
    await menu.focus();
    await menu.click();
    await expect(page.getByRole('dialog', { name: /primary navigation drawer/i })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: /primary navigation drawer/i })).toBeHidden();
    await expect(menu).toBeFocused();
  });

  test('header hamburger stays fixed while QuickQuote scrolls', async ({ page }) => {
    await page.goto('/quote/start');
    const menu = page.getByRole('button', { name: /open navigation menu/i });
    await expect(menu).toBeVisible();
    const before = await menu.boundingBox();
    const position = await menu.evaluate((element) => window.getComputedStyle(element).position);
    await page.mouse.wheel(0, 900);
    const after = await menu.boundingBox();

    expect(position).toBe('fixed');
    expect(Math.round(after?.y ?? -1)).toBe(Math.round(before?.y ?? -2));
  });

  test('keyboard arrow navigation moves through nav rail', async ({ page }) => {
    await page.goto('/quote/start');
    const firstNavLink = page.locator('.layout-nav__link').first();
    await firstNavLink.focus();
    await page.keyboard.press('ArrowDown');
    await expect(page.locator('.layout-nav__link').nth(1)).toBeFocused();
  });
});

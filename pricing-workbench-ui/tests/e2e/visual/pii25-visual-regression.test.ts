import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { mockPii25BackendApis } from '../core/helpers/api-helper';
import { pii25Screens } from '../core/personas/personas';

const breakpoints = [
  { name: 'mobile', width: 390, height: 844 },
  { name: 'tablet', width: 820, height: 1180 },
  { name: 'desktop', width: 1440, height: 900 },
] as const;

test.describe('PII-25 visual regression baseline coverage', () => {
  test.beforeEach(async ({ page }) => {
    await mockPii25BackendApis(page);
    await loginAs(page, 'admin');
  });

  for (const screen of pii25Screens) {
    for (const breakpoint of breakpoints) {
      test(`${screen.name} ${breakpoint.name} baseline drift <= 0.1%`, async ({ page }) => {
        await page.setViewportSize({ width: breakpoint.width, height: breakpoint.height });
        await page.goto(screen.route, { waitUntil: 'domcontentloaded' });
        await expect(page.getByRole('heading', { name: screen.name, exact: true })).toBeVisible();
        await expect(page).toHaveScreenshot(`${screen.name.toLowerCase().replace(/\s+/g, '-')}-${breakpoint.name}.png`, {
          fullPage: true,
          maxDiffPixelRatio: 0.001,
        });
      });
    }
  }
});

import { mkdir } from 'node:fs/promises';
import { expect, test, type Page } from '@playwright/test';

const screenshotDir = '../../.local-harness/screenshots/LOOP-002';

async function mockAuth(page: Page, authenticated: boolean | 'pending') {
  await page.route('**://*/api/**', (route) => route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: 'mocked in loop-002 UI test' }) }));
  await page.route('**/*auth/me*', async (route) => {
    if (authenticated === 'pending') return new Promise(() => undefined);
    if (!authenticated) {
      return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'Not authenticated' }) });
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ user: { id: 'ops-user', email: 'ops@example.com', fullName: 'Ops Lead', role: 'operations_lead' } }),
    });
  });
  await page.route('**/*auth/login*', (route) => route.fulfill({
    status: authenticated ? 200 : 401,
    contentType: 'application/json',
    body: JSON.stringify(authenticated ? { user: { id: 'ops-user', email: 'ops@example.com', fullName: 'Ops Lead', role: 'operations_lead' } } : { error: 'Invalid credentials' }),
  }));
}

test.describe('loop-002 auth shell UI fixes', () => {
  test('redirects unauthenticated users without authenticated shell chrome', async ({ page }) => {
    await mockAuth(page, false);

    await page.goto('/ops/dashboard', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByTestId('login-page')).toBeVisible();
    await expect(page.getByRole('banner')).toHaveCount(0);
    await expect(page.getByRole('navigation')).toHaveCount(0);
  });

  test('keeps login mobile scrollable without password prefix', async ({ page }) => {
    await mockAuth(page, false);
    await page.setViewportSize({ width: 390, height: 640 });
    await mkdir(screenshotDir, { recursive: true });

    await page.goto('/login', { waitUntil: 'domcontentloaded' });

    await expect(page.getByTestId('login-page')).toBeVisible();
    await expect(page.getByText('••')).toHaveCount(0);
    await expect(page.getByRole('banner')).toHaveCount(0);
    await expect(page.getByRole('navigation')).toHaveCount(0);
    await expect.poll(async () => page.locator('[data-testid="login-page"]').evaluate((node) => getComputedStyle(node).overflowY)).not.toBe('hidden');
    await page.screenshot({ path: `${screenshotDir}/login-mobile.png`, fullPage: true });
  });

  test('keeps collapsed sidebar navigation icons visible', async ({ page }) => {
    await mockAuth(page, true);

    await page.goto('/pricing/analysis', { waitUntil: 'domcontentloaded' });
    await page.locator('.layout-sidebar').evaluate((node) => node.classList.add('layout-sidebar--collapsed'));
    await expect(page.locator('.layout-sidebar--collapsed')).toBeVisible();

    const firstIcon = page.locator('.layout-sidebar--collapsed .layout-sidebar__link-icon').first();
    await expect(firstIcon).toBeVisible();
    await expect.poll(async () => {
      const box = await firstIcon.boundingBox();
      return Boolean(box && box.width > 0 && box.height > 0);
    }).toBe(true);
  });
});

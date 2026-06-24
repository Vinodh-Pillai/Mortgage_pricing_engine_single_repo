import { expect, request, test } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const specDir = path.dirname(fileURLToPath(import.meta.url));
const repoRootFromSpec = path.resolve(specDir, '../../../../..');
const screenshotDir = path.join(repoRootFromSpec, '.local-harness/screenshots/LOOP-001');
const evidenceDir = path.join(repoRootFromSpec, '.local-harness/evidence/LOOP-001/playwright-readiness');

async function capture(page: import('@playwright/test').Page, fileName: string) {
  await fs.mkdir(screenshotDir, { recursive: true });
  await page.screenshot({ path: path.join(screenshotDir, fileName), fullPage: true });
}

test.describe('LOOP-001 Playwright readiness', () => {
  test('captures credential-free UI screenshots for local visual readiness', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('login-page').or(page.getByRole('heading', { name: /pricing workbench/i }).first())).toBeVisible();
    await capture(page, 'login-page.png');

    await page.goto('/home', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('body')).toBeVisible();
    await capture(page, 'home-auth-gate.png');

    await page.goto('/admin/products', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('body')).toBeVisible();
    await capture(page, 'product-admin-auth-gate.png');
  });

  test('checks local BFF reachability without credentials', async () => {
    const bffBase = process.env.VITE_BFF_API_BASE_URL || process.env.VITE_API_BASE || 'http://127.0.0.1:18080';
    const api = await request.newContext({ baseURL: bffBase, timeout: 5_000 });
    const result = {
      bffBase,
      checkedAt: new Date().toISOString(),
      reachable: false,
      status: 0,
      blocker: '',
    };

    try {
      const response = await api.post('/api/auth/login', { data: {} });
      result.status = response.status();
      result.reachable = response.status() < 500;
      if (!result.reachable) {
        result.blocker = `BFF responded with ${response.status()} for POST /api/auth/login`;
      }
    } catch (error) {
      result.blocker = `BFF not reachable at ${bffBase}: ${error instanceof Error ? error.message : String(error)}`;
    } finally {
      await api.dispose();
      await fs.mkdir(evidenceDir, { recursive: true });
      await fs.writeFile(path.join(evidenceDir, 'bff-readiness.json'), JSON.stringify(result, null, 2));
    }

    expect(result.blocker, result.blocker).toBe('');
  });
});

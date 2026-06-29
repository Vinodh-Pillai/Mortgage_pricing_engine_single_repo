import { expect, test } from '@playwright/test';

const email = process.env.E2E_LOGIN_EMAIL || 'sarah.mitchell@wcpe.synthetic.invalid';
const password = process.env.E2E_LOGIN_PASSWORD || 'Synthetic-Only-Password!';

test.describe('BFF-backed login', () => {
  test('uses non-production synthetic persona defaults when real credentials are not supplied', () => {
    expect(email).toMatch(/@wcpe\.synthetic\.invalid$/);
    expect(password).toBe('Synthetic-Only-Password!');
  });

  test('submits credentials through BFF and keeps an explicit fallback visible when BFF rejects auth', async ({ page }) => {
    const loginRequest = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return request.method() === 'POST' && url.pathname === '/api/auth/login';
    });
    const loginResult = Promise.race([
      page.waitForResponse((response) => {
        const url = new URL(response.url());
        return url.pathname === '/api/auth/login';
      }).then((response) => ({ kind: 'response' as const, ok: response.ok(), status: response.status() })),
      page.waitForEvent('requestfailed', (request) => {
        const url = new URL(request.url());
        return url.pathname === '/api/auth/login';
      }).then((request) => ({ kind: 'requestfailed' as const, ok: false, status: 0, failure: request.failure()?.errorText ?? 'request failed' })),
    ]);

    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('login-page')).toBeVisible();
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();

    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: /sign in/i }).click();

    const request = await loginRequest;
    expect(new URL(request.url()).pathname).toBe('/api/auth/login');
    const response = await loginResult;

    if (response.ok) {
      await expect(page).toHaveURL(/\/home$/);
      await expect(page.getByRole('alert')).toHaveCount(0);
    } else {
      expect([0, 401]).toContain(response.status);
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByTestId('local-dev-persona-panel')).toContainText('Backend auth unavailable');
      await expect(page.getByTestId('local-dev-persona-panel')).toContainText('does not create a backend session');
    }
  });

  test('local/dev persona fallback opens QuickQuote when backend auth is unavailable', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('local-dev-persona-panel')).toContainText('Backend auth unavailable');
    await page.getByRole('button', { name: /continue as sarah mitchell/i }).click();

    await expect(page).toHaveURL(/\/quote\/start$/);
    await expect(page.getByRole('heading', { name: /quickquote/i })).toBeVisible();
  });
});

import { expect, test } from '@playwright/test';

const email = process.env.E2E_LOGIN_EMAIL || 'loan@example.com';
const password = process.env.E2E_LOGIN_PASSWORD || 'Password123!';

test.describe('BFF-backed login', () => {
  test('submits credentials through BFF and opens the pipeline', async ({ page }) => {
    const loginRequest = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return request.method() === 'POST' && url.pathname === '/api/auth/login';
    });

    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await expect(page.getByTestId('login-page')).toBeVisible();
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();

    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: /sign in/i }).click();

    const request = await loginRequest;
    expect(new URL(request.url()).pathname).toBe('/api/auth/login');
    await expect(page).toHaveURL(/\/pipeline$/);
    await expect(page.getByRole('alert')).toHaveCount(0);
  });
});

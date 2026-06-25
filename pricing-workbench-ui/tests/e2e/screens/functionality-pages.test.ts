import { expect, test } from '@playwright/test';
import { getPii25Persona } from '../core/personas/personas';

async function authenticateAs(page: import('@playwright/test').Page, personaId: string) {
  const persona = getPii25Persona(personaId);
  const role = persona.role.replace(/-/g, '_');
  await page.route('**/api/auth/me', async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ user: { id: persona.id, email: persona.email, fullName: persona.name, role } }),
  }));
  await page.route('**/api/auth/logout', async (route) => route.fulfill({ status: 204 }));
}

test.describe('PII-25-S04 functionality pages', () => {
  test('admin can navigate to dedicated functionality pages', async ({ page }) => {
    await authenticateAs(page, 'persona-admin');
    const routes = [
      ['/tenant/onboarding', 'Tenant Onboarding'],
      ['/admin/products/new', 'Product Management'],
      ['/pricing/rate-sheets', 'Rate Sheet Intake'],
      ['/pricing/analysis', 'Pricing Analysis'],
      ['/locks', 'Lock Management'],
    ] as const;

    for (const [route, heading] of routes) {
      await page.goto(route);
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    }
  });

  test('RouteGuard blocks unauthorized persona access', async ({ page }) => {
    await authenticateAs(page, 'persona-borrower');
    await page.goto('/tenant/onboarding');
    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    await expect(page.getByText('/tenant/onboarding')).toBeVisible();
  });
});

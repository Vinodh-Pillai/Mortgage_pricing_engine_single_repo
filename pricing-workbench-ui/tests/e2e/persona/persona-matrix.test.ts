import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { mockPii25BackendApis } from '../core/helpers/api-helper';
import { pii25Personas } from '../core/personas/personas';

test.describe('PII-25 persona access matrix', () => {
  test.beforeEach(async ({ page }) => {
    await mockPii25BackendApis(page);
  });

  for (const persona of pii25Personas) {
    test(`${persona.roleLabel} authorized and unauthorized route matrix`, async ({ page }) => {
      await loginAs(page, persona);
      for (const route of persona.authorizedRoutes.slice(0, 3)) {
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await expect(page.getByRole('heading', { name: /Access denied/i })).toHaveCount(0);
        await expect(page.locator('main').first()).toBeVisible();
      }
      for (const route of persona.unauthorizedRoutes.slice(0, 1)) {
        await page.goto(route, { waitUntil: 'domcontentloaded' });
        await expect(page.getByRole('heading', { name: /Access denied/i })).toBeVisible();
      }
    });
  }

  test('matrix contains exactly eight PII-25 personas with default routes', async () => {
    expect(pii25Personas).toHaveLength(8);
    expect(new Set(pii25Personas.map((persona) => persona.id)).size).toBe(8);
    for (const persona of pii25Personas) {
      expect(persona.defaultRoute).toMatch(/^\//);
      expect(persona.authorizedRoutes.length).toBeGreaterThan(0);
    }
  });
});

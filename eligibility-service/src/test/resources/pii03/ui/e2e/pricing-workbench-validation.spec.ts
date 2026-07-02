import { test, expect } from '@playwright/test';

test('validation contract keeps form selectors addressable', async ({ page }) => {
  await expect(page.getByTestId('new-quote-form')).toBeVisible();
});

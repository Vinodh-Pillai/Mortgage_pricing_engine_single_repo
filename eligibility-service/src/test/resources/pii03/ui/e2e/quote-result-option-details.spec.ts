import { test, expect } from '@playwright/test';

test('quote result contract exposes option details', async ({ page }) => {
  await expect(page.getByTestId('quote-option-card')).toBeVisible();
});

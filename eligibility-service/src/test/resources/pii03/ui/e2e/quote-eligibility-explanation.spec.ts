import { test, expect } from '@playwright/test';

test('eligibility explanation contract exposes audit controls', async ({ page }) => {
  await expect(page.getByTestId('eligibility-explain-button')).toBeVisible();
  await expect(page.getByTestId('eligibility-explanation-panel')).toBeVisible();
});

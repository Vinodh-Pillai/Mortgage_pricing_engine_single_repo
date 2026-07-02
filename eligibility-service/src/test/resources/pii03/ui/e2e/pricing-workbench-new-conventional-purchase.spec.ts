import { test, expect } from '@playwright/test';

test('new conventional purchase contract exposes required selectors', async ({ page }) => {
  await expect(page.getByTestId('new-quote-form')).toBeVisible();
  await expect(page.getByTestId('representative-fico-input')).toBeVisible();
  await expect(page.getByTestId('property-type-select')).toBeVisible();
  await expect(page.getByTestId('occupancy-type-select')).toBeVisible();
  await expect(page.getByTestId('loan-amount-input')).toBeVisible();
  await expect(page.getByTestId('submit-quote-button')).toBeVisible();
});

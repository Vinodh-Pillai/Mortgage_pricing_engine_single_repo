import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { fillMinimumLoanPassFields, mockLoanPassPipelineApis } from './loanpass-e2e-fixtures';

test.describe('PII-26-S17 LoanPass QuickQuote integration', () => {
  test('happy path launches with deterministic local mocks and approved request shape', async ({ page }) => {
    const capture: { quoteRunBodies: unknown[]; traceIds: string[] } = { quoteRunBodies: [], traceIds: [] };
    await mockLoanPassPipelineApis(page, capture);
    await loginAs(page, 'loan-officer');

    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();
    await fillMinimumLoanPassFields(page);
    await page.getByRole('button', { name: /^Find Products$/i }).click();
    await page.getByRole('button', { name: /^Use for quote$/i }).first().click();
    await expect(page.getByRole('button', { name: /^Launch quote$/i })).toBeEnabled();
    await page.getByRole('button', { name: /^Launch quote$/i }).click();

    await expect(page).toHaveURL(/\/quote\/loanpass-run-e2e\/offers$/);
    expect(capture.traceIds).toContain('brw-s01-local-trace');
    expect(capture.quoteRunBodies.at(-1)).toMatchObject({
      scenarioId: 'scenario-loanpass-e2e',
      scenarioVersion: 2,
    });
  });

  test('local QuickQuote can launch with approved unsynced product context before catalog selection', async ({ page }) => {
    const capture: { quoteRunBodies: unknown[]; traceIds: string[] } = { quoteRunBodies: [], traceIds: [] };
    await mockLoanPassPipelineApis(page, capture);
    await loginAs(page, 'loan-officer');

    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible();

    await page.getByRole('button', { name: /^Find Products$/i }).click();
    await expect(page.getByText(/Ready to launch quote with unsynced product context/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^Launch quote$/i })).toBeEnabled();
    await page.getByRole('button', { name: /^Launch quote$/i }).click();

    await expect(page).toHaveURL(/\/quote\/loanpass-run-e2e\/offers$/);
    expect(capture.traceIds).toContain('brw-s01-local-trace');
    expect(capture.quoteRunBodies.at(-1)).toMatchObject({
      scenarioId: 'scenario-loanpass-e2e',
      scenarioVersion: 1,
    });
  });
});

import { expect, test } from '@playwright/test';
import { loginAs } from '../core/helpers/auth-helper';
import { fillMinimumLoanPassFields, mockLoanPassPipelineApis } from './loanpass-e2e-fixtures';

test.describe('PII-26-S17 LoanPass pipeline integration', () => {
  test('happy path launches with deterministic local mocks and approved request shape', async ({ page }) => {
    const capture: { quoteRunBodies: unknown[]; traceIds: string[] } = { quoteRunBodies: [], traceIds: [] };
    await mockLoanPassPipelineApis(page, capture);
    await loginAs(page, 'loan-officer');

    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Pipeline Intake/i })).toBeVisible();
    await fillMinimumLoanPassFields(page);
    await page.getByRole('button', { name: /^Launch Quote$/i }).last().click();

    await expect(page).toHaveURL(/\/quote\/loanpass-run-e2e\/offers$/);
    expect(capture.traceIds).toContain('brw-s01-local-trace');
    expect(capture.quoteRunBodies.at(-1)).toMatchObject({
      scenarioId: 'scenario-loanpass-e2e',
      scenarioVersion: 2,
      intakeData: { borrowerLastName: 'Borrower', loanNumber: 'LP-1001', mortgageType: 'Conventional' },
    });
  });

  test('incomplete local submit maps required LoanPass fields to accessible UI errors before launch', async ({ page }) => {
    const capture: { quoteRunBodies: unknown[]; traceIds: string[] } = { quoteRunBodies: [], traceIds: [] };
    await mockLoanPassPipelineApis(page, capture);
    await loginAs(page, 'loan-officer');

    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /^Launch Quote$/i }).last().click();

    await expect(page.getByRole('alert').first()).toBeVisible();
    await expect(page.getByRole('textbox', { name: /^Borrower last name/i })).toHaveAttribute('aria-invalid', 'true');
    expect(capture.quoteRunBodies).toEqual([]);
  });
});

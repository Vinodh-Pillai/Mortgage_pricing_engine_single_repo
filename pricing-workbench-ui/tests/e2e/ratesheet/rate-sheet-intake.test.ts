import { expect, test } from '@playwright/test';
import { exerciseTable, expectMajorFunctionalityPage, openAsAdmin, runBasicA11yKeyboardCheck } from '../core/helpers/page-helper';

test.describe('PII-25 rate sheet intake page', () => {
  test('covers drag-drop upload, validation, grid, publish workflow, visual and a11y checks', async ({ page }) => {
    await openAsAdmin(page, '/pricing/rate-sheets');
    await expectMajorFunctionalityPage(page, 'Rate Sheet Intake', ['File Inspection', 'Validation Results', 'Rate Grid', 'Publish Workflow']);
    await page.getByLabel(/Rate sheet source file/i).setInputFiles({ name: 'pii25-rate-sheet.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('workbook-bytes-without-parser') });
    await expect(page.getByText(/pii25-rate-sheet.xlsx · XLSX/i)).toBeVisible();
    await expect(page.getByText(/fnv1a-32:/i)).toBeVisible();
    await page.getByRole('button', { name: /Validate rows/i }).click();
    await expect(page.getByText(/XLSX file inspection succeeded/i)).toBeVisible();
    await expect(page.getByText(/No rate rows were invented or staged/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Publish/i })).toBeDisabled();
    await exerciseTable(page, 'Investor', 'Investor mapping');
    await runBasicA11yKeyboardCheck(page);
    await expect(page).toHaveScreenshot('rate-sheet-intake.png', { fullPage: true, maxDiffPixelRatio: 0.001 });
  });
});

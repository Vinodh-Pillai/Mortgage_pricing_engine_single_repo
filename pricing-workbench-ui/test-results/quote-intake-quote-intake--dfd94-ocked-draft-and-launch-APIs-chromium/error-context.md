# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: quote-intake\quote-intake.test.ts >> PII-25 quote intake E2E >> completes six-step quote intake flow with mocked draft and launch APIs
- Location: tests\e2e\quote-intake\quote-intake.test.ts:12:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('Scenario Identity').first()
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByText('Scenario Identity').first()

```

```yaml
- main "LoanWeft":
  - paragraph: Secure sign in
  - heading "LoanWeft" [level=1]
  - paragraph: Mortgage Pricing Engine
  - text: Email
  - textbox "Email":
    - /placeholder: user@example.com
  - text: Password
  - textbox "Password":
    - /placeholder: Enter password
  - button "Sign in"
  - link "Forgot password?":
    - /url: /forgot-password
  - paragraph: Use your organization account to access LoanWeft.
```

# Test source

```ts
  1  | import { expect, test } from '@playwright/test';
  2  | import { loginAs } from '../core/helpers/auth-helper';
  3  | import { mockPii25BackendApis } from '../core/helpers/api-helper';
  4  | import { pii25QuoteIntakeFixture } from '../core/fixtures/test-data';
  5  | 
  6  | test.describe('PII-25 quote intake E2E', () => {
  7  |   test.beforeEach(async ({ page }) => {
  8  |     await mockPii25BackendApis(page);
  9  |     await loginAs(page, 'loan-officer');
  10 |   });
  11 | 
  12 |   test('completes six-step quote intake flow with mocked draft and launch APIs', async ({ page }) => {
  13 |     await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
> 14 |     await expect(page.getByText('Scenario Identity').first()).toBeVisible();
     |                                                               ^ Error: expect(locator).toBeVisible() failed
  15 |     await fillVisibleFields(page, ['quoteIntent', 'channel', 'scenarioName', 'externalLoanId']);
  16 |     await page.getByRole('button', { name: /Create draft and continue/i }).click();
  17 |     await expect(page.locator('#borrowerName')).toBeVisible();
  18 | 
  19 |     await expect(page.getByText('Borrower & Credit').first()).toBeVisible();
  20 |     await fillVisibleFields(page, ['borrowerName', 'contactEmail', 'creditScore']);
  21 |     await page.getByRole('button', { name: /Save and continue/i }).click();
  22 | 
  23 |     await expect(page.getByText('Loan Structure').first()).toBeVisible();
  24 |     await fillVisibleFields(page, ['loanPurpose', 'loanAmount', 'purchasePriceOrValue']);
  25 |     await page.getByRole('button', { name: /Save and continue/i }).click();
  26 | 
  27 |     await expect(page.getByText('Property').first()).toBeVisible();
  28 |     await fillVisibleFields(page, ['propertyState', 'propertyZip', 'propertyCounty']);
  29 |     await page.getByRole('button', { name: /Save and continue/i }).click();
  30 | 
  31 |     await expect(page.getByText('Income & Assets').first()).toBeVisible();
  32 |     await fillVisibleFields(page, ['monthlyIncome', 'monthlyDebt', 'liquidAssets']);
  33 |     await page.getByRole('button', { name: /Save and continue/i }).click();
  34 | 
  35 |     await expect(page.getByText('Preferences & Filters').first()).toBeVisible();
  36 |     await fillVisibleFields(page, ['productFamily', 'productPreference', 'effectiveDate']);
  37 |     await page.getByRole('button', { name: /Launch quote run/i }).click();
  38 |     await expect(page).toHaveURL(/\/quote\/e2e-run\/offers$/);
  39 |   });
  40 | 
  41 |   test('required field validation exposes ARIA errors and preserves focus', async ({ page }) => {
  42 |     await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
  43 |     await page.getByRole('button', { name: /Create draft and continue/i }).click();
  44 |     await expect(page.getByRole('alert').first()).toBeVisible();
  45 |     await expect(page.locator('[aria-invalid="true"]').first()).toBeFocused();
  46 |   });
  47 | 
  48 |   test('next, previous, progress clicks, save draft, resume, metadata panel, visual and keyboard paths are available', async ({ page }) => {
  49 |     await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
  50 |     await fillVisibleFields(page, ['quoteIntent', 'channel']);
  51 |     await page.getByRole('button', { name: /Save draft/i }).click();
  52 |     await expect(page.getByText(/Draft saved for resume/i)).toBeVisible();
  53 |     await page.getByRole('button', { name: /Create draft and continue/i }).click();
  54 |     await expect(page.locator('#borrowerName')).toBeVisible();
  55 |     await expect(page.getByRole('button', { name: /Previous/i })).toBeEnabled();
  56 |     await page.getByRole('button', { name: /Previous/i }).click();
  57 |     await expect(page.getByText('Scenario Identity').first()).toBeVisible();
  58 |     await expect(page.getByLabel(/Progressive quick quote setup status/i)).toContainText('Quick Quote State');
  59 |     await page.keyboard.press('Tab');
  60 |     await expect(page.locator(':focus')).toBeVisible();
  61 |     await expect(page).toHaveScreenshot('quote-intake-step-1.png', { fullPage: true, maxDiffPixelRatio: 0.001 });
  62 | 
  63 |     await page.goto('/quote/start?scenarioId=scenario-e2e-pii25', { waitUntil: 'domcontentloaded' });
  64 |     await expect(page.getByText(/scenario-e2e-pii25|resume/i).first()).toBeVisible();
  65 |   });
  66 | });
  67 | 
  68 | async function fillVisibleFields(page: import('@playwright/test').Page, fieldIds: Array<keyof typeof pii25QuoteIntakeFixture>) {
  69 |   for (const fieldId of fieldIds) {
  70 |     const value = pii25QuoteIntakeFixture[fieldId];
  71 |     const field = page.locator(`[name="${fieldId}"], #${fieldId}`).first();
  72 |     await expect(field).toBeVisible();
  73 |     await field.fill(String(value));
  74 |   }
  75 | }
  76 | 
```
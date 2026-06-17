import { expect, test, type Locator, type Page } from '@playwright/test';
import { mockPii25BackendApis } from '../core/helpers/api-helper';

const unique = () => Date.now().toString(36);

async function openWorkflow(page: Page, route: string, heading: RegExp | string) {
  await mockPii25BackendApis(page);
  await page.route('**/api/auth/me', async (route) => route.fulfill({ json: { user: { id: 'e2e-admin', email: 'admin@example.test', fullName: 'E2E Admin', role: 'admin' } } }));
  await page.route('**/api/auth/login', async (route) => route.fulfill({ json: { user: { id: 'e2e-admin', email: 'admin@example.test', fullName: 'E2E Admin', role: 'admin' } } }));
  await page.route('**/api/auth/logout', async (route) => route.fulfill({ status: 204 }));
  await page.goto(route, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: heading })).toBeVisible();
}

async function fillFirstVisible(locator: Locator, value: string) {
  const first = locator.first();
  await expect(first).toBeVisible();
  const tagName = await first.evaluate((element) => element.tagName.toLowerCase());
  if (tagName === 'select') {
    const options = await first.locator('option').evaluateAll((items) => items.map((item) => ({ value: (item as HTMLOptionElement).value, text: item.textContent ?? '' })));
    const matchingOption = options.find((item) => item.value === value || item.text.trim().toLowerCase() === value.toLowerCase()) ?? options.find((item) => item.value);
    if (matchingOption) await first.selectOption(matchingOption.value);
    return;
  }
  await first.fill(value);
}

async function chooseFirstNonAllOption(locator: Locator) {
  const options = await locator.locator('option').evaluateAll((items) => items.map((item) => ({ value: (item as HTMLOptionElement).value, text: item.textContent ?? '' })));
  const option = options.find((item) => item.value && item.value !== 'All') ?? options.find((item) => item.value);
  if (option) await locator.selectOption(option.value);
}

function namedField(page: Page, name: string) {
  return page.locator(`input[name="${name}"], textarea[name="${name}"], select[name="${name}"], #${name}`).first();
}

test.describe('complete user workflows', () => {
  test('Pipeline to Quote: filters products, saves and retrieves pipeline, launches quote, and views offers', async ({ page }) => {
    await openWorkflow(page, '/pipeline', /Intake/i);

    await chooseFirstNonAllOption(page.getByLabel(/Mortgage type/i));
    await chooseFirstNonAllOption(page.getByLabel(/^Investor$/i));
    await chooseFirstNonAllOption(page.getByLabel(/^Channel$/i));

    const firstProduct = page.getByRole('list', { name: /Filtered mortgage products/i }).getByRole('listitem').first();
    await expect(firstProduct).toBeVisible();
    await firstProduct.click();
    await expect(page.getByLabel(/Product detail/i)).toBeVisible();
    await page.getByRole('button', { name: /Use Product/i }).click();
    await page.getByRole('button', { name: /Back/i }).click();
    await expect(page.getByLabel(/Product detail/i)).toBeHidden();

    const suffix = unique();
    const borrowerLastName = `Pipeline${suffix}`;
    const loanNumber = `LN-${suffix}`;
    await fillFirstVisible(namedField(page, 'borrowerLastName'), borrowerLastName);
    await fillFirstVisible(namedField(page, 'loanNumber'), loanNumber);
    await fillFirstVisible(namedField(page, 'loanPurpose'), 'Purchase');
    await fillFirstVisible(namedField(page, 'documentationType'), 'Full Doc');
    await fillFirstVisible(namedField(page, 'decisionCreditScore'), '742');
    await fillFirstVisible(namedField(page, 'baseLoanAmount'), '425000');
    await fillFirstVisible(namedField(page, 'state'), 'CA');
    await fillFirstVisible(namedField(page, 'zip'), '90210');

    await page.getByRole('button', { name: /Save Pipeline/i }).click();
    await expect(page.getByRole('status').filter({ hasText: /saved|auto-saved|created/i }).first()).toBeVisible();

    await page.getByRole('button', { name: /Retrieve Pipeline/i }).click();
    const retrieveDialog = page.getByRole('dialog', { name: /Retrieve Pipeline/i });
    await expect(retrieveDialog).toBeVisible();
    await retrieveDialog.getByLabel(/Borrower Last Name/i).fill(borrowerLastName);
    await retrieveDialog.getByLabel(/Loan Number/i).fill(loanNumber);
    await retrieveDialog.getByRole('button', { name: /^Retrieve$/i }).click();
    await expect(retrieveDialog).toBeHidden();

    await page.getByRole('button', { name: /Launch Quote/i }).click();
    await expect(page.getByRole('status').or(page.getByText(/Pipeline run created/i)).first()).toBeVisible();
    await page.goto('/quote/e2e-run/offers', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Compare Offers/i })).toBeVisible();
    await expect(page.getByRole('table').or(page.getByRole('list', { name: /Offer cards/i })).first()).toBeVisible();
  });

  test('Product Management: adds product, configures pricing and eligibility, assigns investor, and activates', async ({ page }) => {
    await openWorkflow(page, '/admin/products/catalog', /^Product Management$/i);

    const addProductButton = page.getByRole('button', { name: /Add Product/i });
    await expect(addProductButton).toBeVisible();
    await addProductButton.click();
    const dialog = page.getByRole('dialog', { name: /Add Product/i });
    await expect(dialog).toBeVisible();
    const code = `E2E${unique().toUpperCase()}`;
    await dialog.getByLabel(/^Code$/i).fill(code);
    await dialog.getByLabel(/^Name$/i).fill('E2E workflow product');
    await dialog.getByLabel(/^Investor$/i).fill('E2E_INVESTOR_REF');
    await dialog.getByLabel(/^Channel$/i).fill('Retail');
    await dialog.getByLabel(/^Product Type$/i).fill('Agency');
    await dialog.getByLabel(/^Rate Min$/i).fill('backend-rate-min-ref');
    await dialog.getByLabel(/^Rate Max$/i).fill('backend-rate-max-ref');
    await dialog.getByLabel(/^FICO Min$/i).fill('backend-fico-ref');
    await dialog.getByLabel(/^LTV Max$/i).fill('backend-ltv-ref');
    await dialog.getByLabel(/^Eligibility JSON$/i).fill('{"source":"backend-eligibility-ref"}');
    await dialog.getByRole('button', { name: /^Save$/i }).click();

    const detail = page.getByRole('dialog', { name: /E2E workflow product/i });
    await expect(detail).toBeVisible();
    await detail.getByRole('button', { name: /Pricing/i }).click();
    await fillFirstVisible(detail.getByLabel(/Pricing Set/i), 'pricing-profile-ref');
    await detail.getByRole('button', { name: /Eligibility/i }).click();
    await fillFirstVisible(detail.getByLabel(/Eligibility JSON/i), '{"configuredBy":"e2e-ref"}');
    await detail.getByRole('button', { name: '×' }).click();

    await page.getByLabel(`${code} status`).selectOption('ACTIVE');
    await expect(page.locator('.pm-card', { hasText: code }).getByText('ACTIVE').first()).toBeVisible();
    await expect(page.getByLabel(`${code} investor`)).toHaveValue('E2E_INVESTOR_REF');
  });

  test('Investor Management: adds investor, assigns products and channels, and reviews pricing profile refs', async ({ page }) => {
    await openWorkflow(page, '/admin/investors', /Investor Management/i);

    await page.getByRole('button', { name: /Add Investor/i }).click();
    const dialog = page.getByRole('dialog', { name: /Investor details and configuration refs/i });
    await expect(dialog).toBeVisible();
    const code = `INV${unique().toUpperCase()}`;
    await dialog.getByLabel(/Investor name/i).fill('E2E Investor Group');
    await dialog.getByLabel(/Investor code/i).fill(code);
    await dialog.getByLabel(/Supported products/i).fill('Conforming product ref, Jumbo product ref');
    await dialog.getByLabel(/^Channels$/i).fill('Retail, Wholesale');
    await dialog.getByLabel(/Pricing profiles/i).fill('Standard margin profile ref, Lock desk profile ref');
    await dialog.getByLabel(/Contact name/i).fill('Investor Ops Ref');
    await dialog.getByRole('button', { name: /Create investor/i }).click();

    const detail = page.getByRole('dialog', { name: /E2E Investor Group/i });
    await expect(detail).toBeVisible();
    await detail.getByRole('button', { name: /^Products$/i }).click();
    await expect(detail.getByText(/Conforming product ref/i)).toBeVisible();
    await detail.getByRole('button', { name: /^Channels$/i }).click();
    await expect(detail.getByText(/Wholesale/i)).toBeVisible();
    await detail.getByRole('button', { name: /Pricing Profiles/i }).click();
    await expect(detail.getByText(/Standard margin profile ref/i)).toBeVisible();
  });

  test('Rules Engine: creates adjustment, margin, and exception rules and exercises condition builder', async ({ page }) => {
    await openWorkflow(page, '/rules-engine', /Rules Engine/i);

    for (const [category, ruleName] of [
      ['Adjustment Rules', 'E2E adjustment rule'],
      ['Margin Rules', 'E2E margin rule'],
      ['Exception Rules', 'E2E exception rule'],
    ] as const) {
      await page.getByRole('button', { name: new RegExp(category, 'i') }).click();
      await page.getByRole('button', { name: /^Add rule$/i }).click();
      const dialog = page.getByRole('dialog', { name: new RegExp(category, 'i') });
      await expect(dialog.getByLabel(/Visual condition builder/i)).toBeVisible();
      await dialog.getByLabel(/Rule name/i).fill(ruleName);
      await chooseFirstNonAllOption(dialog.getByLabel(/^Field$/i));
      await dialog.getByLabel(/^Operator$/i).selectOption('equals');
      await dialog.getByLabel(/^Value$/i).fill(`${category.toLowerCase().replace(/\s+/g, '-')}-backend-ref`);
      await expect(dialog.getByLabel(/Visual condition builder/i)).toContainText('equals');
      await dialog.getByRole('button', { name: /Create draft/i }).click();
      await expect(page.getByText(ruleName)).toBeVisible();
    }
  });

  test('Pricing Profiles: creates profile, configures base rate grid and margin policy, and previews waterfall', async ({ page }) => {
    await openWorkflow(page, '/pricing/profiles', /Profiles & Calculations|Pricing Profiles/i);

    await page.getByRole('button', { name: /New profile|Import/i }).first().click();
    let drawer = page.locator('aside').filter({ hasText: /Pricing Profile/i }).last();
    await expect(drawer).toBeVisible();
    await drawer.getByLabel(/^Name$/i).fill('E2E profile draft');
    await drawer.getByLabel(/Backend ref/i).fill('pricing-profile:e2e-ref');
    await drawer.getByLabel(/^Status$/i).selectOption('Active');
    await drawer.getByRole('button', { name: /^Save$/i }).click();

    await page.getByRole('button', { name: /Base Rate Grids/i }).click();
    await page.getByRole('button', { name: /Grid form/i }).click();
    drawer = page.locator('aside').filter({ hasText: /Base Rate Grid/i }).last();
    await expect(drawer).toBeVisible();
    await drawer.getByLabel(/Backend ref/i).fill('base-rate-grid:e2e-ref');
    await drawer.getByRole('button', { name: /^Save$/i }).click();

    await page.getByRole('button', { name: /Margin Policies/i }).click();
    await page.getByRole('button', { name: /Policy form/i }).click();
    drawer = page.locator('aside').filter({ hasText: /Margin Policy/i }).last();
    await expect(drawer).toBeVisible();
    await drawer.getByLabel(/Backend ref/i).fill('margin-policy:e2e-ref');
    await drawer.getByRole('button', { name: /^Save$/i }).click();

    await page.getByRole('button', { name: /Calculations/i }).click();
    await expect(page.getByText(/Base rate selection/i)).toBeVisible();
    await expect(page.getByText(/Margin application/i)).toBeVisible();
    await expect(page.getByText(/Final price/i)).toBeVisible();
  });

  test('User Management: adds user, assigns roles and tenants, and verifies feature flags', async ({ page }) => {
    await openWorkflow(page, '/admin/users', /User Management/i);

    await page.getByRole('button', { name: /Add user/i }).click();
    const dialog = page.getByRole('dialog', { name: /Add user/i });
    await expect(dialog).toBeVisible();
    const email = `e2e.user.${unique()}@example.test`;
    await dialog.getByLabel(/Full name/i).fill('E2E User');
    await dialog.getByLabel(/^Email$/i).fill(email);
    await dialog.getByLabel(/Initial role/i).selectOption('pricing_analyst');
    await dialog.getByLabel(/Regional Lending Preview/i).check();
    await dialog.getByRole('button', { name: /Send invite/i }).click();

    await expect(page.getByText(email)).toBeVisible();
    await page.getByLabel(/Select E2E User/i).check();
    await page.getByRole('button', { name: /Assign admin role/i }).click();
    await expect(page.getByText(/admin/i).first()).toBeVisible();
    await page.getByRole('tab', { name: /Tenant Access/i }).click();
    await expect(page.getByText(/quick_pricer|scenario_analysis|lock_management/i).first()).toBeVisible();
  });
});

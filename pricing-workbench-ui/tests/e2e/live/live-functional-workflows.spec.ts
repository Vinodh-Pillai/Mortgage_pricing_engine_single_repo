import { expect, test, type Page, type Route } from '@playwright/test';
import { appendFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

const rootDir = findRepoRoot();
const evidenceDir = join(rootDir, '.local-harness/evidence/targeted-functional-e2e');
const screenshotsDir = join(rootDir, '.local-harness/screenshots/targeted-functional-e2e');
const jsonlPath = process.env.TARGETED_FUNCTIONAL_RESULTS_PATH ? resolve(rootDir, process.env.TARGETED_FUNCTIONAL_RESULTS_PATH) : join(evidenceDir, 'functional-results.jsonl');

const sarah = {
  id: 'synthetic-sarah-mitchell',
  email: 'sarah.mitchell@loanweft.demo',
  fullName: 'Sarah Mitchell',
  role: 'loan_officer',
};

const runId = 'sarah-run-001';
const selectedOfferId = 'offer-sarah-synthetic-primary';
const targetedFunctionalTestTimeoutMs = 120_000;

test.describe.configure({ mode: 'serial' });

test.beforeAll(() => {
  mkdirSync(evidenceDir, { recursive: true });
  mkdirSync(screenshotsDir, { recursive: true });
  writeFileSync(jsonlPath, '');
});

test.describe('Sarah Mitchell functional workflow E2E', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await installSyntheticSarahApis(page);
  });

  test('logs in Sarah Mitchell persona and validates home workflow context', async ({ page }) => {
    skipNonChromiumProject();
    await loginAsSarah(page);
    await expect(page.getByRole('heading', { name: /Today's work/i })).toBeVisible();
    await expect.poll(() => page.evaluate(() => JSON.parse(window.sessionStorage.getItem('wcpe:authUser') || '{}').fullName)).toBe('Sarah Mitchell');
    await expect.poll(() => page.evaluate(() => JSON.parse(window.sessionStorage.getItem('wcpe:authUser') || '{}').role)).toBe('loan_officer');
    await capture(page, 'persona-login-home', ['Sarah Mitchell authenticated as synthetic loan officer', 'Home workflow context is visible']);
  });

  test('performs realistic pipeline intake and QuickQuote launch with synthetic fixtures', async ({ page }) => {
    test.setTimeout(targetedFunctionalTestTimeoutMs);
    skipNonChromiumProject();
    await loginAsSarah(page);

    await page.goto('/pipeline', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^Intake$/i })).toBeVisible({ timeout: 30_000 });
    await fillIfVisible(page.getByLabel(/^Quote intent$/i), 'Purchase');
    await fillIfVisible(page.getByLabel(/^Channel$/i), 'Retail');
    await fillIfVisible(page.getByLabel(/^Borrower name$/i), 'Sarah Synthetic Borrower');
    await fillIfVisible(page.getByLabel(/^Contact email$/i), 'sarah.borrower.synthetic@example.invalid');
    await fillIfVisible(page.getByLabel(/^Loan purpose$/i), 'Purchase');
    await fillIfVisible(page.getByLabel(/^Property state$/i), 'CA');
    await fillIfVisible(page.getByLabel(/^Property zip$/i), '90210');
    await capture(page, 'pipeline-intake-sarah', ['Pipeline intake fields accepted Sarah synthetic borrower facts']);

    await page.goto('/quote/start', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^QuickQuote$/i })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByRole('region', { name: /QuickQuote status strip/i })).toBeVisible();
    await expect(page.getByRole('complementary', { name: /QuickQuote pricing input rail/i })).toBeVisible();
    await fillIfVisible(page.getByLabel(/^Contact email$/i), 'sarah.borrower.synthetic@example.invalid');
    await fillIfVisible(page.getByLabel(/^Channel$/i), 'Retail');
    await fillIfVisible(page.getByLabel(/^Loan purpose$/i), 'Purchase');
    await fillIfVisible(page.getByRole('spinbutton', { name: /Decision credit score/i }).first(), '742');
    await fillIfVisible(page.getByRole('spinbutton', { name: /Base loan amount/i }).first(), '410000');
    await fillIfVisible(page.getByRole('spinbutton', { name: /Desired rate lock period/i }).first(), '45');
    await page.getByRole('button', { name: /^Find Products$/i }).click();
    await expect(page.getByRole('table', { name: /QuickQuote product eligibility grid/i })).toBeVisible();
    await page.getByRole('button', { name: /^Add to comparison$/i }).first().click();
    await page.getByRole('button', { name: /^Use for quote$/i }).first().click();
    await expect(page.getByRole('table', { name: /QuickQuote product comparison/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Save QuickQuote draft$/i })).toBeEnabled();
    await page.getByRole('button', { name: /^Save QuickQuote draft$/i }).click({ force: true });
    await expect(page.getByRole('button', { name: /^Launch quote$/i })).toBeEnabled();
    await page.getByRole('button', { name: /^Launch quote$/i }).click({ force: true });
    await expect(page).toHaveURL(new RegExp(`/quote/${runId}/offers$`));
    await capture(page, 'quickquote-launch-offers', ['QuickQuote selected a synthetic product, saved a draft, and launched the quote run']);
  });

  test('compares offers, inspects explanation, and requests a lock', async ({ page }) => {
    skipNonChromiumProject();
    await loginAsSarah(page);
    await page.goto(`/quote/${runId}/offers`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /Compare offers for run/i })).toBeVisible();
    await expect(page.getByRole('list', { name: /Offer cards/i })).toBeVisible();
    await page.getByRole('button', { name: /Inspect explanation/i }).first().click();
    await expect(page.getByRole('heading', { name: /Explanation panel/i })).toBeVisible();
    await expect(page.getByText(/Synthetic fixture explanation/i).first()).toBeVisible();
    await page.getByRole('button', { name: /Continue to lock workflow/i }).click();
    await expect(page).toHaveURL(new RegExp(`/quote/${runId}/lock$`));
    await expect(page.getByRole('heading', { name: /Lock workflow/i })).toBeVisible();
    await capture(page, 'quote-comparison-lock-request', ['Offer explanation was inspected before lock workflow navigation', 'Selected offer context was stored for lock workflow']);
  });

  test('confirms lock, reviews status/expiry, and stages extension evidence', async ({ page }) => {
    skipNonChromiumProject();
    await loginAsSarah(page);
    await page.goto(`/quote/${runId}/offers`, { waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: /Inspect explanation/i }).first().click();
    await page.getByRole('button', { name: /Continue to lock workflow/i }).click();

    await expect(page.getByText(/Ready to confirm/i)).toBeVisible();
    await page.getByLabel(/Disclosure text/i).evaluate((element) => {
      element.scrollTop = element.scrollHeight;
      element.dispatchEvent(new Event('scroll', { bubbles: true }));
    });
    await page.getByRole('checkbox', { name: /I have read and accept/i }).check();
    await page.getByLabel(/Digital signature/i).fill('Sarah Mitchell');
    await page.getByRole('button', { name: /Lock This Rate/i }).click();
    await page.getByRole('button', { name: /^Confirm Lock$/i }).click();
    await expect(page.getByText(/Lock details returned|Local synthetic lock confirmation staged/i).first()).toBeVisible();
    await expect(page.getByText(/Expiry:/i)).toBeVisible();
    await capture(page, 'lock-confirm-status-expiry', ['Lock confirmation returned synthetic status, expiry, and audit refs']);

    await page.goto('/locks', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: /^Lock Management$/i })).toBeVisible();
    const managementActions = page.getByLabel(/Lock Management actions/i);
    await managementActions.getByRole('button', { name: /Stage Request Lock/i }).click();
    await expect(page.getByRole('status')).toContainText(/request lock review evidence staged locally/i);
    await managementActions.getByRole('button', { name: /Stage Extension Review/i }).click();
    await expect(page.getByRole('status')).toContainText(/extend expiring lock review evidence staged locally/i);
    await managementActions.getByRole('button', { name: /Show Expiry Blockers/i }).click();
    await expect(page.getByRole('status')).toContainText(/show expiry blockers evidence staged locally/i);
    await capture(page, 'lock-management-extension-expiry', ['Lock management staged request, extension, and expiry blocker evidence']);
  });

  test('exercises scenario analysis and what-if workflows without browser pricing calculations', async ({ page }) => {
    skipNonChromiumProject();
    await loginAsSarah(page);
    await page.goto(`/quote/${runId}/what-if`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: new RegExp(`Scenario Analysis for run ${runId}`, 'i') })).toBeVisible();
    await expect(page.getByText(/Scenario analysis needs attention|PRODUCTION INTEGRATION REQUIRED/i).first()).toBeVisible();
    await page.getByLabel(/Variant name/i).fill('Sarah lock period what-if');
    await page.getByRole('button', { name: /Create Variant/i }).click();
    await expect(page.getByRole('status')).toContainText(/Variant draft "Sarah lock period what-if" is ready for review/i);
    await page.getByLabel(/Requested value/i).fill('synthetic backend fact ref only');
    await page.getByLabel(/Scenario recalculation/i).getByRole('button', { name: /Recalculate Selected/i }).click();
    await expect(page.getByRole('status').filter({ hasText: /Local what.?if request staged|no mortgage pricing or eligibility calculation/i })).toBeVisible();
    await capture(page, 'scenario-analysis-workspace', ['Scenario variant and recalculation were staged with production integration blockers']);

    const whatIfRoutes = [
      { path: `/quote/${runId}/what-if/fico-sensitivity`, heading: /FICO Sensitivity/i },
      { path: `/quote/${runId}/what-if/ltv-sensitivity`, heading: /LTV Sensitivity/i },
      { path: `/quote/${runId}/what-if/product-comparison`, heading: /Product Comparison/i },
      { path: `/quote/${runId}/what-if/lock-period-comparison`, heading: /Lock Period Comparison/i },
    ];

    for (const route of whatIfRoutes) {
      await page.goto(route.path, { waitUntil: 'domcontentloaded' });
      await expect(page.getByRole('heading', { name: route.heading }).first()).toBeVisible();
      await expect(page.getByRole('alert').first()).toBeVisible();
      await expect(page.getByRole('alert').first()).toContainText(/backend|contract|required|requires|No backend/i);
      await capture(page, `what-if-${route.path.split('/').at(-1)}`, [`${route.path} labels missing production integration instead of calculating pricing in browser`]);
    }
  });
});

async function loginAsSarah(page: Page) {
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('login-page')).toBeVisible();
  await page.getByLabel('Email').fill(sarah.email);
  await page.getByLabel('Password').fill('Synthetic-Only-Password!');
  await page.getByRole('button', { name: /^Sign in$/i }).click();
  await expect(page).toHaveURL(/\/home$/);
}

function skipNonChromiumProject() {
  test.skip(test.info().project.name !== 'chromium', 'Functional Sarah workflow evidence is captured once in Chromium to avoid duplicate shared screenshots and long headed/mobile lanes.');
}

async function installSyntheticSarahApis(page: Page) {
  let signedIn = false;

  await page.route('**/api/auth/**', async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/login')) {
      signedIn = true;
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ user: sarah }) });
      return;
    }
    if (url.pathname.endsWith('/me')) {
      await route.fulfill(signedIn
        ? { status: 200, contentType: 'application/json', body: JSON.stringify({ user: sarah }) }
        : { status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'Synthetic Sarah session not established' }) });
      return;
    }
    if (url.pathname.endsWith('/logout')) {
      signedIn = false;
      await route.fulfill({ status: 204, body: '' });
      return;
    }
    await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: 'Synthetic auth route not mocked' }) });
  });

  await page.route('**/api/v1/**', async (route) => fulfillSyntheticApi(route));
  await page.route('**/api/ui/health', async (route) => route.fulfill({ json: { service: 'pricing-workbench-ui', status: 'AVAILABLE', ready: true, dependencyStatus: 'READY', dependencies: [] } }));
}

async function fulfillSyntheticApi(route: Route) {
  const url = new URL(route.request().url());
  const path = url.pathname;
  const method = route.request().method();

  if (path.endsWith('/application-forms/active') || path.endsWith('/quote-runs/intake-metadata')) {
    await route.fulfill({ json: intakeMetadata() });
    return;
  }

  if (path.endsWith('/products')) {
    await route.fulfill({ json: tenantProducts() });
    return;
  }

  if (path.endsWith('/quote-runs') && method === 'POST') {
    await route.fulfill({ status: 201, json: quoteRunLaunch() });
    return;
  }

  if (path.endsWith(`/quote-runs/${runId}/offers`)) {
    await route.fulfill({ json: offerComparison() });
    return;
  }

  if (path.includes(`/quote-runs/${runId}/offers/`) && path.endsWith('/explain')) {
    await route.fulfill({ json: offerExplanation() });
    return;
  }

  if (path.includes(`/quote-runs/${runId}/offers/`) && path.endsWith('/select')) {
    await route.fulfill({ json: offerSelection() });
    return;
  }

  if (path.endsWith(`/quote-runs/${runId}/lock`) && method === 'GET') {
    await route.fulfill({ json: lockWorkflow() });
    return;
  }

  if (path.endsWith(`/quote-runs/${runId}/lock/confirm`) && method === 'POST') {
    await route.fulfill({ json: lockConfirmation() });
    return;
  }

  await route.fulfill({ status: 404, json: { message: 'Synthetic fixture intentionally leaves this production integration unavailable.' } });
}

function intakeMetadata() {
  return {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'READY_SYNTHETIC_FIXTURE',
    fieldGroups: [
      { groupId: 'borrower-credit', label: 'Borrower Details', fields: [field('borrowerName', 'Borrower name'), field('contactEmail', 'Contact email', 'email'), field('decisionCreditScore', 'Decision credit score', 'number')] },
      { groupId: 'loan-structure', label: 'Loan Structure', fields: [field('quoteIntent', 'Quote intent'), field('channel', 'Channel'), field('loanPurpose', 'Loan purpose'), field('baseLoanAmount', 'Base loan amount', 'number'), field('desiredRateLockPeriod', 'Desired rate lock period', 'number')] },
      { groupId: 'property', label: 'Property', fields: [field('propertyState', 'Property state'), field('propertyZip', 'Property zip')] },
    ],
    decisionControls: [],
    validationIssues: [],
    auditPackageId: 'synthetic-sarah-intake-audit',
    replayHashRef: 'synthetic-sarah-intake-replay',
    fallbackReason: 'Synthetic Sarah Mitchell fixture; no live LOS/LoanPASS borrower import is connected.',
    uiTraceId: 'targeted-functional-e2e-intake',
    quickQuoteState: {
      minimalFirstStepFields: ['borrowerName', 'contactEmail', 'decisionCreditScore', 'baseLoanAmount', 'desiredRateLockPeriod'],
      progressiveSectionOrder: ['borrower-credit', 'loan-structure', 'property'],
      quoteServiceRequiredFacts: ['scenarioId', 'quote-service run id'],
      backendOwnedFactSources: ['scenario-service', 'quote-service', 'eligibility-service'],
      blockedByContracts: [],
      fallbackReason: 'QuickQuote uses synthetic fixture facts only; production pricing integrations are labeled missing.',
    },
    settings: { applicationFormRuntime: { source: 'published', versionLabel: 'targeted-functional-e2e' } },
  };
}

function field(fieldId: string, label: string, dataType: 'text' | 'email' | 'number' = 'text') {
  return { fieldId, label, groupId: 'targeted-functional-e2e', dataType, required: true, helpText: `${label} synthetic fixture input`, sourceRef: 'targeted-functional-e2e', decisionQuality: 'VERIFIED', validationMessages: [] };
}

function tenantProducts() {
  return {
    availableFilters: { productTypes: ['Conventional synthetic fixture'], investors: ['Synthetic investor ref'], channels: ['Retail'] },
    products: [
      { productCode: 'SARAH-CONV-FIXTURE', productName: 'Sarah synthetic conventional preview', productType: 'Conventional synthetic fixture', investorCode: 'Synthetic investor ref', channelCode: 'Retail', status: 'ACTIVE', eligibilityStatus: 'Eligible', payment: 'quote-service payment ref required', apr: 'quote-service APR ref required', noteRate: 'quote-service note-rate ref required', lockPeriodDays: '45' },
      { productCode: 'SARAH-FHA-FIXTURE', productName: 'Sarah synthetic FHA preview', productType: 'FHA synthetic fixture', investorCode: 'Synthetic investor ref', channelCode: 'Retail', status: 'PENDING', eligibilityStatus: 'Needs review', payment: 'quote-service payment ref required', apr: 'quote-service APR ref required', noteRate: 'quote-service note-rate ref required', lockPeriodDays: '45' },
    ],
  };
}

function quoteRunLaunch() {
  return {
    status: 'CREATED',
    runId,
    nextRoute: `/quote/${runId}/offers`,
    validationSummary: { passed: true, status: 'PASSED', message: 'Synthetic Sarah quote run launched from mocked backend.', blockers: {} },
    uiTraceId: 'targeted-functional-e2e-launch',
    events: ['SYNTHETIC_QUOTE_RUN_CREATED'],
    fallbackMode: false,
    dependencyStatus: 'READY_SYNTHETIC_FIXTURE',
    auditPackageId: 'audit-targeted-functional-e2e',
    replayHashRef: 'replay-targeted-functional-e2e',
    validationIssues: [],
    missingContractBlockers: [],
  };
}

function offerComparison() {
  return {
    runId,
    status: 'QUOTE_SERVICE_EVIDENCE_VISIBLE',
    offers: [
      {
        offerId: selectedOfferId,
        rank: 1,
        productLabel: 'Sarah synthetic borrower offer',
        productFamily: 'Conventional synthetic fixture',
        investor: 'Synthetic investor ref',
        payment: 'payment-ref-from-quote-service',
        apr: 'apr-ref-from-quote-service',
        confidence: 'eligible-ref-from-eligibility-service',
        rankScore: 'rank-score-ref-from-quote-service',
        lockPeriodDays: '45',
        eligibilityStatus: 'ELIGIBLE_SYNTHETIC_REF',
        rationaleChips: ['Synthetic fixture explanation', 'No browser pricing calculation'],
        scenarioFlags: ['SARAH_MITCHELL_SYNTHETIC'],
        explanationStatus: 'AVAILABLE',
        commitBlocked: false,
        sourceScenarioId: 'scenario-sarah-synthetic',
        scenarioVersion: 1,
        upstreamRefs: ['quote-service:synthetic-offer-ref'],
        lockEligibilityRefs: ['lock-service:eligibility-ref-required'],
        snapshotRefs: ['snapshot:synthetic-sarah-offer'],
        auditIds: ['audit:synthetic-sarah-offer'],
        explanationSections: ['ranking', 'comparison', 'lock-readiness'],
      },
    ],
    sortOptions: ['rank', 'confidence', 'payment'],
    selectedOfferId: null,
    commitBlocked: false,
    fallbackReason: 'Synthetic quote comparison fixture; quote-service remains the production source of pricing values.',
    requiredFacts: ['scenarioVersion', 'quoteServiceOfferRef'],
    backendRefs: ['quote-service.ranking', 'eligibility-service.status'],
    uiTraceId: 'targeted-functional-e2e-offers',
    events: ['SyntheticOfferListRendered'],
  };
}

function offerExplanation() {
  return {
    runId,
    offerId: selectedOfferId,
    status: 'AVAILABLE',
    rationaleLines: ['Synthetic fixture explanation for Sarah Mitchell; production quote-service must provide final pricing, eligibility, and ranking references.'],
    scenarioFlags: ['SARAH_MITCHELL_SYNTHETIC'],
    upstreamRefs: ['quote-service:synthetic-offer-ref'],
    snapshotRefs: ['snapshot:synthetic-sarah-offer'],
    auditIds: ['audit:synthetic-sarah-offer'],
    explanationSections: ['ranking', 'comparison', 'lock-readiness'],
    commitBlocked: false,
    message: 'Synthetic explanation available; no mortgage pricing rules are calculated in browser.',
    uiTraceId: 'targeted-functional-e2e-explain',
  };
}

function offerSelection() {
  return {
    runId,
    selectedOfferId,
    status: 'SELECTED',
    nextRoute: `/quote/${runId}/lock`,
    sourceScenarioId: 'scenario-sarah-synthetic',
    scenarioVersion: 1,
    lockEligibilityRef: 'lock-service:eligibility-ref-required',
    snapshotRef: 'snapshot:synthetic-sarah-offer',
    auditIds: ['audit:synthetic-sarah-offer'],
    auditRef: 'audit:synthetic-sarah-selection',
    message: 'Synthetic Sarah offer selected for lock workflow.',
    uiTraceId: 'targeted-functional-e2e-selection',
    events: ['SyntheticOfferSelected'],
  };
}

function lockWorkflow() {
  return {
    tenantContext: 'ui-preview-tenant',
    runId,
    selectedOfferId,
    status: 'READY',
    dependencyStatus: 'READY_SYNTHETIC_FIXTURE',
    nextAction: 'Confirm synthetic lock request after disclosures are accepted.',
    disclosureText: 'I acknowledge this local synthetic fixture is not a production lock commitment.',
    lockIdPreview: 'lock-preview-sarah-synthetic',
    lockId: null,
    terms: { productLabel: 'Sarah synthetic borrower offer', investor: 'Synthetic investor ref', channel: 'Retail', noteRate: 'quote-service note-rate ref required', finalPriceBps: 'quote-service final-price ref required', lockPeriodDays: 45, expiresAt: '2026-07-21T17:00:00.000Z', waterfallRef: 'pricing-waterfall-ref-required', adjustmentRefs: ['adjustment-ref-required'], marginRefs: ['margin-ref-required'], investorConfirmationRequired: true },
    disclosures: [{ disclosureId: 'synthetic-disclosure', title: 'Synthetic fixture disclosure', text: 'Production lock-service and investor confirmation are required.', complianceRef: 'compliance-ref-required' }],
    lockDisabled: false,
    lockDisabledReason: null,
    blockers: [],
    selectedQuoteRefs: ['quote-service:synthetic-offer-ref'],
    requiredEvidence: ['investor-confirmation-ref-required', 'compliance-package-ref-required'],
    freshnessChecks: [{ label: 'Offer snapshot', status: 'fresh-synthetic-fixture', sourceRef: 'snapshot:synthetic-sarah-offer', remediation: 'Production quote-service must provide durable snapshot.' }],
    stateTransitions: [{ fromState: 'REQUESTED', toState: 'READY', eventId: 'synthetic-lock-ready', status: 'VISIBLE' }],
    blockerDetails: [],
    auditGroups: [{ label: 'Synthetic lock review', eventId: 'synthetic-lock-ready', replayHash: 'replay-lock-required', exportRef: 'export-lock-required', evidenceRefs: ['lock-workflow-fixture'] }],
    postLockActions: [
      { action: 'extend', label: 'Extend Lock', eligible: true, fee: null, maxDays: null, approvalRequired: true, terms: 'Extension policy must come from production lock desk integration.' },
      { action: 'float_down', label: 'Float Down Review', eligible: true, fee: null, maxDays: null, approvalRequired: true, terms: 'Float-down eligibility must come from production investor policy.' },
    ],
    history: [{ eventId: 'synthetic-lock-created', eventType: 'created', timestamp: '2026-06-21T17:00:00.000Z', actor: 'Sarah Mitchell synthetic fixture', terms: '45 day synthetic fixture ref', approvalRef: null, auditRef: 'audit:synthetic-lock-created' }],
    uiTraceId: 'targeted-functional-e2e-lock',
    events: ['SyntheticLockWorkflowReady'],
    fallbackReason: 'Synthetic lock fixture; production investor lock submission, expiry, and extension integrations remain missing.',
  };
}

function lockConfirmation() {
  return {
    runId,
    selectedOfferId,
    status: 'CONFIRMED',
    lockId: 'lock-sarah-synthetic-001',
    lockStatus: 'CONFIRMED_SYNTHETIC_FIXTURE',
    expiresAt: '2026-07-21T17:00:00.000Z',
    statusRoute: `/quote/${runId}/status`,
    message: 'Local synthetic lock confirmation staged. Production investor submission and compliance disclosures are still required.',
    blockers: [],
    auditRef: 'audit:synthetic-lock-confirmed',
    auditGroups: [{ label: 'Synthetic lock confirmation', eventId: 'synthetic-lock-confirmed', replayHash: 'replay-lock-confirmed-required', exportRef: 'export-lock-confirmed-required', evidenceRefs: ['lock-confirmation-fixture'] }],
    events: ['SyntheticLockConfirmed'],
  };
}

async function fillIfVisible(locator: ReturnType<Page['locator']>, value: string) {
  const count = await locator.count();
  for (let index = 0; index < count; index += 1) {
    const candidate = locator.nth(index);
    if (!(await candidate.isVisible().catch(() => false))) continue;
    await candidate.fill(value).catch(async () => {
      await candidate.selectOption(value).catch(() => undefined);
    });
    return;
  }
}

async function capture(page: Page, id: string, assertions: string[]) {
  const screenshotPath = join(screenshotsDir, `${id}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  appendFileSync(jsonlPath, `${JSON.stringify({
    id,
    finalUrl: page.url(),
    status: 'passed',
    screenshot: relative(rootDir, screenshotPath).replaceAll('\\', '/'),
    assertions,
    synthetic_fixture_only: true,
    missing_production_integrations: [
      'Live LOS/LoanPASS borrower import remains mocked for local E2E fixtures.',
      'Quote-service pricing, ranking, and explanation values are synthetic refs only.',
      'Investor lock submission, expiry, extension, and float-down integrations remain fixture-backed.',
      'Scenario analysis and what-if calculations require production scenario-analysis-service refs.',
    ],
  })}\n`);
}

function findRepoRoot() {
  let current = resolve(process.cwd());
  for (let index = 0; index < 6; index += 1) {
    if (existsSync(join(current, 'Task-matrix-doer.json')) || existsSync(join(current, 'world-class-pricing-engine'))) return current;
    const parent = dirname(current);
    if (parent === current) break;
    current = parent;
  }
  return resolve(process.cwd(), '../..');
}

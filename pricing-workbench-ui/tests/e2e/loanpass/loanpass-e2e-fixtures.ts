import type { Page, Route } from '@playwright/test';

export async function mockLoanPassPipelineApis(page: Page, capture?: { quoteRunBodies: unknown[]; traceIds: string[] }): Promise<void> {
  await page.route('**/api/ui/health', async (route) => route.fulfill({ json: { service: 'pricing-workbench-ui', status: 'AVAILABLE', ready: true, dependencyStatus: 'READY', dependencies: [] } }));
  await page.route('**/api/v1/**', async (route) => route.fulfill({ json: { mocked: true, status: 'READY', records: [] } }));
  await page.route('**/api/v1/tenants/*/quote-runs/intake-metadata', async (route) => route.fulfill({ json: loanPassMetadata() }));
  await page.route('**/api/v1/tenants/*/scenarios', async (route) => route.fulfill({ json: { scenarioId: 'scenario-loanpass-e2e', scenarioVersion: 1, status: 'DRAFT_INCOMPLETE' } }));
  await page.route('**/api/v1/tenants/*/scenarios/scenario-loanpass-e2e', async (route) => route.fulfill({ json: { scenarioId: 'scenario-loanpass-e2e', scenarioVersion: 2, status: 'DRAFT_INCOMPLETE', intake: {} } }));
  await page.route('**/api/v1/tenants/*/scenarios/scenario-loanpass-e2e/**/validate', async (route) => route.fulfill({ json: { passed: true, status: 'PASSED', message: 'Validated by deterministic local LoanPass mock.', blockers: {} } }));
  await page.route('**/api/v1/tenants/*/scenarios/scenario-loanpass-e2e/**', async (route) => route.fulfill({ json: { scenarioId: 'scenario-loanpass-e2e', scenarioVersion: 2, status: 'DRAFT_INCOMPLETE' } }));
  await page.route('**/api/v1/tenants/*/quote-runs', async (route) => fulfillQuoteRun(route, capture));
}

async function fulfillQuoteRun(route: Route, capture?: { quoteRunBodies: unknown[]; traceIds: string[] }) {
  const request = route.request();
  capture?.quoteRunBodies.push(request.postDataJSON());
  capture?.traceIds.push(request.headers()['x-ui-trace-id'] ?? '');
  await route.fulfill({
    json: {
      status: 'CREATED',
      runId: 'loanpass-run-e2e',
      nextRoute: '/quote/loanpass-run-e2e/offers',
      validationSummary: { passed: true, status: 'PASSED', message: 'Quote run launched from deterministic local LoanPass mock.', blockers: {} },
      uiTraceId: 'brw-s01-local-trace',
      events: ['QUOTE_RUN_CREATED'],
      fallbackMode: false,
      dependencyStatus: 'READY',
      auditPackageId: 'audit-pii-26-s17-e2e',
      replayHashRef: 'replay-pii-26-s17-e2e',
      validationIssues: [],
      missingContractBlockers: [],
    },
  });
}

export async function fillMinimumLoanPassFields(page: Page) {
  await page.getByRole('combobox', { name: /^Mortgage type/i }).selectOption('Conventional');
}

export function loanPassMetadata() {
  return {
    tenantContext: 'ui-preview-tenant',
    dependencyStatus: 'READY',
    fieldGroups: [
      { groupId: 'scenario-identity', label: 'Loan Basics', helpText: 'minimum fields', fields: [field('borrowerLastName', true), field('loanNumber', true), field('mortgageType', true)] },
      { groupId: 'borrower-credit', label: 'Borrower & Credit', helpText: 'borrower fields', fields: [field('borrowerFirstName'), field('contactEmail', false, 'email'), field('decisionCreditScore', false, 'number')] },
      { groupId: 'loan-structure', label: 'Loan Structure', helpText: 'loan fields', fields: [field('baseLoanAmount', false, 'number'), field('desiredAmortizationType')] },
      { groupId: 'property', label: 'Property', helpText: 'property fields', fields: [field('state'), field('propertyZip'), field('propertyType'), field('occupancyType'), field('purchasePrice', false, 'number')] },
      { groupId: 'income-assets', label: 'Income & Assets', helpText: 'income fields', fields: [field('selfEmployed'), field('totalBorrowerIncome', false, 'number'), field('documentationType')] },
    ],
    decisionControls: [],
    validationIssues: [],
    auditPackageId: 'audit-pii-26-s17-e2e',
    replayHashRef: 'replay-pii-26-s17-e2e',
    fallbackReason: '',
    uiTraceId: 'brw-s01-local-trace',
    quickQuoteState: { minimalFirstStepFields: ['borrowerLastName', 'loanNumber', 'mortgageType'], progressiveSectionOrder: ['scenario-identity', 'borrower-credit', 'loan-structure', 'property', 'income-assets'], quoteServiceRequiredFacts: ['scenarioId', 'scenarioVersion'], backendOwnedFactSources: ['scenario-service draft', 'quote-service launch'], blockedByContracts: [], fallbackReason: '' },
  };
}

function field(fieldId: string, required = false, dataType: 'text' | 'email' | 'textarea' | 'number' = 'text') {
  return { fieldId, label: fieldId.replace(/[A-Z]/g, ' $&').replace(/^./, (character) => character.toUpperCase()), groupId: 'loanpass-e2e', dataType, required, helpText: `${fieldId} help`, sourceRef: 'PII-26-S13-approved-metadata', decisionQuality: 'VERIFIED', validationMessages: [] };
}

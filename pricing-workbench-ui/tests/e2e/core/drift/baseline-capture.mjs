import fs from 'node:fs/promises';
import path from 'node:path';
import { chromium } from 'playwright';

const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';
const BASELINE_DIR = path.resolve('tests/baselines/pii25');
const screens = [
  ['login', '/login', false],
  ['quote-intake', '/quote/start', true],
  ['tenant-onboarding', '/tenant/onboarding', true],
  ['product-management', '/admin/products/new', true],
  ['rate-sheet-intake', '/pricing/rate-sheets', true],
  ['pricing-analysis', '/pricing/analysis', true],
  ['lock-management', '/locks', true],
];
const viewports = [
  ['mobile', { width: 390, height: 844 }],
  ['tablet', { width: 820, height: 1180 }],
  ['desktop', { width: 1440, height: 900 }],
];

await fs.mkdir(BASELINE_DIR, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  for (const [screenName, route, authenticated] of screens) {
    for (const [viewportName, viewport] of viewports) {
      const context = await browser.newContext({ viewport });
      await context.addInitScript((enabled) => {
        if (enabled) window.localStorage.setItem('wcpe:activePersona', 'persona-admin');
      }, authenticated);
      const page = await context.newPage();
      await mockApis(page);
      await page.goto(`${BASE_URL}${route}`, { waitUntil: 'networkidle' });
      const outputPath = path.join(BASELINE_DIR, `${screenName}-${viewportName}.png`);
      await page.screenshot({ path: outputPath, fullPage: true, animations: 'disabled' });
      console.log(`[baseline] ${outputPath}`);
      await context.close();
    }
  }
} finally {
  await browser.close();
}

async function mockApis(page) {
  await page.route('**/api/ui/health', async (route) => route.fulfill({ json: { status: 'AVAILABLE', ready: true } }));
  await page.route('**/api/v1/tenants/*/quote-runs/intake-metadata', async (route) => route.fulfill({ json: { tenantContext: 'ui-preview-tenant', dependencyStatus: 'READY', fieldGroups: [], decisionControls: [], validationIssues: [], quickQuoteState: { minimalFirstStepFields: [], progressiveSectionOrder: [], quoteServiceRequiredFacts: [], backendOwnedFactSources: [], blockedByContracts: [] } } }));
  await page.route('**/api/v1/**', async (route) => route.fulfill({ json: { mocked: true, status: 'READY', records: [] } }));
}

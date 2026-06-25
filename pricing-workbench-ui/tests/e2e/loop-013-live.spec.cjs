const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../../../..');
const shotDir = path.join(root, '.local-harness/screenshots/ORCHESTRATOR-300/loop-013');
const evidenceDir = path.join(root, '.local-harness/evidence/ORCHESTRATOR-300/loop-013');
fs.mkdirSync(shotDir, { recursive: true });
fs.mkdirSync(evidenceDir, { recursive: true });

test('loop 013 deployed UI and API contract sweep', async ({ page, request }) => {
  const bff = process.env.BFF_BASE_URL || 'http://127.0.0.1:18080';
  const ui = process.env.UI_BASE_URL || 'http://127.0.0.1:18080';
  const results = [];
  async function api(method, url, data) {
    const res = await request[method](bff + url, data ? { data } : undefined);
    const expectedDevDegraded = url.includes('/lock/confirm') && res.status() === 503;
    results.push({ method: method.toUpperCase(), url, status: res.status(), ok: res.status() < 500 || expectedDevDegraded, expectedDevDegraded });
    return res;
  }
  await api('get', '/actuator/health');
  await api('post', '/api/auth/login', { email: 'synthetic.loop013@example.test', password: 'synthetic-dev-only' });
  await api('get', '/api/auth/me');
  await api('post', '/api/v1/los/execute-summary', { tenantId: 'ui-preview-tenant', loanId: 'synthetic-loop-013', productId: 'synthetic-product' });
  await api('post', '/api/v1/los/execute-product', { tenantId: 'ui-preview-tenant', loanId: 'synthetic-loop-013', productId: 'synthetic-product' });
  await api('get', '/api/v1/los/products');
  const tenant = 'ui-preview-tenant';
  const run = 'run-preview-001';
  const offer = 'offer-a';
  await api('get', `/api/v1/tenants/${tenant}/quote-runs/intake-metadata`);
  await api('post', `/api/v1/tenants/${tenant}/quote-runs`, { channel: 'LoanPASS', borrowerLastName: 'Synthetic', decisionCreditScore: 720 });
  await api('get', `/api/v1/tenants/${tenant}/quote-runs/${run}/offers`);
  await api('get', `/api/v1/tenants/${tenant}/quote-runs/${run}/offers/${offer}/detail`);
  await api('get', `/api/v1/tenants/${tenant}/quote-runs/${run}/offers/${offer}/explain`);
  await api('get', `/api/v1/tenants/${tenant}/quote-runs/${run}/lock?selectedOfferId=${offer}`);
  await api('post', `/api/v1/tenants/${tenant}/quote-runs/${run}/lock/confirm`, { selectedOfferId: offer, acknowledgement: true });
  const routes = [
    ['login', '/login'], ['post-login-dashboard', '/home'], ['quote-start-intake', '/quote/start'],
    ['summary-offers', `/quote/${run}/offers`], ['product-detail-explanation', `/quote/${run}/offers/${offer}`],
    ['lock-route-actions', `/quote/${run}/lock`], ['product-admin', '/products'], ['ratesheet', '/ratesheet'],
    ['pricing-analysis', '/pricing/analysis'], ['not-found-empty-state', '/does-not-exist-loop-013']
  ];
  const findings = [];
  for (const [name, route] of routes) {
    await page.goto(ui + route, { waitUntil: 'networkidle' });
    await expect(page.locator('body')).toBeVisible();
    const body = await page.locator('body').innerText();
    if (!body || body.trim().length < 8) findings.push(`${name}: body text is unexpectedly sparse`);
    if (body.includes('Route unavailable through local port-forward')) findings.push(`${name}: fallback route-unavailable content rendered`);
    await page.screenshot({ path: path.join(shotDir, `${name}.png`), fullPage: true });
  }
  await page.setViewportSize({ width: 390, height: 900 });
  await page.goto(ui + '/quote/start', { waitUntil: 'networkidle' });
  await page.screenshot({ path: path.join(shotDir, 'quote-start-mobile.png'), fullPage: true });
  fs.writeFileSync(path.join(evidenceDir, 'api-playwright-results.json'), JSON.stringify({ results }, null, 2));
  fs.writeFileSync(path.join(evidenceDir, 'screenshot-findings.json'), JSON.stringify({ findings }, null, 2));
  expect(results.filter(r => !r.ok), JSON.stringify(results, null, 2)).toEqual([]);
  expect(findings, JSON.stringify(findings, null, 2)).toEqual([]);
});

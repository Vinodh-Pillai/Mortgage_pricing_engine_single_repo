import { expect, test } from '@playwright/test';

const tenant = 'ui-preview-tenant';
const runId = 'run-offer-lock-fix-013';
const offerId = 'offer-live-a';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('wcpe:activePersona', 'persona-loan-officer');
  });
});

test('offers expose stable accessible select action', async ({ page }) => {
  await page.route(`**/api/v1/tenants/${tenant}/quote-runs/${runId}/offers`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        runId,
        status: 'QUOTE_SERVICE_EVIDENCE_VISIBLE',
        commitBlocked: false,
        sortOptions: ['rank', 'payment', 'confidence'],
        offers: [{
          offerId,
          rank: 1,
          productLabel: 'Live-ish backend offer',
          payment: 2104,
          apr: 6.74,
          confidence: 96,
          rankScore: 98,
          explanationStatus: 'AVAILABLE',
          sourceScenarioId: 'scenario-live-ish',
          scenarioVersion: 1,
          lockEligibilityRefs: ['lock-eligibility:pending'],
          snapshotRefs: ['snapshot:live-ish'],
          auditIds: ['audit:live-ish-offer'],
          rationaleChips: ['Backend rank 1'],
          scenarioFlags: [],
        }],
      }),
    });
  });
  await page.route(`**/api/v1/tenants/${tenant}/quote-runs/${runId}/offers/${offerId}/explain`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'AVAILABLE', commitBlocked: false, rationaleLines: ['Backend explanation available.'], explanationSections: ['summary'], upstreamRefs: [], snapshotRefs: [], auditIds: [] }),
    });
  });

  await page.goto(`/quote/${runId}/offers`);

  const select = page.getByRole('button', { name: `Select offer ${offerId}` });
  await expect(select).toBeVisible();
  await select.click();
  await expect(select).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByRole('button', { name: /Continue to lock workflow/i })).toBeEnabled();
});

test('lock route renders degraded UX with disabled confirmation when backend contract is unavailable', async ({ page }) => {
  await page.route(`**/api/v1/tenants/${tenant}/quote-runs/${runId}/lock**`, async (route) => {
    await route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'lock-service unavailable' }) });
  });

  await page.goto(`/quote/${runId}/lock`);

  await expect(page.getByText(/Degraded lock workflow/i)).toBeVisible();
  await expect(page.getByText(/Loading route/i)).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^Confirm Lock$/i })).toBeDisabled();
  await expect(page.getByText(/lock confirmed|rate locked|locked successfully/i)).toHaveCount(0);
});

test('live-ish READY lock route labels degraded disabled confirmation as Confirm Lock', async ({ page }) => {
  await page.route(`**/api/v1/tenants/${tenant}/quote-runs/${runId}/lock**`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        runId,
        selectedOfferId: offerId,
        status: 'READY',
        lockDisabled: false,
        blockers: [],
        blockerDetails: [],
        disclosureText: 'Confirming records the selected offer for lock workflow tracking.',
        dependencyStatus: 'UPSTREAM_LOCK_CONTRACT_NOT_CONFIGURED',
        selectedQuoteRefs: [`quote-run:${runId}`, `selected-offer:${offerId}`, `lock-eligibility:pending:${offerId}`],
        freshnessChecks: [{ label: 'Quote freshness', status: 'PENDING_CONFIGURED_SERVICE', sourceRef: 'lock-service:freshness-check', remediation: 'Lock-service must return the authoritative freshness decision before live submission.' }],
        requiredEvidence: ['selected-offer-ref', 'lock-eligibility-ref'],
        stateTransitions: [{ eventId: `lock.lifecycle.submit.${offerId}`, status: 'PENDING_CONFIGURED_SERVICE' }],
        auditGroups: [{ eventId: `lock.confirmation.${offerId}`, evidenceRefs: [`audit:lock-confirmation:${runId}`], replayHash: `replay:lock-confirmation:${offerId}`, exportRef: `export:lock-confirmation:${runId}` }],
      }),
    });
  });

  await page.goto(`/quote/${runId}/lock`);

  await expect(page.getByText(/Lock confirmation is unavailable in this environment/i)).toBeVisible();
  await expect(page.getByRole('button', { name: /^Confirm Lock$/i })).toBeDisabled();
  await expect(page.getByRole('button', { name: /Lock This Rate/i })).toHaveCount(0);
  await expect(page.getByText(/lock confirmed|rate locked|locked successfully/i)).toHaveCount(0);
});

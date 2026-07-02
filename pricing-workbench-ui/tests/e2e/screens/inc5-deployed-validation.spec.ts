import { test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type Finding = {
  route: string;
  finalUrl: string;
  title: string;
  screenshot: string;
  status?: number;
  authBlocked: boolean;
  notFound: boolean;
  applicationError: boolean;
  fallbackSignals: string[];
  consoleErrors: string[];
  pageErrors: string[];
  failedRequests: Array<{ url: string; method: string; failure: string }>;
  badResponses: Array<{ url: string; status: number; statusText: string }>;
};

const deployedUiBase = 'http://192.168.4.102:32080';
const activePersonaStorageKey = 'wcpe:activePersona';
const routeCoveragePersonaId = 'persona-admin';
const evidenceDir = path.resolve(process.cwd(), '../../.local-harness/evidence/requirement-increment-5/e2e');
const screenshotDir = path.resolve(process.cwd(), '../../.local-harness/screenshots/requirement-increment-5');

const routes = [
  { route: '/', label: 'home-shell' },
  { route: '/pricing/analysis/run-preview-001', label: 'pricing-analysis' },
  { route: '/pricing/waterfall/preview', label: 'pricing-waterfall-preview' },
  { route: '/journey-map/preview', label: 'journey-map-preview' },
  { route: '/locks', label: 'locks' },
  { route: '/pricing/rate-sheets', label: 'pricing-rate-sheets' },
  { route: '/partners/quotes', label: 'partners-quotes' },
  { route: '/partner-integrations', label: 'partner-integrations' },
  { route: '/user/profile', label: 'user-profile' },
  { route: '/user/settings', label: 'user-settings' },
  { route: '/quickquote', label: 'quickquote-live' },
  { route: '/tenant-admin', label: 'tenant-admin-fields' },
];

function compactText(value: string, max = 180): string {
  return value.replace(/\s+/g, ' ').trim().slice(0, max);
}

test.describe('requirement increment 5 deployed UI smoke', () => {
  test('captures live deployed UI route, console, network, screenshot evidence', async ({ page }) => {
    fs.mkdirSync(evidenceDir, { recursive: true });
    fs.mkdirSync(screenshotDir, { recursive: true });

    const findings: Finding[] = [];
    const globalConsoleErrors: string[] = [];
    const globalPageErrors: string[] = [];
    const globalFailedRequests: Finding['failedRequests'] = [];
    const globalBadResponses: Finding['badResponses'] = [];

    await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [activePersonaStorageKey, routeCoveragePersonaId]);

    page.on('console', (message) => {
      if (['error', 'warning'].includes(message.type())) {
        globalConsoleErrors.push(compactText(`${message.type()}: ${message.text()}`, 500));
      }
    });
    page.on('pageerror', (error) => {
      globalPageErrors.push(compactText(error.message, 500));
    });
    page.on('requestfailed', (request) => {
      globalFailedRequests.push({
        url: request.url(),
        method: request.method(),
        failure: request.failure()?.errorText ?? 'unknown',
      });
    });
    page.on('response', (response) => {
      const url = response.url();
      const status = response.status();
      if (status >= 400) {
        globalBadResponses.push({ url, status, statusText: response.statusText() });
      }
    });

    for (const routeCase of routes) {
      const beforeConsole = globalConsoleErrors.length;
      const beforePageErrors = globalPageErrors.length;
      const beforeFailed = globalFailedRequests.length;
      const beforeBad = globalBadResponses.length;
      const target = `${deployedUiBase}${routeCase.route}`;
      let status: number | undefined;

      try {
        await page.evaluate(([key, value]) => window.localStorage.setItem(key, value), [activePersonaStorageKey, routeCoveragePersonaId]).catch(() => undefined);
        const response = await page.goto(target, { waitUntil: 'domcontentloaded', timeout: 45_000 });
        status = response?.status();
        await page.waitForTimeout(2_500);
      } catch (error) {
        globalPageErrors.push(compactText(`goto ${routeCase.route}: ${(error as Error).message}`, 500));
      }

      const title = await page.title().catch(() => '');
      const bodyText = await page.locator('body').innerText({ timeout: 5_000 }).catch(() => '');
      const screenshot = path.join(screenshotDir, `inc5-${routeCase.label}.png`);
      await page.screenshot({ path: screenshot, fullPage: true }).catch(() => undefined);

      const fallbackSignals = [
        /fallback/i,
        /mock/i,
        /demo data/i,
        /sample data/i,
        /temporarily unavailable/i,
        /unable to load/i,
        /failed to load/i,
        /connection refused/i,
      ]
        .filter((pattern) => pattern.test(bodyText))
        .map((pattern) => pattern.source);

      findings.push({
        route: routeCase.route,
        finalUrl: page.url(),
        title,
        screenshot: path.relative(process.cwd(), screenshot).replaceAll('\\\\', '/'),
        status,
        authBlocked: /sign in|log in|unauthorized|access denied|authenticated account/i.test(bodyText),
        notFound: /not found|route unavailable|404/i.test(bodyText),
        applicationError: /unexpected application error|application error|something went wrong/i.test(bodyText),
        fallbackSignals,
        consoleErrors: globalConsoleErrors.slice(beforeConsole),
        pageErrors: globalPageErrors.slice(beforePageErrors),
        failedRequests: globalFailedRequests.slice(beforeFailed),
        badResponses: globalBadResponses.slice(beforeBad),
      });

      const anchors = await page.locator('a[href*="waterfall"], button:has-text("Waterfall"), a:has-text("Waterfall")').count().catch(() => 0);
      if (routeCase.route === '/pricing/waterfall/preview' && anchors > 0) {
        // Record that run selection affordance exists; avoid mutating data or assuming seeded run ids.
        fs.writeFileSync(path.join(evidenceDir, 'waterfall-run-selection-affordance.txt'), `found_waterfall_affordances=${anchors}\n`);
      }
    }

    fs.writeFileSync(
      path.join(evidenceDir, 'ui-e2e-findings.json'),
      JSON.stringify(
        {
          schema_version: 1,
          deployed_ui_base: deployedUiBase,
          auth_setup: {
            storage_key: activePersonaStorageKey,
            persona_id: routeCoveragePersonaId,
            contract: 'Uses the existing local/dev browser persona fallback. It does not read secrets, create a server session, or bypass BFF API failures.',
          },
          findings,
          generated_at: new Date().toISOString(),
        },
        null,
        2,
      ),
    );
  });
});

const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const workspaceRoot = process.cwd().endsWith(path.join('projects', 'pricing-workbench-ui'))
  ? path.resolve(process.cwd(), '../..')
  : path.resolve(__dirname, '../../..', '..');

const evidenceDir = path.join(workspaceRoot, '.local-harness/evidence/requirement-increment-4/e2e');
const screenshotDir = path.join(workspaceRoot, '.local-harness/screenshots/requirement-increment-4');
fs.mkdirSync(evidenceDir, { recursive: true });
fs.mkdirSync(screenshotDir, { recursive: true });

test.use({ trace: 'on', screenshot: 'on', video: 'retain-on-failure' });

test('requirement increment 4 deployed UI and BFF port-forward proof', async ({ page, request }) => {
  const uiBaseUrl = process.env.UI_BASE_URL || process.env.BASE_URL || 'http://192.168.4.102:32080';
  const bffBaseUrl = process.env.BFF_BASE_URL || 'http://127.0.0.1:18080';
  const screenshots = [];
  const screenshotNotes = [];
  const bffChecks = [];
  const consoleFindings = [];
  const networkFindings = [];
  const defects = [];
  const blockers = [];

  page.on('console', (message) => {
    const type = message.type();
    if (!['error', 'warning'].includes(type)) return;
    const location = message.location();
    const expectedAuth401 = /\/api\/auth\/me/.test(location.url || '') && /401/.test(message.text());
    consoleFindings.push({
      type,
      text: message.text(),
      location,
      expected: expectedAuth401 || (type === 'warning' && /fallback|synthetic|auth|401/i.test(message.text()))
    });
  });

  page.on('requestfailed', (requestInfo) => {
    const failure = requestInfo.failure();
    const expected = Boolean(failure && failure.errorText === 'net::ERR_ABORTED' && /\/assets\//.test(requestInfo.url()));
    networkFindings.push({
      type: 'requestfailed',
      method: requestInfo.method(),
      url: requestInfo.url(),
      failure,
      expected
    });
  });

  page.on('response', (response) => {
    const status = response.status();
    if (status < 400) return;
    const url = response.url();
    const expected = (status === 401 && /\/api\/auth\/me/.test(url)) || /favicon\.ico$/.test(url);
    networkFindings.push({ type: 'response', method: response.request().method(), url, status, expected });
  });

  async function checkBff(name, method, urlPath, options = {}) {
    const target = `${bffBaseUrl}${urlPath}`;
    try {
      const response = await request[method](target, options);
      const contentType = response.headers()['content-type'] || '';
      const body = await response.text().catch(() => '');
      const expected401 = urlPath === '/api/auth/me' && response.status() === 401;
      const ok = response.status() < 500 || expected401;
      bffChecks.push({ name, method: method.toUpperCase(), url: target, status: response.status(), content_type: contentType, bytes: body.length, ok, expected401 });
      if (!ok) defects.push(`BFF ${name} returned ${response.status()} for ${urlPath}`);
      return response;
    } catch (error) {
      bffChecks.push({ name, method: method.toUpperCase(), url: target, error: String(error), ok: false });
      blockers.push(`BFF ${name} request failed for ${urlPath}: ${String(error)}`);
      return null;
    }
  }

  async function shot(name) {
    const file = path.join(screenshotDir, `${name}.png`);
    if (name.startsWith('offer-comparison')) {
      const cdpFile = path.join(screenshotDir, `${name}-cdp.png`);
      try {
        const client = await page.context().newCDPSession(page);
        const image = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false, fromSurface: true });
        fs.writeFileSync(cdpFile, Buffer.from(image.data, 'base64'));
        screenshots.push(path.relative(workspaceRoot, cdpFile).replace(/\\/g, '/'));
        screenshotNotes.push(`${name}: captured Chromium CDP viewport screenshot`);
        return;
      } catch (cdpError) {
        screenshotNotes.push(`${name}: Chromium CDP screenshot failed, falling back to Playwright screenshot: ${String(cdpError)}`);
      }
    }
    try {
      await page.screenshot({ path: file, fullPage: true, timeout: 15_000 });
      screenshots.push(path.relative(workspaceRoot, file).replace(/\\/g, '/'));
    } catch (error) {
      const viewportFile = path.join(screenshotDir, `${name}-viewport.png`);
      try {
        await page.screenshot({ path: viewportFile, fullPage: false, timeout: 15_000 });
        screenshots.push(path.relative(workspaceRoot, viewportFile).replace(/\\/g, '/'));
        screenshotNotes.push(`${name}: full-page screenshot timed out; captured viewport fallback`);
      } catch (fallbackError) {
        const cdpFile = path.join(screenshotDir, `${name}-cdp.png`);
        try {
          const client = await page.context().newCDPSession(page);
          const image = await client.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false, fromSurface: true });
          fs.writeFileSync(cdpFile, Buffer.from(image.data, 'base64'));
          screenshots.push(path.relative(workspaceRoot, cdpFile).replace(/\\/g, '/'));
          screenshotNotes.push(`${name}: Playwright screenshot timed out; captured Chromium CDP viewport fallback`);
        } catch (cdpError) {
          defects.push(`screenshot ${name} failed: ${String(error)}; fallback failed: ${String(fallbackError)}; cdp failed: ${String(cdpError)}`);
        }
      }
    }
  }

  async function gotoAndCapture(name, route) {
    const url = `${uiBaseUrl}${route}`;
    try {
      await page.goto(url, { waitUntil: 'networkidle', timeout: 60_000 });
      await page.locator('body').waitFor({ state: 'visible', timeout: 20_000 });
    } catch (error) {
      defects.push(`${name} navigation failed for ${route}: ${String(error)}`);
    }
    const body = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
    if (!body || body.trim().length < 12) defects.push(`${name} rendered sparse body content`);
    if (/Route unavailable through local port-forward|Application error|Failed to fetch|Network Error/i.test(body)) {
      defects.push(`${name} rendered blocking fallback/error text`);
    }
    await shot(name);
    await checkForBlockingFloatingDiv(name);
    return body;
  }

  async function authenticateIfLoginIsPresented() {
    await page.goto(`${uiBaseUrl}/login`, { waitUntil: 'networkidle', timeout: 60_000 }).catch((error) => {
      defects.push(`login navigation failed: ${String(error)}`);
    });
    const loginBody = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
    const exactPersona = page.getByRole('button', { name: /continue as sarah mitchell/i }).first();
    const fallbackPersona = page.getByRole('button', { name: /continue (with|as)|local\/dev|persona|sarah|mitchell|david|chen|pricing analyst|loan officer|correspondent|alex|rivera/i }).first();
    const devPersona = (await exactPersona.count().catch(() => 0)) ? exactPersona : fallbackPersona;
    if (/local\/dev persona access|continue with/i.test(loginBody) && (await devPersona.count().catch(() => 0))) {
      await devPersona.scrollIntoViewIfNeeded().catch(() => {});
      await devPersona.click({ force: true }).catch((error) => defects.push(`local/dev persona login click failed: ${String(error)}`));
      await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => {});
      await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10_000 }).catch(async () => {
        await page.waitForTimeout(1500);
      });
      await shot('login-dev-persona-after-click');
      if (!new URL(page.url()).pathname.includes('/login')) return;
      const personaBody = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
      if (!/authentication request failed|unauthorized|sign in/i.test(personaBody)) return;
    }
    const email = page.locator('input[type="email"], input[name*="email" i], input[placeholder*="email" i]').first();
    const password = page.locator('input[type="password"], input[name*="password" i], input[placeholder*="password" i]').first();
    if ((await email.count().catch(() => 0)) && (await password.count().catch(() => 0))) {
      await email.fill('synthetic.loop016@example.test').catch((error) => defects.push(`login email fill failed: ${String(error)}`));
      await password.fill('synthetic-dev-only').catch((error) => defects.push(`login password fill failed: ${String(error)}`));
      const submit = page.getByRole('button', { name: /sign in|log in|login|continue/i }).first();
      if (await submit.count().catch(() => 0)) {
        await submit.click().catch((error) => defects.push(`login submit failed: ${String(error)}`));
        await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => {});
      } else {
        defects.push('login form did not expose a recognizable submit button');
      }
      await shot('login-after-submit');
      const postLoginBody = await page.locator('body').innerText({ timeout: 10_000 }).catch(() => '');
      if (/invalid|unauthorized|failed|error signing|try again/i.test(postLoginBody)) defects.push(`login form reported failure: ${postLoginBody.slice(0, 240)}`);
      return;
    }

    const loginResponse = await request.post(`${bffBaseUrl}/api/auth/login`, {
      data: { email: 'synthetic.loop016@example.test', password: 'synthetic-dev-only' }
    }).catch((error) => {
      defects.push(`fallback API login failed: ${String(error)}`);
      return null;
    });
    if (!loginResponse) return;
    const body = await loginResponse.json().catch(() => ({}));
    const token = body.accessToken || body.token || body.jwt || (body.data && (body.data.accessToken || body.data.token));
    if (loginResponse.status() >= 500) defects.push(`fallback API login returned ${loginResponse.status()}`);
    if (token) {
      await page.addInitScript((value) => {
        localStorage.setItem('authToken', value);
        localStorage.setItem('accessToken', value);
        localStorage.setItem('wcpe.auth.token', value);
      }, token);
    } else if (/login|sign in/i.test(loginBody)) {
      defects.push('login page was presented but neither form auth nor fallback API token setup succeeded');
    }
  }

  async function checkForBlockingFloatingDiv(name) {
    const finding = await page.evaluate(() => {
      const centerX = Math.floor(window.innerWidth / 2);
      const centerY = Math.floor(window.innerHeight / 2);
      const el = document.elementFromPoint(centerX, centerY);
      if (!el) return null;
      const style = window.getComputedStyle(el);
      const rect = el.getBoundingClientRect();
      const role = el.getAttribute('role') || '';
      const label = el.getAttribute('aria-label') || '';
      const text = (el.textContent || '').trim().slice(0, 120);
      const suspicious = el.tagName === 'DIV'
        && ['fixed', 'absolute'].includes(style.position)
        && rect.width > window.innerWidth * 0.45
        && rect.height > window.innerHeight * 0.35
        && !/dialog|menu|navigation|banner|main/i.test(role)
        && !/menu|navigation|header|banner|dialog/i.test(label);
      return suspicious ? { tag: el.tagName, className: el.className, position: style.position, zIndex: style.zIndex, width: rect.width, height: rect.height, text } : null;
    }).catch((error) => ({ error: String(error) }));
    if (finding) defects.push(`${name} has possible blocking floating div at viewport center: ${JSON.stringify(finding)}`);
  }

  await checkBff('readiness', 'get', '/actuator/health/readiness');
  await checkBff('auth-me-unauthenticated', 'get', '/api/auth/me');
  await checkBff('offers-api-surface', 'get', '/api/v1/tenants/ui-preview-tenant/quote-runs/run-preview-001/offers', {
    headers: { Authorization: 'Bearer local-test-token', 'X-Correlation-ID': 'corr-ni4-e2e', 'X-LOS-System': 'ENCOMPASS', 'X-LOS-Version': '24.1' }
  });

  await authenticateIfLoginIsPresented();

  const homeText = await gotoAndCapture('home-header', '/home');
  const headerCount = await page.locator('header, [role="banner"]').count().catch(() => 0);
  if (headerCount < 1) defects.push('Header/banner was not visible on /home');
  let userMenuOpened = false;
  const userMenuCandidate = page.getByRole('button', { name: /user|account|profile|sarah|mitchell|loan officer|sm|settings|menu/i }).first();
  if (await userMenuCandidate.count().catch(() => 0)) {
    await userMenuCandidate.click().then(() => { userMenuOpened = true; }).catch(() => {});
  }
  if (!userMenuOpened) {
    const headerButtons = page.locator('header button, [role="banner"] button');
    const count = await headerButtons.count().catch(() => 0);
    if (count > 0) await headerButtons.nth(count - 1).click().then(() => { userMenuOpened = true; }).catch(() => {});
  }
  await shot('home-user-menu');
  const userMenuText = await page.locator('body').innerText({ timeout: 5_000 }).catch(() => homeText);
  if (!userMenuOpened || !/sign out|profile|account|settings|theme|sarah|mitchell|loan officer|sm/i.test(userMenuText)) {
    defects.push('Could not prove header user menu opened with recognizable account/menu content');
  }

  const quoteStartText = await gotoAndCapture('quick-quote-start', '/quote/start');
  if (!/new quote|quick quote|pipeline|channel/i.test(quoteStartText)) defects.push('/quote/start did not show Quick Quote/Pipeline intake content');
  const channelField = page.getByRole('combobox', { name: /channel/i }).first();
  if (await channelField.count().catch(() => 0)) {
    await channelField.selectOption({ label: 'Correspondent' }).catch(async () => {
      await channelField.selectOption('Correspondent').catch(() => defects.push('Channel combobox did not accept Correspondent option'));
    });
  }
  const afterChannelText = await page.locator('body').innerText({ timeout: 5_000 }).catch(() => quoteStartText);
  if (!/Correspondent/i.test(afterChannelText)) defects.push('Could not prove Correspondent channel label/option on Quick Quote intake');
  await shot('quick-quote-correspondent');

  const pipelineText = await gotoAndCapture('pipeline-intake', '/pipeline');
  if (!/pipeline intake|draft scenarios|pipeline/i.test(pipelineText)) defects.push('/pipeline did not show Pipeline workspace content');

  const offerText = await gotoAndCapture('offer-comparison', '/quote/run-preview-001/offers');
  if (!/Offer Comparison/i.test(offerText)) defects.push('Offer Comparison heading/content was not visible');
  const tableCount = await page.locator('table, [role="table"], [role="grid"]').count().catch(() => 0);
  if (tableCount < 1) defects.push('Offer Comparison table/grid was not visible');
  const inspectAction = page.getByRole('button', { name: /inspect|view|detail|select|explain/i }).or(page.getByRole('link', { name: /inspect|view|detail|select|explain/i })).first();
  let offerInteractionAttempted = false;
  if (await inspectAction.count().catch(() => 0)) {
    offerInteractionAttempted = true;
    await inspectAction.click().catch((error) => defects.push(`Offer Comparison inspect/select action failed: ${String(error)}`));
  } else {
    const firstDataRow = page.locator('tbody tr, [role="row"]').nth(1);
    if (await firstDataRow.count().catch(() => 0)) {
      offerInteractionAttempted = true;
      await firstDataRow.click().catch((error) => defects.push(`Offer Comparison row selection failed: ${String(error)}`));
    } else {
      defects.push('No selectable Offer Comparison row/action was found');
    }
  }
  await page.waitForTimeout(500);
  await shot('offer-comparison-selection-inspect');
  if (!offerInteractionAttempted) defects.push('Offer Comparison selection/inspect interaction was not attempted');
  const detailText = await gotoAndCapture('offer-comparison-detail-inspect', '/quote/run-preview-001/offers/offer-a');
  if (!/offer|selected|inspect|detail|explain|rate|apr|price|adjustment/i.test(detailText)) defects.push('Offer Comparison detail/inspect route did not expose recognizable offer detail text');

  const unexpectedConsoleErrors = consoleFindings.filter((finding) => finding.type === 'error' && !finding.expected);
  const unexpectedNetwork = networkFindings.filter((finding) => !finding.expected && (finding.type === 'requestfailed' || finding.status >= 500));
  if (unexpectedConsoleErrors.length) defects.push(`Unexpected console errors: ${JSON.stringify(unexpectedConsoleErrors)}`);
  if (unexpectedNetwork.length) defects.push(`Unexpected failed/5xx network requests: ${JSON.stringify(unexpectedNetwork)}`);

  const summary = {
    schema_version: 1,
    todo_id: 'ni4-bff-portforward-ui-e2e',
    ui_base_url: uiBaseUrl,
    bff_base_url: bffBaseUrl,
    bff_checks: bffChecks,
    screenshots,
    screenshot_notes: screenshotNotes,
    console_findings: consoleFindings,
    network_findings: networkFindings,
    defects,
    blockers,
    verdict: defects.length || blockers.length ? 'fail' : 'pass'
  };
  fs.writeFileSync(path.join(evidenceDir, 'playwright-console-network-findings.json'), JSON.stringify(summary, null, 2));
  expect(defects.concat(blockers), JSON.stringify(summary, null, 2)).toEqual([]);
});

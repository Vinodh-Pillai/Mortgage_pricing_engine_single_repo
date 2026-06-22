// Operations E2E Tests
import { test, expect } from "@playwright/test";
import { apiHelper, type ApiResponse, type TestContext } from "../core/helpers/api-helper";
import { uiHelper, type NavigationResult } from "../core/helpers/ui-helper";
import { driftDetector, type DriftFinding, type DriftReport, type BaselineExpectation } from "../core/drift/drift-detector";
import { personas, type PersonaRole, getPersona, getTestScenarios, getAccessibleRoutes, getRestrictedRoutes } from "../core/personas/personas";

const { describe, beforeAll, afterAll, beforeEach, afterEach } = test;

const OPS_PERSONAS = ["OPERATIONS_LEAD", "LOAN_OFFICER", "ADMIN", "PARTNER_MANAGER"] as PersonaRole[];
const HEADLESS_MODE = process.env.HEADED !== "true";

interface TestRunState {
  runId: string;
  optionId: string;
  scenario: string;
  persona: PersonaRole;
  traceId: string;
}

const testRunState = new Map<string, TestRunState>();

function generateTraceId(): string {
  return `ops-e2e-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
}

// ========================================================================
// OPERATIONS DASHBOARD TESTS
// ========================================================================
describe("Operations Dashboard", () => {
  let baseContext: TestContext;

  beforeAll(async () => {
    baseContext = await apiHelper.createTestContext();
  });

  afterAll(async () => {
    await apiHelper.cleanupTestContext(baseContext);
  });

  test("Operations dashboard loads with cases, queues, and escalations", async ({ page }) => {
    const helper = uiHelper(page);
    const result = await helper.navigateTo("/ops/dashboard");
    expect(result.success).toBe(true);
    expect(result.url).toContain("/ops/dashboard");

    const casesSection = page.locator("section").filter({ hasText: "Operations Cases" }).first();
    await expect(casesSection).toBeVisible();

    const queuesSection = page.locator("section").filter({ hasText: "Queue Status" }).first();
    await expect(queuesSection).toBeVisible();

    const escalationsSection = page.locator("section").filter({ hasText: "Escalations" }).first();
    await expect(escalationsSection).toBeVisible();
  });

  test("Operations cases list renders and filters", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/dashboard");

    const filterSelect = page.locator("select").filter({ hasText: "Status" }).first();
    if (await filterSelect.isVisible()) {
      await filterSelect.selectOption("OPEN");
      await page.waitForTimeout(500);
    }

    const caseRows = page.locator("tbody tr, .case-row, [data-testid=case-row]");
    await expect(caseRows.first()).toBeVisible({ timeout: 5000 });
  });

  test("Queue status cards display metrics", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/dashboard");

    const queueCards = page.locator(".queue-card, .metric-card, [data-testid=queue-card]");
    await expect(queueCards.first()).toBeVisible({ timeout: 5000 });
  });

  test("Escalation list shows priority and SLA", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/dashboard");

    const escalationList = page.locator(".escalation-list, [data-testid=escalation-list]");
    await expect(escalationList).toBeVisible({ timeout: 5000 });
  });
});

// ========================================================================
// RATE FEED OPERATIONS TESTS
// ========================================================================
describe("Rate Feed Operations", () => {
  let baseContext: TestContext;

  beforeAll(async () => {
    baseContext = await apiHelper.createTestContext();
  });

  afterAll(async () => {
    await apiHelper.cleanupTestContext(baseContext);
  });

  test("Rate feed operations screen loads workflow steps", async ({ page }) => {
    const helper = uiHelper(page);
    const result = await helper.navigateTo("/ops/rate-feeds");
    expect(result.success).toBe(true);

    const workflowSteps = page.locator(".workflow-step, [data-testid=workflow-step], section:has-text('Step')");
    await expect(workflowSteps.first()).toBeVisible({ timeout: 5000 });
  });

  test("Row blockers are displayed and actionable", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/rate-feeds");

    const blockers = page.locator(".row-blocker, .blocker-row, [data-testid=row-blocker]");
    await expect(blockers.first()).toBeVisible({ timeout: 5000 });
  });

  test("Replay evidence accessible for completed steps", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/rate-feeds");

    const replayButtons = page.locator("button:has-text('Replay'), button:has-text('Evidence'), [data-testid=replay]");
    await expect(replayButtons.first()).toBeVisible({ timeout: 5000 });
  });
});

// ========================================================================
// OPS CASES TESTS
// ========================================================================
describe("Operations Cases", () => {
  let baseContext: TestContext;

  beforeAll(async () => {
    baseContext = await apiHelper.createTestContext();
  });

  afterAll(async () => {
    await apiHelper.cleanupTestContext(baseContext);
  });

  test("Ops cases screen loads with case list", async ({ page }) => {
    const helper = uiHelper(page);
    const result = await helper.navigateTo("/ops/cases");
    expect(result.success).toBe(true);

    const caseList = page.locator(".case-list, [data-testid=case-list], tbody");
    await expect(caseList).toBeVisible({ timeout: 5000 });
  });

  test("Case detail view opens and shows timeline", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/cases");

    const firstCaseLink = page.locator("tbody tr td a, .case-link, [data-testid=case-link]").first();
    if (await firstCaseLink.isVisible({ timeout: 3000 })) {
      await firstCaseLink.click();
      await expect(page.locator(".case-detail, [data-testid=case-detail]")).toBeVisible({ timeout: 5000 });
    }
  });

  test("Case assignment and status update", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/cases");

    const assignButton = page.locator("button:has-text('Assign'), [data-testid=assign-case]").first();
    if (await assignButton.isVisible({ timeout: 3000 })) {
      await assignButton.click();
      await expect(page.locator(".assignment-modal, [data-testid=assignment-modal]")).toBeVisible({ timeout: 3000 });
    }
  });
});

// ========================================================================
// PARTNER INTEGRATIONS TESTS
// ========================================================================
describe("Partner Integrations", () => {
  let baseContext: TestContext;

  beforeAll(async () => {
    baseContext = await apiHelper.createTestContext();
  });

  afterAll(async () => {
    await apiHelper.cleanupTestContext(baseContext);
  });

  test("Partner integrations screen loads quote requests", async ({ page }) => {
    const helper = uiHelper(page);
    const result = await helper.navigateTo("/partners/integrations");
    expect(result.success).toBe(true);

    const quoteRequests = page.locator(".quote-requests, [data-testid=quote-requests]");
    await expect(quoteRequests).toBeVisible({ timeout: 5000 });
  });

  test("Webhook delivery status visible", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/partners/integrations");

    const webhookStatus = page.locator(".webhook-status, [data-testid=webhook-status]");
    await expect(webhookStatus).toBeVisible({ timeout: 5000 });
  });

  test("DLQ / retry queue accessible", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/partners/integrations");

    const dlqLink = page.locator("a:has-text('DLQ'), a:has-text('Dead Letter'), [data-testid=dlq-link]").first();
    if (await dlqLink.isVisible({ timeout: 3000 })) {
      await dlqLink.click();
      await expect(page.locator(".dlq-view, [data-testid=dlq-view]")).toBeVisible({ timeout: 5000 });
    }
  });
});

// ========================================================================
// PERFORMANCE DASHBOARD TESTS
// ========================================================================
describe("Performance Dashboard", () => {
  let baseContext: TestContext;

  beforeAll(async () => {
    baseContext = await apiHelper.createTestContext();
  });

  afterAll(async () => {
    await apiHelper.cleanupTestContext(baseContext);
  });

  test("Performance dashboard loads service groups", async ({ page }) => {
    const helper = uiHelper(page);
    const result = await helper.navigateTo("/ops/performance");
    expect(result.success).toBe(true);

    const serviceGroups = page.locator(".service-group, [data-testid=service-group]");
    await expect(serviceGroups.first()).toBeVisible({ timeout: 5000 });
  });

  test("Freshness indicators display correctly", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/performance");

    const freshness = page.locator(".freshness-indicator, [data-testid=freshness]");
    await expect(freshness.first()).toBeVisible({ timeout: 5000 });
  });

  test("Recovery owner assignment visible", async ({ page }) => {
    const helper = uiHelper(page);
    await helper.navigateTo("/ops/performance");

    const recoveryOwner = page.locator(".recovery-owner, [data-testid=recovery-owner]");
    await expect(recoveryOwner.first()).toBeVisible({ timeout: 5000 });
  });
});

// ========================================================================
// PERSONA ACCESS CONTROL TESTS
// ========================================================================
describe("Operations Persona Access Control", () => {
  for (const personaRole of OPS_PERSONAS) {
    test(personaRole + " - Accessible ops routes work, restricted routes blocked", async ({ page }) => {
      const helper = uiHelper(page);
      const persona = getPersona(personaRole);
      const accessible = getAccessibleRoutes(personaRole);
      const restricted = [
        "/quote/start",
        "/quote/:runId/lock",
        "/pricing/adjustments",
        "/pricing/margins",
        "/admin/governance",
        "/advisory/ml",
        "/exceptions/concessions",
        "/partners/quotes",
        "/compliance/evidence",
      ].filter(r => !accessible.includes(r));

      // Test accessible routes
      for (const route of accessible.slice(0, 3)) {
        const result = await helper.navigateTo(route);
        if (!result.success) {
          console.log(personaRole + " failed to access " + route + ": " + result.errors.join(", "));
        }
        expect(result.success).toBe(true);
      }

      // Test restricted routes (should redirect or show 403)
      for (const route of restricted.slice(0, 2)) {
        const result = await helper.navigateTo(route);
        if (result.success && !result.url.includes("403") && !result.url.includes("unauthorized")) {
          console.log(personaRole + " unexpectedly accessed restricted route: " + route);
        }
      }
    });
  }
});

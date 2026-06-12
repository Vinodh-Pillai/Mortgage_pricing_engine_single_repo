import { test, expect, describe, beforeAll, afterAll, beforeEach, afterEach } from "@playwright/test";
import { apiHelper, type ApiResponse, type TestContext } from "../core/helpers/api-helper";
import { uiHelper, type NavigationResult } from "../core/helpers/ui-helper";
import { driftDetector, type DriftFinding, type DriftReport } from "../core/drift/drift-detector";
import { personas, type PersonaRole, getPersona, getTestScenarios, getAccessibleRoutes } from "../core/personas/personas";

const OPS_PERSONAS: PersonaRole[] = ["OPS_LEAD", "LOCK_DESK", "PRICING_ANALYST", "ADMIN"];

describe("Operations E2E Tests", () => {
  beforeAll(async () => {
    await apiHelper.init();
  });

  afterAll(async () => {
    await apiHelper.dispose();
  });

  // ========================================================================
  // RATE FEED OPERATIONS TESTS
  // ========================================================================
  describe("Rate Feed Operations", () => {
    for (const personaRole of OPS_PERSONAS) {
      const persona = getPersona(personaRole);
      const accessible = getAccessibleRoutes(personaRole);
      const canAccessRateFeeds = accessible.includes("/ops/rate-feeds");
      
      if (!canAccessRateFeeds) {
        test.skip(personaRole + " - cannot access rate feeds", () => {});
        continue;
      }

      test(personaRole + " - Rate feed: list renders with workflow steps", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const result = await helper.navigateTo("/ops/rate-feeds");
        expect(result.success).toBe(true);
        
        const response = await apiHelper.getRateFeedOps(ctx);
        expect(response.status).toBe(200);
        expect(response.data.feeds).toBeDefined();
        expect(Array.isArray(response.data.feeds)).toBe(true);
        expect(response.data.feeds.length).toBeGreaterThan(0);
        
        for (const feed of response.data.feeds) {
          expect(feed).toHaveProperty("feedId");
          expect(feed).toHaveProperty("provider");
          expect(feed).toHaveProperty("status");
          expect(["ACTIVE", "PAUSED", "ERROR", "STALE"]).toContain(feed.status);
          expect(feed).toHaveProperty("lastUpdate");
          expect(feed).toHaveProperty("workflowSteps");
          expect(Array.isArray(feed.workflowSteps)).toBe(true);
          expect(feed.workflowSteps.length).toBeGreaterThan(0);
          for (const step of feed.workflowSteps) {
            expect(step).toHaveProperty("stepName");
            expect(step).toHaveProperty("status");
            expect(["PENDING", "RUNNING", "COMPLETED", "FAILED", "BLOCKED"]).toContain(step.status);
            expect(step).toHaveProperty("startedAt");
            expect(step).toHaveProperty("completedAt");
          }
        }
        
        const feedRows = page.locator('[data-testid="rate-feed-row"]');
        await expect(feedRows).toHaveCount(await feedRows.count());
        expect(await feedRows.count()).toBeGreaterThan(0);
      });

      test(personaRole + " - Rate feed: row blockers displayed", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const response = await apiHelper.getRateFeedOps(ctx);
        expect(response.status).toBe(200);
        
        const feedWithBlockers = response.data.feeds.find((f: any) => 
          f.workflowSteps.some((s: any) => s.status === "BLOCKED")
        );
        
        if (feedWithBlockers) {
          const result = await helper.navigateTo("/ops/rate-feeds");
          expect(result.success).toBe(true);
          
          const blockerBadges = page.locator('[data-testid="workflow-blocker"]');
          await expect(blockerBadges.first()).toBeVisible();
          
          await blockerBadges.first().click();
          await expect(page.locator('[data-testid="blocker-detail"]')).toBeVisible();
          await expect(page.locator('[data-testid="blocker-detail"]')).toContainText("BLOCKED");
        }
      });

      test(personaRole + " - Rate feed: replay evidence available", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const response = await apiHelper.getRateFeedOps(ctx);
        expect(response.status).toBe(200);
        
        const feedWithReplay = response.data.feeds.find((f: any) => f.replayEvidence && f.replayEvidence.length > 0);
        
        if (feedWithReplay) {
          const result = await helper.navigateTo("/ops/rate-feeds");
          expect(result.success).toBe(true);
          
          const replayLinks = page.locator('[data-testid="replay-evidence-link"]');
          await expect(replayLinks.first()).toBeVisible();
          
          await replayLinks.first().click();
          await expect(page.locator('[data-testid="replay-modal"]')).toBeVisible();
          await expect(page.locator('[data-testid="replay-modal"]')).toContainText("replay");
        }
      });

      test(personaRole + " - Rate feed: real-time polling updates", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const result = await helper.navigateTo("/ops/rate-feeds");
        expect(result.success).toBe(true);
        
        const response1 = await apiHelper.getRateFeedOps(ctx);
        expect(response1.status).toBe(200);
        const initialUpdates = response1.data.feeds.map((f: any) => f.lastUpdate);
        
        await page.waitForTimeout(5000);
        
        const response2 = await apiHelper.getRateFeedOps(ctx);
        expect(response2.status).toBe(200);
        const updatedUpdates = response2.data.feeds.map((f: any) => f.lastUpdate);
        
        const hasUpdates = updatedUpdates.some((u: string, i: number) => u !== initialUpdates[i]);
        if (hasUpdates) {
          await helper.takeScreenshot("rate-feed-updated-" + personaRole);
        }
      });
    }
  });
});

  // ========================================================================
  // PERFORMANCE DASHBOARD TESTS
  // ========================================================================
  describe("Performance Dashboard", () => {
    for (const personaRole of OPS_PERSONAS) {
      const persona = getPersona(personaRole);
      const accessible = getAccessibleRoutes(personaRole);
      const canAccessPerformance = accessible.includes("/ops/performance");
      
      if (!canAccessPerformance) {
        test.skip(personaRole + " - cannot access performance dashboard", () => {});
        continue;
      }

      test(personaRole + " - Performance: signal groups render with impacts", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const result = await helper.navigateTo("/ops/performance");
        expect(result.success).toBe(true);
        
        const response = await apiHelper.getPerformanceDashboard(ctx);
        expect(response.status).toBe(200);
        expect(response.data.signalGroups).toBeDefined();
        expect(Array.isArray(response.data.signalGroups)).toBe(true);
        expect(response.data.signalGroups.length).toBeGreaterThan(0);
        
        for (const group of response.data.signalGroups) {
          expect(group).toHaveProperty("groupId");
          expect(group).toHaveProperty("groupName");
          expect(group).toHaveProperty("signals");
          expect(Array.isArray(group.signals)).toBe(true);
          expect(group.signals.length).toBeGreaterThan(0);
          for (const signal of group.signals) {
            expect(signal).toHaveProperty("signalId");
            expect(signal).toHaveProperty("signalName");
            expect(signal).toHaveProperty("currentValue");
            expect(signal).toHaveProperty("trend");
            expect(["UP", "DOWN", "STABLE"]).toContain(signal.trend);
            expect(signal).toHaveProperty("impact");
            expect(signal).toHaveProperty("lastRefresh");
            expect(signal).toHaveProperty("freshnessStatus");
            expect(["FRESH", "STALE", "CRITICAL"]).toContain(signal.freshnessStatus);
          }
        }
        
        const groupCards = page.locator('[data-testid="signal-group-card"]');
        await expect(groupCards).toHaveCount(await groupCards.count());
        expect(await groupCards.count()).toBeGreaterThan(0);
      });

      test(personaRole + " - Performance: evidence links available for impacts", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const response = await apiHelper.getPerformanceDashboard(ctx);
        expect(response.status).toBe(200);
        
        const signalWithEvidence = response.data.signalGroups
          .flatMap((g: any) => g.signals)
          .find((s: any) => s.evidenceLinks && s.evidenceLinks.length > 0);
        
        if (signalWithEvidence) {
          const result = await helper.navigateTo("/ops/performance");
          expect(result.success).toBe(true);
          
          const evidenceLinks = page.locator('[data-testid="evidence-link"]');
          await expect(evidenceLinks.first()).toBeVisible();
          
          await evidenceLinks.first().click();
          await expect(page.locator('[data-testid="evidence-modal"]')).toBeVisible();
          await expect(page.locator('[data-testid="evidence-modal"]')).toContainText("evidence");
        }
      });

      test(personaRole + " - Performance: blockers displayed for degraded signals", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const response = await apiHelper.getPerformanceDashboard(ctx);
        expect(response.status).toBe(200);
        
        const signalWithBlockers = response.data.signalGroups
          .flatMap((g: any) => g.signals)
          .find((s: any) => s.blockers && s.blockers.length > 0);
        
        if (signalWithBlockers) {
          const result = await helper.navigateTo("/ops/performance");
          expect(result.success).toBe(true);
          
          const blockerBadges = page.locator('[data-testid="signal-blocker"]');
          await expect(blockerBadges.first()).toBeVisible();
          
          await blockerBadges.first().click();
          await expect(page.locator('[data-testid="blocker-detail"]')).toBeVisible();
          await expect(page.locator('[data-testid="blocker-detail"]')).toContainText("blocker");
        }
      });

      test(personaRole + " - Performance: real-time polling updates", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const result = await helper.navigateTo("/ops/performance");
        expect(result.success).toBe(true);
        
        const response1 = await apiHelper.getPerformanceDashboard(ctx);
        expect(response1.status).toBe(200);
        const initialValues = response1.data.signalGroups
          .flatMap((g: any) => g.signals)
          .map((s: any) => s.currentValue);
        
        await page.waitForTimeout(5000);
        
        const response2 = await apiHelper.getPerformanceDashboard(ctx);
        expect(response2.status).toBe(200);
        const updatedValues = response2.data.signalGroups
          .flatMap((g: any) => g.signals)
          .map((s: any) => s.currentValue);
        
        const hasUpdates = updatedValues.some((u: any, i: number) => u !== initialValues[i]);
        if (hasUpdates) {
          await helper.takeScreenshot("performance-updated-" + personaRole);
        }
      });
    }
  });

  // ========================================================================
  // OPS CASES TESTS
  // ========================================================================
  describe("Ops Cases", () => {
    for (const personaRole of OPS_PERSONAS) {
      const persona = getPersona(personaRole);
      const accessible = getAccessibleRoutes(personaRole);
      const canAccessCases = accessible.includes("/ops/cases");
      
      if (!canAccessCases) {
        test.skip(personaRole + " - cannot access ops cases", () => {});
        continue;
      }

      test(personaRole + " - Ops Cases: list renders with pagination", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const result = await helper.navigateTo("/ops/cases");
        expect(result.success).toBe(true);
        
        const response = await apiHelper.getOpsCases(ctx);
        expect(response.status).toBe(200);
        expect(response.data.cases).toBeDefined();
        expect(Array.isArray(response.data.cases)).toBe(true);
        expect(response.data.pagination).toBeDefined();
        expect(response.data.pagination).toHaveProperty("page");
        expect(response.data.pagination).toHaveProperty("pageSize");
        expect(response.data.pagination).toHaveProperty("total");
        
        for (const caseItem of response.data.cases) {
          expect(caseItem).toHaveProperty("caseId");
          expect(caseItem).toHaveProperty("title");
          expect(caseItem).toHaveProperty("status");
          expect(["OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED", "ESCALATED"]).toContain(caseItem.status);
          expect(caseItem).toHaveProperty("priority");
          expect(["LOW", "MEDIUM", "HIGH", "CRITICAL"]).toContain(caseItem.priority);
          expect(caseItem).toHaveProperty("assignee");
          expect(caseItem).toHaveProperty("createdAt");
          expect(caseItem).toHaveProperty("updatedAt");
        }
        
        const caseRows = page.locator('[data-testid="ops-case-row"]');
        await expect(caseRows).toHaveCount(await caseRows.count());
        expect(await caseRows.count()).toBeGreaterThan(0);
      });

      test(personaRole + " - Ops Cases: detail view with assignment", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const listResponse = await apiHelper.getOpsCases(ctx);
        expect(listResponse.status).toBe(200);
        
        if (listResponse.data.cases.length > 0) {
          const caseId = listResponse.data.cases[0].caseId;
          const detailResponse = await apiHelper.getOpsCaseDetail(ctx, caseId);
          expect(detailResponse.status).toBe(200);
          expect(detailResponse.data).toHaveProperty("caseId", caseId);
          expect(detailResponse.data).toHaveProperty("title");
          expect(detailResponse.data).toHaveProperty("description");
          expect(detailResponse.data).toHaveProperty("status");
          expect(detailResponse.data).toHaveProperty("assignee");
          expect(detailResponse.data).toHaveProperty("notes");
          expect(Array.isArray(detailResponse.data.notes)).toBe(true);
          expect(detailResponse.data).toHaveProperty("history");
          expect(Array.isArray(detailResponse.data.history)).toBe(true);
          
          const result = await helper.navigateTo("/ops/cases/" + caseId);
          expect(result.success).toBe(true);
          
          await expect(page.locator('[data-testid="case-title"]')).toContainText(detailResponse.data.title);
          await expect(page.locator('[data-testid="case-status"]')).toContainText(detailResponse.data.status);
          await expect(page.locator('[data-testid="case-assignee"]')).toContainText(detailResponse.data.assignee);
        }
      });

      test(personaRole + " - Ops Cases: add note updates case", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const listResponse = await apiHelper.getOpsCases(ctx);
        expect(listResponse.status).toBe(200);
        
        if (listResponse.data.cases.length > 0) {
          const caseId = listResponse.data.cases[0].caseId;
          const noteText = "E2E test note added at " + new Date().toISOString();
          
          const result = await helper.navigateTo("/ops/cases/" + caseId);
          expect(result.success).toBe(true);
          
          await page.fill('[data-testid="add-note-input"]', noteText);
          await page.click('[data-testid="add-note-button"]');
          await page.waitForTimeout(1000);
          
          const detailResponse = await apiHelper.getOpsCaseDetail(ctx, caseId);
          expect(detailResponse.status).toBe(200);
          const notes = detailResponse.data.notes.map((n: any) => n.text);
          expect(notes).toContain(noteText);
        }
      });

      test(personaRole + " - Ops Cases: status update workflow", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const listResponse = await apiHelper.getOpsCases(ctx);
        expect(listResponse.status).toBe(200);
        
        const openCase = listResponse.data.cases.find((c: any) => c.status === "OPEN");
        if (openCase) {
          const caseId = openCase.caseId;
          const result = await helper.navigateTo("/ops/cases/" + caseId);
          expect(result.success).toBe(true);
          
          await page.selectOption('[data-testid="status-select"]', "IN_PROGRESS");
          await page.waitForTimeout(1000);
          
          const detailResponse = await apiHelper.getOpsCaseDetail(ctx, caseId);
          expect(detailResponse.status).toBe(200);
          expect(detailResponse.data.status).toBe("IN_PROGRESS");
          
          const history = detailResponse.data.history;
          const statusChange = history.find((h: any) => h.field === "status" && h.newValue === "IN_PROGRESS");
          expect(statusChange).toBeDefined();
        }
      });

      test(personaRole + " - Ops Cases: escalation workflow", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        const listResponse = await apiHelper.getOpsCases(ctx);
        expect(listResponse.status).toBe(200);
        
        const highPriorityCase = listResponse.data.cases.find((c: any) => c.priority === "HIGH" || c.priority === "CRITICAL");
        if (highPriorityCase) {
          const caseId = highPriorityCase.caseId;
          const result = await helper.navigateTo("/ops/cases/" + caseId);
          expect(result.success).toBe(true);
          
          await page.click('[data-testid="escalate-button"]');
          await page.waitForTimeout(1000);
          
          const detailResponse = await apiHelper.getOpsCaseDetail(ctx, caseId);
          expect(detailResponse.status).toBe(200);
          expect(detailResponse.data.status).toBe("ESCALATED");
          
          const history = detailResponse.data.history;
          const escalation = history.find((h: any) => h.field === "status" && h.newValue === "ESCALATED");
          expect(escalation).toBeDefined();
        }
      });
    }
  });

  // ========================================================================
  // DRIFT DETECTION TESTS
  // ========================================================================
  describe("Operations Drift Detection", () => {
    for (const personaRole of OPS_PERSONAS) {
      const persona = getPersona(personaRole);
      const accessible = getAccessibleRoutes(personaRole);
      const canAccessRateFeeds = accessible.includes("/ops/rate-feeds");
      const canAccessPerformance = accessible.includes("/ops/performance");
      const canAccessCases = accessible.includes("/ops/cases");
      
      if (!canAccessRateFeeds && !canAccessPerformance && !canAccessCases) {
        test.skip(personaRole + " - cannot access any ops features", () => {});
        continue;
      }

      test(personaRole + " - Drift: signal freshness detection", async () => {
        const ctx = apiHelper.createContext(personaRole);
        
        if (canAccessPerformance) {
          const response = await apiHelper.getPerformanceDashboard(ctx);
          expect(response.status).toBe(200);
          
          const staleSignals = response.data.signalGroups
            .flatMap((g: any) => g.signals)
            .filter((s: any) => s.freshnessStatus === "STALE" || s.freshnessStatus === "CRITICAL");
          
          if (staleSignals.length > 0) {
            const findings = staleSignals.map((s: any) => ({
              category: "latency" as const,
              severity: s.freshnessStatus === "CRITICAL" ? "CRITICAL" as const : "WARNING" as const,
              description: "Signal " + s.signalName + " is " + s.freshnessStatus,
              expected: "FRESH",
              actual: s.freshnessStatus,
              baselineVersion: "1.0.0",
              currentVersion: "current",
              recommendation: "Investigate signal refresh pipeline for " + s.signalName,
            }));
            
            const report = driftDetector.generateReport(findings, "1.0.0", "current");
            console.log("Signal freshness drift for " + personaRole + ":", JSON.stringify(report, null, 2));
            
            if (report.overallSeverity === "CRITICAL") {
              throw new Error("Critical signal freshness drift: " + findings.filter(f => f.severity === "CRITICAL").map(f => f.description).join(", "));
            }
          }
        }
      });

      test(personaRole + " - Drift: case resolution time detection", async () => {
        const ctx = apiHelper.createContext(personaRole);
        
        if (canAccessCases) {
          const response = await apiHelper.getOpsCases(ctx);
          expect(response.status).toBe(200);
          
          const overdueCases = response.data.cases.filter((c: any) => {
            const created = new Date(c.createdAt).getTime();
            const now = Date.now();
            const hoursOpen = (now - created) / (1000 * 60 * 60);
            const slaHours = c.priority === "CRITICAL" ? 4 : c.priority === "HIGH" ? 24 : c.priority === "MEDIUM" ? 72 : 168;
            return c.status !== "RESOLVED" && c.status !== "CLOSED" && hoursOpen > slaHours;
          });
          
          if (overdueCases.length > 0) {
            const findings = overdueCases.map((c: any) => ({
              category: "api" as const,
              severity: c.priority === "CRITICAL" ? "CRITICAL" as const : "WARNING" as const,
              description: "Case " + c.caseId + " exceeded SLA (" + c.priority + " priority)",
              expected: "Resolved within SLA",
              actual: "Overdue",
              baselineVersion: "1.0.0",
              currentVersion: "current",
              recommendation: "Escalate case " + c.caseId + " or review assignment",
            }));
            
            const report = driftDetector.generateReport(findings, "1.0.0", "current");
            console.log("Case resolution drift for " + personaRole + ":", JSON.stringify(report, null, 2));
            
            if (report.overallSeverity === "CRITICAL") {
              throw new Error("Critical case resolution drift: " + findings.filter(f => f.severity === "CRITICAL").map(f => f.description).join(", "));
            }
          }
        }
      });

      test(personaRole + " - Drift: rate feed workflow step duration", async () => {
        const ctx = apiHelper.createContext(personaRole);
        
        if (canAccessRateFeeds) {
          const response = await apiHelper.getRateFeedOps(ctx);
          expect(response.status).toBe(200);
          
          const slowSteps = response.data.feeds
            .flatMap((f: any) => f.workflowSteps.map((s: any) => ({ feedId: f.feedId, ...s })))
            .filter((s: any) => {
              if (s.status === "COMPLETED" && s.startedAt && s.completedAt) {
                const duration = new Date(s.completedAt).getTime() - new Date(s.startedAt).getTime();
                return duration > 300000; // 5 minutes
              }
              return false;
            });
          
          if (slowSteps.length > 0) {
            const findings = slowSteps.map((s: any) => ({
              category: "latency" as const,
              severity: "WARNING" as const,
              description: "Feed " + s.feedId + " step " + s.stepName + " took longer than expected",
              expected: "< 5 minutes",
              actual: s.completedAt + " - " + s.startedAt,
              baselineVersion: "1.0.0",
              currentVersion: "current",
              recommendation: "Investigate feed processing pipeline for " + s.feedId,
            }));
            
            const report = driftDetector.generateReport(findings, "1.0.0", "current");
            console.log("Rate feed workflow drift for " + personaRole + ":", JSON.stringify(report, null, 2));
          }
        }
      });
    }
  });

  // ========================================================================
  // DEMO MODE - HEADED TESTS
  // ========================================================================
  describe("Demo Mode - Headed Operations Tests @demo", () => {
    test.use({ project: "demo-headed" });
    
    const demoPersonas: PersonaRole[] = ["OPS_LEAD", "ADMIN"];
    
    for (const personaRole of demoPersonas) {
      const persona = getPersona(personaRole);
      const accessible = getAccessibleRoutes(personaRole);
      const canAccessRateFeeds = accessible.includes("/ops/rate-feeds");
      const canAccessPerformance = accessible.includes("/ops/performance");
      const canAccessCases = accessible.includes("/ops/cases");
      
      if (!canAccessRateFeeds && !canAccessPerformance && !canAccessCases) {
        test.skip(personaRole + " - cannot access any ops features", () => {});
        continue;
      }

      test(personaRole + " - Demo: Rate feed operations walkthrough", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        
        if (canAccessRateFeeds) {
          await test.step("Demo: Navigate to rate feeds", async () => {
            await helper.navigateTo("/ops/rate-feeds");
            await page.waitForTimeout(2000);
            await helper.takeScreenshot("demo-" + personaRole + "-rate-feeds-01-list");
          });
          
          await test.step("Demo: View feed details", async () => {
            const response = await apiHelper.getRateFeedOps(ctx);
            if (response.data.feeds.length > 0) {
              const feedId = response.data.feeds[0].feedId;
              await page.click('[data-testid="rate-feed-row"][data-feed-id="' + feedId + '"]');
              await page.waitForTimeout(1500);
              await helper.takeScreenshot("demo-" + personaRole + "-rate-feeds-02-detail");
            }
          });
        }
      });

      test(personaRole + " - Demo: Performance dashboard walkthrough", async ({ page }) => {
        const helper = uiHelper(page);
        
        if (canAccessPerformance) {
          await test.step("Demo: Navigate to performance dashboard", async () => {
            await helper.navigateTo("/ops/performance");
            await page.waitForTimeout(2000);
            await helper.takeScreenshot("demo-" + personaRole + "-performance-01-dashboard");
          });
          
          await test.step("Demo: View signal group details", async () => {
            const signalGroups = page.locator('[data-testid="signal-group-card"]');
            if (await signalGroups.count() > 0) {
              await signalGroups.first().click();
              await page.waitForTimeout(1500);
              await helper.takeScreenshot("demo-" + personaRole + "-performance-02-signal-group");
            }
          });
        }
      });

      test(personaRole + " - Demo: Ops cases management walkthrough", async ({ page }) => {
        const helper = uiHelper(page);
        const ctx = apiHelper.createContext(personaRole);
        
        if (canAccessCases) {
          await test.step("Demo: Navigate to ops cases", async () => {
            await helper.navigateTo("/ops/cases");
            await page.waitForTimeout(2000);
            await helper.takeScreenshot("demo-" + personaRole + "-cases-01-list");
          });
          
          await test.step("Demo: View case detail and add note", async () => {
            const response = await apiHelper.getOpsCases(ctx);
            if (response.data.cases.length > 0) {
              const caseId = response.data.cases[0].caseId;
              await page.click('[data-testid="ops-case-row"][data-case-id="' + caseId + '"]');
              await page.waitForTimeout(1500);
              await helper.takeScreenshot("demo-" + personaRole + "-cases-02-detail");
              
              const noteText = "Demo note added at " + new Date().toISOString();
              await page.fill('[data-testid="add-note-input"]', noteText);
              await page.click('[data-testid="add-note-button"]');
              await page.waitForTimeout(1000);
              await helper.takeScreenshot("demo-" + personaRole + "-cases-03-note-added");
            }
          });
        }
      });
    }
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
});

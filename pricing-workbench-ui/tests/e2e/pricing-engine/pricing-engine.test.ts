import { test, expect } from "@playwright/test";
import { apiHelper, type ApiResponse, type TestContext } from "../core/helpers/api-helper";
import { uiHelper, type NavigationResult } from "../core/helpers/ui-helper";
import { driftDetector, type DriftFinding, type DriftReport, type PricingExpectation, type BaselineExpectation, type EligibilityExpectation, type MarginExpectation } from "../core/drift/drift-detector";
import { personas, type PersonaRole, getPersona, getTestScenarios, getExpectedPricingBehavior, getAccessibleRoutes } from "../core/personas/personas";
import { testScenarios, expectedPricingOutcomes, type TestScenario } from "../core/fixtures/test-data";

const { describe, beforeAll, afterAll, beforeEach, afterEach } = test;

const SCENARIO_KEYS = ["primePurchase", "nearPrimeRefi", "subPrimeCashOut", "wholesaleWithCoBorrower"] as const;
const PRICING_PERSONAS: PersonaRole[] = ["PRICING_ANALYST", "ADMIN"];
const INTAKE_STEPS = [
  "quoteIntentChannel",
  "borrowerCredit",
  "loanStructure",
  "property",
  "incomeAssets",
  "preferencesLaunch",
] as const;

type ScenarioKey = typeof SCENARIO_KEYS[number];
type IntakeStep = typeof INTAKE_STEPS[number];

interface IntakeData {
  step: IntakeStep;
  data: Record<string, unknown>;
  completed: boolean;
}

interface TestRunState {
  runId: string;
  optionId: string;
  scenario: ScenarioKey;
  persona: PersonaRole;
  intakeData: IntakeData[];
  traceId: string;
}

const testRunState = new Map<string, TestRunState>();

function buildIntakeData(scenario: TestScenario): Record<string, unknown> {
  return {
    quoteIntent: scenario.loan.quoteIntent,
    channel: scenario.loan.channel,
    borrower: {
      borrowerName: scenario.borrower.borrowerName,
      borrowerRole: scenario.borrower.borrowerRole,
      contactEmail: scenario.borrower.contactEmail,
      creditScore: scenario.borrower.creditScore,
      creditScoreSource: scenario.borrower.creditScoreSource,
      creditReportDate: scenario.borrower.creditReportDate,
      monthlyIncome: scenario.borrower.monthlyIncome,
      incomeType: scenario.borrower.incomeType,
      employmentType: scenario.borrower.employmentType,
      monthlyDebt: scenario.borrower.monthlyDebt,
      liquidAssets: scenario.borrower.liquidAssets,
      reserves: scenario.borrower.reserves,
    },
    coBorrower: scenario.coBorrower ? {
      borrowerName: scenario.coBorrower.borrowerName,
      borrowerRole: scenario.coBorrower.borrowerRole,
      contactEmail: scenario.coBorrower.contactEmail,
      creditScore: scenario.coBorrower.creditScore,
      creditScoreSource: scenario.coBorrower.creditScoreSource,
      creditReportDate: scenario.coBorrower.creditReportDate,
      monthlyIncome: scenario.coBorrower.monthlyIncome,
      incomeType: scenario.coBorrower.incomeType,
      employmentType: scenario.coBorrower.employmentType,
      monthlyDebt: scenario.coBorrower.monthlyDebt,
      liquidAssets: scenario.coBorrower.liquidAssets,
      reserves: scenario.coBorrower.reserves,
    } : undefined,
    loan: {
      quoteIntent: scenario.loan.quoteIntent,
      channel: scenario.loan.channel,
      loanPurpose: scenario.loan.loanPurpose,
      loanAmount: scenario.loan.loanAmount,
      purchasePriceOrValue: scenario.loan.purchasePriceOrValue,
      downPaymentOrEquity: scenario.loan.downPaymentOrEquity,
      lienPosition: scenario.loan.lienPosition,
      termMonths: scenario.loan.termMonths,
      amortizationType: scenario.loan.amortizationType,
      requestedLockPeriodDays: scenario.loan.requestedLockPeriodDays,
    },
    property: {
      propertyState: scenario.property.propertyState,
      propertyCounty: scenario.property.propertyCounty,
      propertyZip: scenario.property.propertyZip,
      propertyType: scenario.property.propertyType,
      occupancyType: scenario.property.occupancyType,
      unitCount: scenario.property.unitCount,
      purchasePrice: scenario.property.purchasePrice,
      appraisedValue: scenario.property.appraisedValue,
    },
    productPreference: scenario.productPreference,
    effectiveDate: scenario.effectiveDate,
  };
}

async function verifyPricingOutcome(
  actualRate: number,
  actualPrice: number,
  expected: typeof expectedPricingOutcomes[ScenarioKey]
): Promise<{ passed: boolean; errors: string[] }> {
  const errors: string[] = [];
  
  if (actualRate < expected.minRate || actualRate > expected.maxRate) {
    errors.push(`Rate ${actualRate} outside expected range [${expected.minRate}, ${expected.maxRate}]`);
  }
  
  if (actualPrice < expected.minPrice || actualPrice > expected.maxPrice) {
    errors.push(`Price ${actualPrice} outside expected range [${expected.minPrice}, ${expected.maxPrice}]`);
  }
  
  return { passed: errors.length === 0, errors };
}

async function verifyDriftForPricing(
  scenarioKey: ScenarioKey,
  persona: PersonaRole,
  actualOutcome: {
    rate: number;
    price: number;
    payment: number;
    apr: number;
    eligibleProducts: string[];
    ineligibleProducts: string[];
    adjustments: Array<{ category: string; bps: number }>;
  }
): Promise<DriftFinding[]> {
  const findings = await driftDetector.comparePricingOutcome(
    scenarioKey,
    persona,
    { scenario: scenarioKey, persona },
    actualOutcome
  );
  
  return findings;
}

async function verifyDriftForEligibility(
  scenarioKey: ScenarioKey,
  persona: PersonaRole,
  actualDecision: string,
  actualBlockers: string[]
): Promise<DriftFinding[]> {
  return driftDetector.compareEligibility(scenarioKey, persona, { scenario: scenarioKey }, actualDecision, actualBlockers);
}

async function verifyDriftForMargins(
  scenarioKey: ScenarioKey,
  persona: PersonaRole,
  actualMargins: {
    companyMarginBps: number;
    branchMarginBps: number;
    loMarginBps: number;
    floorPass: boolean;
  }
): Promise<DriftFinding[]> {
  const key = `${scenarioKey}-${persona}`;
  const baseline = driftDetector['baselines'].get(key);
  const findings: DriftFinding[] = [];
  
  if (!baseline) return findings;
  
  const marginExpectation = baseline.expectations.marginCalculations.find(
    m => m.scenario === scenarioKey
  );
  
  if (!marginExpectation) return findings;
  
  if (actualMargins.companyMarginBps < marginExpectation.expected.companyMarginBps.min ||
      actualMargins.companyMarginBps > marginExpectation.expected.companyMarginBps.max) {
    findings.push({
      category: 'margin',
      severity: 'CRITICAL',
      description: `Company margin outside expected range for ${scenarioKey}`,
      expected: `${marginExpectation.expected.companyMarginBps.min} - ${marginExpectation.expected.companyMarginBps.max}`,
      actual: actualMargins.companyMarginBps,
      baselineVersion: baseline.version,
      currentVersion: 'current',
      recommendation: 'Investigate margin calculation changes',
    });
  }
  
  if (actualMargins.floorPass !== marginExpectation.expected.floorPass) {
    findings.push({
      category: 'margin',
      severity: 'WARNING',
      description: `Floor pass mismatch for ${scenarioKey}`,
      expected: marginExpectation.expected.floorPass,
      actual: actualMargins.floorPass,
      baselineVersion: baseline.version,
      currentVersion: 'current',
      recommendation: 'Verify floor rule evaluation',
    });
  }
  
  return findings;
}

async function runDriftReport(findings: DriftFinding[], baselineVersion: string, scenario: string, persona: string): Promise<void> {
  if (findings.length > 0) {
    const report = driftDetector.generateReport(findings, baselineVersion, "current");
    console.log(`\n=== DRIFT REPORT: ${scenario} (${persona}) ===`);
    console.log(JSON.stringify(report, null, 2));
    
    if (report.overallSeverity === "CRITICAL") {
      throw new Error(`CRITICAL drift detected for ${scenario} (${persona}): ${findings.filter(f => f.severity === "CRITICAL").map(f => f.description).join(", ")}`);
    }
  }
}

describe("Pricing Engine E2E Tests", () => {
  let api: typeof apiHelper;
  let globalTraceId: string;
  
  beforeAll(async () => {
    await apiHelper.init();
    globalTraceId = `e2e-pricing-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    
    driftDetector.loadBaselinesFromDir('./tests/baselines/pricing');
  });
  
  afterAll(async () => {
    await apiHelper.dispose();
  });
  
  // ========================================================================
  // INTAKE FLOW - Creates quote runs for all scenarios
  // ========================================================================
  describe("Quote Intake Flow", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      
      describe(`Scenario: ${scenario.scenarioName}`, () => {
        for (const personaRole of PRICING_PERSONAS) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(`${personaRole} - not applicable for ${scenarioKey}`, () => {});
            continue;
          }
          
          test(`${personaRole} - Full intake flow`, async () => {
            const ctx = apiHelper.createContext(personaRole);
            ctx.traceId = globalTraceId;
            
            // Step 1: Quote Intent & Channel
            await test.step("Step 1: Quote Intent & Channel", async () => {
              const intakeStep1 = {
                quoteIntent: scenario.loan.quoteIntent,
                channel: scenario.loan.channel,
              };
              
              const validation = await apiHelper.validateIntake(ctx, { ...buildIntakeData(scenario), ...intakeStep1 });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });
            
            // Step 2: Borrower & Credit
            await test.step("Step 2: Borrower & Credit", async () => {
              const borrowerData = {
                borrower: scenario.borrower,
                coBorrower: scenario.coBorrower,
              };
              
              const validation = await apiHelper.validateIntake(ctx, { ...buildIntakeData(scenario), ...borrowerData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });
            
            // Step 3: Loan Structure
            await test.step("Step 3: Loan Structure", async () => {
              const loanData = { loan: scenario.loan };
              const validation = await apiHelper.validateIntake(ctx, { ...buildIntakeData(scenario), ...loanData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });
            
            // Step 4: Property
            await test.step("Step 4: Property", async () => {
              const propertyData = { property: scenario.property };
              const validation = await apiHelper.validateIntake(ctx, { ...buildIntakeData(scenario), ...propertyData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });
            
            // Step 5: Income & Assets
            await test.step("Step 5: Income & Assets", async () => {
              const incomeData = {
                incomeAssets: {
                  monthlyIncome: scenario.borrower.monthlyIncome,
                  incomeType: scenario.borrower.incomeType,
                  employmentType: scenario.borrower.employmentType,
                  monthlyDebt: scenario.borrower.monthlyDebt,
                  liquidAssets: scenario.borrower.liquidAssets,
                  reserves: scenario.borrower.reserves,
                },
              };
              const validation = await apiHelper.validateIntake(ctx, { ...buildIntakeData(scenario), ...incomeData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });
            
            // Step 6: Preferences & Launch Quote Run
            await test.step("Step 6: Preferences & Launch Quote Run", async () => {
              const fullIntake = buildIntakeData(scenario);
              const launchResponse = await apiHelper.launchQuoteRun(ctx, fullIntake);
              expect(launchResponse.status).toBe(201);
              expect(launchResponse.data.runId).toBeDefined();
              expect(launchResponse.data.status).toBe("PROCESSING");
              
              const runId = launchResponse.data.runId;
              
              // Poll for completion
              let status = "PROCESSING";
              let attempts = 0;
              const maxAttempts = 30;
              
              while (status === "PROCESSING" && attempts < maxAttempts) {
                await new Promise(resolve => setTimeout(resolve, 2000));
                const statusResponse = await apiHelper.getQuoteRunStatus(ctx, runId);
                expect(statusResponse.status).toBe(200);
                status = statusResponse.data.status;
                attempts++;
              }
              
              expect(status).toBe("COMPLETED");
              
              // Get offers to select one
              const offersResponse = await apiHelper.getOffers(ctx, runId);
              expect(offersResponse.status).toBe(200);
              expect(offersResponse.data.offers.length).toBeGreaterThan(0);
              
              const selectedOffer = offersResponse.data.offers[0];
              
              // Store run state
              const runState: TestRunState = {
                runId,
                optionId: selectedOffer.optionId,
                scenario: scenarioKey,
                persona: personaRole,
                intakeData: INTAKE_STEPS.map(step => ({ step, data: {}, completed: true })),
                traceId: globalTraceId,
              };
              testRunState.set(`${personaRole}-${scenarioKey}`, runState);
            });
          });
        }
      });
    }
  });
  
  // ========================================================================
  // PRICING WATERFALL SCREEN TESTS
  // ========================================================================
  describe("Pricing Waterfall Screen", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      
      describe(`Scenario: ${scenario.scenarioName}`, () => {
        for (const personaRole of PRICING_PERSONAS) {
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(`${personaRole} - not applicable for ${scenarioKey}`, () => {});
            continue;
          }
          
          test(`${personaRole} - Waterfall: base selection renders from backend`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            // API call to get waterfall data (verifies backend data source)
            const waterfallResponse = await apiHelper.getPricingWaterfall(ctx, runState.runId);
            expect(waterfallResponse.status).toBe(200);
            expect(waterfallResponse.data.ledger).toBeDefined();
            expect(Array.isArray(waterfallResponse.data.ledger)).toBe(true);
            expect(waterfallResponse.data.ledger.length).toBeGreaterThan(0);
            
            // Verify each ledger step has required fields from backend
            for (const step of waterfallResponse.data.ledger) {
              expect(step).toHaveProperty('stepName');
              expect(step).toHaveProperty('stepType');
              expect(step).toHaveProperty('value');
              expect(step).toHaveProperty('basisPoints');
              expect(step).toHaveProperty('configRef');
              expect(step).toHaveProperty('auditRef');
            }
            
            // Navigate to UI
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/pricing-waterfall`);
            expect(navResult.success).toBe(true);
            
            // Verify base selection visible
            await expect(page.locator('[data-testid="base-selection"]')).toBeVisible();
            
            // Verify ledger steps rendered
            const ledgerSteps = page.locator('[data-testid="ledger-step"]');
            await expect(ledgerSteps).toHaveCount(await ledgerSteps.count());
            expect(await ledgerSteps.count()).toBeGreaterThan(0);
            
            // Verify final price displayed
            await expect(page.locator('[data-testid="final-price"]')).toBeVisible();
            
            // Verify config refs present
            const configRefs = page.locator('[data-testid="config-ref"]');
            expect(await configRefs.count()).toBeGreaterThan(0);
            
            // Verify audit refs present
            const auditRefs = page.locator('[data-testid="audit-ref"]');
            expect(await auditRefs.count()).toBeGreaterThan(0);
          });
          
          test(`${personaRole} - Waterfall: redaction handling for sensitive data`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const waterfallResponse = await apiHelper.getPricingWaterfall(ctx, runState.runId);
            expect(waterfallResponse.status).toBe(200);
            
            // Check for redacted fields in API response
            const hasRedacted = waterfallResponse.data.ledger.some((step: any) => 
              step.redacted === true || step.value === "[REDACTED]" || step.value === "***"
            );
            
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/pricing-waterfall`);
            expect(navResult.success).toBe(true);
            
            // Verify redaction UI indicators
            const redactedElements = page.locator('[data-testid="redacted-value"]');
            if (hasRedacted) {
              await expect(redactedElements.first()).toBeVisible();
              await expect(redactedElements.first()).toContainText("[REDACTED]");
            }
            
            // Verify tooltip explains redaction reason
            if (await redactedElements.count() > 0) {
              await redactedElements.first().hover();
              await expect(page.locator('[role="tooltip"]')).toContainText("redacted");
            }
          });
          
          test(`${personaRole} - Waterfall: export functionality (CSV/JSON)`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/pricing-waterfall`);
            expect(navResult.success).toBe(true);
            
            // Test CSV export
            const csvExportPromise = page.waitForEvent('download');
            await page.click('[data-testid="export-csv"]');
            const csvDownload = await csvExportPromise;
            expect(csvDownload.suggestedFilename()).toMatch(/waterfall-.*\.csv$/);
            
            // Test JSON export
            const jsonExportPromise = page.waitForEvent('download');
            await page.click('[data-testid="export-json"]');
            const jsonDownload = await jsonExportPromise;
            expect(jsonDownload.suggestedFilename()).toMatch(/waterfall-.*\.json$/);
            
            // Verify export contains replay hash
            const jsonContent = await jsonDownload.createReadStream();
            const jsonText = await new Promise<string>((resolve) => {
              let data = '';
              jsonContent.on('data', (chunk: Buffer) => data += chunk.toString());
              jsonContent.on('end', () => resolve(data));
            });
            const exportedData = JSON.parse(jsonText);
            expect(exportedData).toHaveProperty('replayHash');
            expect(exportedData).toHaveProperty('ledger');
            expect(Array.isArray(exportedData.ledger)).toBe(true);
          });
          
          test(`${personaRole} - Waterfall: drift detection against baseline`, async () => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const waterfallResponse = await apiHelper.getPricingWaterfall(ctx, runState.runId);
            expect(waterfallResponse.status).toBe(200);
            
            // Build actual outcome from waterfall
            const finalStep = waterfallResponse.data.ledger[waterfallResponse.data.ledger.length - 1];
            const actualOutcome = {
              rate: finalStep.rate || 0,
              price: finalStep.price || 0,
              payment: finalStep.payment || 0,
              apr: finalStep.apr || 0,
              eligibleProducts: waterfallResponse.data.eligibleProducts || [],
              ineligibleProducts: waterfallResponse.data.ineligibleProducts || [],
              adjustments: waterfallResponse.data.ledger
                .filter((s: any) => s.stepType === 'ADJUSTMENT')
                .map((s: any) => ({ category: s.category, bps: s.basisPoints })),
            };
            
            const findings = await verifyDriftForPricing(scenarioKey, personaRole, actualOutcome);
            await runDriftReport(findings, "1.0.0", scenarioKey, personaRole);
          });
        }
      });
    }
  });
  
  // ========================================================================
  // ADJUSTMENT EVIDENCE SCREEN TESTS
  // ========================================================================
  describe("Adjustment Evidence Screen", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      
      describe(`Scenario: ${scenario.scenarioName}`, () => {
        for (const personaRole of PRICING_PERSONAS) {
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(`${personaRole} - not applicable for ${scenarioKey}`, () => {});
            continue;
          }
          
          test(`${personaRole} - Adjustments: table renders with fact refs from backend`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            // API call to get adjustment evidence
            const adjResponse = await apiHelper.getAdjustmentEvidence(ctx);
            expect(adjResponse.status).toBe(200);
            expect(adjResponse.data.adjustments).toBeDefined();
            expect(Array.isArray(adjResponse.data.adjustments)).toBe(true);
            
            // Verify each adjustment has fact refs
            for (const adj of adjResponse.data.adjustments) {
              expect(adj).toHaveProperty('category');
              expect(adj).toHaveProperty('description');
              expect(adj).toHaveProperty('basisPoints');
              expect(adj).toHaveProperty('factRefs');
              expect(Array.isArray(adj.factRefs)).toBe(true);
              expect(adj.factRefs.length).toBeGreaterThan(0);
              expect(adj).toHaveProperty('configRef');
              expect(adj).toHaveProperty('auditRef');
            }
            
            // Navigate to UI
            const navResult = await helper.navigateTo('/pricing/adjustments');
            expect(navResult.success).toBe(true);
            
            // Verify adjustments table rendered
            const adjTable = page.locator('[data-testid="adjustments-table"]');
            await expect(adjTable).toBeVisible();
            
            const rows = page.locator('[data-testid="adjustment-row"]');
            expect(await rows.count()).toBeGreaterThan(0);
            
            // Verify fact refs displayed
            const factRefCells = page.locator('[data-testid="fact-ref"]');
            expect(await factRefCells.count()).toBeGreaterThan(0);
          });
          
          test(`${personaRole} - Adjustments: conflict detection and display`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const adjResponse = await apiHelper.getAdjustmentEvidence(ctx);
            expect(adjResponse.status).toBe(200);
            
            // Check for conflicts in API response
            const hasConflicts = adjResponse.data.adjustments.some((a: any) => a.conflicts && a.conflicts.length > 0);
            
            const navResult = await helper.navigateTo('/pricing/adjustments');
            expect(navResult.success).toBe(true);
            
            // Verify conflict badges if present
            if (hasConflicts) {
              const conflictBadges = page.locator('[data-testid="conflict-badge"]');
              await expect(conflictBadges.first()).toBeVisible();
              
              // Click conflict to see details
              await conflictBadges.first().click();
              await expect(page.locator('[data-testid="conflict-detail"]')).toBeVisible();
              await expect(page.locator('[data-testid="conflict-detail"]')).toContainText("conflict");
            }
          });
          
          test(`${personaRole} - Adjustments: compensation hooks rendered`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const adjResponse = await apiHelper.getAdjustmentEvidence(ctx);
            expect(adjResponse.status).toBe(200);
            
            // Check for compensation hooks
            const hasCompensation = adjResponse.data.adjustments.some((a: any) => a.compensationHook !== undefined);
            
            const navResult = await helper.navigateTo('/pricing/adjustments');
            expect(navResult.success).toBe(true);
            
            if (hasCompensation) {
              const compElements = page.locator('[data-testid="compensation-hook"]');
              await expect(compElements.first()).toBeVisible();
              await expect(compElements.first()).toContainText(/compensation|hook/i);
            }
          });
          
          test(`${personaRole} - Adjustments: category summaries and drill-down`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const adjResponse = await apiHelper.getAdjustmentEvidence(ctx);
            expect(adjResponse.status).toBe(200);
            
            // Verify category summaries in API
            expect(adjResponse.data.categorySummaries).toBeDefined();
            expect(Array.isArray(adjResponse.data.categorySummaries)).toBe(true);
            
            for (const summary of adjResponse.data.categorySummaries) {
              expect(summary).toHaveProperty('category');
              expect(summary).toHaveProperty('totalBps');
              expect(summary).toHaveProperty('count');
              expect(summary).toHaveProperty('auditRef');
            }
            
            const navResult = await helper.navigateTo('/pricing/adjustments');
            expect(navResult.success).toBe(true);
            
            // Verify category summary cards
            const summaryCards = page.locator('[data-testid="category-summary"]');
            expect(await summaryCards.count()).toBeGreaterThan(0);
            
            // Click category to drill down
            await summaryCards.first().click();
            await expect(page.locator('[data-testid="category-detail"]')).toBeVisible();
          });
          
          test(`${personaRole} - Adjustments: drift detection against baseline`, async () => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const adjResponse = await apiHelper.getAdjustmentEvidence(ctx);
            expect(adjResponse.status).toBe(200);
            
            // Build adjustment outcome for drift detection
            const actualOutcome = {
              rate: 0,
              price: 0,
              payment: 0,
              apr: 0,
              eligibleProducts: [],
              ineligibleProducts: [],
              adjustments: adjResponse.data.adjustments.map((a: any) => ({
                category: a.category,
                bps: a.basisPoints,
              })),
            };
            
            const findings = await verifyDriftForPricing(scenarioKey, personaRole, actualOutcome);
            await runDriftReport(findings, "1.0.0", scenarioKey, personaRole);
          });
        }
      });
    }
  });
  
  // ========================================================================
  // MARGIN PROFITABILITY SCREEN TESTS
  // ========================================================================
  describe("Margin Profitability Screen", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      
      describe(`Scenario: ${scenario.scenarioName}`, () => {
        for (const personaRole of PRICING_PERSONAS) {
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(`${personaRole} - not applicable for ${scenarioKey}`, () => {});
            continue;
          }
          
          test(`${personaRole} - Margins: sections render from backend`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const marginResponse = await apiHelper.getMarginProfitability(ctx);
            expect(marginResponse.status).toBe(200);
            expect(marginResponse.data.sections).toBeDefined();
            expect(Array.isArray(marginResponse.data.sections)).toBe(true);
            expect(marginResponse.data.sections.length).toBeGreaterThan(0);
            
            // Verify each section has required fields
            for (const section of marginResponse.data.sections) {
              expect(section).toHaveProperty('sectionName');
              expect(section).toHaveProperty('companyMarginBps');
              expect(section).toHaveProperty('branchMarginBps');
              expect(section).toHaveProperty('loMarginBps');
              expect(section).toHaveProperty('floorEvidence');
              expect(section).toHaveProperty('approvalStatus');
              expect(section).toHaveProperty('configRef');
              expect(section).toHaveProperty('auditRef');
            }
            
            const navResult = await helper.navigateTo('/pricing/margins');
            expect(navResult.success).toBe(true);
            
            // Verify margin sections rendered
            const marginSections = page.locator('[data-testid="margin-section"]');
            await expect(marginSections).toHaveCount(await marginSections.count());
            expect(await marginSections.count()).toBeGreaterThan(0);
          });
          
          test(`${personaRole} - Margins: floor evidence display`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const marginResponse = await apiHelper.getMarginProfitability(ctx);
            expect(marginResponse.status).toBe(200);
            
            // Check for floor evidence in API
            const hasFloorEvidence = marginResponse.data.sections.some((s: any) => s.floorEvidence && s.floorEvidence.length > 0);
            
            const navResult = await helper.navigateTo('/pricing/margins');
            expect(navResult.success).toBe(true);
            
            if (hasFloorEvidence) {
              const floorBadges = page.locator('[data-testid="floor-evidence"]');
              await expect(floorBadges.first()).toBeVisible();
              
              // Click to see floor rule details
              await floorBadges.first().click();
              await expect(page.locator('[data-testid="floor-rule-detail"]')).toBeVisible();
              await expect(page.locator('[data-testid="floor-rule-detail"]')).toContainText(/floor|minimum/i);
            }
          });
          
          test(`${personaRole} - Margins: approval status workflow`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const marginResponse = await apiHelper.getMarginProfitability(ctx);
            expect(marginResponse.status).toBe(200);
            
            // Verify approval statuses present
            const approvalStatuses = marginResponse.data.sections.map((s: any) => s.approvalStatus);
            expect(approvalStatuses.length).toBeGreaterThan(0);
            
            const navResult = await helper.navigateTo('/pricing/margins');
            expect(navResult.success).toBe(true);
            
            // Verify approval status badges
            const statusBadges = page.locator('[data-testid="approval-status"]');
            expect(await statusBadges.count()).toBeGreaterThan(0);
            
            // Verify status values are valid
            const validStatuses = ['PENDING', 'APPROVED', 'REJECTED', 'REQUIRES_REVIEW'];
            for (let i = 0; i < await statusBadges.count(); i++) {
              const badgeText = await statusBadges.nth(i).textContent();
              expect(validStatuses).toContain(badgeText?.trim().toUpperCase());
            }
          });
          
          test(`${personaRole} - Margins: redaction handling for sensitive margin data`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const marginResponse = await apiHelper.getMarginProfitability(ctx);
            expect(marginResponse.status).toBe(200);
            
            // Check for redacted margin fields
            const hasRedacted = marginResponse.data.sections.some((s: any) => 
              s.redacted === true || s.companyMarginBps === "[REDACTED]"
            );
            
            const navResult = await helper.navigateTo('/pricing/margins');
            expect(navResult.success).toBe(true);
            
            if (hasRedacted) {
              const redactedElements = page.locator('[data-testid="redacted-margin"]');
              await expect(redactedElements.first()).toBeVisible();
              await expect(redactedElements.first()).toContainText("[REDACTED]");
            }
          });
          
          test(`${personaRole} - Margins: drift detection against baseline`, async () => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            
            const marginResponse = await apiHelper.getMarginProfitability(ctx);
            expect(marginResponse.status).toBe(200);
            
            // Aggregate margin data across sections
            const totalCompanyBps = marginResponse.data.sections.reduce((sum: number, s: any) => sum + (s.companyMarginBps || 0), 0);
            const totalBranchBps = marginResponse.data.sections.reduce((sum: number, s: any) => sum + (s.branchMarginBps || 0), 0);
            const totalLoBps = marginResponse.data.sections.reduce((sum: number, s: any) => sum + (s.loMarginBps || 0), 0);
            const allFloorsPass = marginResponse.data.sections.every((s: any) => s.floorPass !== false);
            
            const actualMargins = {
              companyMarginBps: totalCompanyBps,
              branchMarginBps: totalBranchBps,
              loMarginBps: totalLoBps,
              floorPass: allFloorsPass,
            };
            
            const findings = await verifyDriftForMargins(scenarioKey, personaRole, actualMargins);
            await runDriftReport(findings, "1.0.0", scenarioKey, personaRole);
          });
        }
      });
    }
  });
  
  // ========================================================================
  // ELIGIBILITY EXPLANATION TESTS
  // ========================================================================
  describe("Eligibility Explanation Screen", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      
      describe(`Scenario: ${scenario.scenarioName}`, () => {
        for (const personaRole of PRICING_PERSONAS) {
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(`${personaRole} - not applicable for ${scenarioKey}`, () => {});
            continue;
          }
          
          // Only PRICING_ANALYST, ADMIN, COMPLIANCE_OFFICER can view eligibility
          const canViewEligibility = ["PRICING_ANALYST", "ADMIN", "COMPLIANCE_OFFICER"].includes(personaRole);
          if (!canViewEligibility) {
            test.skip(`${personaRole} - cannot view eligibility`, () => {});
            continue;
          }
          
          test(`${personaRole} - Eligibility: decision badge from backend`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            const eligResponse = await apiHelper.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligResponse.status).toBe(200);
            expect(eligResponse.data.decision).toBeDefined();
            expect(['ELIGIBLE', 'INELIGIBLE', 'CONDITIONAL']).toContain(eligResponse.data.decision);
            expect(eligResponse.data.blockers).toBeDefined();
            expect(Array.isArray(eligResponse.data.blockers)).toBe(true);
            expect(eligResponse.data.factTraceability).toBeDefined();
            expect(eligResponse.data.cacheHealth).toBeDefined();
            
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/eligibility`);
            expect(navResult.success).toBe(true);
            
            // Verify decision badge
            const decisionBadge = page.locator(`[data-testid="eligibility-badge"][data-decision="${eligResponse.data.decision}"]`);
            await expect(decisionBadge).toBeVisible();
            await expect(decisionBadge).toContainText(eligResponse.data.decision);
          });
          
          test(`${personaRole} - Eligibility: blockers displayed with reasons`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            const eligResponse = await apiHelper.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligResponse.status).toBe(200);
            
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/eligibility`);
            expect(navResult.success).toBe(true);
            
            // Verify blockers if any
            if (eligResponse.data.blockers.length > 0) {
              const blockers = page.locator('[data-testid="eligibility-blocker"]');
              await expect(blockers).toHaveCount(eligResponse.data.blockers.length);
              
              for (let i = 0; i < eligResponse.data.blockers.length; i++) {
                await expect(blockers.nth(i)).toContainText(eligResponse.data.blockers[i].code);
                await expect(blockers.nth(i)).toContainText(eligResponse.data.blockers[i].reason);
              }
            } else {
              const noBlockers = page.locator('[data-testid="no-blockers"]');
              await expect(noBlockers).toBeVisible();
            }
          });
          
          test(`${personaRole} - Eligibility: fact traceability links`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            const eligResponse = await apiHelper.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligResponse.status).toBe(200);
            expect(eligResponse.data.factTraceability).toBeDefined();
            expect(Array.isArray(eligResponse.data.factTraceability)).toBe(true);
            
            // Verify each fact has traceability info
            for (const fact of eligResponse.data.factTraceability) {
              expect(fact).toHaveProperty('factId');
              expect(fact).toHaveProperty('source');
              expect(fact).toHaveProperty('value');
              expect(fact).toHaveProperty('auditRef');
              expect(fact).toHaveProperty('replayHash');
            }
            
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/eligibility`);
            expect(navResult.success).toBe(true);
            
            // Verify fact traceability links in UI
            const factLinks = page.locator('[data-testid="fact-trace-link"]');
            expect(await factLinks.count()).toBeGreaterThanOrEqual(eligResponse.data.factTraceability.length);
            
            // Click first fact link to verify traceability modal
            if (await factLinks.count() > 0) {
              await factLinks.first().click();
              await expect(page.locator('[data-testid="fact-trace-modal"]')).toBeVisible();
              await expect(page.locator('[data-testid="fact-trace-modal"]')).toContainText("auditRef");
            }
          });
          
          test(`${personaRole} - Eligibility: cache health indicators`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            const eligResponse = await apiHelper.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligResponse.status).toBe(200);
            expect(eligResponse.data.cacheHealth).toBeDefined();
            expect(eligResponse.data.cacheHealth).toHaveProperty('status');
            expect(['HEALTHY', 'DEGRADED', 'STALE', 'MISSING']).toContain(eligResponse.data.cacheHealth.status);
            expect(eligResponse.data.cacheHealth).toHaveProperty('lastRefresh');
            expect(eligResponse.data.cacheHealth).toHaveProperty('ttlSeconds');
            
            const navResult = await helper.navigateTo(`/quote/${runState.runId}/eligibility`);
            expect(navResult.success).toBe(true);
            
            // Verify cache health indicator
            const cacheIndicator = page.locator('[data-testid="cache-health"]');
            await expect(cacheIndicator).toBeVisible();
            await expect(cacheIndicator).toContainText(eligResponse.data.cacheHealth.status);
          });
          
          test(`${personaRole} - Eligibility: required next facts for CONDITIONAL decisions`, async ({ page }) => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const helper = uiHelper(page);
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            const eligResponse = await apiHelper.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligResponse.status).toBe(200);
            
            // If CONDITIONAL, verify required next facts
            if (eligResponse.data.decision === 'CONDITIONAL') {
              expect(eligResponse.data.requiredNextFacts).toBeDefined();
              expect(Array.isArray(eligResponse.data.requiredNextFacts)).toBe(true);
              expect(eligResponse.data.requiredNextFacts.length).toBeGreaterThan(0);
              
              const navResult = await helper.navigateTo(`/quote/${runState.runId}/eligibility`);
              expect(navResult.success).toBe(true);
              
              const nextFactsList = page.locator('[data-testid="required-next-facts"]');
              await expect(nextFactsList).toBeVisible();
              await expect(nextFactsList).toContainText(eligResponse.data.requiredNextFacts[0]);
            }
          });
          
          test(`${personaRole} - Eligibility: drift detection against baseline`, async () => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            const eligResponse = await apiHelper.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligResponse.status).toBe(200);
            
            const findings = await verifyDriftForEligibility(
              scenarioKey,
              personaRole,
              eligResponse.data.decision,
              eligResponse.data.blockers.map((b: any) => b.code)
            );
            
            await runDriftReport(findings, "1.0.0", scenarioKey, personaRole);
          });
        }
      });
    }
  });
  
  // ========================================================================
  // COMPREHENSIVE DRIFT DETECTION ACROSS ALL PRICING OUTCOMES
  // ========================================================================
  describe("Comprehensive Drift Detection", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      
      describe(`Scenario: ${scenario.scenarioName}`, () => {
        for (const personaRole of PRICING_PERSONAS) {
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(`${personaRole} - not applicable for ${scenarioKey}`, () => {});
            continue;
          }
          
          test(`${personaRole} - Full pricing outcome drift detection`, async () => {
            const runState = testRunState.get(`${personaRole}-${scenarioKey}`);
            if (!runState) { test.skip("No run state available"); return; }
            
            const ctx = apiHelper.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            
            // Collect all pricing outcomes
            const [waterfallResponse, adjResponse, marginResponse] = await Promise.all([
              apiHelper.getPricingWaterfall(ctx, runState.runId),
              apiHelper.getAdjustmentEvidence(ctx),
              apiHelper.getMarginProfitability(ctx),
            ]);
            
            expect(waterfallResponse.status).toBe(200);
            expect(adjResponse.status).toBe(200);
            expect(marginResponse.status).toBe(200);
            
            // Build comprehensive outcome
            const finalWaterfallStep = waterfallResponse.data.ledger[waterfallResponse.data.ledger.length - 1];
            const actualOutcome = {
              rate: finalWaterfallStep.rate || 0,
              price: finalWaterfallStep.price || 0,
              payment: finalWaterfallStep.payment || 0,
              apr: finalWaterfallStep.apr || 0,
              eligibleProducts: waterfallResponse.data.eligibleProducts || [],
              ineligibleProducts: waterfallResponse.data.ineligibleProducts || [],
              adjustments: adjResponse.data.adjustments.map((a: any) => ({
                category: a.category,
                bps: a.basisPoints,
              })),
            };
            
            // Run pricing drift detection
            const pricingFindings = await verifyDriftForPricing(scenarioKey, personaRole, actualOutcome);
            
            // Run margin drift detection
            const totalCompanyBps = marginResponse.data.sections.reduce((sum: number, s: any) => sum + (s.companyMarginBps || 0), 0);
            const totalBranchBps = marginResponse.data.sections.reduce((sum: number, s: any) => sum + (s.branchMarginBps || 0), 0);
            const totalLoBps = marginResponse.data.sections.reduce((sum: number, s: any) => sum + (s.loMarginBps || 0), 0);
            const allFloorsPass = marginResponse.data.sections.every((s: any) => s.floorPass !== false);
            
            const marginFindings = await verifyDriftForMargins(scenarioKey, personaRole, {
              companyMarginBps: totalCompanyBps,
              branchMarginBps: totalBranchBps,
              loMarginBps: totalLoBps,
              floorPass: allFloorsPass,
            });
            
            // Combine and report all findings
            const allFindings = [...pricingFindings, ...marginFindings];
            await runDriftReport(allFindings, "1.0.0", scenarioKey, personaRole);
          });
        }
      });
    }
  });

  // ========================================================================
  // HEADED MODE DEMO TEST
  // ========================================================================
  describe("Headed Mode Demo", () => {
    test("PRICING_ANALYST - Demo: Full pricing engine walkthrough (headed)", async ({ page }, testInfo) => {
      // Only run in headed mode
      if (!testInfo.project.use.headless) {
        const scenarioKey: ScenarioKey = "primePurchase";
        const personaRole: PersonaRole = "PRICING_ANALYST";
        const scenario = testScenarios[scenarioKey];
        
        const ctx = apiHelper.createContext(personaRole);
        ctx.traceId = `demo-${Date.now()}`;
        
        // Run intake flow
        const fullIntake = buildIntakeData(scenario);
        const launchResponse = await apiHelper.launchQuoteRun(ctx, fullIntake);
        expect(launchResponse.status).toBe(201);
        
        const runId = launchResponse.data.runId;
        
        // Poll for completion
        let status = "PROCESSING";
        let attempts = 0;
        while (status === "PROCESSING" && attempts < 30) {
          await new Promise(resolve => setTimeout(resolve, 2000));
          const statusResponse = await apiHelper.getQuoteRunStatus(ctx, runId);
          status = statusResponse.data.status;
          attempts++;
        }
        expect(status).toBe("COMPLETED");
        
        const offersResponse = await apiHelper.getOffers(ctx, runId);
        const selectedOffer = offersResponse.data.offers[0];
        
        const runState: TestRunState = {
          runId,
          optionId: selectedOffer.optionId,
          scenario: scenarioKey,
          persona: personaRole,
          intakeData: INTAKE_STEPS.map(step => ({ step, data: {}, completed: true })),
          traceId: ctx.traceId,
        };
        
        const helper = uiHelper(page);
        
        // 1. Navigate to Offer Comparison
        await test.step("Demo: Offer Comparison Screen", async () => {
          await helper.navigateTo(`/quote/${runId}/offers`);
          await page.waitForTimeout(2000); // Pause for demo
          await expect(page.locator('[data-testid="offer-rate"]')).toBeVisible();
        });
        
        // 2. Navigate to Pricing Waterfall
        await test.step("Demo: Pricing Waterfall Screen", async () => {
          await helper.navigateTo(`/quote/${runId}/pricing-waterfall`);
          await page.waitForTimeout(3000); // Pause for demo
          await expect(page.locator('[data-testid="base-selection"]')).toBeVisible();
          await expect(page.locator('[data-testid="ledger-step"]')).toHaveCount(await page.locator('[data-testid="ledger-step"]').count());
          await expect(page.locator('[data-testid="final-price"]')).toBeVisible();
          
          // Demonstrate export
          await page.click('[data-testid="export-json"]');
          await page.waitForTimeout(1000);
        });
        
        // 3. Navigate to Adjustments
        await test.step("Demo: Adjustment Evidence Screen", async () => {
          await helper.navigateTo('/pricing/adjustments');
          await page.waitForTimeout(2000);
          await expect(page.locator('[data-testid="adjustments-table"]')).toBeVisible();
          
          // Click category summary to drill down
          const summaryCards = page.locator('[data-testid="category-summary"]');
          if (await summaryCards.count() > 0) {
            await summaryCards.first().click();
            await page.waitForTimeout(1000);
          }
        });
        
        // 4. Navigate to Margins
        await test.step("Demo: Margin Profitability Screen", async () => {
          await helper.navigateTo('/pricing/margins');
          await page.waitForTimeout(2000);
          await expect(page.locator('[data-testid="margin-section"]')).toHaveCount(await page.locator('[data-testid="margin-section"]').count());
          
          // Show floor evidence
          const floorBadges = page.locator('[data-testid="floor-evidence"]');
          if (await floorBadges.count() > 0) {
            await floorBadges.first().click();
            await page.waitForTimeout(1000);
          }
        });
        
        // 5. Navigate to Eligibility
        await test.step("Demo: Eligibility Explanation Screen", async () => {
          await helper.navigateTo(`/quote/${runId}/eligibility`);
          await page.waitForTimeout(2000);
          await expect(page.locator('[data-testid="eligibility-badge"]')).toBeVisible();
          await expect(page.locator('[data-testid="cache-health"]')).toBeVisible();
          
          // Show fact traceability
          const factLinks = page.locator('[data-testid="fact-trace-link"]');
          if (await factLinks.count() > 0) {
            await factLinks.first().click();
            await page.waitForTimeout(1000);
          }
        });
        
        console.log("\n=== DEMO COMPLETE ===");
        console.log(`Run ID: ${runId}`);
        console.log(`Option ID: ${selectedOffer.optionId}`);
        console.log(`Trace ID: ${ctx.traceId}`);
      } else {
        test.skip("Headed mode demo only runs with --project=demo-headed");
      }
    });
  });
});

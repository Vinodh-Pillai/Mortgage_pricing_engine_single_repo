import { test, expect } from "@playwright/test";
import { apiHelper, type ApiResponse, type TestContext } from "../core/helpers/api-helper";
import { uiHelper, type NavigationResult, type FormField } from "../core/helpers/ui-helper";
import { driftDetector, type PricingExpectation, type BaselineExpectation } from "../core/drift/drift-detector";
import { personas, type PersonaRole, getPersona, getTestScenarios, getExpectedPricingBehavior } from "../core/personas/personas";
import { testScenarios, expectedPricingOutcomes, type TestScenario } from "../core/fixtures/test-data";

const { describe, beforeAll, afterAll, beforeEach, afterEach } = test;

const SCENARIO_KEYS = ["primePurchase", "nearPrimeRefi", "subPrimeCashOut", "wholesaleWithCoBorrower"] as const;
const PERSONA_ROLES = ["RETAIL_LO", "WHOLESALE_LO", "CORRESPONDENT_LO", "PRICING_ANALYST", "LOCK_DESK"] as const;
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
type PersonaRole = typeof PERSONA_ROLES[number];

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
    errors.push("Rate " + actualRate + " outside expected range [" + expected.minRate + ", " + expected.maxRate + "]");
  }
  if (actualPrice < expected.minPrice || actualPrice > expected.maxPrice) {
    errors.push("Price " + actualPrice + " outside expected range [" + expected.minPrice + ", " + expected.maxPrice + "]");
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
): Promise<void> {
  const findings = await driftDetector.comparePricingOutcome(
    scenarioKey,
    persona,
    { scenario: scenarioKey, persona },
    actualOutcome
  );
  if (findings.length > 0) {
    const report = driftDetector.generateReport(findings, "1.0.0", "current");
    console.log("Drift detected for " + scenarioKey + " (" + persona + "):", JSON.stringify(report, null, 2));
    if (report.overallSeverity === "CRITICAL") {
      throw new Error("Critical drift detected: " + findings.filter(f => f.severity === "CRITICAL").map(f => f.description).join(", "));
    }
  }
}

describe("Quote Lifecycle E2E Tests", () => {
  let testRunState = new Map<string, TestRunState>();
  const api = apiHelper;
  const ui = uiHelper;

  beforeAll(async () => {
    await api.init();
    console.log("API Helper initialized");
  });

  afterAll(async () => {
    await api.dispose();
    console.log("API Helper disposed");
  });

  // ========================================================================
  // INTAKE FLOW TESTS - Progressive 6-step quote intake
  // ========================================================================
  describe("Progressive Quote Intake (6 Steps)", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      const expectedOutcome = expectedPricingOutcomes[scenarioKey];
      
      describe("Scenario: " + scenario.scenarioName, () => {
        for (const personaRole of PERSONA_ROLES) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(personaRole + " - not applicable for " + scenarioKey, () => {});
            continue;
          }

          test(personaRole + " - Full intake flow", async ({ page }) => {
            const helper = ui(page);
            const traceId = "e2e-" + personaRole + "-" + scenarioKey + "-" + Date.now();
            const ctx = api.createContext(personaRole);
            ctx.traceId = traceId;

            // Step 1: Quote Intent & Channel
            await test.step("Step 1: Quote Intent & Channel", async () => {
              const result = await helper.navigateTo("/quote/start");
              expect(result.success).toBe(true);
              const intakeStep1 = { quoteIntent: scenario.loan.quoteIntent, channel: scenario.loan.channel };
              const validation = await api.validateIntake(ctx, { ...buildIntakeData(scenario), ...intakeStep1 });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });

            // Step 2: Borrower & Credit
            await test.step("Step 2: Borrower & Credit", async () => {
              const borrowerData = { borrower: scenario.borrower, coBorrower: scenario.coBorrower };
              const validation = await api.validateIntake(ctx, { ...buildIntakeData(scenario), ...borrowerData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });

            // Step 3: Loan Structure
            await test.step("Step 3: Loan Structure", async () => {
              const loanData = { loan: scenario.loan };
              const validation = await api.validateIntake(ctx, { ...buildIntakeData(scenario), ...loanData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });

            // Step 4: Property
            await test.step("Step 4: Property", async () => {
              const propertyData = { property: scenario.property };
              const validation = await api.validateIntake(ctx, { ...buildIntakeData(scenario), ...propertyData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });

            // Step 5: Income & Assets
            await test.step("Step 5: Income & Assets", async () => {
              const incomeData = { incomeAssets: { monthlyIncome: scenario.borrower.monthlyIncome, incomeType: scenario.borrower.incomeType, employmentType: scenario.borrower.employmentType, monthlyDebt: scenario.borrower.monthlyDebt, liquidAssets: scenario.borrower.liquidAssets, reserves: scenario.borrower.reserves } };
              const validation = await api.validateIntake(ctx, { ...buildIntakeData(scenario), ...incomeData });
              expect(validation.status).toBe(200);
              expect(validation.data.valid).toBe(true);
            });

            // Step 6: Preferences & Launch Quote Run
            await test.step("Step 6: Preferences & Launch Quote Run", async () => {
              const fullIntake = buildIntakeData(scenario);
              const launchResponse = await api.launchQuoteRun(ctx, fullIntake);
              expect(launchResponse.status).toBe(201);
              expect(launchResponse.data.runId).toBeDefined();
              expect(launchResponse.data.status).toBe("PROCESSING");
              const runId = launchResponse.data.runId;
              let status = "PROCESSING";
              let attempts = 0;
              const maxAttempts = 30;
              while (status === "PROCESSING" && attempts < maxAttempts) {
                await new Promise(resolve => setTimeout(resolve, 2000));
                const statusResponse = await api.getQuoteRunStatus(ctx, runId);
                expect(statusResponse.status).toBe(200);
                status = statusResponse.data.status;
                attempts++;
              }
              expect(status).toBe("COMPLETED");
              const runState = { runId, optionId: "", scenario: scenarioKey, persona: personaRole, intakeData: INTAKE_STEPS.map(step => ({ step, data: {}, completed: true })), traceId };
              testRunState.set(personaRole + "-" + scenarioKey, runState);
            });
          });
        }
      });
    }
  });

  // ========================================================================
  // OFFER COMPARISON TESTS
  // ========================================================================
  describe("Offer Comparison Screen", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      const expectedOutcome = expectedPricingOutcomes[scenarioKey];
      
      describe("Scenario: " + scenario.scenarioName, () => {
        for (const personaRole of PERSONA_ROLES) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(personaRole + " - not applicable for " + scenarioKey, () => {});
            continue;
          }

          test(personaRole + " - Offer comparison loads and verifies offers", async ({ page }) => {
            const helper = ui(page);
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState) { test.skip("No run state available - intake test must run first"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            const result = await helper.navigateTo("/quote/" + runState.runId + "/offers");
            expect(result.success).toBe(true);
            const offersResponse = await api.getOffers(ctx, runState.runId);
            expect(offersResponse.status).toBe(200);
            expect(offersResponse.data.offers).toBeDefined();
            expect(Array.isArray(offersResponse.data.offers)).toBe(true);
            expect(offersResponse.data.offers.length).toBeGreaterThan(0);
            for (const offer of offersResponse.data.offers) {
              expect(offer.optionId).toBeDefined();
              expect(typeof offer.rate).toBe("number");
              expect(typeof offer.price).toBe("number");
              expect(typeof offer.payment).toBe("number");
              expect(typeof offer.apr).toBe("number");
              expect(offer.product).toBeDefined();
            }
            const sortedByRate = [...offersResponse.data.offers].sort((a, b) => a.rate - b.rate);
            expect(sortedByRate[0].rate).toBeLessThanOrEqual(sortedByRate[sortedByRate.length - 1].rate);
            const sortedByPrice = [...offersResponse.data.offers].sort((a, b) => b.price - a.price);
            expect(sortedByPrice[0].price).toBeGreaterThanOrEqual(sortedByPrice[sortedByPrice.length - 1].price);
            for (const product of expectedOutcome.eligibleProducts) {
              const productOffers = offersResponse.data.offers.filter((o) => o.product === product);
              expect(productOffers.length).toBeGreaterThan(0);
            }
            const eligibleOffer = offersResponse.data.offers.find((o) => o.eligibilityStatus === "ELIGIBLE") || offersResponse.data.offers[0];
            runState.optionId = eligibleOffer.optionId;
            await helper.selectOffer(eligibleOffer.optionId);
            const selectionResponse = await api.selectOffer(ctx, runState.runId, eligibleOffer.optionId, { selected: true });
            expect(selectionResponse.status).toBe(200);
          });

          test(personaRole + " - Offer explanation accessible", async ({ page }) => {
            const helper = ui(page);
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const explanationResponse = await api.getOfferExplanation(ctx, runState.runId, runState.optionId);
            expect(explanationResponse.status).toBe(200);
            expect(explanationResponse.data.explanation).toBeDefined();
            expect(explanationResponse.data.adjustments).toBeDefined();
            expect(Array.isArray(explanationResponse.data.adjustments)).toBe(true);
            const adjustmentCategories = explanationResponse.data.adjustments.map((a) => a.category);
            expect(adjustmentCategories.length).toBeGreaterThan(0);
          });
        }
      });
    }
  });

  // ========================================================================
  // QUOTE DETAIL WATERFALL TESTS
  // ========================================================================
  describe("Quote Detail Waterfall Screen", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      const expectedOutcome = expectedPricingOutcomes[scenarioKey];
      
      describe("Scenario: " + scenario.scenarioName, () => {
        for (const personaRole of PERSONA_ROLES) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          if (!testScenariosForPersona.includes(scenarioKey)) {
            test.skip(personaRole + " - not applicable for " + scenarioKey, () => {});
            continue;
          }
          const canAccessWaterfall = ["RETAIL_LO", "WHOLESALE_LO", "CORRESPONDENT_LO", "PRICING_ANALYST", "LOCK_DESK"].includes(personaRole);
          if (!canAccessWaterfall) { test.skip(personaRole + " - cannot access waterfall", () => {}); continue; }

          test(personaRole + " - Waterfall: base selection, ledger, final price, compliance, audit", async ({ page }) => {
            const helper = ui(page);
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const result = await helper.navigateTo("/quote/" + runState.runId + "/offers/" + runState.optionId);
            expect(result.success).toBe(true);
            const detailResponse = await api.getQuoteDetail(ctx, runState.runId, runState.optionId);
            expect(detailResponse.status).toBe(200);
            expect(detailResponse.data).toBeDefined();
            expect(detailResponse.data.baseSelection).toBeDefined();
            expect(detailResponse.data.baseSelection.product).toBeDefined();
            expect(detailResponse.data.baseSelection.rate).toBeDefined();
            expect(detailResponse.data.baseSelection.price).toBeDefined();
            expect(detailResponse.data.pricingWaterfall).toBeDefined();
            expect(Array.isArray(detailResponse.data.pricingWaterfall.ledger)).toBe(true);
            expect(detailResponse.data.pricingWaterfall.ledger.length).toBeGreaterThanOrEqual(3);
            for (const step of detailResponse.data.pricingWaterfall.ledger) {
              expect(step.category).toBeDefined();
              expect(step.description).toBeDefined();
              expect(step.adjustmentBps).toBeDefined();
              expect(typeof step.adjustmentBps).toBe("number");
              expect(step.runningPrice).toBeDefined();
              expect(typeof step.runningPrice).toBe("number");
            }
            expect(detailResponse.data.finalPrice).toBeDefined();
            expect(detailResponse.data.finalPrice.rate).toBeDefined();
            expect(detailResponse.data.finalPrice.price).toBeDefined();
            expect(detailResponse.data.finalPrice.payment).toBeDefined();
            expect(detailResponse.data.finalPrice.apr).toBeDefined();
            expect(detailResponse.data.compliance).toBeDefined();
            expect(detailResponse.data.compliance.qmCompliance).toBeDefined();
            expect(detailResponse.data.compliance.hpmlStatus).toBeDefined();
            expect(detailResponse.data.compliance.abilityToRepay).toBeDefined();
            expect(detailResponse.data.auditRefs).toBeDefined();
            expect(Array.isArray(detailResponse.data.auditRefs.traceIds)).toBe(true);
            expect(detailResponse.data.auditRefs.traceIds.length).toBeGreaterThan(0);
            expect(detailResponse.data.auditRefs.pricingEngineVersion).toBeDefined();
            expect(detailResponse.data.auditRefs.rateSheetVersion).toBeDefined();
            const verification = await verifyPricingOutcome(detailResponse.data.finalPrice.rate, detailResponse.data.finalPrice.price, expectedOutcome);
            if (!verification.passed) { console.log("Pricing verification errors:", verification.errors); }
            const actualOutcome = {
              rate: detailResponse.data.finalPrice.rate,
              price: detailResponse.data.finalPrice.price,
              payment: detailResponse.data.finalPrice.payment,
              apr: detailResponse.data.finalPrice.apr,
              eligibleProducts: (detailResponse.data.offers || []).map((o) => o.product).filter((p) => expectedOutcome.eligibleProducts.includes(p)),
              ineligibleProducts: (detailResponse.data.offers || []).map((o) => o.product).filter((p) => expectedOutcome.ineligibleProducts.includes(p)),
              adjustments: detailResponse.data.pricingWaterfall.ledger.map((s) => ({ category: s.category, bps: s.adjustmentBps })),
            };
            await verifyDriftForPricing(scenarioKey, personaRole, actualOutcome);
          });

          test(personaRole + " - Pricing waterfall API endpoint", async () => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState) { test.skip("No run state available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            const waterfallResponse = await api.getPricingWaterfall(ctx, runState.runId);
            expect(waterfallResponse.status).toBe(200);
            expect(waterfallResponse.data.ledger).toBeDefined();
            expect(waterfallResponse.data.ledger.length).toBeGreaterThan(0);
          });
        }
      });
    }
  });

  // ========================================================================
  // LOCK WORKFLOW TESTS
  // ========================================================================
  describe("Lock Workflow", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      describe("Scenario: " + scenario.scenarioName, () => {
        const lockingPersonas = ["RETAIL_LO", "WHOLESALE_LO", "CORRESPONDENT_LO", "LOCK_DESK"] as const;
        for (const personaRole of lockingPersonas) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          if (!testScenariosForPersona.includes(scenarioKey)) { test.skip(personaRole + " - not applicable for " + scenarioKey, () => {}); continue; }

          test(personaRole + " - Lock workflow: terms, disclosures, confirmation, countdown", async ({ page }) => {
            const helper = ui(page);
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const result = await helper.navigateTo("/quote/" + runState.runId + "/lock");
            expect(result.success).toBe(true);
            const lockResponse = await api.getLockWorkflow(ctx, runState.runId, runState.optionId);
            expect(lockResponse.status).toBe(200);
            expect(lockResponse.data).toBeDefined();
            expect(lockResponse.data.lockTerms).toBeDefined();
            expect(lockResponse.data.lockTerms.lockPeriodDays).toBeDefined();
            expect(lockResponse.data.lockTerms.lockPeriodDays).toBeGreaterThan(0);
            expect(lockResponse.data.lockTerms.expirationDate).toBeDefined();
            expect(lockResponse.data.lockTerms.rate).toBeDefined();
            expect(lockResponse.data.lockTerms.price).toBeDefined();
            expect(lockResponse.data.disclosures).toBeDefined();
            expect(Array.isArray(lockResponse.data.disclosures)).toBe(true);
            expect(lockResponse.data.disclosures.length).toBeGreaterThan(0);
            for (const disclosure of lockResponse.data.disclosures) {
              expect(disclosure.id).toBeDefined();
              expect(disclosure.title).toBeDefined();
              expect(disclosure.content).toBeDefined();
              expect(disclosure.required).toBeDefined();
            }
            expect(lockResponse.data.countdown).toBeDefined();
            expect(lockResponse.data.countdown.secondsRemaining).toBeDefined();
            expect(lockResponse.data.countdown.secondsRemaining).toBeGreaterThan(0);
            expect(lockResponse.data.countdown.expiresAt).toBeDefined();
            const confirmResponse = await api.confirmLock(ctx, runState.runId, runState.optionId, true);
            expect(confirmResponse.status).toBe(200);
            expect(confirmResponse.data.lockConfirmed).toBe(true);
            expect(confirmResponse.data.lockConfirmationId).toBeDefined();
            expect(confirmResponse.data.lockExpiration).toBeDefined();
            const updatedLockResponse = await api.getLockWorkflow(ctx, runState.runId, runState.optionId);
            expect(updatedLockResponse.status).toBe(200);
            expect(updatedLockResponse.data.status).toBe("CONFIRMED");
            expect(updatedLockResponse.data.confirmationId).toBe(confirmResponse.data.lockConfirmationId);
            await helper.verifyLockWorkflow({ status: "CONFIRMED", hasCountdown: true, hasDisclosures: true });
          });

          test(personaRole + " - Lock confirmation without disclosures fails", async () => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const confirmResponse = await api.confirmLock(ctx, runState.runId, runState.optionId, false);
            expect(confirmResponse.status).toBe(400);
            expect(confirmResponse.data.error).toContain("disclosures");
          });

          test(personaRole + " - Lock countdown decrements", async () => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const lockResponse1 = await api.getLockWorkflow(ctx, runState.runId, runState.optionId);
            expect(lockResponse1.status).toBe(200);
            const initialCountdown = lockResponse1.data.countdown.secondsRemaining;
            await new Promise(resolve => setTimeout(resolve, 2000));
            const lockResponse2 = await api.getLockWorkflow(ctx, runState.runId, runState.optionId);
            expect(lockResponse2.status).toBe(200);
            const updatedCountdown = lockResponse2.data.countdown.secondsRemaining;
            expect(updatedCountdown).toBeLessThan(initialCountdown);
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
      const expectedOutcome = expectedPricingOutcomes[scenarioKey];
      describe("Scenario: " + scenario.scenarioName, () => {
        for (const personaRole of PERSONA_ROLES) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          if (!testScenariosForPersona.includes(scenarioKey)) { test.skip(personaRole + " - not applicable for " + scenarioKey, () => {}); continue; }
          const canViewEligibility = ["RETAIL_LO", "WHOLESALE_LO", "CORRESPONDENT_LO", "PRICING_ANALYST", "COMPLIANCE_OFFICER"].includes(personaRole);
          if (!canViewEligibility) { test.skip(personaRole + " - cannot view eligibility", () => {}); continue; }

          test(personaRole + " - Eligibility: decision, blockers, required facts", async ({ page }) => {
            const helper = ui(page);
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const result = await helper.navigateTo("/quote/" + runState.runId + "/eligibility");
            expect(result.success).toBe(true);
            const eligibilityResponse = await api.getEligibility(ctx, runState.runId, runState.optionId);
            expect(eligibilityResponse.status).toBe(200);
            expect(eligibilityResponse.data).toBeDefined();
            expect(eligibilityResponse.data.decision).toBeDefined();
            expect(["ELIGIBLE", "INELIGIBLE", "CONDITIONAL"]).toContain(eligibilityResponse.data.decision);
            if (eligibilityResponse.data.blockers) {
              expect(Array.isArray(eligibilityResponse.data.blockers)).toBe(true);
              for (const blocker of eligibilityResponse.data.blockers) {
                expect(blocker.code).toBeDefined();
                expect(blocker.message).toBeDefined();
                expect(blocker.severity).toBeDefined();
                expect(["ERROR", "WARNING", "INFO"]).toContain(blocker.severity);
              }
            }
            expect(eligibilityResponse.data.requiredFacts).toBeDefined();
            expect(Array.isArray(eligibilityResponse.data.requiredFacts)).toBe(true);
            expect(eligibilityResponse.data.requiredFacts.length).toBeGreaterThan(0);
            for (const fact of eligibilityResponse.data.requiredFacts) {
              expect(fact.factId).toBeDefined();
              expect(fact.description).toBeDefined();
              expect(fact.value).toBeDefined();
              expect(fact.source).toBeDefined();
            }
            expect(eligibilityResponse.data.productEligibility).toBeDefined();
            expect(Array.isArray(eligibilityResponse.data.productEligibility)).toBe(true);
            for (const productElig of eligibilityResponse.data.productEligibility) {
              expect(productElig.product).toBeDefined();
              expect(productElig.eligible).toBeDefined();
              expect(productElig.reason).toBeDefined();
              if (expectedOutcome.eligibleProducts.includes(productElig.product)) { expect(productElig.eligible).toBe(true); }
              if (expectedOutcome.ineligibleProducts.includes(productElig.product)) { expect(productElig.eligible).toBe(false); }
            }
            const findings = await driftDetector.compareEligibility(scenarioKey, personaRole, { scenario: scenarioKey, persona: personaRole }, eligibilityResponse.data.decision, eligibilityResponse.data.blockers?.map((b) => b.code) || []);
            if (findings.length > 0) {
              const report = driftDetector.generateReport(findings, "1.0.0", "current");
              console.log("Eligibility drift for " + scenarioKey + " (" + personaRole + "):", JSON.stringify(report, null, 2));
            }
            const expectedDecision = scenarioKey === "subPrimeCashOut" ? "CONDITIONAL" : "ELIGIBLE";
            await helper.verifyEligibility({ decision: expectedDecision, hasBlockers: scenarioKey === "subPrimeCashOut", minBlockers: scenarioKey === "subPrimeCashOut" ? 1 : 0 });
          });

          test(personaRole + " - Eligibility explanation for specific product", async () => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No selected offer available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            for (const product of expectedOutcome.eligibleProducts) {
              const eligibilityResponse = await api.getEligibility(ctx, runState.runId, runState.optionId);
              expect(eligibilityResponse.status).toBe(200);
              const productElig = eligibilityResponse.data.productEligibility?.find((p) => p.product === product);
              expect(productElig).toBeDefined();
              expect(productElig.eligible).toBe(true);
            }
          });
        }
      });
    }
  });

  // ========================================================================
  // PERSONA-SPECIFIC ACCESS TESTS
  // ========================================================================
  describe("Persona Access Control", () => {
    for (const personaRole of PERSONA_ROLES) {
      const persona = getPersona(personaRole);
      test(personaRole + " - Accessible routes work, restricted routes blocked", async ({ page }) => {
        const helper = ui(page);
        const results = await helper.testPersonaAccess(personaRole);
        console.log(personaRole + " access results:", JSON.stringify(results, null, 2));
        expect(results.accessible.length).toBeGreaterThan(0);
      });
    }
  });

  // ========================================================================
  // PRICING OUTCOME VERIFICATION AGAINST TEST DATA
  // ========================================================================
  describe("Pricing Outcome Verification", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      const expectedOutcome = expectedPricingOutcomes[scenarioKey];
      describe("Scenario: " + scenario.scenarioName, () => {
        for (const personaRole of PERSONA_ROLES) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          if (!testScenariosForPersona.includes(scenarioKey)) { test.skip(personaRole + " - not applicable for " + scenarioKey, () => {}); continue; }

          test(personaRole + " - Pricing outcomes within expected ranges", async () => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState || !runState.optionId) { test.skip("No run state available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.optionId = runState.optionId;
            ctx.traceId = runState.traceId;
            const detailResponse = await api.getQuoteDetail(ctx, runState.runId, runState.optionId);
            expect(detailResponse.status).toBe(200);
            const finalPrice = detailResponse.data.finalPrice;
            expect(finalPrice).toBeDefined();
            expect(finalPrice.rate).toBeGreaterThanOrEqual(expectedOutcome.minRate);
            expect(finalPrice.rate).toBeLessThanOrEqual(expectedOutcome.maxRate);
            expect(finalPrice.price).toBeGreaterThanOrEqual(expectedOutcome.minPrice);
            expect(finalPrice.price).toBeLessThanOrEqual(expectedOutcome.maxPrice);
            const offersResponse = await api.getOffers(ctx, runState.runId);
            expect(offersResponse.status).toBe(200);
            const eligibleProducts = new Set(offersResponse.data.offers.filter((o) => o.eligibilityStatus === "ELIGIBLE").map((o) => o.product));
            for (const product of expectedOutcome.eligibleProducts) { expect(eligibleProducts.has(product)).toBe(true); }
            const ineligibleProducts = new Set(offersResponse.data.offers.filter((o) => o.eligibilityStatus !== "ELIGIBLE").map((o) => o.product));
            for (const product of expectedOutcome.ineligibleProducts) {
              if (ineligibleProducts.has(product)) {
                const productOffers = offersResponse.data.offers.filter((o) => o.product === product);
                for (const offer of productOffers) { expect(offer.eligibilityStatus).not.toBe("ELIGIBLE"); }
              }
            }
            const expectedBehavior = getExpectedPricingBehavior(personaRole);
            expect(finalPrice.rate).toBeGreaterThanOrEqual(expectedBehavior.expectedRateRange.min);
            expect(finalPrice.rate).toBeLessThanOrEqual(expectedBehavior.expectedRateRange.max);
          });
        }
      });
    }
  });

  // ========================================================================
  // DRIFT DETECTION TESTS
  // ========================================================================
  describe("Drift Detection", () => {
    for (const scenarioKey of SCENARIO_KEYS) {
      const scenario = testScenarios[scenarioKey];
      describe("Scenario: " + scenario.scenarioName, () => {
        for (const personaRole of PERSONA_ROLES) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          if (!testScenariosForPersona.includes(scenarioKey)) { test.skip(personaRole + " - not applicable for " + scenarioKey, () => {}); continue; }

          test(personaRole + " - API response drift detection", async () => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState) { test.skip("No run state available"); return; }
            const ctx = api.createContext(personaRole);
            ctx.runId = runState.runId;
            ctx.traceId = runState.traceId;
            const endpoints = [
              { endpoint: "/api/v1/tenants/" + ctx.tenantId + "/quote-runs/" + runState.runId + "/offers", fn: () => api.getOffers(ctx, runState.runId) },
              { endpoint: "/api/v1/tenants/" + ctx.tenantId + "/quote-runs/" + runState.runId + "/offers/" + runState.optionId + "/detail", fn: () => api.getQuoteDetail(ctx, runState.runId, runState.optionId) },
              { endpoint: "/api/v1/tenants/" + ctx.tenantId + "/quote-runs/" + runState.runId + "/eligibility", fn: () => api.getEligibility(ctx, runState.runId, runState.optionId) },
            ];
            for (const { endpoint, fn } of endpoints) {
              const startTime = Date.now();
              const response = await fn();
              const responseTime = Date.now() - startTime;
              expect(response.status).toBe(200);
              const findings = await driftDetector.compareApiResponse(scenarioKey, personaRole, endpoint, response.data, response.status, responseTime);
              if (findings.length > 0) {
                const report = driftDetector.generateReport(findings, "1.0.0", "current");
                console.log("API drift for " + endpoint + " (" + scenarioKey + ", " + personaRole + "):", JSON.stringify(report, null, 2));
                const criticalFindings = findings.filter(f => f.severity === "CRITICAL");
                if (criticalFindings.length > 0) { throw new Error("Critical API drift detected: " + criticalFindings.map(f => f.description).join(", ")); }
              }
            }
          });

          test(personaRole + " - UI state drift detection", async ({ page }) => {
            const runState = testRunState.get(personaRole + "-" + scenarioKey);
            if (!runState) { test.skip("No run state available"); return; }
            const helper = ui(page);
            const routes = ["/quote/" + runState.runId + "/offers", "/quote/" + runState.runId + "/offers/" + runState.optionId, "/quote/" + runState.runId + "/lock", "/quote/" + runState.runId + "/eligibility"];
            for (const route of routes) {
              const startTime = Date.now();
              const result = await helper.navigateTo(route);
              const loadTime = Date.now() - startTime;
              expect(result.success).toBe(true);
              expect(loadTime).toBeLessThan(10000);
              await helper.verifyNoConsoleErrors();
            }
          });
        }
      });
    }
  });

  // ========================================================================
  // HEADED MODE DEMO TESTS
  // ========================================================================
  describe("Demo Mode - Headed Tests @demo", () => {
    test.use({ project: "demo-headed" });
    for (const scenarioKey of SCENARIO_KEYS.slice(0, 2)) {
      const scenario = testScenarios[scenarioKey];
      describe("Demo: " + scenario.scenarioName, () => {
        for (const personaRole of ["RETAIL_LO", "WHOLESALE_LO"] as const) {
          const persona = getPersona(personaRole);
          const testScenariosForPersona = getTestScenarios(personaRole);
          if (!testScenariosForPersona.includes(scenarioKey)) { test.skip(personaRole + " - not applicable for " + scenarioKey, () => {}); continue; }

          test(personaRole + " - Full quote lifecycle demo", async ({ page }) => {
            const helper = ui(page);
            const ctx = api.createContext(personaRole);
            const traceId = "demo-" + personaRole + "-" + scenarioKey + "-" + Date.now();
            ctx.traceId = traceId;
            await test.step("Demo: Start Quote", async () => { await helper.navigateTo("/quote/start"); await page.waitForTimeout(1000); await helper.takeScreenshot("demo-" + personaRole + "-" + scenarioKey + "-01-start"); });
            await test.step("Demo: Fill Intake", async () => {
              const intakeData = buildIntakeData(scenario);
              const launchResponse = await api.launchQuoteRun(ctx, intakeData);
              expect(launchResponse.status).toBe(201);
              const runId = launchResponse.data.runId;
              let status = "PROCESSING"; let attempts = 0;
              while (status === "PROCESSING" && attempts < 30) { await new Promise(resolve => setTimeout(resolve, 2000)); const statusResponse = await api.getQuoteRunStatus(ctx, runId); status = statusResponse.data.status; attempts++; }
              expect(status).toBe("COMPLETED");
              await helper.takeScreenshot("demo-" + personaRole + "-" + scenarioKey + "-02-intake-complete");
            });
            await test.step("Demo: View Offers", async () => {
              const runState = testRunState.get(personaRole + "-" + scenarioKey);
              if (runState) { await helper.navigateTo("/quote/" + runState.runId + "/offers"); await page.waitForTimeout(1500); await helper.takeScreenshot("demo-" + personaRole + "-" + scenarioKey + "-03-offers"); }
            });
            await test.step("Demo: View Quote Detail", async () => {
              const runState = testRunState.get(personaRole + "-" + scenarioKey);
              if (runState && runState.optionId) { await helper.navigateTo("/quote/" + runState.runId + "/offers/" + runState.optionId); await page.waitForTimeout(1500); await helper.takeScreenshot("demo-" + personaRole + "-" + scenarioKey + "-04-detail"); }
            });
            await test.step("Demo: View Lock Workflow", async () => {
              const runState = testRunState.get(personaRole + "-" + scenarioKey);
              if (runState && ["RETAIL_LO", "WHOLESALE_LO", "LOCK_DESK", "CORRESPONDENT_LO"].includes(personaRole)) { await helper.navigateTo("/quote/" + runState.runId + "/lock"); await page.waitForTimeout(1500); await helper.takeScreenshot("demo-" + personaRole + "-" + scenarioKey + "-05-lock"); }
            });
            await test.step("Demo: View Eligibility", async () => {
              const runState = testRunState.get(personaRole + "-" + scenarioKey);
              if (runState && ["RETAIL_LO", "WHOLESALE_LO", "CORRESPONDENT_LO", "PRICING_ANALYST", "COMPLIANCE_OFFICER"].includes(personaRole)) { await helper.navigateTo("/quote/" + runState.runId + "/eligibility"); await page.waitForTimeout(1500); await helper.takeScreenshot("demo-" + personaRole + "-" + scenarioKey + "-06-eligibility"); }
            });
          });
        }
      });
    }
  });

  // ========================================================================
  // INTEGRATION: FULL LIFECYCLE VERIFICATION
  // ========================================================================
  describe("Full Lifecycle Integration", () => {
    test("All scenarios complete intake for at least one persona", () => {
      for (const scenarioKey of SCENARIO_KEYS) {
        let hasRunState = false;
        for (const personaRole of PERSONA_ROLES) {
          if (testRunState.has(personaRole + "-" + scenarioKey)) { hasRunState = true; break; }
        }
        expect(hasRunState).toBe(true);
      }
    });

    test("All personas have valid pricing outcomes for their scenarios", () => {
      for (const personaRole of PERSONA_ROLES) {
        const testScenariosForPersona = getTestScenarios(personaRole);
        for (const scenarioKey of testScenariosForPersona) {
          const runState = testRunState.get(personaRole + "-" + scenarioKey);
          if (runState) { expect(runState.runId).toBeDefined(); expect(runState.traceId).toBeDefined(); }
        }
      }
    });
  });
});

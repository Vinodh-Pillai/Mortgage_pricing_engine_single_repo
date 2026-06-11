import { chromium } from 'playwright';
import { apiHelper } from '../helpers/api-helper.js';
import { uiHelper } from '../helpers/ui-helper.js';
import { personas, PersonaRole } from '../personas/personas.js';
import { testScenarios, testBorrowers, testLoans, testProperties } from '../fixtures/test-data.js';
import { DriftDetector } from './drift-detector.js';
import fs from 'fs';
import path from 'path';

const BASELINES_DIR = './tests/baselines';
const RESULTS_DIR = './tests/results/drift';
const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';
const API_BASE = process.env.VITE_API_BASE || 'http://localhost:8080';

async function runDriftCheck() {
  console.log('[Drift Checker] Starting drift detection...');
  
  if (!fs.existsSync(BASELINES_DIR)) {
    console.error('[Drift Checker] No baselines found. Run baseline capture first.');
    process.exit(1);
  }

  const detector = new DriftDetector();
  loadBaselines(detector);
  
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  const allFindings: any[] = [];

  try {
    apiHelper.init();
    
    for (const [personaRole, persona] of Object.entries(personas)) {
      console.log(`\n[Drift Check] Checking persona: ${personaRole}`);
      
      const testCtx = apiHelper.createContext(personaRole as PersonaRole);
      
      // Check API endpoints
      const apiFindings = await checkApiEndpoints(detector, testCtx, personaRole);
      allFindings.push(...apiFindings);
      
      // Check key routes
      for (const route of persona.accessibleRoutes.slice(0, 2)) {
        const uiFindings = await checkRoute(detector, page, uiHelper(page), testCtx, personaRole, route);
        allFindings.push(...uiFindings);
      }
      
      // Check pricing outcomes for test scenarios
      for (const scenarioName of persona.testScenarios) {
        const pricingFindings = await checkPricingOutcome(detector, testCtx, personaRole, scenarioName);
        allFindings.push(...pricingFindings);
      }
    }

    // Generate report
    const report = detector.generateReport(allFindings, '1.0.0', 'current');
    await saveReport(report);
    
    // Print summary
    printSummary(report);
    
    // Exit with error code if critical drift detected
    if (report.overallSeverity === 'CRITICAL') {
      console.log('\n[Drift Check] CRITICAL drift detected! Exiting with error.');
      process.exit(1);
    } else if (report.overallSeverity === 'WARNING') {
      console.log('\n[Drift Check] WARNING drift detected.');
      process.exit(0);
    } else {
      console.log('\n[Drift Check] No significant drift detected.');
      process.exit(0);
    }
    
  } catch (error) {
    console.error('[Drift Check] Error:', error);
    process.exit(1);
  } finally {
    await context.close();
    await browser.close();
    await apiHelper.dispose();
  }
}

function loadBaselines(detector: DriftDetector) {
  const files = fs.readdirSync(BASELINES_DIR).filter(f => f.endsWith('.json') && f !== 'manifest.json');
  
  for (const file of files) {
    const content = fs.readFileSync(path.join(BASELINES_DIR, file), 'utf-8');
    const baseline = JSON.parse(content);
    detector.loadBaseline(baseline);
  }
  
  console.log(`[Drift Check] Loaded ${files.length} baselines`);
}

async function checkApiEndpoints(detector: DriftDetector, testCtx: any, personaRole: string) {
  const findings: any[] = [];
  const endpoints = [
    { path: `/api/v1/tenants/${testCtx.tenantId}/quote-runs/intake-metadata`, name: 'intake-metadata' },
    { path: '/api/v1/ops/rate-feeds', name: 'rate-feeds' },
    { path: '/api/v1/ops/performance', name: 'performance' },
    { path: '/api/v1/ops/cases', name: 'ops-cases' },
    { path: '/api/v1/adjustments/evidence', name: 'adjustments', params: { tenantContext: testCtx.tenantId } },
    { path: '/api/v1/margins/profitability', name: 'margins', params: { tenantContext: testCtx.tenantId } },
    { path: '/api/v1/exceptions/concessions/workbench', name: 'exceptions', params: { tenantContext: testCtx.tenantId } },
    { path: '/api/v1/admin/governance', name: 'governance' },
    { path: '/api/v1/products/catalog/manager', name: 'catalog' },
    { path: '/api/v1/compliance/evidence', name: 'compliance' },
    { path: '/api/v1/ml-advisory/insights', name: 'ml-advisory' },
  ];

  for (const endpoint of endpoints) {
    try {
      const startTime = Date.now();
      const response = await apiHelper.get(testCtx, endpoint.path, endpoint.params);
      const responseTime = Date.now() - startTime;

      const apiFindings = await detector.compareApiResponse(
        `api-${endpoint.name}`,
        personaRole,
        endpoint.path,
        response.data,
        response.status,
        responseTime
      );
      
      findings.push(...apiFindings);
      
      if (apiFindings.length > 0) {
        console.log(`  [DRIFT] ${endpoint.name}: ${apiFindings.length} findings`);
      }
      
    } catch (error) {
      console.log(`  [ERROR] ${endpoint.name} check failed:`, error);
    }
  }

  return findings;
}

async function checkRoute(detector: DriftDetector, page: any, ui: any, testCtx: any, personaRole: string, route: string) {
  const findings: any[] = [];
  
  try {
    const startTime = Date.now();
    const result = await ui.navigateTo(route, { waitForLoad: true, timeout: 30000 });
    const loadTime = Date.now() - startTime;
    
    if (!result.success) {
      findings.push({
        category: 'ui',
        severity: 'CRITICAL',
        description: `Route ${route} failed to load`,
        expected: 'success',
        actual: `failed: ${result.errors.join(', ')}`,
        baselineVersion: '1.0.0',
        currentVersion: 'current',
        recommendation: 'Check route configuration and backend availability',
      });
      return findings;
    }

    // Verify key elements exist
    const expectedElements = [
      '[role="main"]',
      '[role="navigation"]',
      '[role="banner"]',
      'h1',
    ];

    for (const selector of expectedElements) {
      const element = page.locator(selector);
      if (await element.count() === 0) {
        findings.push({
          category: 'ui',
          severity: 'WARNING',
          description: `Missing expected element: ${selector} on ${route}`,
          expected: 'element present',
          actual: 'element missing',
          baselineVersion: '1.0.0',
          currentVersion: 'current',
          recommendation: 'Check UI component rendering',
        });
      }
    }

    // Check load time
    if (loadTime > 10000) {
      findings.push({
        category: 'latency',
        severity: 'WARNING',
        description: `Route ${route} load time exceeded budget`,
        expected: '<= 10000ms',
        actual: `${loadTime}ms`,
        baselineVersion: '1.0.0',
        currentVersion: 'current',
        recommendation: 'Optimize route loading performance',
      });
    }
    
  } catch (error) {
    findings.push({
      category: 'ui',
      severity: 'CRITICAL',
      description: `Route check failed for ${route}`,
      expected: 'success',
      actual: error instanceof Error ? error.message : String(error),
      baselineVersion: '1.0.0',
      currentVersion: 'current',
      recommendation: 'Investigate route error',
    });
  }

  return findings;
}

async function checkPricingOutcome(detector: DriftDetector, testCtx: any, personaRole: string, scenarioName: string) {
  const findings: any[] = [];
  
  try {
    const scenario = testScenarios[scenarioName];
    if (!scenario) return findings;

    // Launch quote run
    const intakeData = buildIntakeData(scenario);
    const launchResponse = await apiHelper.launchQuoteRun(testCtx, intakeData);
    
    if (launchResponse.status !== 200 && launchResponse.status !== 201) {
      console.log(`  [WARN] Quote launch failed for ${scenarioName}: ${launchResponse.status}`);
      return findings;
    }

    const runId = launchResponse.data?.runId;
    if (!runId) return findings;

    // Wait for offers to be ready
    await waitForOffers(testCtx, runId);

    // Get offers
    const offersResponse = await apiHelper.getOffers(testCtx, runId);
    if (offersResponse.status !== 200 || !offersResponse.data?.offers) return findings;

    const offers = offersResponse.data.offers;
    if (offers.length === 0) return findings;

    // Analyze first offer
    const firstOffer = offers[0];
    const actualOutcome = {
      rate: parseFloat(firstOffer.rate) || 0,
      price: parseFloat(firstOffer.price) || 0,
      payment: parseFloat(firstOffer.payment) || 0,
      apr: parseFloat(firstOffer.apr) || 0,
      eligibleProducts: offers.map((o: any) => o.productLabel).filter(Boolean),
      ineligibleProducts: [],
      adjustments: [],
    };

    const pricingFindings = await detector.comparePricingOutcome(
      scenarioName,
      personaRole,
      intakeData,
      actualOutcome
    );
    
    findings.push(...pricingFindings);
    
  } catch (error) {
    console.log(`  [ERROR] Pricing check failed for ${scenarioName}:`, error);
  }

  return findings;
}

function buildIntakeData(scenario: any) {
  return {
    quoteIntent: scenario.loan.quoteIntent,
    channel: scenario.loan.channel,
    scenarioName: scenario.scenarioName,
    externalLoanId: scenario.externalLoanId,
    sourceSystem: 'E2E_TEST',
    borrowerName: scenario.borrower.borrowerName,
    borrowerRole: scenario.borrower.borrowerRole,
    coBorrowerName: scenario.coBorrower?.borrowerName || '',
    coBorrowerRole: scenario.coBorrower?.borrowerRole || '',
    contactEmail: scenario.borrower.contactEmail,
    creditStatus: 'AVAILABLE',
    creditScore: scenario.borrower.creditScore.toString(),
    creditScoreSource: scenario.borrower.creditScoreSource,
    creditReportDate: scenario.borrower.creditReportDate,
    creditReadiness: 'VERIFIED',
    loanPurpose: scenario.loan.loanPurpose,
    loanAmount: scenario.loan.loanAmount.toString(),
    purchasePriceOrValue: scenario.loan.purchasePriceOrValue.toString(),
    downPaymentOrEquity: scenario.loan.downPaymentOrEquity.toString(),
    subordinateFinancingAmount: '0',
    helocDrawnAmount: '0',
    helocLimitAmount: '0',
    lienPosition: scenario.loan.lienPosition,
    termMonths: scenario.loan.termMonths.toString(),
    amortizationType: scenario.loan.amortizationType,
    requestedLockPeriodDays: scenario.loan.requestedLockPeriodDays.toString(),
    propertyState: scenario.property.propertyState,
    propertyCounty: scenario.property.propertyCounty,
    propertyZip: scenario.property.propertyZip,
    propertyType: scenario.property.propertyType,
    occupancyType: scenario.property.occupancyType,
    unitCount: scenario.property.unitCount.toString(),
    purchasePrice: scenario.property.purchasePrice.toString(),
    appraisedValue: scenario.property.appraisedValue?.toString() || '',
    condoProjectType: '',
    manufacturedHomeFlag: 'false',
    monthlyIncome: scenario.borrower.monthlyIncome.toString(),
    incomeType: scenario.borrower.incomeType,
    employmentType: scenario.borrower.employmentType,
    monthlyDebt: scenario.borrower.monthlyDebt.toString(),
    suppliedDti: '',
    reserveMonths: '',
    incomeVerificationStatus: 'VERIFIED',
    assetVerificationStatus: 'VERIFIED',
    liquidAssets: scenario.borrower.liquidAssets.toString(),
    reserves: scenario.borrower.reserves.toString(),
    productFamily: scenario.productPreference || '',
    productPreference: '',
    quoteFilters: '',
    effectiveDate: scenario.effectiveDate || new Date().toISOString().split('T')[0],
    actorId: 'e2e-test',
    clientContext: 'e2e-test',
  };
}

async function waitForOffers(testCtx: any, runId: string, maxAttempts = 20) {
  for (let i = 0; i < maxAttempts; i++) {
    const response = await apiHelper.getOffers(testCtx, runId);
    if (response.status === 200 && response.data?.offers?.length > 0) {
      return;
    }
    await new Promise(resolve => setTimeout(resolve, 3000));
  }
}

async function saveReport(report: any) {
  if (!fs.existsSync(RESULTS_DIR)) {
    fs.mkdirSync(RESULTS_DIR, { recursive: true });
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const filepath = path.join(RESULTS_DIR, `drift-report-${timestamp}.json`);
  fs.writeFileSync(filepath, JSON.stringify(report, null, 2));
  
  console.log(`\n[Drift Check] Report saved to ${filepath}`);
}

function printSummary(report: any) {
  console.log('\n=================================');
  console.log('DRIFT DETECTION SUMMARY');
  console.log('=================================');
  console.log(`Baseline Version: ${report.baselineVersion}`);
  console.log(`Current Version:  ${report.currentVersion}`);
  console.log(`Timestamp:        ${report.timestamp}`);
  console.log(`Drift Detected:   ${report.driftDetected ? 'YES' : 'NO'}`);
  console.log(`Overall Severity: ${report.overallSeverity}`);
  console.log('\nFindings:');
  console.log(`  Total:      ${report.summary.totalFindings}`);
  console.log(`  Critical:   ${report.summary.criticalCount}`);
  console.log(`  Warning:    ${report.summary.warningCount}`);
  console.log(`  Info:       ${report.summary.infoCount}`);
  
  if (report.findings.length > 0) {
    console.log('\nTop Findings:');
    report.findings
      .filter((f: any) => f.severity === 'CRITICAL' || f.severity === 'WARNING')
      .slice(0, 10)
      .forEach((f: any, i: number) => {
        console.log(`  ${i + 1}. [${f.severity}] ${f.category}: ${f.description}`);
        console.log(`     Expected: ${f.expected}`);
        console.log(`     Actual:   ${f.actual}`);
      });
  }
  console.log('=================================\n');
}

runDriftCheck().catch(console.error);